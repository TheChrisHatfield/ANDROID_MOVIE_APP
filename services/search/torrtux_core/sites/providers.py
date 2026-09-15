"""Site adapters ported from torrtux-c (read-only upstream)."""
import json
import re
from urllib.parse import quote, urljoin

import requests
from bs4 import BeautifulSoup

from torrtux_core.base import TorrentSite
from torrtux_core.http_client import http_get

APIBAY_SEARCH_URL = "https://apibay.org/q.php"


def _bytes_to_size_label(raw: str | int) -> str:
    try:
        nbytes = int(raw)
    except (TypeError, ValueError):
        return str(raw)
    if nbytes >= 1024**3:
        return f"{nbytes / (1024**3):.2f} GB"
    if nbytes >= 1024**2:
        return f"{nbytes / (1024**2):.2f} MB"
    return f"{nbytes} B"


def _magnet_from_hash(info_hash: str, name: str) -> str:
    return f"magnet:?xt=urn:btih:{info_hash.upper()}&dn={quote(name)}"


def _extract_next_data(content: bytes) -> dict | None:
    text = content.decode("utf-8", errors="replace")
    match = re.search(
        r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>',
        text,
    )
    if not match:
        return None
    try:
        return json.loads(match.group(1))
    except json.JSONDecodeError:
        return None


class PirateBay(TorrentSite):
    def __init__(self):
        super().__init__(
            "The Pirate Bay",
            [
                "https://tpb.party",
                "https://pirateproxy.live",
                "https://thehiddenbay.com",
                "https://piratebay.live",
                "https://thepiratebay.rocks",
                "https://tpb.pm",
                "https://piratebay.ink",
                "https://piratebayproxy.net",
                "https://thepiratebay10.org",
                "https://thepiratebay3.to",
                "https://thepiratebay.org",
            ],
        )
        self._apibay_mode = False

    def test_connection(self) -> bool:
        for url in self.base_urls:
            try:
                probe_url = f"{url.rstrip('/')}/search/test/1/99/0"
                response = http_get(probe_url, timeout=12)
                if response.status_code == 200 and b'id="searchResult"' in response.content:
                    self.working_url = url.rstrip("/")
                    self._apibay_mode = False
                    return True
            except Exception:
                continue
        try:
            response = http_get(f"{APIBAY_SEARCH_URL}?q=test&cat=0", timeout=12)
            if response.status_code == 200:
                data = response.json()
                if isinstance(data, list):
                    self.working_url = "https://tpb.party"
                    self._apibay_mode = True
                    return True
        except Exception:
            pass
        return False

    def build_search_url(self, query, page=0):
        if self._apibay_mode:
            return f"{APIBAY_SEARCH_URL}?q={quote(query)}&cat=0"
        return f"{self.working_url}/search/{quote(query)}/{page + 1}/99/0"

    def _parse_apibay_rows(self, content: bytes) -> list[dict]:
        try:
            rows = json.loads(content)
        except json.JSONDecodeError:
            return []
        if not isinstance(rows, list):
            return []
        results = []
        mirror = self.working_url or "https://tpb.party"
        for row in rows:
            try:
                torrent_id = row.get("id")
                if torrent_id in (0, "0", None):
                    continue
                name = row.get("name", "-")
                if name == "No results returned":
                    continue
                info_hash = row.get("info_hash", "")
                if not info_hash or info_hash == "0000000000000000000000000000000000000000":
                    continue
                results.append(
                    {
                        "name": name,
                        "size": _bytes_to_size_label(row.get("size", "-")),
                        "seeds": str(row.get("seeders", "-")),
                        "leeches": str(row.get("leechers", "-")),
                        "date": "-",
                        "magnet": _magnet_from_hash(info_hash, name) if info_hash else None,
                        "detail_url": f"{mirror}/description.php?id={torrent_id}" if torrent_id else None,
                        "site": self.name,
                    }
                )
            except Exception:
                continue
        return results

    def parse_results(self, content, query):
        stripped = content.lstrip()
        if self._apibay_mode or stripped.startswith(b"[") or stripped.startswith(b"{"):
            return self._parse_apibay_rows(content)

        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", id="searchResult")
        if not table:
            return results
        for row in table.find_all("tr")[1:]:
            try:
                name_cell = row.find("a", class_="detLink") or row.find(
                    "a",
                    href=lambda href: href and "/torrent/" in href,
                )
                name = name_cell.get_text(strip=True) if name_cell else "-"
                detail_url = urljoin(self.working_url, name_cell["href"]) if name_cell else None
                magnet_link = row.find("a", href=lambda href: href and href.startswith("magnet:"))
                magnet = magnet_link["href"] if magnet_link else None
                desc_cell = row.find("font", class_="detDesc")
                if desc_cell:
                    desc_text = desc_cell.get_text().split(",")
                    date = desc_text[0].replace("Uploaded ", "").strip() if desc_text else "-"
                    size = desc_text[1].replace("Size ", "").strip() if len(desc_text) > 1 else "-"
                else:
                    cells = row.find_all("td")
                    date = cells[2].get_text(strip=True) if len(cells) > 2 else "-"
                    aligned = row.find_all("td", align="right")
                    size = aligned[0].get_text(strip=True) if len(aligned) > 0 else "-"
                aligned = row.find_all("td", align="right")
                seeds = aligned[1].get_text(strip=True) if len(aligned) > 1 else "-"
                leeches = aligned[2].get_text(strip=True) if len(aligned) > 2 else "-"
                results.append(
                    {
                        "name": name,
                        "size": size,
                        "seeds": seeds,
                        "leeches": leeches,
                        "date": date,
                        "magnet": magnet,
                        "detail_url": detail_url,
                        "site": self.name,
                    }
                )
            except Exception:
                continue
        return results

