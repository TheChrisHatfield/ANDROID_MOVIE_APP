"""Build merged genre torrent pool from multi-indexer fan-out + optional TMDB enrichment."""
from __future__ import annotations

import logging
from concurrent.futures import ThreadPoolExecutor

from metadata.genre_browse import (
    TMDB_ENRICH_TITLE_LIMIT,
    broad_indexer_genre_pool,
    curated_genre_search,
)
from metadata.genre_tree import get_branch_feedback, rank_pool_thompson
from metadata.title_parse import group_key_for, parse_torrent_movie_title
from metadata.tmdb_client import TmdbClient
from torrtux_core.searcher import SearchOutcome, TorrentSearcher

logger = logging.getLogger(__name__)

# Cap releases per unique movie so the pool surfaces more distinct titles.
MAX_RELEASES_PER_MOVIE = 4


def _stable_row_key(row: dict) -> str:
    site = str(row.get("site") or "").lower()
    detail = str(row.get("detail_url") or "").strip()
    name = str(row.get("name") or "").strip().lower()
    if detail:
        return f"{site}|{detail}"
    return f"{site}|{name}"


def _merge_outcomes(*outcomes: SearchOutcome | None) -> tuple[list[dict], list[str]]:
    merged: dict[str, dict] = {}
    failed: list[str] = []
    for outcome in outcomes:
        if outcome is None:
            continue
        for row in outcome.results:
            key = _stable_row_key(row)
            existing = merged.get(key)
            if existing is None:
                merged[key] = dict(row)
            else:
                if not existing.get("magnet") and row.get("magnet"):
                    existing["magnet"] = row["magnet"]
                if not existing.get("poster_url") and row.get("poster_url"):
                    existing["poster_url"] = row["poster_url"]
                if not existing.get("overview") and row.get("overview"):
                    existing["overview"] = row["overview"]
                existing["_genre_rank"] = min(
                    int(existing.get("_genre_rank", 9999)),
                    int(row.get("_genre_rank", 9999)),
                )
        failed.extend(outcome.failed_sites)
    return list(merged.values()), list(dict.fromkeys(failed))


def _diversify_unique_movies(rows: list[dict], max_per_movie: int = MAX_RELEASES_PER_MOVIE) -> list[dict]:
    """Limit releases per parsed movie title so shelves show more unique films."""
    counts: dict[str, int] = {}
    diversified: list[dict] = []
    for row in rows:
        title, year = parse_torrent_movie_title(str(row.get("name") or ""))
        if not title.strip():
            diversified.append(row)
            continue
        key = group_key_for(title, year)
        used = counts.get(key, 0)
        if used >= max_per_movie:
            continue
        counts[key] = used + 1
        diversified.append(row)
    return diversified


def fetch_genre_pool_rows(
    searcher: TorrentSearcher,
    genre_id: str,
    tmdb: TmdbClient | None,
    *,
    sites: list | None = None,
    movie_profile: bool = True,
    page_limit: int | None = None,
    parallel: bool = True,
    min_seeds: int | None = None,
    max_seeds: int | None = None,
    max_size: str | None = None,
    discover_page_offset: int = 1,
) -> SearchOutcome:
    """
    Build a wide unranked pool: all indexers first, TMDB discover for metadata only.
    Ranking (MCT + Thompson) is deferred to rank_genre_pool_rows on each serve.
    """
    indexer: SearchOutcome | None = None
    enrich: SearchOutcome | None = None

    with ThreadPoolExecutor(max_workers=2) as pool:
        futures = {
            "indexer": pool.submit(
                broad_indexer_genre_pool,
                searcher,
                genre_id,
                sites=sites,
                movie_profile=movie_profile,
                page_limit=page_limit,
                parallel=parallel,
                min_seeds=min_seeds,
                max_seeds=max_seeds,
                max_size=max_size,
            ),
        }
        if tmdb is not None and tmdb.configured:
            futures["enrich"] = pool.submit(
                curated_genre_search,
                searcher,
                genre_id,
                tmdb,
                sites=sites,
                movie_profile=movie_profile,
                min_seeds=min_seeds,
                max_seeds=max_seeds,
                max_size=max_size,
                title_limit=TMDB_ENRICH_TITLE_LIMIT,
                discover_page_offset=discover_page_offset,
            )
        for name, future in futures.items():
            try:
                result = future.result()
                if name == "indexer":
                    indexer = result
                else:
                    enrich = result
            except Exception as exc:
                logger.warning("genre pool %s fetch failed: %s", name, exc)

    rows, failed = _merge_outcomes(indexer, enrich)
    rows = _diversify_unique_movies(rows)
    if not rows:
        if indexer and indexer.movie_indexers_unavailable:
            return SearchOutcome([], failed, movie_indexers_unavailable=True)
        if indexer and indexer.indexers_unavailable:
            return SearchOutcome([], [], indexers_unavailable=True)
        return SearchOutcome([], failed, all_sources_failed=bool(failed))

    return SearchOutcome(
        results=rows,
        failed_sites=failed,
        all_sources_failed=False,
        indexers_unavailable=indexer.indexers_unavailable if indexer else False,
        movie_indexers_unavailable=indexer.movie_indexers_unavailable if indexer else False,
    )


def rank_genre_pool_rows(
    rows: list[dict],
    genre_id: str,
    searcher: TorrentSearcher,
    *,
    sites: list | None = None,
    movie_profile: bool = True,
    limit: int | None = None,
    mct_live: bool = False,
) -> list[dict]:
    """MCT + Thompson rank on each request — non-deterministic shelf order."""
    ranked = rank_pool_thompson(
        rows,
        feedback=get_branch_feedback(genre_id),
        genre_id=genre_id,
        searcher=searcher,
        sites=sites,
        movie_profile=movie_profile,
        mct_live=mct_live,
    )
    if limit is not None:
        ranked = ranked[:limit]
    return ranked


def build_genre_pool(
    searcher: TorrentSearcher,
    genre_id: str,
    tmdb: TmdbClient | None,
    *,
    sites: list | None = None,
    movie_profile: bool = True,
    min_seeds: int | None = None,
    max_seeds: int | None = None,
    max_size: str | None = None,
    limit: int | None = None,
    discover_page_offset: int = 1,
) -> SearchOutcome:
    """Fetch wide pool then rank (convenience wrapper)."""
    outcome = fetch_genre_pool_rows(
        searcher,
        genre_id,
        tmdb,
        sites=sites,
        movie_profile=movie_profile,
        min_seeds=min_seeds,
        max_seeds=max_seeds,
        max_size=max_size,
        discover_page_offset=discover_page_offset,
    )
    if not outcome.results:
        return outcome

    ranked = rank_genre_pool_rows(
        outcome.results,
        genre_id,
        searcher,
        sites=sites,
        movie_profile=movie_profile,
        limit=limit,
        mct_live=True,
    )
    return SearchOutcome(
        results=ranked,
        failed_sites=outcome.failed_sites,
        all_sources_failed=outcome.all_sources_failed,
        indexers_unavailable=outcome.indexers_unavailable,
        movie_indexers_unavailable=outcome.movie_indexers_unavailable,
    )
