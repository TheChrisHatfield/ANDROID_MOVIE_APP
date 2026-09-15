"""Base torrent site adapter (ported from torrtux-c, CLI deps removed)."""
from __future__ import annotations

import logging
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from torrtux_core.http_client import http_get

logger = logging.getLogger(__name__)


class TorrentSite:
    def __init__(self, name: str, base_urls, search_path: str = "", result_selector: str = ""):
        self.name = name
        self.base_urls = base_urls if isinstance(base_urls, list) else [base_urls]
        self.search_path = search_path
        self.result_selector = result_selector
        self.working_url = None

    def test_connection(self) -> bool:
        for url in self.base_urls:
            try:
                response = http_get(url, timeout=10, allow_redirects=True)
                if response.status_code == 200:
                    self.working_url = url
                    return True
            except Exception:
                continue
        return False

    def build_search_url(self, query: str, page: int = 0) -> str:
        raise NotImplementedError

    def parse_results(self, content: bytes, query: str) -> list[dict]:
        raise NotImplementedError

    def absolute_detail_url(self, detail_url: str | None) -> str | None:
        if not detail_url:
            return None
        if self.working_url and not detail_url.startswith(("http://", "https://")):
            return urljoin(self.working_url, detail_url)
        return detail_url

    def make_result(
        self,
        *,
        name: str,
        size: str = "-",
        seeds: str = "-",
        leeches: str = "-",
        date: str = "-",
        detail_url: str | None,
        magnet: str | None = None,
    ) -> dict:
        return {
            "name": name,
            "size": size,
            "seeds": seeds,
            "leeches": leeches,
            "date": date,
            "magnet": magnet,
            "detail_url": self.absolute_detail_url(detail_url),
            "site": self.name,
        }

    def get_magnet_link(self, detail_url: str | None) -> str | None:
        detail_url = self.absolute_detail_url(detail_url)
        if not detail_url:
            return None
        try:
            response = http_get(detail_url, timeout=15)
            if response.status_code == 200:
                soup = BeautifulSoup(response.content, "lxml")
                for anchor in soup.find_all("a", href=True):
                    if anchor["href"].startswith("magnet:"):
                        return anchor["href"]
        except Exception as exc:
            logger.warning("magnet fetch failed for %s: %s", self.name, exc)
        return None

    def build_latest_url(self, page: int = 0) -> str | None:
        try:
            return self.build_search_url("", page)
        except Exception:
            return None
