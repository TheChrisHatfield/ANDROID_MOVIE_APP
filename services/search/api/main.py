"""FastAPI search service — wraps torrtux_core."""
from __future__ import annotations

import logging
import os
import re
import time
from contextlib import asynccontextmanager
from pathlib import Path
from uuid import UUID

from typing import Literal

from fastapi import FastAPI, HTTPException, Query

from api.cache import ResultCache
from api.genre_pool_cache import GenrePoolCache
from api.genre_service import GenreBrowseService, record_genre_feedback
from api.models import HealthResponse, MagnetResponse, MovieGroup, SearchResponse, SitesHealthResponse, TorrentResult
from metadata.grouping import build_movie_groups
from metadata.tmdb_client import TmdbClient
from torrtux_core.filters import filter_size_bytes
from torrtux_core.genres import MOVIE_GENRES, genre_display_label
from torrtux_core.searcher import TorrentSearcher
from torrtux_core.sites.providers import X1337

logger = logging.getLogger(__name__)

BrowseFeed = Literal["trending", "top-100", "top-100-movies", "top-100-television"]
BROWSE_FEED_LABELS: dict[str, str] = {
    "trending": "1337x Trending",
    "top-100": "1337x Top 100",
    "top-100-movies": "1337x Top 100 Movies",
    "top-100-television": "1337x Top 100 Television",
}

def _load_local_env() -> None:
    env_file = Path(__file__).resolve().parents[1] / ".env"
    if not env_file.is_file():
        return
    for line in env_file.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        key = key.strip()
        if key and key not in os.environ:
            os.environ[key] = value.strip().strip('"').strip("'")


_load_local_env()
_searcher = TorrentSearcher()
_result_cache = ResultCache()
_tmdb = TmdbClient()
_tmdb_clients: dict[str, TmdbClient] = {}
_sites_health_cache: dict[str, object] = {"checked_at": 0.0, "working": []}
_SITES_HEALTH_TTL = 300


def _tmdb_for_request(api_key: str | None) -> tuple[TmdbClient, bool]:
    user_key = (api_key or "").strip()
    if not user_key:
        return _tmdb, False
    if _tmdb.configured and user_key == _tmdb.api_key:
        return _tmdb, False
    client = _tmdb_clients.get(user_key)
    if client is None:
        client = TmdbClient(api_key=user_key)
        _tmdb_clients[user_key] = client
    if not client.validate_key():
        return _tmdb, True
    return client, False


_genre_pool_cache = GenrePoolCache()
_genre_service = GenreBrowseService(_searcher, _genre_pool_cache, _tmdb_for_request)


def _quality_from_result_name(name: str | None) -> str | None:
    if not name:
        return None
    bracketed = re.search(r"\[(\d+p)\]", name, re.IGNORECASE)
    if bracketed:
        return bracketed.group(1)
    loose = re.search(r"\b(\d{3,4}p)\b", name, re.IGNORECASE)
    return loose.group(1).lower() if loose else None


def _refresh_sites_health(force: bool = False) -> list[str]:
    now = time.time()
    if not force and now - float(_sites_health_cache["checked_at"]) < _SITES_HEALTH_TTL:
        if _searcher.working_sites:
            return [site.name for site in _searcher.working_sites]
        force = True
    _searcher.test_sites(quiet=True)
    working = [site.name for site in _searcher.working_sites]
    _sites_health_cache["checked_at"] = now
    _sites_health_cache["working"] = working
    return working


def _build_search_response(
    query: str,
    raw_results: list[dict],
    failed_sites: list[str],
    *,
    limit: int,
    group: bool,
    enrich: bool,
    tmdb_api_key: str | None,
    max_enrich_groups: int = 50,
) -> SearchResponse:
    stored = _result_cache.put_many(raw_results)
    flat_results = [TorrentResult(**row) for row in stored]
    groups: list[MovieGroup] = []
    display_results = flat_results
    enrichment_capped = False
    tmdb_key_rejected = False

    if group and stored:
        tmdb_client, tmdb_key_rejected = _tmdb_for_request(tmdb_api_key)
        group_rows, ungrouped_rows, enrichment_capped = build_movie_groups(
            stored,
            tmdb=tmdb_client,
            enrich_metadata=enrich,
            max_groups=min(limit, max_enrich_groups),
        )
        groups = [MovieGroup(**g) for g in group_rows]
        if groups:
            display_results = [TorrentResult(**row) for row in ungrouped_rows]
    elif (tmdb_api_key or "").strip():
        _, tmdb_key_rejected = _tmdb_for_request(tmdb_api_key)

    visible_count = sum(len(g.releases) for g in groups) + len(display_results)
    return SearchResponse(
        query=query,
        count=visible_count,
        total_count=len(stored),
        results=display_results,
        failed_sites=failed_sites,
        groups=groups,
        tmdb_key_rejected=tmdb_key_rejected,
        tmdb_enrichment_capped=enrichment_capped if group and stored else False,
    )


