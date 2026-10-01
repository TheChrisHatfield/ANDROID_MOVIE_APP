import time
from unittest.mock import MagicMock, patch

from torrtux_core import searcher as searcher_mod
from torrtux_core.searcher import SearchOutcome, TorrentSearcher


def test_search_applies_movie_profile_before_seed_filters():
    from torrtux_core.filters import filter_movie_profile

    class FakeSite:
        name = "YTS"

    searcher = TorrentSearcher(site_classes=[])
    searcher.working_sites = [FakeSite()]
    tv_row = {
        "name": "Some Show S01E01 1080p",
        "seeds": "-",
        "size": "1 GB",
        "site": "YTS",
    }
    movie_row = {
        "name": "Movie Title 2024 1080p",
        "seeds": "100",
        "size": "2 GB",
        "site": "YTS",
    }
    raw_rows = [tv_row, movie_row]

    with patch.object(searcher, "search_all_sites", return_value=(raw_rows, [])):
        out = searcher.search("test", min_seeds=50, movie_profile=True)

    assert [row["name"] for row in out.results] == [movie_row["name"]]
    assert tv_row not in filter_movie_profile(raw_rows)


def test_browse_1337x_applies_movie_profile_except_tv_feed():
    from torrtux_core.sites.providers import X1337

    searcher = TorrentSearcher(site_classes=[X1337])
    mock_site = MagicMock(spec=X1337)
    mock_site.name = "1337x"
    mock_site.working_url = "https://1337xx.to"
    searcher.working_sites = [mock_site]
    raw = [
        {"name": "Movie Title 2024 1080p", "seeds": "50", "size": "2 GB", "site": "1337x"},
        {"name": "Some Show S01E01 1080p", "seeds": "40", "size": "1 GB", "site": "1337x"},
    ]
    with patch.object(searcher, "_browse_site", return_value=(raw, False)):
        movies = searcher.browse_1337x("trending")
    assert len(movies.results) == 1
    assert movies.results[0]["name"].startswith("Movie Title")
    with patch.object(searcher, "_browse_site", return_value=(raw, False)):
        tv = searcher.browse_1337x("top-100-television", movie_profile=False)
    assert len(tv.results) == 2
    with patch.object(searcher, "_browse_site", return_value=(raw, False)):
        tv_blocked = searcher.browse_1337x("top-100-television", movie_profile=True)
    assert len(tv_blocked.results) == 0


def test_site_for_name_is_case_insensitive():
    searcher = TorrentSearcher(site_classes=[])
    mock_site = MagicMock()
    mock_site.name = "1337x"
    searcher.working_sites = [mock_site]
    assert searcher.site_for_name("1337X") is mock_site
    assert searcher.site_for_name("1337x") is mock_site


def test_site_for_detail_url_matches_unique_host():
    searcher = TorrentSearcher(site_classes=[])
    mock_site = MagicMock()
    mock_site.name = "1337x"
    mock_site.known_hosts.return_value = {"1337x.to", "www.1337x.to"}
    other = MagicMock()
    other.name = "YTS"
    other.known_hosts.return_value = {"yts.mx"}
    searcher.working_sites = [mock_site, other]
    searcher.sites = [mock_site, other]
    assert searcher.site_for_detail_url("https://1337x.to/torrent/1/inception/") is mock_site
    assert searcher.site_for_detail_url("https://unknown.example/torrent/1/") is None


def test_search_sorts_before_limit():
    searcher = TorrentSearcher(site_classes=[])
    searcher.working_sites = [MagicMock(name="MockSite")]

    raw_rows = [
        {"name": "low", "seeds": "5", "size": "1 GB", "site": "MockSite"},
        {"name": "high", "seeds": "99", "size": "1 GB", "site": "MockSite"},
        {"name": "mid", "seeds": "20", "size": "1 GB", "site": "MockSite"},
    ]

    with patch.object(searcher, "search_all_sites", return_value=(raw_rows, [])):
        out = searcher.search("test", limit=2, movie_profile=False)

    assert [row["name"] for row in out.results] == ["high", "mid"]


