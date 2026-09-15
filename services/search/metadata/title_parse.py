"""Extract canonical movie title + year from torrent release names."""
from __future__ import annotations

import re

_YEAR_RE = re.compile(r"\b((?:19|20)\d{2})\b")
_QUALITY_RE = re.compile(
    r"\b(?:\d{3,4}p|4k|2160p|1080p|720p|480p|"
    r"bluray|blu-ray|web[- ]?dl|webrip|hdrip|dvdrip|x264|x265|hevc|h\.?264|h\.?265|"
    r"aac|dts|remux|proper|repack|extended|imax)\b",
    re.IGNORECASE,
)
_NOISE_RE = re.compile(
    r"\[.*?\]|\(.*?\)|\{.*?\}|\+.*$|"
    r"\b(?:yify|rarbg|ettv|eztv|galaxy|torrent|sample)\b",
    re.IGNORECASE,
)


def parse_torrent_movie_title(raw_name: str) -> tuple[str, int | None]:
    """Return (display_title, year) parsed from a torrent release name."""
    name = raw_name.strip()
    if not name:
        return "", None

    year: int | None = None
    year_match = _YEAR_RE.search(name)
    if year_match:
        year = int(year_match.group(1))

    cut = len(name)
    if year_match:
        cut = min(cut, year_match.start())
    quality_match = _QUALITY_RE.search(name)
    if quality_match:
        cut = min(cut, quality_match.start())

    title_part = name[:cut] if cut > 0 else name
    title_part = _NOISE_RE.sub(" ", title_part)
    title_part = re.sub(r"[._]+", " ", title_part)
    title_part = re.sub(r"\s+", " ", title_part).strip(" -")

    if not title_part and year_match:
        before_year = name[: year_match.start()]
        title_part = re.sub(r"[._]+", " ", before_year).strip()

    if not title_part:
        title_part = name

    return title_part, year


def group_key_for(title: str, year: int | None) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", title.lower().strip()).strip("-")
    if not slug:
        slug = "unknown"
    if year:
        return f"{slug}-{year}"
    return slug