class X1337(TorrentSite):
    def __init__(self):
        super().__init__(
            "1337x",
            [
                "https://1337x.to",
                "https://1337x.st",
                "https://x1337x.ws",
                "https://1337x.gd",
                "https://1337x.is",
                "https://1337x.unblockit.boo"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/search/{quote(query)}/{page+1}/"

    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", class_="table-list")
        if not table:
            return results

        for row in table.find_all("tr")[1:]:
            try:
                name_td = row.find("td", class_="name")
                if not name_td:
                    continue
                links = name_td.find_all("a", href=True)
                name_cell = next(
                    (a for a in links if "/torrent/" in a["href"]),
                    links[-1] if links else None,
                )
                if not name_cell:
                    continue
                name = name_cell.text
                detail_url = urljoin(self.working_url, name_cell["href"])
                seeds = row.find("td", class_="seeds").text
                leeches = row.find("td", class_="leeches").text
                size = row.find("td", class_="size").text.split("B")[0] + "B"
                date = row.find("td", class_="coll-date").text

                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": None,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class YTS(TorrentSite):
    def __init__(self):
        super().__init__(
            "YTS",
            [
                "https://yts.rs",
                "https://yts.lt",
                "https://yts.mx",
            ],
        )

    def build_search_url(self, query, page=0):
        return f"{self.working_url}/browse-movies/{quote(query)}/all/all/{page}/latest"

    def _movie_rows_from_payload(self, payload: dict) -> list[dict]:
        movies = payload.get("props", {}).get("pageProps", {}).get("movies") or []
        results = []
        for movie in movies:
            torrents = movie.get("torrents") or []
            if not torrents:
                continue
            title = movie.get("title_long") or movie.get("title") or "-"
            slug = movie.get("slug")
            detail_url = self.absolute_detail_url(
                movie.get("url")
                or (urljoin(self.working_url, f"/movie/{slug}") if slug else None)
            )
            for torrent in torrents:
                info_hash = torrent.get("hash")
                seeds_val = int(torrent.get("seeds") or 0)
                peers_val = int(torrent.get("peers") or 0)
                leeches_val = max(peers_val - seeds_val, 0) if peers_val else None
                quality = torrent.get("quality")
                label = f"{title} [{quality}]" if quality else title
                results.append(
                    {
                        "name": label,
                        "size": torrent.get("size", "-"),
                        "seeds": str(torrent.get("seeds", "-")),
                        "leeches": str(leeches_val) if leeches_val is not None else "-",
                        "date": str(movie.get("year", "-")),
                        "magnet": _magnet_from_hash(info_hash, label) if info_hash else None,
                        "detail_url": detail_url,
                        "site": self.name,
                    }
                )
        return results

    def _movie_row_from_card(self, card) -> dict | None:
        title_el = card.select_one("a.title, .browse-movie-title")
        year_el = card.select_one("span.year, .browse-movie-year")
        link_el = card.select_one("a[href*='/movie/']")
        if not title_el or not link_el:
            return None
        name = title_el.get_text(strip=True)
        year = year_el.get_text(strip=True) if year_el else "-"
        detail_url = urljoin(self.working_url, link_el["href"])
        return {
            "name": f"{name} ({year})" if year != "-" else name,
            "size": "-",
            "seeds": "-",
            "leeches": "-",
            "date": year,
            "magnet": None,
            "detail_url": detail_url,
            "site": self.name,
        }

    def parse_results(self, content, query):
        payload = _extract_next_data(content)
        if payload:
            rows = self._movie_rows_from_payload(payload)
            if rows:
                return rows

        soup = BeautifulSoup(content, "lxml")
        results = []
        seen_urls: set[str] = set()
        for movie in soup.select(".browse-movie-wrap"):
            try:
                title_el = movie.select_one(".browse-movie-title")
                year_el = movie.select_one(".browse-movie-year")
                link_el = movie.select_one("a[href]")
                if not title_el or not link_el:
                    continue
                name = title_el.get_text(strip=True)
                year = year_el.get_text(strip=True) if year_el else "-"
                detail_url = urljoin(self.working_url, link_el["href"])
                if detail_url in seen_urls:
                    continue
                seen_urls.add(detail_url)
                results.append(
                    {
                        "name": f"{name} ({year})" if year != "-" else name,
                        "size": "-",
                        "seeds": "-",
                        "leeches": "-",
                        "date": year,
                        "magnet": None,
                        "detail_url": detail_url,
                        "site": self.name,
                    }
                )
            except Exception:
                continue
        seen_urls: set[str] = {row["detail_url"] for row in results if row.get("detail_url")}
        for movie in soup.select(".card"):
            try:
                row = self._movie_row_from_card(movie)
                if row and row.get("detail_url") not in seen_urls:
                    results.append(row)
                    seen_urls.add(row["detail_url"])
            except Exception:
                continue
        return results

    def _magnet_from_movie_payload(self, payload: dict, quality: str | None = None) -> str | None:
        page_props = payload.get("props", {}).get("pageProps", {})
        movie = page_props.get("movie")
        if movie is None and isinstance(page_props.get("props"), dict):
            movie = page_props["props"].get("movie")
        if not movie:
            return None
        torrents = movie.get("torrents") or []
        if not torrents:
            return None
        chosen = None
        if quality:
            chosen = next(
                (row for row in torrents if str(row.get("quality", "")).lower() == quality.lower()),
                None,
            )
        if chosen is None:
            chosen = max(torrents, key=lambda row: int(row.get("seeds") or 0))
        title = movie.get("title_long") or movie.get("title") or ""
        info_hash = chosen.get("hash")
        label = f"{title} [{chosen.get('quality')}]" if chosen.get("quality") else title
        return _magnet_from_hash(info_hash, label) if info_hash else None

    def get_magnet_link(self, detail_url: str | None, quality: str | None = None) -> str | None:
        detail_url = self.absolute_detail_url(detail_url)
        if not detail_url:
            return None
        try:
            response = http_get(detail_url, timeout=20)
            if response.status_code != 200:
                return None
            payload = _extract_next_data(response.content)
            if payload:
                magnet = self._magnet_from_movie_payload(payload, quality=quality)
                if magnet:
                    return magnet
            soup = BeautifulSoup(response.content, "lxml")
            for anchor in soup.find_all("a", href=True):
                href = anchor["href"]
                if href.startswith("magnet:"):
                    return href
        except Exception:
            pass
        return None

class Nyaa(TorrentSite):
    def __init__(self):
        super().__init__(
            "Nyaa",
            [
                "https://nyaa.si",
                "https://nyaa.net"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/?f=0&c=0_0&q={quote(query)}&p={page+1}"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", class_="torrent-list")
        if not table:
            table = soup.find("table", class_="table")
        if not table:
            return results
        for row in table.find_all("tr")[1:]:
            try:
                cols = row.find_all("td")
                if len(cols) < 7:
                    continue
                name = cols[1].find("a", href=True).text.strip()
                detail_url = urljoin(self.working_url, cols[1].find("a", href=True)["href"])
                size = cols[3].text.strip()
                date = cols[4].text.strip()
                seeds = cols[5].text.strip()
                leeches = cols[6].text.strip()
                magnet = cols[2].find("a", href=lambda h: h and h.startswith("magnet:"))
                magnet_el = magnet
                magnet = magnet_el["href"] if magnet_el else None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class EZTV(TorrentSite):
    def __init__(self):
        super().__init__(
            "EZTV",
            [
                "https://eztv.re",
                "https://eztv.wf",
                "https://eztv.unblockit.boo"
            ]
        )
    def build_search_url(self, query, page=0):
        base = f"{self.working_url}/search/{quote(query)}"
        return base if page == 0 else f"{base}/{page + 1}"

    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", class_="forum_header_border")
        if not table:
            return results
        for row in table.find_all("tr")[1:]:
            try:
                cols = row.find_all("td")
                if len(cols) < 5:
                    continue
                name = cols[1].get_text(strip=True)
                detail_url = urljoin(self.working_url, cols[1].find("a")["href"])
                size = cols[3].get_text(strip=True)
                date = cols[4].get_text(strip=True)
                seeds = "-"
                leeches = "-"
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class TorrentGalaxy(TorrentSite):
    def __init__(self):
        super().__init__(
            "TorrentGalaxy",
            [
                "https://torrentgalaxy.to",
                "https://tgx.rs"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/torrents.php?search={quote(query)}&page={page+1}"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", class_="tgxtable")
        if not table:
            return results
        for row in table.find_all("tr", class_="tgxtablerow"):
            try:
                cols = row.find_all("td")
                if len(cols) < 10:
                    continue
                name = cols[1].find("a").text.strip()
                detail_url = urljoin(self.working_url, cols[1].find("a")["href"])
                size = cols[5].text.strip()
                seeds = cols[7].text.strip()
                leeches = cols[8].text.strip()
                date = cols[4].text.strip()
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class LimeTorrents(TorrentSite):
    def __init__(self):
        super().__init__(
            "LimeTorrents",
            [
                "https://www.limetorrents.lol",
                "https://www.limetorrents.pro",
                "https://www.limetorrents.cyou",
                "https://www.limetorrents.zone"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/search/all/{quote(query)}/seeds/{page+1}/"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", id="table2") or soup.find("table", class_="table2")
        if not table:
            return results
        for row in table.find_all("tr")[1:]:
            try:
                cols = row.find_all("td")
                if len(cols) < 5:
                    continue
                name_link = next(
                    (a for a in cols[0].find_all("a", href=True) if a["href"].startswith("/")),
                    cols[0].find("a", href=True),
                )
                name = name_link.get_text(strip=True) if name_link else cols[0].get_text(strip=True)
                detail_url = None
                if name_link:
                    href = name_link["href"]
                    detail_url = (
                        urljoin(self.working_url, href)
                        if href.startswith("/")
                        else href
                        if href.startswith("http")
                        else None
                    )
                date = cols[1].text.strip()
                size = cols[2].text.strip()
                seeds = cols[3].text.strip().replace(",", "")
                leeches = cols[4].text.strip().replace(",", "")
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class TorrentDownloads(TorrentSite):
    def __init__(self):
        super().__init__(
            "TorrentDownloads",
            [
                "https://www.torrentdownloads.pro",
                "https://www.torrentdownloads.me",
                "https://www.torrentdownloads.unblockit.boo"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/search/?search={quote(query)}&page={page+1}"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", class_="torrent_table")
        if not table:
            return results
        for row in table.find_all("tr")[1:]:
            try:
                cols = row.find_all("td")
                if len(cols) < 5:
                    continue
                name = cols[0].find("a").text.strip()
                detail_url = urljoin(self.working_url, cols[0].find("a")["href"])
                size = cols[1].text.strip()
                date = cols[2].text.strip()
                seeds = cols[3].text.strip()
                leeches = cols[4].text.strip()
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class Torlock(TorrentSite):
    def __init__(self):
        super().__init__(
            "Torlock",
            [
                "https://www.torlock.com",
                "https://torlock.unblocked.lol"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/all/torrents/{quote(query)}.html?page={page+1}"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", id="torrenttable")
        if not table:
            return results
        for row in table.find_all("tr")[1:]:
            try:
                cols = row.find_all("td")
                if len(cols) < 7:
                    continue
                name = cols[0].find("a").text.strip()
                detail_url = urljoin(self.working_url, cols[0].find("a")["href"])
                size = cols[3].text.strip()
                date = cols[4].text.strip()
                seeds = cols[5].text.strip()
                leeches = cols[6].text.strip()
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class Zooqle(TorrentSite):
    def __init__(self):
        super().__init__(
            "Zooqle",
            [
                "https://zooqle.com",
                "https://zooqle.unblockit.boo"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/search?q={quote(query)}&pg={page+1}"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        for row in soup.select(".torrent-list tbody tr"):
            try:
                cols = row.find_all("td")
                if len(cols) < 8:
                    continue
                name = cols[1].find("a").text.strip()
                detail_url = urljoin(self.working_url, cols[1].find("a")["href"])
                size = cols[5].text.strip()
                seeds = cols[6].text.strip()
                leeches = cols[7].text.strip()
                date = "-"
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class TorrentFunk(TorrentSite):
    def __init__(self):
        super().__init__(
            "TorrentFunk",
            [
                "https://www.torrentfunk.com"
            ]
        )
    def build_search_url(self, query, page=0):
        base = f"{self.working_url}/search/all/{quote(query)}/"
        return base if page == 0 else f"{base}{page + 1}/"

    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        for row in soup.select(".search-results .odd, .search-results .even"):
            try:
                name = row.find("a", class_="torrent-name").text.strip()
                detail_url = urljoin(self.working_url, row.find("a", class_="torrent-name")["href"])
                size = row.find("td", class_="size").text.strip()
                seeds = row.find("td", class_="seeds").text.strip()
                leeches = row.find("td", class_="leeches").text.strip()
                date = "-"
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class ETTV(TorrentSite):
    def __init__(self):
        super().__init__(
            "ETTV",
            [
                "https://www.ettvdl.com",
                "https://ettvcentral.com"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/torrents-search.php?search={quote(query)}&page={page+1}"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", class_="table")
        if not table:
            return results
        for row in table.find_all("tr")[1:]:
            try:
                cols = row.find_all("td")
                if len(cols) < 7:
                    continue
                name = cols[1].find("a").text.strip()
                detail_url = urljoin(self.working_url, cols[1].find("a")["href"])
                size = cols[2].text.strip()
                seeds = cols[5].text.strip()
                leeches = cols[6].text.strip()
                date = cols[3].text.strip()
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class Bitsearch(TorrentSite):
    def __init__(self):
        super().__init__(
            "Bitsearch",
            [
                "https://bitsearch.to"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/search?q={quote(query)}&page={page+1}"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        for row in soup.select(".search-results .result"):
            try:
                name = row.find("a", class_="name").text.strip()
                detail_url = urljoin(self.working_url, row.find("a", class_="name")["href"])
                size = row.find("span", class_="size").text.strip()
                seeds = row.find("span", class_="seeds").text.strip()
                leeches = row.find("span", class_="leeches").text.strip()
                date = "-"
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class Glodls(TorrentSite):
    def __init__(self):
        super().__init__(
            "Glodls",
            [
                "https://glodls.to"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/search_results.php?search={quote(query)}&page={page+1}"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", class_="table")
        if not table:
            return results
        for row in table.find_all("tr")[1:]:
            try:
                cols = row.find_all("td")
                if len(cols) < 7:
                    continue
                name = cols[1].find("a").text.strip()
                detail_url = urljoin(self.working_url, cols[1].find("a")["href"])
                size = cols[2].text.strip()
                seeds = cols[5].text.strip()
                leeches = cols[6].text.strip()
                date = cols[3].text.strip()
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class TorrentProject(TorrentSite):
    def __init__(self):
        super().__init__(
            "TorrentProject",
            [
                "https://torrentproject2.com",
                "https://torrentproject.se"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/?t={quote(query)}&page={page+1}"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        for row in soup.select(".table tr"):
            try:
                cols = row.find_all("td")
                if len(cols) < 6:
                    continue
                name = cols[0].find("a").text.strip()
                detail_url = urljoin(self.working_url, cols[0].find("a")["href"])
                size = cols[2].text.strip()
                seeds = cols[3].text.strip()
                leeches = cols[4].text.strip()
                date = cols[1].text.strip()
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class SkyTorrents(TorrentSite):
    def __init__(self):
        super().__init__(
            "SkyTorrents",
            [
                "https://www.skytorrents.lol",
                "https://skytorrents.unblockit.boo"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/search/all/{quote(query)}/page/{page+1}/"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", class_="table")
        if not table:
            return results
        for row in table.find_all("tr")[1:]:
            try:
                cols = row.find_all("td")
                if len(cols) < 7:
                    continue
                name = cols[1].find("a").text.strip()
                detail_url = urljoin(self.working_url, cols[1].find("a")["href"])
                size = cols[2].text.strip()
                seeds = cols[5].text.strip()
                leeches = cols[6].text.strip()
                date = cols[3].text.strip()
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class YourBittorrent(TorrentSite):
    def __init__(self):
        super().__init__(
            "YourBittorrent",
            [
                "https://yourbittorrent.com"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/?q={quote(query)}&page={page+1}"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        for row in soup.select(".search-result .row"):
            try:
                name = row.find("a", class_="torrent-name").text.strip()
                detail_url = urljoin(self.working_url, row.find("a", class_="torrent-name")["href"])
                size = row.find("span", class_="size").text.strip()
                seeds = row.find("span", class_="seeds").text.strip()
                leeches = row.find("span", class_="leeches").text.strip()
                date = "-"
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class KickassTorrents(TorrentSite):
    def __init__(self):
        super().__init__(
            "KickassTorrents",
            [
                "https://kickasstorrents.to",
                "https://katcr.to",
                "https://kickasstorrents.bz"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/usearch/{quote(query)}/{page+1}/"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", class_="data")
        if not table:
            return results
        for row in table.find_all("tr", class_="odd") + table.find_all("tr", class_="even"):
            try:
                cols = row.find_all("td")
                if len(cols) < 5:
                    continue
                name = cols[0].find("a", class_="cellMainLink").text.strip()
                detail_url = urljoin(self.working_url, cols[0].find("a", class_="cellMainLink")["href"])
                size = cols[1].text.strip()
                seeds = cols[2].text.strip()
                leeches = cols[3].text.strip()
                date = cols[4].text.strip()
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class Torrentz2(TorrentSite):
    def __init__(self):
        super().__init__(
            "Torrentz2",
            [
                "https://torrentz2.nz",
                "https://torrentz2.is",
                "https://torrentz2.eu"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/search?f={quote(query)}&p={page}"

    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        for row in soup.select(".results dl"):
            try:
                name = row.find("a").text.strip()
                detail_url = urljoin(self.working_url, row.find("a")["href"])
                # Torrentz2 does not provide size/seeds/leeches directly
                size = "-"
                seeds = "-"
                leeches = "-"
                date = "-"
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class RARBG(TorrentSite):
    def __init__(self):
        super().__init__(
            "RARBG",
            [
                "https://rarbgmirror.com",
                "https://rarbgprx.org"
            ]
        )

    def test_connection(self) -> bool:
        return False

    def build_search_url(self, query, page=0):
        # Archive only, no real search, so return None
        return None
    def parse_results(self, content, query):
        return []

class MagnetDL(TorrentSite):
    def __init__(self):
        super().__init__(
            "MagnetDL",
            [
                "https://www.magnetdl.com",
                "https://magnetdl.unblockit.boo"
            ]
        )
    def build_search_url(self, query, page=0):
        # MagnetDL uses the first letter of the query in the URL path
        first_letter = next((c for c in query.lower() if c.isalnum()), "a")
        return f"{self.working_url}/{first_letter}/{quote(query)}/?page={page+1}"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", class_="download")
        if not table:
            return results
        for row in table.find_all("tr")[1:]:
            try:
                cols = row.find_all("td")
                if len(cols) < 7:
                    continue
                name = cols[0].find("a").text.strip()
                detail_url = urljoin(self.working_url, cols[0].find("a")["href"])
                size = cols[3].text.strip()
                seeds = cols[4].text.strip()
                leeches = cols[5].text.strip()
                date = cols[2].text.strip()
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class GoodTorrent(TorrentSite):
    def __init__(self):
        super().__init__(
            "Good-Torrent",
            ["https://good-torrent.com"]
        )

    def test_connection(self) -> bool:
        return False

    def build_search_url(self, query, page=0):
        return f"{self.working_url}/search/{quote(query)}"
    def parse_results(self, content, query):
        # Initial: No detailed parsing yet, to be improved after site structure analysis.
        soup = BeautifulSoup(content, "lxml")
        results = []
        # Can be improved later
        return results

class ArabTorrents(TorrentSite):
    def __init__(self):
        super().__init__(
            "Arab-Torrents",
            ["https://www.arab-torrents.net"]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/torrents-search.php?search={quote(query)}&page={page+1}"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", class_="table")
        if not table:
            return results
        for row in table.find_all("tr")[1:]:
            try:
                cols = row.find_all("td")
                if len(cols) < 7:
                    continue
                name = cols[1].find("a").text.strip()
                detail_url = urljoin(self.working_url, cols[1].find("a")["href"])
                size = cols[2].text.strip()
                seeds = cols[5].text.strip()
                leeches = cols[6].text.strip()
                date = cols[3].text.strip()
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class FitGirlRepacks(TorrentSite):
    def __init__(self):
        super().__init__(
            "FitGirl Repacks",
            ["https://fitgirl-repacks.site"]
        )
    def build_search_url(self, query, page=0):
        base = f"{self.working_url}/?s={quote(query)}"
        return base if page == 0 else f"{self.working_url}/page/{page + 1}/?s={quote(query)}"

    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        for post in soup.select(".post"):
            try:
                title = post.find("h1", class_="entry-title") or post.find("h1", class_="post-title")
                if not title:
                    continue
                link = title.find("a")
                if not link or not link.get("href"):
                    continue
                name = title.get_text(strip=True)
                detail_url = urljoin(self.working_url, link["href"])
                size = "-"
                seeds = "-"
                leeches = "-"
                date = "-"
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

class LinuxTracker(TorrentSite):
    def __init__(self):
        super().__init__(
            "LinuxTracker",
            ["https://linuxtracker.org"]
        )
    def build_search_url(self, query, page=0):
        return (
            f"{self.working_url}/index.php?page=torrents"
            f"&search={quote(query)}&pages={page + 1}"
        )

    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", class_="torrents")
        if not table:
            return results
        for row in table.find_all("tr")[1:]:
            try:
                cols = row.find_all("td")
                if len(cols) < 7:
                    continue
                name = cols[1].find("a").text.strip()
                detail_url = urljoin(self.working_url, cols[1].find("a")["href"])
                size = cols[4].text.strip()
                seeds = cols[5].text.strip()
                leeches = cols[6].text.strip()
                date = cols[3].text.strip()
                magnet = None
                results.append({
                    "name": name,
                    "size": size,
                    "seeds": seeds,
                    "leeches": leeches,
                    "date": date,
                    "magnet": magnet,
                    "detail_url": detail_url,
                    "site": self.name
                })
            except:
                continue
        return results

