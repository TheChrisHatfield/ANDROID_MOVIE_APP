"""FastAPI search service — wraps torrtux_core."""
from __future__ import annotations

import logging
import re
import time
from contextlib import asynccontextmanager
from uuid import UUID

from fastapi import FastAPI, HTTPException, Query

from api.cache import ResultCache
from api.models import HealthResponse, MagnetResponse, SearchResponse, SitesHealthResponse, TorrentResult
from torrtux_core.filters import filter_size_bytes
from torrtux_core.searcher import TorrentSearcher

logger = logging.getLogger(__name__)

_searcher = TorrentSearcher()
_result_cache = ResultCache()
_sites_health_cache: dict[str, object] = {"checked_at": 0.0, "working": []}
_SITES_HEALTH_TTL = 300


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
            return list(_sites_health_cache["working"])
        force = True
    _searcher.test_sites(quiet=True)
    working = [site.name for site in _searcher.working_sites]
    _sites_health_cache["checked_at"] = now
    _sites_health_cache["working"] = working
    return working


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
    return HealthResponse()


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
        movie_profile=movie_profile and not site_list,
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

    stored = _result_cache.put_many(outcome.results)
    return SearchResponse(
        query=q,
        count=len(stored),
        results=[TorrentResult(**row) for row in stored],
        failed_sites=outcome.failed_sites,
    )


@app.get("/v1/results/{result_id}/magnet", response_model=MagnetResponse)
def get_magnet(result_id: UUID) -> MagnetResponse:
    row = _result_cache.get(result_id)
    if not row:
        raise HTTPException(status_code=404, detail="Result not found or expired")

    magnet = row.get("magnet")
    if not magnet or not str(magnet).strip():
        magnet = None
    if not magnet:
        site_name = row.get("site")
        detail_url = row.get("detail_url")
        site = next((s for s in _searcher.working_sites if s.name == site_name), None)
        if site is None:
            site = next((s for s in _searcher.sites if s.name == site_name), None)
        if site and detail_url:
            quality = _quality_from_result_name(row.get("name"))
            if hasattr(site, "get_magnet_link"):
                try:
                    magnet = site.get_magnet_link(detail_url, quality=quality)
                except TypeError:
                    magnet = site.get_magnet_link(detail_url)
            else:
                magnet = site.get_magnet_link(detail_url)
        if magnet:
            _result_cache.resolve_magnet(result_id, magnet)

    if not magnet or not str(magnet).strip():
        raise HTTPException(status_code=404, detail="Magnet unavailable")

    return MagnetResponse(id=str(result_id), magnet=magnet)
