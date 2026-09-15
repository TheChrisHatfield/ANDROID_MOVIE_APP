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
_FAILED_TTL_SECONDS = 300


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
        self._key_valid: bool | None = None

    @property
    def configured(self) -> bool:
        return bool(self.api_key)

    def validate_key(self) -> bool:
        """Probe TMDB once; cache whether this API key is accepted."""
        if not self.configured:
            self._key_valid = False
            return False
        if self._key_valid is not None:
            return self._key_valid
        try:
            resp = requests.get(
                f"{_TMDB_BASE}/configuration",
                params={"api_key": self.api_key},
                timeout=self.timeout,
            )
            self._key_valid = resp.status_code == 200
        except Exception as exc:
            logger.warning("TMDB key validation failed: %s", exc)
            self._key_valid = False
        return self._key_valid

    def lookup(self, title: str, year: int | None = None) -> TmdbMovieInfo | None:
        if not self.configured or not title.strip():
            return None
        cache_key = f"{title.lower()}|{year or ''}"
        with self._lock:
            cached = self._cache.get(cache_key)
            if cached:
                ttl = _CACHE_TTL_SECONDS if cached[1] is not None else _FAILED_TTL_SECONDS
                if time.time() - cached[0] < ttl:
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
            movie = self._pick_best_movie(results, year)
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

    def _pick_best_movie(self, results: list[dict], year: int | None) -> dict:
        if not year:
            return results[0]
        best = results[0]
        best_delta = 9999
        for candidate in results:
            release_date = str(candidate.get("release_date") or "")
            candidate_year = None
            if len(release_date) >= 4:
                try:
                    candidate_year = int(release_date[:4])
                except ValueError:
                    candidate_year = None
            if candidate_year is None:
                continue
            delta = abs(candidate_year - year)
            if delta < best_delta:
                best = candidate
                best_delta = delta
        return best

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
