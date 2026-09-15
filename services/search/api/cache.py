"""In-memory search result cache for magnet lazy-fetch."""
from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Any
from uuid import UUID, uuid4

DEFAULT_MAX_ENTRIES = 500
DEFAULT_TTL_SECONDS = 3600


@dataclass
class CacheEntry:
    payload: dict[str, Any]
    created_at: float = field(default_factory=time.time)


class ResultCache:
    def __init__(self, max_entries: int = DEFAULT_MAX_ENTRIES, ttl_seconds: int = DEFAULT_TTL_SECONDS):
        self.max_entries = max_entries
        self.ttl_seconds = ttl_seconds
        self._store: dict[UUID, CacheEntry] = {}

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

    def put_many(self, rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
        self._evict_expired()
        stored = []
        for row in rows:
            result_id = uuid4()
            payload = dict(row)
            payload["id"] = str(result_id)
            self._store[result_id] = CacheEntry(payload=payload)
            stored.append(payload)
        self._evict_overflow()
        return stored

    def get(self, result_id: UUID) -> dict[str, Any] | None:
        self._evict_expired()
        entry = self._store.get(result_id)
        if not entry:
            return None
        if time.time() - entry.created_at > self.ttl_seconds:
            del self._store[result_id]
            return None
        return entry.payload

    def resolve_magnet(self, result_id: UUID, magnet: str) -> dict[str, Any] | None:
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
        return payload
