"""In-memory genre browse pools — stale-while-revalidate (LOD-style prefetch cache)."""
from __future__ import annotations

import threading
import time
from dataclasses import dataclass, field

FRESH_TTL_SECONDS = 600
STALE_TTL_SECONDS = 1800


@dataclass
class GenrePoolEntry:
    genre_id: str
    rows: list[dict]
    failed_sites: list[str]
    created_at: float = field(default_factory=time.time)
    partial: bool = False
    refreshing: bool = False
    movie_profile: bool = True

    def age_seconds(self) -> float:
        return time.time() - self.created_at

    def is_fresh(self) -> bool:
        return self.age_seconds() < FRESH_TTL_SECONDS

    def is_usable(self) -> bool:
        return self.rows and self.age_seconds() < STALE_TTL_SECONDS


def _entry_key(genre_id: str, movie_profile: bool) -> str:
    return f"{genre_id.strip().lower()}|mp={int(movie_profile)}"


class GenrePoolCache:
    def __init__(self) -> None:
        self._entries: dict[str, GenrePoolEntry] = {}
        self._lock = threading.Lock()

    def get(self, genre_id: str, *, movie_profile: bool = True) -> GenrePoolEntry | None:
        key = _entry_key(genre_id, movie_profile)
        with self._lock:
            entry = self._entries.get(key)
            if entry and entry.is_usable():
                return entry
            return None

    def put(
        self,
        genre_id: str,
        rows: list[dict],
        failed_sites: list[str],
        *,
        partial: bool = False,
        movie_profile: bool = True,
    ) -> GenrePoolEntry:
        key = _entry_key(genre_id, movie_profile)
        entry = GenrePoolEntry(
            genre_id=genre_id.strip().lower(),
            rows=list(rows),
            failed_sites=list(failed_sites),
            partial=partial,
            refreshing=False,
            movie_profile=movie_profile,
        )
        with self._lock:
            self._entries[key] = entry
        return entry

    def mark_refreshing(self, genre_id: str, *, movie_profile: bool = True) -> bool:
        key = _entry_key(genre_id, movie_profile)
        with self._lock:
            entry = self._entries.get(key)
            if entry and entry.refreshing:
                return False
            if entry:
                entry.refreshing = True
            else:
                self._entries[key] = GenrePoolEntry(
                    genre_id=genre_id.strip().lower(),
                    rows=[],
                    failed_sites=[],
                    refreshing=True,
                    movie_profile=movie_profile,
                )
            return True

    def clear_refreshing(self, genre_id: str, *, movie_profile: bool = True) -> None:
        key = _entry_key(genre_id, movie_profile)
        with self._lock:
            entry = self._entries.get(key)
            if entry:
                entry.refreshing = False
