from unittest.mock import MagicMock, patch

from fastapi.testclient import TestClient

from api.main import app, _searcher
from torrtux_core.searcher import SearchOutcome


client = TestClient(app)


@patch.object(_searcher, "test_sites", return_value=True)
@patch.object(_searcher, "search")
def test_search_returns_cached_results(mock_search, _mock_test):
    mock_search.return_value = SearchOutcome(
        results=[
            {
                "name": "Inception 2010",
                "site": "YTS",
                "size": "1.5 GB",
                "seeds": "120",
                "leeches": "4",
                "date": "2024",
                "magnet": "magnet:?xt=urn:btih:abc",
                "detail_url": None,
            }
        ],
        failed_sites=["EZTV"],
    )
    _searcher.working_sites = [MagicMock(name="YTS")]

    response = client.get("/v1/search", params={"q": "inception"})
    assert response.status_code == 200
    body = response.json()
    assert body["count"] == 1
    assert body["results"][0]["name"] == "Inception 2010"
    assert body["results"][0]["id"]
    assert body["failed_sites"] == ["EZTV"]


def test_health():
    assert client.get("/v1/health").json() == {"status": "ok"}


@patch.object(_searcher, "test_sites", return_value=True)
@patch.object(_searcher, "search")
def test_search_all_sources_failed_returns_503(mock_search, _mock_test):
    mock_search.return_value = SearchOutcome(results=[], failed_sites=["YTS"], all_sources_failed=True)
    _searcher.working_sites = [MagicMock(name="YTS")]

    response = client.get("/v1/search", params={"q": "inception"})
    assert response.status_code == 503


@patch.object(_searcher, "test_sites", return_value=True)
@patch.object(_searcher, "search")
def test_movie_indexers_unavailable_message(mock_search, _mock_test):
    mock_search.return_value = SearchOutcome([], [], movie_indexers_unavailable=True)
    _searcher.working_sites = [MagicMock(name="EZTV")]
    response = client.get("/v1/search", params={"q": "inception"})
    assert response.status_code == 503
    assert response.json()["detail"] == "No movie indexers available"


@patch.object(_searcher, "test_sites", return_value=True)
def test_invalid_max_size_returns_400(_mock_test):
    _searcher.working_sites = [MagicMock(name="YTS")]
    response = client.get("/v1/search", params={"q": "inception", "max_size": "garbage"})
    assert response.status_code == 400
    assert response.json()["detail"] == "Invalid max_size"


@patch.object(_searcher, "test_sites", return_value=True)
def test_empty_sites_param_returns_400(_mock_test):
    _searcher.working_sites = [MagicMock(name="YTS")]
    response = client.get("/v1/search", params={"q": "inception", "sites": ""})
    assert response.status_code == 400
