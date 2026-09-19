"""Pydantic models for search API."""
from __future__ import annotations

from pydantic import AliasChoices, BaseModel, Field


class HealthResponse(BaseModel):
    status: str = "ok"
    tmdb_configured: bool = False


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
    poster_url: str | None = None
    branch_key: str | None = Field(
        default=None,
        validation_alias=AliasChoices("_branch_key", "branch_key"),
        serialization_alias="branch_key",
    )

    model_config = {"populate_by_name": True}


class MovieGroup(BaseModel):
    group_key: str
    title: str
    year: int | None = None
    overview: str | None = None
    poster_url: str | None = None
    trailer_youtube_key: str | None = None
    release_count: int
    releases: list[TorrentResult]


class SearchResponse(BaseModel):
    query: str
    count: int
    total_count: int
    results: list[TorrentResult]
    failed_sites: list[str] = Field(default_factory=list)
    groups: list[MovieGroup] = Field(default_factory=list)
    tmdb_key_rejected: bool = False
    tmdb_enrichment_capped: bool = False


class MagnetResponse(BaseModel):
    id: str
    magnet: str
