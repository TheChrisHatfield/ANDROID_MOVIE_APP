"""Group torrent rows by parsed movie title (Kodi-style compact search results)."""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from metadata.title_parse import group_key_for, parse_torrent_movie_title
from metadata.tmdb_client import TmdbClient, TmdbMovieInfo

_MAX_TMDB_WORKERS = 4


def _apply_tmdb(bucket: dict, tmdb: TmdbClient) -> None:
    info: TmdbMovieInfo | None = tmdb.lookup(bucket["title"], bucket["year"])
    if not info:
        return
    if info.title:
        bucket["title"] = info.title
    if info.year is not None:
        bucket["year"] = info.year
    if info.overview:
        bucket["overview"] = info.overview
    if info.poster_url:
        bucket["poster_url"] = info.poster_url
    if info.trailer_youtube_key:
        bucket["trailer_youtube_key"] = info.trailer_youtube_key


def _enrich_buckets_parallel(buckets: list[dict], tmdb: TmdbClient) -> None:
    if not buckets:
        return
    with ThreadPoolExecutor(max_workers=_MAX_TMDB_WORKERS) as pool:
        list(pool.map(lambda bucket: _apply_tmdb(bucket, tmdb), buckets))


def _apply_indexer_metadata(bucket: dict, releases: list[dict]) -> None:
    """Use poster/overview/trailer from indexer rows (e.g. YTS) without TMDB."""
    for row in releases:
        if not bucket.get("poster_url") and row.get("poster_url"):
            bucket["poster_url"] = row["poster_url"]
        if not bucket.get("overview") and row.get("overview"):
            bucket["overview"] = row["overview"]
        if not bucket.get("trailer_youtube_key") and row.get("trailer_youtube_key"):
            bucket["trailer_youtube_key"] = row["trailer_youtube_key"]
        if (
            bucket.get("poster_url")
            and bucket.get("overview")
            and bucket.get("trailer_youtube_key")
        ):
            break


def _year_from_row(row: dict) -> int | None:
    title, year = parse_torrent_movie_title(str(row.get("name") or ""))
    if year is not None:
        return year
    date_val = str(row.get("date") or "").strip()
    if date_val.isdigit() and 1900 <= int(date_val) <= 2100:
        return int(date_val)
    return None


def _append_group(bucket: dict, releases: list[dict], groups: list[dict]) -> None:
    groups.append(
        {
            "group_key": bucket["group_key"],
            "title": bucket["title"],
            "year": bucket["year"],
            "overview": bucket["overview"],
            "poster_url": bucket["poster_url"],
            "trailer_youtube_key": bucket["trailer_youtube_key"],
            "release_count": len(releases),
            "releases": releases,
        }
    )


def build_movie_groups(
    rows: list[dict],
    tmdb: TmdbClient | None = None,
    enrich_metadata: bool = True,
    max_groups: int = 50,
) -> tuple[list[dict], list[dict]]:
    """
    Return (groups, ungrouped_rows).
    Each group dict: group_key, title, year, overview, poster_url, trailer_youtube_key,
    release_count, releases (list of row dicts with id preserved).
    """
    buckets: dict[str, dict] = {}
    order: list[str] = []

    for row in rows:
        title, _ = parse_torrent_movie_title(str(row.get("name") or ""))
        year = _year_from_row(row)
        key = group_key_for(title, year)
        if key not in buckets:
            buckets[key] = {
                "group_key": key,
                "title": title,
                "year": year,
                "overview": None,
                "poster_url": None,
                "trailer_youtube_key": None,
                "releases": [],
            }
            order.append(key)
        buckets[key]["releases"].append(row)

    groups: list[dict] = []
    ungrouped: list[dict] = []
    staged: list[tuple[dict, list[dict]]] = []

    def process_key(key: str) -> None:
        bucket = buckets[key]
        releases = bucket["releases"]
        if not str(bucket.get("title") or "").strip():
            ungrouped.extend(releases)
            return
        staged.append((bucket, releases))

    primary_keys = order[:max_groups]
    overflow_keys = order[max_groups:]

    for key in primary_keys:
        process_key(key)
    for key in overflow_keys:
        process_key(key)

    for bucket, releases in staged:
        _apply_indexer_metadata(bucket, releases)

    if enrich_metadata and tmdb and tmdb.configured:
        tmdb_buckets = [buckets[key] for key in primary_keys if key in buckets]
        _enrich_buckets_parallel(tmdb_buckets, tmdb)

    for bucket, releases in staged:
        _append_group(bucket, releases, groups)

    return groups, ungrouped
