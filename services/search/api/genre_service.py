"""Genre browse service: pool cache, background refresh, Thompson feedback."""
from __future__ import annotations

import logging
import threading
from typing import Callable

from api.genre_pool_cache import GenrePoolCache
from metadata.genre_pool import build_genre_pool
from metadata.genre_tree import record_genre_branch_feedback
from metadata.tmdb_client import TmdbClient
from torrtux_core.filters import apply_filters
from torrtux_core.genres import MOVIE_GENRES
from torrtux_core.searcher import SearchOutcome, TorrentSearcher

logger = logging.getLogger(__name__)

# Build a wide pool for cache; per-request filters applied at serve time.
_POOL_BUILD_LIMIT = 150

def record_genre_feedback(genre_id: str, group_key: str, success: bool) -> None:
    record_genre_branch_feedback(genre_id, group_key, success)


def _serve_cached_rows(
    rows: list[dict],
    *,
    min_seeds: int | None,
    max_seeds: int | None,
    max_size: str | None,
    limit: int | None,
) -> list[dict]:
    """Re-apply request filters to cached pool rows (cache stores unfiltered merge)."""
    return apply_filters(
        rows,
        min_seeds=min_seeds,
        max_seeds=max_seeds,
        max_size=max_size,
        limit=limit,
    )


class GenreBrowseService:
    def __init__(
        self,
        searcher: TorrentSearcher,
        cache: GenrePoolCache,
        tmdb_resolver: Callable[[str | None], tuple[TmdbClient, bool]],
    ) -> None:
        self._searcher = searcher
        self._cache = cache
        self._tmdb_resolver = tmdb_resolver

    def warm_genres(self, genre_ids: list[str], *, movie_profile: bool = True) -> None:
        for genre_id in genre_ids:
            normalized = genre_id.strip().lower()
            if normalized not in MOVIE_GENRES:
                continue
            self._schedule_refresh(normalized, movie_profile=movie_profile, partial_ok=True)

    def browse(
        self,
        genre_id: str,
        *,
        movie_profile: bool = True,
        min_seeds: int | None = None,
        max_seeds: int | None = None,
        max_size: str | None = None,
        limit: int | None = None,
        tmdb_api_key: str | None = None,
        enrich: bool = True,
        force_refresh: bool = False,
    ) -> SearchOutcome:
        normalized = genre_id.strip().lower()
        if not force_refresh:
            cached = self._cache.get(normalized, movie_profile=movie_profile)
            if cached and cached.rows and not cached.partial:
                if not cached.is_fresh():
                    self._schedule_refresh(
                        normalized,
                        movie_profile=movie_profile,
                        min_seeds=min_seeds,
                        max_seeds=max_seeds,
                        max_size=max_size,
                        limit=limit,
                        tmdb_api_key=tmdb_api_key,
                        enrich=enrich,
                    )
                filtered = _serve_cached_rows(
                    cached.rows,
                    min_seeds=min_seeds,
                    max_seeds=max_seeds,
                    max_size=max_size,
                    limit=limit,
                )
                return SearchOutcome(
                    results=filtered,
                    failed_sites=list(cached.failed_sites),
                )

        return self._refresh_sync(
            normalized,
            movie_profile=movie_profile,
            min_seeds=min_seeds,
            max_seeds=max_seeds,
            max_size=max_size,
            limit=limit,
            tmdb_api_key=tmdb_api_key,
            enrich=enrich,
            partial_ok=False,
        )

    def _schedule_refresh(
        self,
        genre_id: str,
        *,
        movie_profile: bool,
        min_seeds: int | None = None,
        max_seeds: int | None = None,
        max_size: str | None = None,
        limit: int | None = None,
        tmdb_api_key: str | None = None,
        enrich: bool = True,
        partial_ok: bool = True,
    ) -> None:
        if not self._cache.mark_refreshing(genre_id, movie_profile=movie_profile):
            return

        def run() -> None:
            try:
                self._refresh_sync(
                    genre_id,
                    movie_profile=movie_profile,
                    min_seeds=min_seeds,
                    max_seeds=max_seeds,
                    max_size=max_size,
                    limit=limit,
                    tmdb_api_key=tmdb_api_key,
                    enrich=enrich,
                    partial_ok=partial_ok,
                )
            finally:
                self._cache.clear_refreshing(genre_id, movie_profile=movie_profile)

        threading.Thread(target=run, daemon=True, name=f"genre-pool-{genre_id}").start()

    def _refresh_sync(
        self,
        genre_id: str,
        *,
        movie_profile: bool,
        min_seeds: int | None,
        max_seeds: int | None,
        max_size: str | None,
        limit: int | None,
        tmdb_api_key: str | None,
        enrich: bool,
        partial_ok: bool,
    ) -> SearchOutcome:
        tmdb_client, _ = self._tmdb_resolver(tmdb_api_key)
        use_tmdb = enrich and tmdb_client.configured and tmdb_client.validate_key()
        outcome = build_genre_pool(
            self._searcher,
            genre_id,
            tmdb_client if use_tmdb else None,
            movie_profile=movie_profile,
            limit=_POOL_BUILD_LIMIT,
        )
        if outcome.results:
            self._cache.put(
                genre_id,
                outcome.results,
                outcome.failed_sites,
                partial=partial_ok and len(outcome.results) < _POOL_BUILD_LIMIT,
                movie_profile=movie_profile,
            )
        filtered = _serve_cached_rows(
            outcome.results,
            min_seeds=min_seeds,
            max_seeds=max_seeds,
            max_size=max_size,
            limit=limit,
        )
        return SearchOutcome(
            results=filtered,
            failed_sites=outcome.failed_sites,
            all_sources_failed=outcome.all_sources_failed,
            indexers_unavailable=outcome.indexers_unavailable,
            movie_indexers_unavailable=outcome.movie_indexers_unavailable,
        )
