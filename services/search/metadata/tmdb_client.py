"""TMDB metadata lookup (poster, overview, YouTube trailer). Optional — requires TMDB_API_KEY."""
from __future__ import annotations

import logging
import os
import threading
import time
from dataclasses import dataclass
from typing import Any

import requests

logger = logging.getLogger(__name__)

_TMDB_BASE = "https://api.themoviedb.org/3"
_CACHE_TTL_SECONDS = 3600


@dataclass
class TmdbMovieInfo:
    title: str
    year: int | None = None
    overview: str | None = None
    poster_url: str | None = None
    trailer_youtube_key: str | None = None


class TmdbClient:
    def __init__(self, api_key: str | None = None, timeout: float = 8.0):
        self.api_key = (api_key or os.environ.get("TMDB_API_KEY") or "").strip()
        self.timeout = timeout
        self._cache: dict[str, tuple[float, TmdbMovieInfo | None]] = {}
        self._lock = threading.Lock()

    @property
    def configured(self) -> bool:
        return bool(self.api_key)

    def lookup(self, title: str, year: int | None = None) -> TmdbMovieInfo | None:
        if not self.configured or not title.strip():
            return None
        cache_key = f"{title.lower()}|{year or ''}"
        with self._lock:
            cached = self._cache.get(cache_key)
            if cached and time.time() - cached[0] < _CACHE_TTL_SECONDS:
                return cached[1]

        info = self._fetch(title, year)
        with self._lock:
            self._cache[cache_key] = (time.time(), info)
        return info

    def _fetch(self, title: str, year: int | None) -> TmdbMovieInfo | None:
        try:
            params: dict[str, Any] = {"api_key": self.api_key, "query": title}
            if year:
                params["year"] = year
            search_resp = requests.get(
                f"{_TMDB_BASE}/search/movie",
                params=params,
                timeout=self.timeout,
            )
            search_resp.raise_for_status()
            results = search_resp.json().get("results") or []
            if not results:
                return None
            movie = results[0]
            movie_id = movie.get("id")
            if not movie_id:
                return None

            overview = (movie.get("overview") or "").strip() or None
            poster_path = movie.get("poster_path")
            poster_url = f"https://image.tmdb.org/t/p/w342{poster_path}" if poster_path else None
            release_date = movie.get("release_date") or ""
            movie_year = year
            if release_date and len(release_date) >= 4:
                try:
                    movie_year = int(release_date[:4])
                except ValueError:
                    pass

            trailer_key = self._fetch_trailer_key(movie_id)
            return TmdbMovieInfo(
                title=movie.get("title") or title,
                year=movie_year,
                overview=overview,
                poster_url=poster_url,
                trailer_youtube_key=trailer_key,
            )
        except Exception as exc:
            logger.warning("TMDB lookup failed for %s: %s", title, exc)
            return None

    def _fetch_trailer_key(self, movie_id: int) -> str | None:
        try:
            resp = requests.get(
                f"{_TMDB_BASE}/movie/{movie_id}/videos",
                params={"api_key": self.api_key},
                timeout=self.timeout,
            )
            resp.raise_for_status()
            for video in resp.json().get("results") or []:
                if video.get("site") == "YouTube" and video.get("type") == "Trailer":
                    key = video.get("key")
                    if key:
                        return str(key)
            return None
        except Exception:
            return None
