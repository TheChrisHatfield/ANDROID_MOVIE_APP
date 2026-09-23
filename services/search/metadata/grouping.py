"""Group torrent rows by parsed movie title (Kodi-style compact search results)."""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from metadata.title_parse import group_key_for, parse_torrent_movie_title
from torrtux_core.filters import interleave_by_site
from metadata.tmdb_client import TmdbClient, TmdbMovieInfo
from metadata.web_poster import fill_missing_posters, is_usable_poster_url
from metadata.web_trailer import extract_youtube_id, fill_missing_trailers, is_usable_trailer_key

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
    extracted = extract_youtube_id(info.trailer_youtube_key)
    if extracted:
        bucket["trailer_youtube_key"] = extracted


def _enrich_buckets_parallel(buckets: list[dict], tmdb: TmdbClient) -> None:
    if not buckets:
        return
    with ThreadPoolExecutor(max_workers=_MAX_TMDB_WORKERS) as pool:
        list(pool.map(lambda bucket: _apply_tmdb(bucket, tmdb), buckets))


def _apply_indexer_metadata(bucket: dict, releases: list[dict]) -> None:
    """Use poster/overview/trailer from indexer rows (e.g. YTS) without TMDB."""
    for row in releases:
        if not bucket.get("poster_url") and is_usable_poster_url(row.get("poster_url")):
            bucket["poster_url"] = row["poster_url"]
        if not bucket.get("overview") and row.get("overview"):
            bucket["overview"] = row["overview"]
        if not is_usable_trailer_key(bucket.get("trailer_youtube_key")):
            extracted = extract_youtube_id(row.get("trailer_youtube_key"))
            if extracted:
                bucket["trailer_youtube_key"] = extracted
        if (
            is_usable_poster_url(bucket.get("poster_url"))
            and bucket.get("overview")
            and is_usable_trailer_key(bucket.get("trailer_youtube_key"))
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
    releases = interleave_by_site(releases)
    poster = bucket["poster_url"]
    if poster:
        for release in releases:
            if not release.get("poster_url"):
                release["poster_url"] = poster
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
) -> tuple[list[dict], list[dict], bool]:
    """
    Return (groups, ungrouped_rows, tmdb_enrichment_capped).
    Each group dict: group_key, title, year, overview, poster_url, trailer_youtube_key,
    release_count, releases (list of row dicts with id preserved).
    """
    buckets: dict[str, dict] = {}
    order: list[str] = []

    for row in rows:
        title, _ = parse_torrent_movie_title(str(row.get("name") or ""))
        year = _year_from_row(row)
        branch_key = str(row.get("_branch_key") or row.get("branch_key") or "").strip()
        key = branch_key if branch_key else group_key_for(title, year)
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

    titled_order = [
        key for key in order if str(buckets[key].get("title") or "").strip()
    ]
    untitled_keys = [
        key for key in order if not str(buckets[key].get("title") or "").strip()
    ]
    primary_keys = titled_order[:max_groups]
    overflow_keys = titled_order[max_groups:]

    for key in untitled_keys:
        process_key(key)
    for key in primary_keys:
        process_key(key)
    for key in overflow_keys:
        process_key(key)

    for bucket, releases in staged:
        _apply_indexer_metadata(bucket, releases)

    if enrich_metadata and tmdb and tmdb.configured:
        tmdb_buckets = [buckets[key] for key in primary_keys]
        _enrich_buckets_parallel(tmdb_buckets, tmdb)

    if enrich_metadata:
        primary = [buckets[key] for key in primary_keys]
        with ThreadPoolExecutor(max_workers=2) as pool:
            poster_job = pool.submit(fill_missing_posters, primary)
            trailer_job = pool.submit(fill_missing_trailers, primary)
            poster_job.result()
            trailer_job.result()

    for bucket, releases in staged:
        _append_group(bucket, releases, groups)

    if any(
        release.get("_branch_order") is not None
        for bucket, releases in staged
        for release in releases
    ):
        groups.sort(
            key=lambda group: min(
                int(release.get("_branch_order", 9999))
                for release in group.get("releases", [])
            ),
        )
    elif any(
        release.get("_genre_rank") is not None
        for bucket, releases in staged
        for release in releases
    ):
        groups.sort(
            key=lambda group: min(
                int(release.get("_genre_rank", 9999))
                for release in group.get("releases", [])
            ),
        )

    return groups, ungrouped, bool(overflow_keys and enrich_metadata)