def test_search_interleaves_sites_before_limit():
    searcher = TorrentSearcher(site_classes=[])
    searcher.working_sites = [MagicMock(name="YTS"), MagicMock(name="1337x")]

    raw_rows = [
        {"name": "x1", "seeds": "500", "size": "1 GB", "site": "1337x"},
        {"name": "x2", "seeds": "400", "size": "1 GB", "site": "1337x"},
        {"name": "x3", "seeds": "300", "size": "1 GB", "site": "1337x"},
        {"name": "y1", "seeds": "10", "size": "1 GB", "site": "YTS"},
    ]

    with patch.object(searcher, "search_all_sites", return_value=(raw_rows, [])):
        out = searcher.search("test", limit=3, movie_profile=False)

    sites = [row["site"] for row in out.results]
    assert "YTS" in sites
    assert sites.count("1337x") == 2


def test_yts_detail_url_is_absolute():
    from torrtux_core.sites.providers import YTS

    site = YTS()
    site.working_url = "https://yts.mx"
    html = b"""
    <div class="browse-movie-wrap">
      <div class="browse-movie-title">Test Movie</div>
      <div class="browse-movie-year">2024</div>
      <a href="/movies/test-movie-2024">link</a>
    </div>
    """
    rows = site.parse_results(html, "test")
    assert len(rows) == 1
    assert rows[0]["detail_url"] == "https://yts.mx/movies/test-movie-2024"
    assert rows[0]["magnet"] is None


def test_empty_parse_is_not_indexer_failure():
    searcher = TorrentSearcher(site_classes=[])
    mock_site = MagicMock()
    mock_site.name = "MockSite"
    mock_site._apibay_mode = False
    mock_site.build_search_url.return_value = "http://example.com/search"

    with patch("torrtux_core.searcher.http_get") as mock_get:
        mock_get.return_value = MagicMock(status_code=200, content=b"<html></html>")
        mock_site.parse_results.return_value = []
        results, errored = searcher._search_site(mock_site, "test", 1)

    assert results == []
    assert errored is False


def test_parse_exception_keeps_partial_results():
    searcher = TorrentSearcher(site_classes=[])
    mock_site = MagicMock()
    mock_site.name = "MockSite"
    mock_site._apibay_mode = False
    mock_site.build_search_url.side_effect = [
        "http://example.com/search?page=1",
        "http://example.com/search?page=2",
    ]

    def parse_side_effect(_content, _query):
        if mock_site.build_search_url.call_count <= 1:
            return [{"name": "page1", "seeds": "1", "site": "MockSite"}]
        raise ValueError("bad html")

    mock_site.parse_results.side_effect = parse_side_effect

    with patch("torrtux_core.searcher.http_get") as mock_get:
        mock_get.return_value = MagicMock(status_code=200, content=b"<html></html>")
        results, errored = searcher._search_site(mock_site, "test", 2)

    assert len(results) == 1
    assert results[0]["name"] == "page1"
    assert errored is False


def test_http_error_marks_site_failed():
    searcher = TorrentSearcher(site_classes=[])
    mock_site = MagicMock()
    mock_site.name = "MockSite"
    mock_site.build_search_url.return_value = "http://example.com/search"

    with patch("torrtux_core.searcher.http_get") as mock_get:
        mock_get.return_value = MagicMock(status_code=503, content=b"")
        _, errored = searcher._search_site(mock_site, "test", 1)

    assert errored is True


def test_browse_genre_fans_out_to_movie_indexers():
    searcher = TorrentSearcher(site_classes=[])

    with patch.object(searcher, "search") as mock_search:
        mock_search.return_value = SearchOutcome(
            results=[{"name": "Horror Movie", "seeds": "10", "size": "1 GB", "site": "YTS"}],
            failed_sites=[],
        )
        out = searcher.browse_genre("horror", page_limit=1, limit=50)

    mock_search.assert_called_once_with(
        "horror",
        sites=None,
        movie_profile=True,
        page_limit=1,
        parallel=True,
        min_seeds=None,
        max_seeds=None,
        max_size=None,
        limit=50,
    )
    assert out.results[0]["name"] == "Horror Movie"


