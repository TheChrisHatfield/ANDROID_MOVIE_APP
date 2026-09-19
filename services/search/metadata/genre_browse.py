"""Netflix-style genre browse: TMDB discover popular titles, then torrent search per title."""
from __future__ import annotations

import logging
from concurrent.futures import ThreadPoolExecutor, as_completed

from metadata.tmdb_client import TmdbClient, TmdbDiscoverMovie
from torrtux_core.filters import seed_count, sort_by_seeds_desc
from torrtux_core.genres import genre_search_query, tmdb_genre_id
from torrtux_core.searcher import SearchOutcome, TorrentSearcher

logger = logging.getLogger(__name__)

GENRE_TITLE_SEARCH_LIMIT = 12
GENRE_TITLE_FANOUT_TIMEOUT_SEC = 28
GENRE_RELEASES_PER_TITLE = 6


def _attach_discover_metadata(row: dict, rank: int, movie: TmdbDiscoverMovie) -> None:
    row["_genre_rank"] = rank
    if movie.poster_url and not row.get("poster_url"):
        row["poster_url"] = movie.poster_url
    if movie.overview and not row.get("overview"):
        row["overview"] = movie.overview


def _sort_genre_rows(rows: list[dict]) -> list[dict]:
    def sort_key(row: dict) -> tuple:
        rank = int(row.get("_genre_rank", 9999))
        seeds = seed_count(row.get("seeds", "-")) or 0
        return (rank, -seeds)

    return sorted(rows, key=sort_key)


def curated_genre_search(
    searcher: TorrentSearcher,
    genre_id: str,
    tmdb: TmdbClient,
    *,
    sites: list | None = None,
    movie_profile: bool = True,
    min_seeds: int | None = None,
    max_seeds: int | None = None,
    max_size: str | None = None,
    limit: int | None = None,
    title_limit: int = GENRE_TITLE_SEARCH_LIMIT,
) -> SearchOutcome | None:
    """
    Discover popular movies in genre via TMDB, search torrents per title across indexers.
    Returns None when TMDB discover cannot run (caller should keyword-fallback).
    """
    normalized = genre_id.strip().lower()
    tmdb_id = tmdb_genre_id(normalized)
    if not tmdb_id or not tmdb.configured:
        return None

    movies = tmdb.discover_movies(tmdb_id, limit=title_limit)
    if not movies:
        return None

    all_results: list[dict] = []
    failed_sites: list[str] = []

    with ThreadPoolExecutor(max_workers=min(6, len(movies))) as pool:
        futures = {
            pool.submit(
                searcher.search,
                movie.title,
                sites=sites,
                movie_profile=movie_profile,
                page_limit=1,
                parallel=True,
                min_seeds=min_seeds,
                max_seeds=max_seeds,
                max_size=max_size,
                limit=GENRE_RELEASES_PER_TITLE,
            ): (rank, movie)
            for rank, movie in enumerate(movies)
        }
        try:
            for future in as_completed(futures, timeout=GENRE_TITLE_FANOUT_TIMEOUT_SEC):
                rank, movie = futures[future]
                try:
                    outcome = future.result()
                    for row in outcome.results:
                        _attach_discover_metadata(row, rank, movie)
                    all_results.extend(outcome.results)
                    failed_sites.extend(outcome.failed_sites)
                except Exception as exc:
                    logger.warning("genre title search failed %s: %s", movie.title, exc)
        except TimeoutError:
            pending = [movie.title for future, (_, movie) in futures.items() if not future.done()]
            for future in futures:
                future.cancel()
            if pending:
                logger.warning(
                    "genre curated search timed out after %ss; skipped %s",
                    GENRE_TITLE_FANOUT_TIMEOUT_SEC,
                    ", ".join(pending[:5]),
                )

    if not all_results:
        return None

    sorted_results = _sort_genre_rows(all_results)
    if limit is not None:
        sorted_results = sorted_results[:limit]

    queried_names = {site.name for site in (sites or [])} if sites else set()
    all_sources_failed = bool(queried_names) and not sorted_results
    return SearchOutcome(
        results=sorted_results,
        failed_sites=list(dict.fromkeys(failed_sites)),
        all_sources_failed=all_sources_failed,
    )


def keyword_genre_search(
    searcher: TorrentSearcher,
    genre_id: str,
    *,
    sites: list | None = None,
    movie_profile: bool = True,
    page_limit: int = 1,
    parallel: bool = True,
    min_seeds: int | None = None,
    max_seeds: int | None = None,
    max_size: str | None = None,
    limit: int | None = None,
) -> SearchOutcome:
    normalized = genre_id.strip().lower()
    query = genre_search_query(normalized)
    if not query:
        return SearchOutcome([], [], indexers_unavailable=True)
    return searcher.search(
        query,
        sites=sites,
        movie_profile=movie_profile,
        page_limit=page_limit,
        parallel=parallel,
        min_seeds=min_seeds,
        max_seeds=max_seeds,
        max_size=max_size,
        limit=limit,
    )
