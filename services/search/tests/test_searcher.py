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
        out = searcher.search("test", limit=2)

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


def test_all_sources_failed_flag():
    searcher = TorrentSearcher(site_classes=[])
    mock_site = MagicMock()
    mock_site.name = "MockSite"
    searcher.working_sites = [mock_site]

    with patch.object(searcher, "search_all_sites", return_value=([], ["MockSite"])):
        out = searcher.search("test", movie_profile=False)

    assert out.results == []
    assert out.all_sources_failed is True
