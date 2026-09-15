"""Torrent search orchestration (API-friendly, no CLI deps)."""
from __future__ import annotations

import logging
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass

from torrtux_core.filters import apply_filters, sort_by_seeds_desc
from torrtux_core.http_client import http_get
from torrtux_core.profiles import EXCLUDED_FROM_MOVIE_PROFILE, MOVIE_SITE_NAMES
from torrtux_core.sites import ALL_SITE_CLASSES

logger = logging.getLogger(__name__)


@dataclass
class SearchOutcome:
    results: list[dict]
    failed_sites: list[str]
    all_sources_failed: bool = False
    indexers_unavailable: bool = False
    movie_indexers_unavailable: bool = False


class TorrentSearcher:
    def __init__(self, site_classes=None):
        classes = site_classes or ALL_SITE_CLASSES
        self.sites = [cls() for cls in classes]
        self.working_sites: list = []
        self._lock = threading.Lock()

    def test_sites(self, quiet: bool = True) -> bool:
        with self._lock:
            self.working_sites = []
            for site in self.sites:
                if site.test_connection():
                    self.working_sites.append(site)
                    if not quiet:
                        logger.info("indexer ok: %s", site.name)
                elif not quiet:
                    logger.warning("indexer down: %s", site.name)
            return bool(self.working_sites)

    def filter_working_by_names(self, names: list[str]) -> None:
        wanted = {n.strip().lower() for n in names}
        self.working_sites = [s for s in self.working_sites if s.name.lower() in wanted]

    def apply_movie_profile(self) -> None:
        wanted = {n.lower() for n in MOVIE_SITE_NAMES}
        self.working_sites = [
            s
            for s in self.working_sites
            if s.name.lower() in wanted and s.name not in EXCLUDED_FROM_MOVIE_PROFILE
        ]

    def _search_site(self, site, query: str, page_limit: int) -> tuple[list[dict], bool]:
        results: list[dict] = []
        errored = False
        prev_url: str | None = None
        for page in range(page_limit):
            try:
                search_url = site.build_search_url(query, page)
            except Exception as exc:
                logger.debug("build_search_url failed %s: %s", site.name, exc)
                errored = page == 0 and not results
                break
            if not search_url or not isinstance(search_url, str) or not search_url.startswith("http"):
                errored = page == 0 and not results
                break
            if page > 0 and search_url == prev_url:
                break
            prev_url = search_url
            try:
                response = http_get(search_url, timeout=15)
            except Exception as exc:
                logger.debug("search request failed %s: %s", site.name, exc)
                errored = page == 0 and not results
                break
            if response.status_code != 200:
                errored = page == 0 and not results
                break
            if page > 0 and getattr(site, "_apibay_mode", False):
                break
            try:
                page_results = site.parse_results(response.content, query)
            except Exception as exc:
                logger.debug("parse_results failed %s page %s: %s", site.name, page, exc)
                errored = page == 0 and not results
                break
            if not page_results:
                break
            results.extend(page_results)
        return results, errored

    def search_all_sites(
        self,
        query: str,
        page_limit: int = 1,
        parallel: bool = True,
        sites: list | None = None,
    ) -> tuple[list[dict], list[str]]:
        active_sites = sites if sites is not None else self.working_sites
        if not active_sites:
            return [], []

        failed_sites: list[str] = []
        if parallel:
            all_results: list[dict] = []
            with ThreadPoolExecutor(max_workers=min(8, len(active_sites))) as pool:
                futures = {
                    pool.submit(self._search_site, site, query, page_limit): site
                    for site in active_sites
                }
                for future in as_completed(futures):
                    site = futures[future]
                    try:
                        site_results, errored = future.result()
                        if errored:
                            failed_sites.append(site.name)
                        all_results.extend(site_results)
                    except Exception as exc:
                        logger.warning("parallel search error %s: %s", site.name, exc)
                        failed_sites.append(site.name)
            return all_results, failed_sites

        all_results = []
        for site in active_sites:
            site_results, errored = self._search_site(site, query, page_limit)
            if errored:
                failed_sites.append(site.name)
            all_results.extend(site_results)
        return all_results, failed_sites

    def _select_working_sites(
        self,
        sites: list[str] | None,
        movie_profile: bool,
        pool: list | None = None,
    ) -> list:
        pool = list(pool if pool is not None else self.working_sites)
        if sites:
            wanted = {n.strip().lower() for n in sites if n.strip()}
            if not wanted:
                return []
            return [s for s in pool if s.name.lower() in wanted]
        if movie_profile:
            wanted = {n.lower() for n in MOVIE_SITE_NAMES}
            return [
                s
                for s in pool
                if s.name.lower() in wanted and s.name not in EXCLUDED_FROM_MOVIE_PROFILE
            ]
        return pool

    def search(
        self,
        query: str,
        *,
        sites: list[str] | None = None,
        movie_profile: bool = True,
        page_limit: int = 1,
        parallel: bool = True,
        min_seeds: int | None = None,
        max_seeds: int | None = None,
        min_size: str | None = None,
        max_size: str | None = None,
        limit: int | None = None,
    ) -> SearchOutcome:
        with self._lock:
            pool = list(self.working_sites)
        selected = self._select_working_sites(sites, movie_profile, pool)
        if sites and not selected and pool:
            return SearchOutcome([], [], indexers_unavailable=True)
        if movie_profile and not sites and not selected and pool:
            return SearchOutcome([], [], movie_indexers_unavailable=True)
        queried_names = {site.name for site in selected}
        raw, failed_sites = self.search_all_sites(
            query,
            page_limit=page_limit,
            parallel=parallel,
            sites=selected,
        )
        filtered = apply_filters(
            raw,
            min_seeds=min_seeds,
            max_seeds=max_seeds,
            min_size=min_size,
            max_size=max_size,
        )
        sorted_results = sort_by_seeds_desc(filtered)
        if limit is not None:
            sorted_results = sorted_results[:limit]
        all_sources_failed = bool(queried_names) and not sorted_results and queried_names.issubset(
            set(failed_sites)
        )
        return SearchOutcome(
            results=sorted_results,
            failed_sites=failed_sites,
            all_sources_failed=all_sources_failed,
        )
