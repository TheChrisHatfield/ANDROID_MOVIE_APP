from unittest.mock import MagicMock, patch

from torrtux_core.searcher import TorrentSearcher


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


def test_all_sources_failed_flag():
    searcher = TorrentSearcher(site_classes=[])
    mock_site = MagicMock()
    mock_site.name = "MockSite"
    searcher.working_sites = [mock_site]

    with patch.object(searcher, "search_all_sites", return_value=([], ["MockSite"])):
        out = searcher.search("test", movie_profile=False)

    assert out.results == []
    assert out.all_sources_failed is True
