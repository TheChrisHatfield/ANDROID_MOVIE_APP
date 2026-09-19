from unittest.mock import MagicMock, patch

from fastapi.testclient import TestClient

from api.main import app, _searcher
from torrtux_core.searcher import SearchOutcome
from torrtux_core.sites.providers import X1337


client = TestClient(app)


def test_x1337_build_browse_urls():
    site = X1337()
    site.working_url = "https://1337xx.to"
    assert site.build_browse_url("trending") == "https://1337xx.to/trending/"
    assert site.build_browse_url("top-100-movies") == "https://1337xx.to/top-100-movies/"
    assert site.build_browse_url("top-100-movies", use_fallback=True) == "https://1337xx.to/cat/Movies/1/"
    assert site.build_browse_url("top-100-television", 1) == "https://1337xx.to/top-100-television/2/"
    assert site.build_genre_browse_url("horror") == "https://1337xx.to/search/horror/1/"
    assert site.build_genre_browse_url("sci-fi", 1) == "https://1337xx.to/search/science%20fiction/2/"


@patch.object(_searcher, "browse_1337x")
def test_browse_1337x_endpoint(mock_browse):
    mock_browse.return_value = SearchOutcome(
        results=[
            {
                "name": "Trending Movie 2024 1080p",
                "site": "1337x",
                "size": "2 GB",
                "seeds": "500",
                "leeches": "10",
                "date": "Today",
                "magnet": None,
                "detail_url": "https://1337xx.to/torrent/1/trending/",
            }
        ],
        failed_sites=[],
    )
    response = client.get("/v1/browse/1337x/trending", params={"group": False})
    assert response.status_code == 200
    body = response.json()
    assert body["query"] == "1337x Trending"
    assert body["count"] == 1
    assert body["results"][0]["site"] == "1337x"
    mock_browse.assert_called_once()


@patch.object(_searcher, "browse_1337x")
def test_browse_rejects_pages_above_max(mock_browse):
    response = client.get("/v1/browse/1337x/trending", params={"pages": 10})
    assert response.status_code == 422
    mock_browse.assert_not_called()


@patch.object(_searcher, "browse_1337x")
def test_browse_1337x_unavailable(mock_browse):
    mock_browse.return_value = SearchOutcome([], [], indexers_unavailable=True)
    response = client.get("/v1/browse/1337x/top-100")
    assert response.status_code == 503


@patch.object(_searcher, "browse_1337x_genre")
def test_browse_1337x_genre_endpoint(mock_browse):
    mock_browse.return_value = SearchOutcome(
        results=[
            {
                "name": "Horror Movie 2024 1080p",
                "site": "1337x",
                "size": "2 GB",
                "seeds": "100",
                "leeches": "5",
                "date": "Today",
                "magnet": None,
                "detail_url": "https://1337xx.to/torrent/2/horror/",
            }
        ],
        failed_sites=[],
    )
    response = client.get("/v1/browse/1337x/genre/horror", params={"group": False})
    assert response.status_code == 200
    assert response.json()["query"] == "1337x Horror"
    mock_browse.assert_called_once_with("horror", page_limit=1, min_seeds=None, max_seeds=None, max_size=None, limit=100)


def test_browse_genre_rejects_unknown():
    response = client.get("/v1/browse/genre/not-a-genre")
    assert response.status_code == 400
    response = client.get("/v1/browse/1337x/genre/not-a-genre")
    assert response.status_code == 400


@patch("api.main._genre_service.browse")
def test_browse_genre_endpoint_uses_all_indexers(mock_browse):
    from api.main import _searcher

    mock_browse.return_value = SearchOutcome(
        results=[
            {
                "name": "Horror Movie 2024 1080p",
                "site": "YTS",
                "size": "2 GB",
                "seeds": "100",
                "leeches": "5",
                "date": "Today",
                "magnet": None,
                "detail_url": "https://yts.mx/movies/horror/",
            },
            {
                "name": "Scary Film 2023 720p",
                "site": "1337x",
                "size": "1.5 GB",
                "seeds": "80",
                "leeches": "3",
                "date": "Yesterday",
                "magnet": None,
                "detail_url": "https://1337xx.to/torrent/2/horror/",
            },
        ],
        failed_sites=["MagnetDL"],
    )
    _searcher.working_sites = [MagicMock(name="YTS")]
    response = client.get("/v1/browse/genre/horror", params={"group": False})
    assert response.status_code == 200
    body = response.json()
    assert body["query"] == "Horror"
    assert body["count"] == 2
    assert {row["site"] for row in body["results"]} == {"YTS", "1337x"}
    assert body["failed_sites"] == ["MagnetDL"]
    mock_browse.assert_called_once()
