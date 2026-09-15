"""Group torrent rows by parsed movie title (Kodi-style compact search results)."""
from __future__ import annotations

from metadata.title_parse import group_key_for, parse_torrent_movie_title
from metadata.tmdb_client import TmdbClient, TmdbMovieInfo


def _apply_tmdb(bucket: dict, tmdb: TmdbClient) -> None:
    info: TmdbMovieInfo | None = tmdb.lookup(bucket["title"], bucket["year"])
    if info:
        bucket["title"] = info.title
        bucket["year"] = info.year or bucket["year"]
        bucket["overview"] = info.overview
        bucket["poster_url"] = info.poster_url
        bucket["trailer_youtube_key"] = info.trailer_youtube_key


def _append_group(
    bucket: dict,
    releases: list[dict],
    groups: list[dict],
    tmdb: TmdbClient | None,
    enrich_metadata: bool,
) -> None:
    if enrich_metadata and tmdb and tmdb.configured:
        _apply_tmdb(bucket, tmdb)
    bucket["release_count"] = len(releases)
    groups.append(
        {
            "group_key": bucket["group_key"],
            "title": bucket["title"],
            "year": bucket["year"],
            "overview": bucket["overview"],
            "poster_url": bucket["poster_url"],
            "trailer_youtube_key": bucket["trailer_youtube_key"],
            "release_count": bucket["release_count"],
            "releases": releases,
        }
    )


def build_movie_groups(
    rows: list[dict],
    tmdb: TmdbClient | None = None,
    enrich_metadata: bool = True,
    max_groups: int = 30,
) -> tuple[list[dict], list[dict]]:
    """
    Return (groups, ungrouped_rows).
    Each group dict: group_key, title, year, overview, poster_url, trailer_youtube_key,
    release_count, releases (list of row dicts with id preserved).
    """
    buckets: dict[str, dict] = {}
    order: list[str] = []

    for row in rows:
        title, year = parse_torrent_movie_title(str(row.get("name") or ""))
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

    def process_key(key: str) -> None:
        bucket = buckets[key]
        releases = bucket["releases"]
        if len(releases) == 1:
            ungrouped.extend(releases)
            return
        if bucket["year"] is None:
            ungrouped.extend(releases)
            return
        _append_group(bucket, releases, groups, tmdb, enrich_metadata)

    for key in order[:max_groups]:
        process_key(key)

    for key in order[max_groups:]:
        process_key(key)

    return groups, ungrouped
