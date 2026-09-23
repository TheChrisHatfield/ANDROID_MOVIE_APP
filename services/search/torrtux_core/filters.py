"""Torrent result filters (ported from torrtux-c)."""
from __future__ import annotations

import re

_THOUSANDS_COMMA = re.compile(r"(?<=\d),(?=\d{3})")
_TV_SHOW_RE = re.compile(
    r"\b(?:s\d{1,2}e\d{1,2}|season\s+\d+|complete\s+series|"
    r"tv\s+series|episodes?\s+\d+|mini\s*series)\b",
    re.IGNORECASE,
)
_SOFTWARE_RE = re.compile(
    r"\b(?:windows\s+\d+|macos|linux\s+distro|adobe|photoshop|"
    r"microsoft\s+office|keygen|crackonly|audiobook|epub|ebook)\b",
    re.IGNORECASE,
)


def _normalize_size_label(size_str: str) -> str:
    return _THOUSANDS_COMMA.sub("", size_str.strip().upper())


def parse_size(size_str: str) -> float:
    """Return size in bytes, or -1.0 when the label cannot be parsed."""
    try:
        normalized = _normalize_size_label(size_str)
        for suffix, factor in (
            ("TIB", 1024**4),
            ("TB", 1024**4),
            ("GIB", 1024**3),
            ("GB", 1024**3),
            ("MIB", 1024**2),
            ("MB", 1024**2),
            ("KIB", 1024),
            ("KB", 1024),
        ):
            if normalized.endswith(suffix):
                return float(normalized[: -len(suffix)].strip()) * factor
        if normalized.endswith("B"):
            return float(normalized[:-1].strip())
        return float(normalized)
    except (ValueError, AttributeError):
        return -1.0


def filter_size_bytes(raw: str) -> float:
    """Parse a user-supplied size filter; raise ValueError when invalid."""
    normalized = _normalize_size_label(raw)
    has_unit = any(
        normalized.endswith(suffix)
        for suffix in ("TIB", "TB", "GIB", "GB", "MIB", "MB", "KIB", "KB", "B")
    )
    if not has_unit:
        raise ValueError(f"Invalid size filter: {raw}")
    value = parse_size(raw)
    if value < 0:
        raise ValueError(f"Invalid size filter: {raw}")
    return value


def seed_count(seeds: str) -> int | None:
    if not seeds or seeds == "-":
        return None
    normalized = str(seeds).replace(",", "").strip()
    if normalized.isdigit():
        return int(normalized)
    return None


def apply_filters(
    results: list[dict],
    *,
    min_seeds: int | None = None,
    max_seeds: int | None = None,
    min_size: str | None = None,
    max_size: str | None = None,
    limit: int | None = None,
) -> list[dict]:
    filtered = list(results)
    if min_seeds is not None and min_seeds > 0:
        filtered = [
            r
            for r in filtered
            if seed_count(r.get("seeds", "-")) is not None
            and seed_count(r["seeds"]) >= min_seeds
        ]
    if max_seeds is not None:
        filtered = [
            r
            for r in filtered
            if seed_count(r.get("seeds", "-")) is not None
            and seed_count(r["seeds"]) <= max_seeds
        ]
    if min_size is not None:
        min_bytes = filter_size_bytes(min_size)
        filtered = [
            r
            for r in filtered
            if r.get("size") not in (None, "-")
            and (size_bytes := parse_size(r["size"])) >= 0
            and size_bytes >= min_bytes
        ]
    if max_size is not None:
        max_bytes = filter_size_bytes(max_size)
        filtered = [
            r
            for r in filtered
            if (r.get("site") == "YTS" and r.get("size") in (None, "-"))
            or (
                r.get("size") not in (None, "-")
                and (size_bytes := parse_size(r["size"])) >= 0
                and size_bytes <= max_bytes
            )
        ]
    if limit is not None:
        filtered = filtered[:limit]
    return filtered


def is_likely_movie_release(name: str) -> bool:
    """Drop obvious TV packs and software when movie profile is enabled."""
    label = (name or "").strip()
    if not label:
        return False
    if _TV_SHOW_RE.search(label):
        return False
    if _SOFTWARE_RE.search(label):
        return False
    return True


def filter_movie_profile(results: list[dict]) -> list[dict]:
    return [row for row in results if is_likely_movie_release(str(row.get("name") or ""))]


def seed_sort_value(row: dict) -> int:
    value = seed_count(row.get("seeds", "-"))
    return value if value is not None else -1


def sort_by_seeds_desc(results: list[dict]) -> list[dict]:
    return sorted(results, key=seed_sort_value, reverse=True)


def _site_sort_key(site_name: str, site_order: list[str]) -> tuple[int, str]:
    lowered = site_name.casefold()
    order_index = {name.casefold(): index for index, name in enumerate(site_order)}
    return (order_index.get(lowered, len(site_order)), lowered)


def interleave_by_site(
    results: list[dict],
    *,
    limit: int | None = None,
    site_order: list[str] | None = None,
) -> list[dict]:
    """
    Round-robin merge across indexers so one high-seed site (often 1337x) cannot
    fill the entire result window. Within each site, rows stay seed-sorted.
    """
    if not results:
        return []
    from torrtux_core.profiles import MOVIE_SITE_NAMES

    order = site_order or MOVIE_SITE_NAMES
    by_site: dict[str, list[dict]] = {}
    for row in results:
        site = str(row.get("site") or "unknown").strip() or "unknown"
        key = site.casefold()
        by_site.setdefault(key, []).append(row)
    for bucket in by_site.values():
        bucket.sort(key=seed_sort_value, reverse=True)

    site_keys = sorted(by_site.keys(), key=lambda site: _site_sort_key(site, order))
    indices = {site: 0 for site in site_keys}
    merged: list[dict] = []
    while True:
        if limit is not None and len(merged) >= limit:
            break
        progressed = False
        for site in site_keys:
            if limit is not None and len(merged) >= limit:
                break
            bucket = by_site[site]
            index = indices[site]
            if index < len(bucket):
                merged.append(bucket[index])
                indices[site] = index + 1
                progressed = True
        if not progressed:
            break
    return merged
