"""Pydantic models for search API."""
from __future__ import annotations

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: str = "ok"


class SitesHealthResponse(BaseModel):
    working: list[str]
    count: int


class TorrentResult(BaseModel):
    id: str
    name: str
    site: str
    size: str | None = None
    seeds: str | None = None
    leeches: str | None = None
    date: str | None = None
    magnet: str | None = None
    detail_url: str | None = None


class SearchResponse(BaseModel):
    query: str
    count: int
    results: list[TorrentResult]
    failed_sites: list[str] = Field(default_factory=list)


class MagnetResponse(BaseModel):
    id: str
    magnet: str
