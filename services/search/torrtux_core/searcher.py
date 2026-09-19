"""Torrent search orchestration (API-friendly, no CLI deps)."""
from __future__ import annotations

import logging
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass

from torrtux_core.filters import apply_filters, filter_movie_profile, sort_by_seeds_desc
from torrtux_core.http_client import http_get
from torrtux_core.profiles import EXCLUDED_FROM_MOVIE_PROFILE, MOVIE_SITE_NAMES
from torrtux_core.sites import ALL_SITE_CLASSES

logger = logging.getLogger(__name__)

INDEXER_HTTP_TIMEOUT = 8
SEARCH_FANOUT_TIMEOUT_SEC = 22


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

    def site_for_name(self, site_name: str | None):
        """Thread-safe lookup of an indexer by display name."""
        if not site_name:
            return None
        with self._lock:
            working = list(self.working_sites)
            all_sites = list(self.sites)
        lowered = site_name.casefold()
        site = next((s for s in working if s.name.casefold() == lowered), None)
        if site is None:
            site = next((s for s in all_sites if s.name.casefold() == lowered), None)
        return site

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
                response = http_get(search_url, timeout=INDEXER_HTTP_TIMEOUT)
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
                try:
                    completed = as_completed(futures, timeout=SEARCH_FANOUT_TIMEOUT_SEC)
                    for future in completed:
                        site = futures[future]
                        try:
                            site_results, errored = future.result()
                            if errored:
                                failed_sites.append(site.name)
                            all_results.extend(site_results)
                        except Exception as exc:
                            logger.warning("parallel search error %s: %s", site.name, exc)
                            failed_sites.append(site.name)
                except TimeoutError:
                    pending = [site.name for future, site in futures.items() if not future.done()]
                    for future in futures:
                        future.cancel()
                    if pending:
                        logger.warning(
                            "search fan-out timed out after %ss; skipping %s",
                            SEARCH_FANOUT_TIMEOUT_SEC,
                            ", ".join(pending),
                        )
                        failed_sites.extend(pending)
            return all_results, failed_sites

        all_results = []
        for site in active_sites:
            site_results, errored = self._search_site(site, query, page_limit)
            if errored:
                failed_sites.append(site.name)
            all_results.extend(site_results)
        return all_results, failed_sites

    def _browse_site(self, site, feed: str, page_limit: int) -> tuple[list[dict], bool]:
        from torrtux_core.sites.providers import X1337

        results, errored = self._browse_site_pages(site, feed, page_limit, use_fallback=False)
        if not results and isinstance(site, X1337) and feed in site.BROWSE_FALLBACKS:
            fallback_results, fallback_errored = self._browse_site_pages(
                site, feed, page_limit, use_fallback=True,
            )
            if fallback_results:
                return fallback_results, False
            errored = errored or fallback_errored
        return results, errored

    def _browse_site_pages(
        self,
        site,
        feed: str,
        page_limit: int,
        *,
        use_fallback: bool,
    ) -> tuple[list[dict], bool]:
        results: list[dict] = []
        errored = False
        prev_url: str | None = None
        for page in range(page_limit):
            try:
                browse_url = site.build_browse_url(feed, page, use_fallback=use_fallback)
            except Exception as exc:
                logger.debug("build_browse_url failed %s: %s", site.name, exc)
                errored = page == 0 and not results
                break
            if not browse_url.startswith("http"):
                errored = page == 0 and not results
                break
            if page > 0 and browse_url == prev_url:
                break
            prev_url = browse_url
            try:
                response = http_get(browse_url, timeout=INDEXER_HTTP_TIMEOUT)
            except Exception as exc:
                logger.debug("browse request failed %s: %s", site.name, exc)
                errored = page == 0 and not results
                break
            if response.status_code != 200:
                errored = page == 0 and not results
                break
            try:
                page_results = site.parse_results(response.content, feed)
            except Exception as exc:
                logger.debug("parse_results failed %s browse page %s: %s", site.name, page, exc)
                errored = page == 0 and not results
                break
            if not page_results:
                break
            results.extend(page_results)
        return results, errored

    def browse_1337x(
        self,
        feed: str,
        *,
        movie_profile: bool = True,
        page_limit: int = 1,
        min_seeds: int | None = None,
        max_seeds: int | None = None,
        max_size: str | None = None,
        limit: int | None = None,
    ) -> SearchOutcome:
        from torrtux_core.sites.providers import X1337

        with self._lock:
            pool = list(self.working_sites)
        site = next((s for s in pool if s.name == "1337x"), None)
        if site is None:
            candidate = next((s for s in self.sites if s.name == "1337x"), None)
            if candidate and candidate.test_connection():
                with self._lock:
                    if candidate not in self.working_sites:
                        self.working_sites.append(candidate)
                site = candidate
        if site is None or not isinstance(site, X1337):
            return SearchOutcome([], [], indexers_unavailable=True)
        if feed not in X1337.BROWSE_FEEDS:
            return SearchOutcome([], [], indexers_unavailable=True)

        from torrtux_core.filters import filter_movie_profile

        raw, errored = self._browse_site(site, feed, page_limit)
        if feed == "top-100-television":
            profiled = raw if not movie_profile else []
        elif movie_profile:
            profiled = filter_movie_profile(raw)
        else:
            profiled = raw
        filtered = apply_filters(
            profiled,
            min_seeds=min_seeds,
            max_seeds=max_seeds,
            max_size=max_size,
        )
        sorted_results = sort_by_seeds_desc(filtered)
        if limit is not None:
            sorted_results = sorted_results[:limit]
        failed_sites = ["1337x"] if errored and not sorted_results else []
        all_sources_failed = errored and not sorted_results
        return SearchOutcome(
            results=sorted_results,
            failed_sites=failed_sites,
            all_sources_failed=all_sources_failed,
        )

    def _browse_genre_pages(
        self,
        site,
        genre: str,
        page_limit: int,
    ) -> tuple[list[dict], bool]:
        from torrtux_core.sites.providers import X1337

        if not isinstance(site, X1337):
            return [], True
        results: list[dict] = []
        errored = False
        prev_url: str | None = None
        for page in range(page_limit):
            try:
                browse_url = site.build_genre_browse_url(genre, page)
            except Exception as exc:
                logger.debug("build_genre_browse_url failed %s: %s", site.name, exc)
                errored = page == 0 and not results
                break
            if page > 0 and browse_url == prev_url:
                break
            prev_url = browse_url
            try:
                response = http_get(browse_url, timeout=INDEXER_HTTP_TIMEOUT)
            except Exception as exc:
                logger.debug("genre browse request failed %s: %s", site.name, exc)
                errored = page == 0 and not results
                break
            if response.status_code != 200:
                errored = page == 0 and not results
                break
            try:
                page_results = site.parse_results(response.content, genre)
            except Exception as exc:
                logger.debug("parse_results failed %s genre page %s: %s", site.name, page, exc)
                errored = page == 0 and not results
                break
            if not page_results:
                break
            results.extend(page_results)
        return results, errored

    def browse_genre(
        self,
        genre: str,
        *,
        sites: list[str] | None = None,
        movie_profile: bool = True,
        page_limit: int = 1,
        parallel: bool = True,
        min_seeds: int | None = None,
        max_seeds: int | None = None,
        max_size: str | None = None,
        limit: int | None = None,
        tmdb=None,
    ) -> SearchOutcome:
        from metadata.genre_browse import curated_genre_search, keyword_genre_search
        from torrtux_core.genres import tmdb_genre_id

        normalized = genre.strip().lower()
        if not tmdb_genre_id(normalized):
            return SearchOutcome([], [], indexers_unavailable=True)

        if tmdb is not None:
            curated = curated_genre_search(
                self,
                normalized,
                tmdb,
                sites=sites,
                movie_profile=movie_profile,
                min_seeds=min_seeds,
                max_seeds=max_seeds,
                max_size=max_size,
                limit=limit,
            )
            if curated is not None:
                return curated

        return keyword_genre_search(
            self,
            normalized,
            sites=sites,
            movie_profile=movie_profile,
            page_limit=page_limit,
            parallel=parallel,
            min_seeds=min_seeds,
            max_seeds=max_seeds,
            max_size=max_size,
            limit=limit,
        )

    def browse_1337x_genre(
        self,
        genre: str,
        *,
        page_limit: int = 1,
        min_seeds: int | None = None,
        max_seeds: int | None = None,
        max_size: str | None = None,
        limit: int | None = None,
    ) -> SearchOutcome:
        from torrtux_core.filters import filter_movie_profile
        from torrtux_core.sites.providers import X1337

        with self._lock:
            pool = list(self.working_sites)
        site = next((s for s in pool if s.name == "1337x"), None)
        if site is None:
            candidate = next((s for s in self.sites if s.name == "1337x"), None)
            if candidate and candidate.test_connection():
                with self._lock:
                    if candidate not in self.working_sites:
                        self.working_sites.append(candidate)
                site = candidate
        if site is None or not isinstance(site, X1337):
            return SearchOutcome([], [], indexers_unavailable=True)
        if genre not in X1337.MOVIE_GENRES:
            return SearchOutcome([], [], indexers_unavailable=True)

        raw, errored = self._browse_genre_pages(site, genre, page_limit)
        filtered = apply_filters(
            filter_movie_profile(raw),
            min_seeds=min_seeds,
            max_seeds=max_seeds,
            max_size=max_size,
        )
        sorted_results = sort_by_seeds_desc(filtered)
        if limit is not None:
            sorted_results = sorted_results[:limit]
        failed_sites = ["1337x"] if errored and not sorted_results else []
        all_sources_failed = errored and not sorted_results
        return SearchOutcome(
            results=sorted_results,
            failed_sites=failed_sites,
            all_sources_failed=all_sources_failed,
        )

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
        profiled = filter_movie_profile(raw) if movie_profile else raw
        filtered = apply_filters(
            profiled,
            min_seeds=min_seeds,
            max_seeds=max_seeds,
            min_size=min_size,
            max_size=max_size,
        )
        sorted_results = sort_by_seeds_desc(filtered)
        if limit is not None:
            sorted_results = sorted_results[:limit]
        failed = set(failed_sites)
        all_sources_failed = (
            bool(queried_names)
            and not sorted_results
            and queried_names.issubset(failed)
            and len(failed) >= len(queried_names)
        )
        return SearchOutcome(
            results=sorted_results,
            failed_sites=failed_sites,
            all_sources_failed=all_sources_failed,
        )
