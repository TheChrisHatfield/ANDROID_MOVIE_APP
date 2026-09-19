"""Genre browse service: pool cache, background refresh, Thompson feedback."""
from __future__ import annotations

import logging
import threading
import time
from typing import Callable

from api.genre_pool_cache import GenrePoolCache, GenrePoolEntry
from metadata.genre_pool import fetch_genre_pool_rows, pool_rows_from_ranked, rank_genre_pool_rows
from metadata.genre_tree import record_genre_branch_feedback
from metadata.tmdb_client import TmdbClient
from torrtux_core.filters import apply_filters
from torrtux_core.genres import MOVIE_GENRES
from torrtux_core.searcher import SearchOutcome, TorrentSearcher

logger = logging.getLogger(__name__)

# Build a wide pool for cache; per-request filters applied at serve time.
_POOL_BUILD_LIMIT = 150
_REFRESH_WAIT_SECONDS = 45.0
_REFRESH_POLL_SECONDS = 0.05

def _discover_page_offset(genre_id: str, *, rotate: bool) -> int:
    """Rotate TMDB discover pages on refresh so genre shelves surface new titles."""
    if not rotate:
        return 1
    bucket = (hash(genre_id.strip().lower()) + int(time.time()) // 1800) % 3
    return bucket + 1


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
    """Re-apply request filters to ranked pool rows."""
    return apply_filters(
        rows,
        min_seeds=min_seeds,
        max_seeds=max_seeds,
        max_size=max_size,
        limit=limit,
    )


def _rank_and_serve_rows(
    rows: list[dict],
    genre_id: str,
    searcher: TorrentSearcher,
    *,
    movie_profile: bool,
    min_seeds: int | None,
    max_seeds: int | None,
    max_size: str | None,
    limit: int | None,
    mct_live: bool = False,
) -> list[dict]:
    """Re-rank with MCT + Thompson on each request, then apply filters."""
    ranked = rank_genre_pool_rows(
        rows,
        genre_id,
        searcher,
        movie_profile=movie_profile,
        limit=None,
        mct_live=mct_live,
    )
    return _serve_cached_rows(
        ranked,
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
            self._schedule_refresh(normalized, movie_profile=movie_profile, partial_ok=False)

    def browse(
        self,
        genre_id: str,
        *,
        movie_profile: bool = True,
        page_limit: int | None = None,
        parallel: bool = True,
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
            if cached and cached.rows:
                if not cached.is_fresh():
                    self._schedule_refresh(
                        normalized,
                        movie_profile=movie_profile,
                        page_limit=page_limit,
                        parallel=parallel,
                        min_seeds=min_seeds,
                        max_seeds=max_seeds,
                        max_size=max_size,
                        limit=limit,
                        tmdb_api_key=tmdb_api_key,
                        enrich=enrich,
                        partial_ok=False,
                    )
                filtered = _rank_and_serve_rows(
                    cached.rows,
                    normalized,
                    self._searcher,
                    movie_profile=movie_profile,
                    min_seeds=min_seeds,
                    max_seeds=max_seeds,
                    max_size=max_size,
                    limit=limit,
                    mct_live=False,
                )
                return SearchOutcome(
                    results=filtered,
                    failed_sites=list(cached.failed_sites),
                )

        owned_refresh = self._cache.mark_refreshing(normalized, movie_profile=movie_profile)
        if not owned_refresh:
            served = self._wait_for_in_flight_refresh(
                normalized,
                movie_profile=movie_profile,
                min_seeds=min_seeds,
                max_seeds=max_seeds,
                max_size=max_size,
                limit=limit,
                wait_for_completion=force_refresh,
            )
            if served is not None:
                return served
            owned_refresh = self._cache.mark_refreshing(normalized, movie_profile=movie_profile)
            if not owned_refresh:
                return SearchOutcome([], [])

        try:
            return self._refresh_sync(
                normalized,
                movie_profile=movie_profile,
                page_limit=page_limit,
                parallel=parallel,
                min_seeds=min_seeds,
                max_seeds=max_seeds,
                max_size=max_size,
                limit=limit,
                tmdb_api_key=tmdb_api_key,
                enrich=enrich,
                partial_ok=False,
                discover_page_offset=_discover_page_offset(normalized, rotate=force_refresh),
            )
        finally:
            if owned_refresh:
                self._cache.clear_refreshing(normalized, movie_profile=movie_profile)

    def _wait_for_in_flight_refresh(
        self,
        genre_id: str,
        *,
        movie_profile: bool,
        min_seeds: int | None,
        max_seeds: int | None,
        max_size: str | None,
        limit: int | None,
        wait_for_completion: bool = False,
    ) -> SearchOutcome | None:
        deadline = time.time() + _REFRESH_WAIT_SECONDS
        while time.time() < deadline:
            refreshing = self._cache.is_refreshing(genre_id, movie_profile=movie_profile)
            cached = self._cache.get(genre_id, movie_profile=movie_profile)
            if wait_for_completion:
                if not refreshing:
                    if cached and cached.rows:
                        return self._outcome_from_cached_rows(
                            cached,
                            genre_id,
                            movie_profile=movie_profile,
                            min_seeds=min_seeds,
                            max_seeds=max_seeds,
                            max_size=max_size,
                            limit=limit,
                        )
                    return None
            else:
                if cached and cached.rows:
                    return self._outcome_from_cached_rows(
                        cached,
                        genre_id,
                        movie_profile=movie_profile,
                        min_seeds=min_seeds,
                        max_seeds=max_seeds,
                        max_size=max_size,
                        limit=limit,
                    )
                if not refreshing:
                    break
            time.sleep(_REFRESH_POLL_SECONDS)
        cached = self._cache.get(genre_id, movie_profile=movie_profile)
        if cached and cached.rows:
            return self._outcome_from_cached_rows(
                cached,
                genre_id,
                movie_profile=movie_profile,
                min_seeds=min_seeds,
                max_seeds=max_seeds,
                max_size=max_size,
                limit=limit,
            )
        return None

    def _outcome_from_cached_rows(
        self,
        cached: GenrePoolEntry,
        genre_id: str,
        *,
        movie_profile: bool,
        min_seeds: int | None,
        max_seeds: int | None,
        max_size: str | None,
        limit: int | None,
    ) -> SearchOutcome:
        filtered = _rank_and_serve_rows(
            cached.rows,
            genre_id,
            self._searcher,
            movie_profile=movie_profile,
            min_seeds=min_seeds,
            max_seeds=max_seeds,
            max_size=max_size,
            limit=limit,
            mct_live=False,
        )
        return SearchOutcome(
            results=filtered,
            failed_sites=list(cached.failed_sites),
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
        partial_ok: bool = False,
        page_limit: int | None = None,
        parallel: bool = True,
    ) -> None:
        if not self._cache.mark_refreshing(genre_id, movie_profile=movie_profile):
            return

        def run() -> None:
            try:
                self._refresh_sync(
                    genre_id,
                    movie_profile=movie_profile,
                    page_limit=page_limit,
                    parallel=parallel,
                    min_seeds=min_seeds,
                    max_seeds=max_seeds,
                    max_size=max_size,
                    limit=limit,
                    tmdb_api_key=tmdb_api_key,
                    enrich=enrich,
                    partial_ok=partial_ok,
                    discover_page_offset=_discover_page_offset(genre_id, rotate=True),
                )
            finally:
                self._cache.clear_refreshing(genre_id, movie_profile=movie_profile)

        threading.Thread(target=run, daemon=True, name=f"genre-pool-{genre_id}").start()

    def _refresh_sync(
        self,
        genre_id: str,
        *,
        movie_profile: bool,
        page_limit: int | None = None,
        parallel: bool = True,
        min_seeds: int | None,
        max_seeds: int | None,
        max_size: str | None,
        limit: int | None,
        tmdb_api_key: str | None,
        enrich: bool,
        partial_ok: bool,
        discover_page_offset: int = 1,
    ) -> SearchOutcome:
        tmdb_client, _ = self._tmdb_resolver(tmdb_api_key)
        use_tmdb = enrich and tmdb_client.configured and tmdb_client.validate_key()
        outcome = fetch_genre_pool_rows(
            self._searcher,
            genre_id,
            tmdb_client if use_tmdb else None,
            movie_profile=movie_profile,
            page_limit=page_limit,
            parallel=parallel,
            discover_page_offset=discover_page_offset,
        )
        ranked = rank_genre_pool_rows(
            outcome.results,
            genre_id,
            self._searcher,
            movie_profile=movie_profile,
            limit=None,
            mct_live=True,
        )
        pool_rows = pool_rows_from_ranked(ranked) if ranked else []
        if pool_rows:
            self._cache.put(
                genre_id,
                pool_rows,
                outcome.failed_sites,
                partial=partial_ok,
                movie_profile=movie_profile,
            )
        elif not partial_ok:
            self._cache.invalidate(genre_id, movie_profile=movie_profile)
        filtered = _rank_and_serve_rows(
            pool_rows,
            genre_id,
            self._searcher,
            movie_profile=movie_profile,
            min_seeds=min_seeds,
            max_seeds=max_seeds,
            max_size=max_size,
            limit=limit,
            mct_live=False,
        )
        return SearchOutcome(
            results=filtered,
            failed_sites=outcome.failed_sites,
            all_sources_failed=outcome.all_sources_failed,
            indexers_unavailable=outcome.indexers_unavailable,
            movie_indexers_unavailable=outcome.movie_indexers_unavailable,
        )
