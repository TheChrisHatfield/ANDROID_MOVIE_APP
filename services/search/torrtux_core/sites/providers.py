"""Site adapters ported from torrtux-c (read-only upstream)."""
from urllib.parse import urljoin, quote

import requests
from bs4 import BeautifulSoup

from torrtux_core.base import TorrentSite

class PirateBay(TorrentSite):
    def __init__(self):
        super().__init__(
            "The Pirate Bay",
            [
                "https://thepiratebay.org",
                "https://tpb.party",
                "https://pirateproxy.live",
                "https://thehiddenbay.com",
                "https://piratebay.live",
                "https://thepiratebay.rocks",
                "https://tpb.pm",
                "https://piratebay.ink",
                "https://piratebayproxy.net",
                "https://thepiratebay10.org",
                "https://thepiratebay3.to"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/s/?q={quote(query)}&page={page}&orderby=99"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        table = soup.find("table", id="searchResult")
        if not table:
            return results
        for row in table.find_all("tr")[1:]:
            try:
                name_cell = row.find("a", class_="detLink")
                name = name_cell.get_text(strip=True) if name_cell else "-"
                detail_url = urljoin(self.working_url, name_cell["href"]) if name_cell else None
                magnet_link = row.find("a", href=lambda href: href and href.startswith("magnet:"))
                magnet = magnet_link["href"] if magnet_link else None
                desc_cell = row.find("font", class_="detDesc")
                desc_text = desc_cell.get_text().split(",") if desc_cell else []
                date = desc_text[0].replace("Uploaded ", "").strip() if len(desc_text) > 0 else "-"
                size = desc_text[1].replace("Size ", "").strip() if len(desc_text) > 1 else "-"
                seed_leeches = row.find_all("td", align="right")
                seeds = seed_leeches[0].get_text() if len(seed_leeches) > 0 else "-"
                leeches = seed_leeches[1].get_text() if len(seed_leeches) > 1 else "-"
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
            except Exception as e:
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
                name_cell = row.find("td", class_="name").find_all("a")[1]
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
                "https://yts.mx",
                "https://yts.rs",
                "https://yts.lt"
            ]
        )
    def build_search_url(self, query, page=0):
        return f"{self.working_url}/browse-movies/{quote(query)}/all/all/0/latest"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        for movie in soup.select(".browse-movie-wrap"):
            try:
                name = movie.select_one(".browse-movie-title").text.strip()
                year = movie.select_one(".browse-movie-year").text.strip()
                detail_url = urljoin(self.working_url, movie.select_one("a")['href'])
                seeds = "-"
                leeches = "-"
                size = "-"
                date = year
                magnet = None
                results.append({
                    "name": f"{name} ({year})",
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

    def get_magnet_link(self, detail_url: str | None) -> str | None:
        detail_url = self.absolute_detail_url(detail_url)
        if not detail_url:
            return None
        try:
            from torrtux_core.http_client import http_get

            response = http_get(detail_url, timeout=15)
            if response.status_code != 200:
                return None
            soup = BeautifulSoup(response.content, "lxml")
            for anchor in soup.find_all("a", href=True):
                href = anchor["href"]
                if href.startswith("magnet:"):
                    return href
            for anchor in soup.select("a.download-torrent, a[href*='magnet']"):
                href = anchor.get("href", "")
                if href.startswith("magnet:"):
                    return href
        except Exception:
            pass
        return super().get_magnet_link(detail_url)

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
        return f"{self.working_url}/search/{quote(query)}"
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
                detail_url = (
                    urljoin(self.working_url, name_link["href"])
                    if name_link and name_link["href"].startswith("/")
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
        return f"{self.working_url}/search/all/{quote(query)}/"
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
        return f"{self.working_url}/search?f={quote(query)}"
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
        first_letter = query[0].lower() if query else 'a'
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
        return f"{self.working_url}/?s={quote(query)}"
    def parse_results(self, content, query):
        soup = BeautifulSoup(content, "lxml")
        results = []
        for post in soup.select(".post"):
            try:
                name = post.find("h1", class_="post-title").text.strip()
                detail_url = urljoin(self.working_url, post.find("a")["href"])
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
        return f"{self.working_url}/index.php?page=torrents&search={quote(query)}"
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