@asynccontextmanager
async def lifespan(_: FastAPI):
    if not logging.getLogger().handlers:
        logging.basicConfig(level=logging.INFO)
    # FR-008: probe on first /v1/sites/health or /v1/search (not blocking startup)
    yield
    _result_cache.clear()
    logger.info("Search API shutdown: result cache cleared")


app = FastAPI(title="Torrent Movie Search API", version="1.0.0", lifespan=lifespan)


@app.get("/v1/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(tmdb_configured=_tmdb.configured)


@app.get("/v1/sites/health", response_model=SitesHealthResponse)
def sites_health(refresh: bool = Query(False)) -> SitesHealthResponse:
    working = _refresh_sites_health(force=refresh)
    return SitesHealthResponse(working=working, count=len(working))


@app.get("/v1/search", response_model=SearchResponse)
def search(
    q: str = Query(..., min_length=1),
    sites: str | None = Query(None),
    limit: int = Query(50, ge=1, le=200),
    pages: int = Query(1, ge=1, le=10),
    min_seeds: int | None = Query(None, ge=0),
    max_seeds: int | None = Query(None, ge=0),
    max_size: str | None = Query(None),
    parallel: bool = Query(True),
    movie_profile: bool = Query(True),
    group: bool = Query(True, description="Group duplicate movies; Kodi-style compact results"),
    enrich: bool = Query(True, description="Fetch poster/overview/trailer via TMDB when API key set"),
    tmdb_api_key: str | None = Query(None, description="Optional TMDB API key override (else TMDB_API_KEY env)"),
) -> SearchResponse:
    q = q.strip()
    if not q:
        raise HTTPException(status_code=400, detail="Query cannot be empty")
    if min_seeds is not None and max_seeds is not None and min_seeds > max_seeds:
        raise HTTPException(status_code=400, detail="min_seeds cannot exceed max_seeds")

    if not _searcher.working_sites:
        _refresh_sites_health(force=True)
    if not _searcher.working_sites:
        raise HTTPException(status_code=503, detail="No working indexers")

    site_list = None
    if sites is not None:
        site_list = [s.strip() for s in sites.split(",") if s.strip()]
        if not site_list:
            raise HTTPException(status_code=400, detail="sites parameter must name at least one indexer")

    try:
        if max_size is not None:
            filter_size_bytes(max_size)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid max_size")

    outcome = _searcher.search(
        q,
        sites=site_list,
        movie_profile=movie_profile,
        page_limit=pages,
        parallel=parallel,
        min_seeds=min_seeds,
        max_seeds=max_seeds,
        max_size=max_size,
        limit=limit,
    )
    if outcome.movie_indexers_unavailable:
        raise HTTPException(status_code=503, detail="No movie indexers available")
    if outcome.indexers_unavailable:
        raise HTTPException(status_code=503, detail="Requested indexers unavailable")
    if outcome.all_sources_failed:
        _refresh_sites_health(force=True)
        raise HTTPException(status_code=503, detail="No sources available")

    return _build_search_response(
        q,
        outcome.results,
        outcome.failed_sites,
        limit=limit,
        group=group,
        enrich=enrich,
        tmdb_api_key=tmdb_api_key,
    )


@app.get("/v1/browse/1337x/{feed}", response_model=SearchResponse)
def browse_1337x(
    feed: BrowseFeed,
    limit: int = Query(100, ge=1, le=200),
    pages: int = Query(1, ge=1, le=5),
    min_seeds: int | None = Query(None, ge=0),
    max_seeds: int | None = Query(None, ge=0),
    max_size: str | None = Query(None),
    movie_profile: bool = Query(True, description="Movie indexers and title filter (no TV/software)"),
    group: bool = Query(True, description="Group duplicate movies; Kodi-style compact results"),
    enrich: bool = Query(True, description="Fetch poster/overview/trailer via TMDB when API key set"),
    tmdb_api_key: str | None = Query(None, description="Optional TMDB API key override (else TMDB_API_KEY env)"),
) -> SearchResponse:
    if feed not in X1337.BROWSE_FEEDS:
        raise HTTPException(status_code=400, detail="Unknown browse feed")
    if min_seeds is not None and max_seeds is not None and min_seeds > max_seeds:
        raise HTTPException(status_code=400, detail="min_seeds cannot exceed max_seeds")

    try:
        if max_size is not None:
            filter_size_bytes(max_size)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid max_size")

    if movie_profile and feed == "top-100-television":
        raise HTTPException(status_code=400, detail="TV browse feed unavailable in movie profile")

    outcome = _searcher.browse_1337x(
        feed,
        movie_profile=movie_profile,
        page_limit=pages,
        min_seeds=min_seeds,
        max_seeds=max_seeds,
        max_size=max_size,
        limit=limit,
    )
    if outcome.indexers_unavailable:
        raise HTTPException(status_code=503, detail="1337x unavailable")
    if outcome.all_sources_failed:
        _refresh_sites_health(force=True)
        raise HTTPException(status_code=503, detail="1337x browse unavailable")

    label = BROWSE_FEED_LABELS.get(feed, f"1337x {feed}")
    return _build_search_response(
        label,
        outcome.results,
        outcome.failed_sites,
        limit=limit,
        group=group,
        enrich=enrich,
        tmdb_api_key=tmdb_api_key,
        max_enrich_groups=12,
    )


@app.get("/v1/browse/genre/{genre}", response_model=SearchResponse)
def browse_genre(
    genre: str,
    limit: int = Query(100, ge=1, le=200),
    pages: int = Query(1, ge=1, le=5),
    min_seeds: int | None = Query(None, ge=0),
    max_seeds: int | None = Query(None, ge=0),
    max_size: str | None = Query(None),
    parallel: bool = Query(True),
    movie_profile: bool = Query(True),
    group: bool = Query(True, description="Group duplicate movies; Kodi-style compact results"),
    enrich: bool = Query(True, description="Fetch poster/overview/trailer via TMDB when API key set"),
    tmdb_api_key: str | None = Query(None, description="Optional TMDB API key override (else TMDB_API_KEY env)"),
    force_refresh: bool = Query(False, description="Bypass genre pool cache (pull-to-refresh)"),
) -> SearchResponse:
    normalized = genre.strip().lower()
    if normalized not in MOVIE_GENRES:
        raise HTTPException(status_code=400, detail="Unknown genre")
    if min_seeds is not None and max_seeds is not None and min_seeds > max_seeds:
        raise HTTPException(status_code=400, detail="min_seeds cannot exceed max_seeds")

    if not _searcher.working_sites:
        _refresh_sites_health(force=True)
    if not _searcher.working_sites:
        raise HTTPException(status_code=503, detail="No working indexers")

    try:
        if max_size is not None:
            filter_size_bytes(max_size)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid max_size")

    outcome = _genre_service.browse(
        normalized,
        movie_profile=movie_profile,
        min_seeds=min_seeds,
        max_seeds=max_seeds,
        max_size=max_size,
        limit=limit,
        tmdb_api_key=tmdb_api_key,
        enrich=enrich,
        force_refresh=force_refresh,
    )
    if outcome.movie_indexers_unavailable:
        raise HTTPException(status_code=503, detail="No movie indexers available")
    if outcome.indexers_unavailable:
        raise HTTPException(status_code=503, detail="Requested indexers unavailable")
    if outcome.all_sources_failed:
        _refresh_sites_health(force=True)
        raise HTTPException(status_code=503, detail="No sources available")

    label = genre_display_label(normalized)
    return _build_search_response(
        label,
        outcome.results,
        outcome.failed_sites,
        limit=limit,
        group=group,
        enrich=enrich,
        tmdb_api_key=tmdb_api_key,
        max_enrich_groups=12,
    )


@app.post("/v1/browse/genre/warm")
def warm_genre_pools(
    genres: str | None = Query(
        None,
        description="Comma-separated genre ids; omit to warm all genres in background",
    ),
    movie_profile: bool = Query(True),
) -> dict[str, object]:
    if genres:
        genre_ids = [part.strip().lower() for part in genres.split(",") if part.strip()]
        unknown = [g for g in genre_ids if g not in MOVIE_GENRES]
        if unknown:
            raise HTTPException(status_code=400, detail=f"Unknown genre(s): {', '.join(unknown)}")
    else:
        genre_ids = list(MOVIE_GENRES.keys())
    _genre_service.warm_genres(genre_ids, movie_profile=movie_profile)
    return {"status": "warming", "count": len(genre_ids)}


@app.post("/v1/browse/genre/{genre}/feedback")
def genre_branch_feedback(
    genre: str,
    group_key: str = Query(..., min_length=1),
    success: bool = Query(True),
) -> dict[str, str]:
    normalized = genre.strip().lower()
    if normalized not in MOVIE_GENRES:
        raise HTTPException(status_code=400, detail="Unknown genre")
    record_genre_feedback(normalized, group_key, success)
    return {"status": "ok"}


@app.get("/v1/browse/1337x/genre/{genre}", response_model=SearchResponse)
def browse_1337x_genre(
    genre: str,
    limit: int = Query(100, ge=1, le=200),
    pages: int = Query(1, ge=1, le=5),
    min_seeds: int | None = Query(None, ge=0),
    max_seeds: int | None = Query(None, ge=0),
    max_size: str | None = Query(None),
    group: bool = Query(True, description="Group duplicate movies; Kodi-style compact results"),
    enrich: bool = Query(True, description="Fetch poster/overview/trailer via TMDB when API key set"),
    tmdb_api_key: str | None = Query(None, description="Optional TMDB API key override (else TMDB_API_KEY env)"),
) -> SearchResponse:
    normalized = genre.strip().lower()
    if normalized not in MOVIE_GENRES:
        raise HTTPException(status_code=400, detail="Unknown genre")
    if min_seeds is not None and max_seeds is not None and min_seeds > max_seeds:
        raise HTTPException(status_code=400, detail="min_seeds cannot exceed max_seeds")

    try:
        if max_size is not None:
            filter_size_bytes(max_size)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid max_size")

    outcome = _searcher.browse_1337x_genre(
        normalized,
        page_limit=pages,
        min_seeds=min_seeds,
        max_seeds=max_seeds,
        max_size=max_size,
        limit=limit,
    )
    if outcome.indexers_unavailable:
        raise HTTPException(status_code=503, detail="1337x unavailable")
    if outcome.all_sources_failed:
        _refresh_sites_health(force=True)
        raise HTTPException(status_code=503, detail="1337x genre browse unavailable")

    label = f"1337x {normalized.replace('-', ' ').title()}"
    return _build_search_response(
        label,
        outcome.results,
        outcome.failed_sites,
        limit=limit,
        group=group,
        enrich=enrich,
        tmdb_api_key=tmdb_api_key,
        max_enrich_groups=12,
    )


def _fetch_magnet_for_row(row: dict) -> str | None:
    magnet = row.get("magnet")
    if magnet and str(magnet).strip():
        return str(magnet).strip()
    site_name = row.get("site")
    detail_url = row.get("detail_url")
    site = _searcher.site_for_name(site_name)
    if not site or not detail_url:
        return None
    quality = _quality_from_result_name(row.get("name"))
    try:
        if hasattr(site, "get_magnet_link"):
            try:
                magnet = site.get_magnet_link(detail_url, quality=quality)
            except TypeError:
                magnet = site.get_magnet_link(detail_url)
        else:
            magnet = site.get_magnet_link(detail_url)
    except Exception as exc:
        logger.warning("Magnet fetch failed for %s: %s", detail_url, exc)
        magnet = None
    if magnet and str(magnet).strip():
        return str(magnet).strip()
    return None


@app.get("/v1/results/{result_id}/magnet", response_model=MagnetResponse)
def get_magnet(result_id: UUID) -> MagnetResponse:
    row = _result_cache.get(result_id)
    if not row:
        raise HTTPException(status_code=404, detail="Result not found or expired")

    magnet = _fetch_magnet_for_row(row)
    if magnet and (not row.get("magnet") or not str(row.get("magnet")).strip()):
        _result_cache.resolve_magnet(result_id, magnet)

    if not magnet:
        raise HTTPException(status_code=404, detail="Magnet unavailable")

    return MagnetResponse(id=str(result_id), magnet=magnet)


@app.get("/v1/magnet/resolve", response_model=MagnetResponse)
def resolve_magnet_by_detail(
    site: str = Query(..., min_length=1),
    detail_url: str = Query(..., min_length=1),
    result_id: UUID | None = None,
    name: str | None = None,
) -> MagnetResponse:
    row = {
        "site": site,
        "detail_url": detail_url,
        "name": name or "",
        "magnet": None,
    }
    magnet = _fetch_magnet_for_row(row)
    if not magnet:
        raise HTTPException(status_code=404, detail="Magnet unavailable")
    stored = _result_cache.put_many([{**row, "magnet": magnet}])
    if not stored:
        raise HTTPException(status_code=404, detail="Magnet unavailable")
    return MagnetResponse(id=str(UUID(stored[0]["id"])), magnet=magnet)
