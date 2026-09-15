"""Shared HTTP helpers for indexer requests."""
from __future__ import annotations

import requests

DEFAULT_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
}


def http_get(url: str, *, timeout: int = 15, allow_redirects: bool = True) -> requests.Response:
    return requests.get(
        url,
        timeout=timeout,
        headers=DEFAULT_HEADERS,
        allow_redirects=allow_redirects,
    )
