"""Torrent result filters (ported from torrtux-c)."""
from __future__ import annotations


def parse_size(size_str: str) -> float:
    """Return size in bytes, or -1.0 when the label cannot be parsed."""
    try:
        normalized = size_str.strip().upper().replace(",", "")
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
    if min_seeds is not None:
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
            if r.get("size") not in (None, "-")
            and (size_bytes := parse_size(r["size"])) >= 0
            and size_bytes <= max_bytes
        ]
    if limit is not None:
        filtered = filtered[:limit]
    return filtered


def sort_by_seeds_desc(results: list[dict]) -> list[dict]:
    def key(row: dict) -> int:
        value = seed_count(row.get("seeds", "-"))
        return value if value is not None else -1

    return sorted(results, key=key, reverse=True)