def test_parallel_search_fanout_timeout_returns_partial_results():
    searcher = TorrentSearcher(site_classes=[])

    class NamedSite:
        def __init__(self, name: str):
            self.name = name

    fast = NamedSite("Fast")
    slow = NamedSite("Slow")

    def search_side_effect(site, query, page_limit):
        if site.name == "Slow":
            time.sleep(0.3)
            return [{"name": "slow", "seeds": "1", "size": "1 GB", "site": "Slow"}], False
        time.sleep(0.05)
        return [{"name": "fast", "seeds": "1", "size": "1 GB", "site": "Fast"}], False

    with patch.object(searcher, "_search_site", side_effect=search_side_effect):
        with patch.object(searcher_mod, "search_fanout_timeout_sec", return_value=0.1):
            results, failed = searcher.search_all_sites("q", parallel=True, sites=[fast, slow])

    assert any(row["name"] == "fast" for row in results)
    assert "Slow" in failed


def test_sites_health_probe_deadline_keeps_fast_indexers():
    class FastSite:
        name = "Fast"

        def test_connection(self):
            return True

    class SlowSite:
        name = "Slow"

        def test_connection(self):
            time.sleep(0.4)
            return True

    searcher = TorrentSearcher(site_classes=[])
    searcher.sites = [FastSite(), SlowSite()]
    with patch.object(searcher_mod, "SITES_HEALTH_DEADLINE_SEC", 0.15):
        assert searcher.test_sites() is True
    assert [site.name for site in searcher.working_sites] == ["Fast"]


def test_browse_1337x_works_without_prior_health():
    from torrtux_core.sites.providers import X1337

    searcher = TorrentSearcher(site_classes=[X1337])
    searcher.working_sites = []
    searcher.sites[0].working_url = "https://1337xx.to"
    raw = [
        {"name": "Movie Title 2024 1080p", "seeds": "50", "size": "2 GB", "site": "1337x"},
    ]
    with patch.object(searcher, "_browse_site", return_value=(raw, False)):
        out = searcher.browse_1337x("trending")
    assert len(out.results) == 1
    assert out.indexers_unavailable is False
    assert any(site.name == "1337x" for site in searcher.working_sites)


def test_try_1337x_mirrors_advances_past_dead_host():
    class Site:
        name = "1337x"
        working_url = "https://bad.example"
        base_urls = ["https://bad.example", "https://good.example"]

    searcher = TorrentSearcher(site_classes=[])
    site = Site()
    calls: list[str] = []

    def attempt():
        calls.append(site.working_url)
        if site.working_url == "https://good.example":
            return [{"name": "ok", "seeds": "1", "size": "1 GB", "site": "1337x"}], False
        return [], True

    raw, errored = searcher._try_1337x_mirrors(site, attempt)
    assert not errored
    assert raw[0]["name"] == "ok"
    assert "https://good.example" in calls
def test_all_sources_failed_flag():
    searcher = TorrentSearcher(site_classes=[])
    mock_site = MagicMock()
    mock_site.name = "MockSite"
    searcher.working_sites = [mock_site]

    with patch.object(searcher, "search_all_sites", return_value=([], ["MockSite"])):
        out = searcher.search("test", movie_profile=False)

    assert out.results == []
    assert out.all_sources_failed is True


def test_indexer_http_timeout_matches_fr020():
    assert searcher_mod.INDEXER_HTTP_TIMEOUT == 15


def test_search_fanout_timeout_covers_default_two_pages():
    assert searcher_mod.search_fanout_timeout_sec(2) >= searcher_mod.INDEXER_HTTP_TIMEOUT * 2
    assert searcher_mod.search_fanout_timeout_sec(10) <= searcher_mod.SEARCH_FANOUT_TIMEOUT_CAP_SEC
