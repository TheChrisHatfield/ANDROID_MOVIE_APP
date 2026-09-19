"""Build merged genre torrent pool from TMDB discover + multi-indexer keyword fan-out."""
from __future__ import annotations

import logging
from concurrent.futures import ThreadPoolExecutor

from metadata.genre_browse import (
    GENRE_KEYWORD_PAGE_LIMIT,
    curated_genre_search,
    keyword_genre_search,
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
    """
    Merge curated TMDB-title searches with broad keyword indexer fan-out,
    then Thompson-rank the combined pool.
    """
    curated: SearchOutcome | None = None
    keyword: SearchOutcome | None = None

    with ThreadPoolExecutor(max_workers=2) as pool:
        futures = {}
        if tmdb is not None and tmdb.configured:
            futures["curated"] = pool.submit(
                curated_genre_search,
                searcher,
                genre_id,
                tmdb,
                sites=sites,
                movie_profile=movie_profile,
                min_seeds=min_seeds,
                max_seeds=max_seeds,
                max_size=max_size,
                limit=limit,
                discover_page_offset=discover_page_offset,
            )
        futures["keyword"] = pool.submit(
            keyword_genre_search,
            searcher,
            genre_id,
            sites=sites,
            movie_profile=movie_profile,
            page_limit=GENRE_KEYWORD_PAGE_LIMIT,
            parallel=True,
            min_seeds=min_seeds,
            max_seeds=max_seeds,
            max_size=max_size,
            limit=limit or 120,
        )
        for name, future in futures.items():
            try:
                result = future.result()
                if name == "curated":
                    curated = result
                else:
                    keyword = result
            except Exception as exc:
                logger.warning("genre pool %s fetch failed: %s", name, exc)

    rows, failed = _merge_outcomes(curated, keyword)
    rows = _diversify_unique_movies(rows)
    if not rows:
        if keyword and keyword.indexers_unavailable:
            return SearchOutcome([], [], indexers_unavailable=True)
        return SearchOutcome([], failed, all_sources_failed=bool(failed))

    ranked = rank_pool_thompson(
        rows,
        feedback=get_branch_feedback(genre_id),
        genre_id=genre_id,
    )
    if limit is not None:
        ranked = ranked[:limit]

    return SearchOutcome(
        results=ranked,
        failed_sites=failed,
        all_sources_failed=False,
    )
