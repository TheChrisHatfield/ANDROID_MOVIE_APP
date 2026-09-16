"""In-memory search result cache for magnet lazy-fetch."""
from __future__ import annotations

import threading
import time
from dataclasses import dataclass, field
from typing import Any
from uuid import NAMESPACE_URL, UUID, uuid4, uuid5

DEFAULT_MAX_ENTRIES = 500
DEFAULT_TTL_SECONDS = 3600


@dataclass
class CacheEntry:
    payload: dict[str, Any]
    created_at: float = field(default_factory=time.time)


def _stable_row_key(row: dict[str, Any]) -> str | None:
    site = str(row.get("site") or "").strip().lower()
    detail = str(row.get("detail_url") or "").strip()
    name = str(row.get("name") or "").strip().lower()
    if detail and site:
        return f"{site}|{detail}"
    if name and site:
        return f"{site}|{name}"
    return None


def _result_id_for_row(row: dict[str, Any]) -> UUID:
    stable_key = _stable_row_key(row)
    if stable_key:
        return uuid5(NAMESPACE_URL, stable_key)
    return uuid4()


class ResultCache:
    def __init__(self, max_entries: int = DEFAULT_MAX_ENTRIES, ttl_seconds: int = DEFAULT_TTL_SECONDS):
        self.max_entries = max_entries
        self.ttl_seconds = ttl_seconds
        self._store: dict[UUID, CacheEntry] = {}
        self._lock = threading.Lock()

    def _evict_expired(self) -> None:
        now = time.time()
        expired = [key for key, entry in self._store.items() if now - entry.created_at > self.ttl_seconds]
        for key in expired:
            del self._store[key]

    def _evict_overflow(self) -> None:
        if len(self._store) <= self.max_entries:
            return
        ordered = sorted(self._store.items(), key=lambda item: item[1].created_at)
        for key, _ in ordered[: len(self._store) - self.max_entries]:
            del self._store[key]

    def _make_room(self) -> None:
        while len(self._store) >= self.max_entries:
            ordered = sorted(self._store.items(), key=lambda item: item[1].created_at)
            if not ordered:
                break
            del self._store[ordered[0][0]]

    def put_many(self, rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
        with self._lock:
            self._evict_expired()
            stored: list[dict[str, Any]] = []
            for row in rows:
                result_id = _result_id_for_row(row)
                payload = dict(row)
                payload["id"] = str(result_id)
                existing = self._store.get(result_id)
                if existing is not None:
                    old_magnet = existing.payload.get("magnet")
                    if not payload.get("magnet") and old_magnet:
                        payload["magnet"] = old_magnet
                    existing.payload = payload
                    existing.created_at = time.time()
                else:
                    self._make_room()
                    self._store[result_id] = CacheEntry(payload=payload)
                stored.append(dict(payload))
            return [row for row in stored if UUID(row["id"]) in self._store]

    def get(self, result_id: UUID) -> dict[str, Any] | None:
        with self._lock:
            self._evict_expired()
            entry = self._store.get(result_id)
            if not entry:
                return None
            if time.time() - entry.created_at > self.ttl_seconds:
                del self._store[result_id]
                return None
            entry.created_at = time.time()
            return dict(entry.payload)

    def clear(self) -> None:
        with self._lock:
            self._store.clear()

    def resolve_magnet(self, result_id: UUID, magnet: str) -> dict[str, Any] | None:
        with self._lock:
            self._evict_expired()
            cached = self._store.get(result_id)
            if not cached:
                return None
            if time.time() - cached.created_at > self.ttl_seconds:
                del self._store[result_id]
                return None
            payload = dict(cached.payload)
            payload["magnet"] = magnet
            cached.payload = payload
            cached.created_at = time.time()
            return dict(payload)
