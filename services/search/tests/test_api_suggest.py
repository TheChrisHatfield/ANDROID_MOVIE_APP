from unittest.mock import patch

from fastapi.testclient import TestClient

from api.main import app
from metadata.tmdb_client import TmdbDiscoverMovie

client = TestClient(app)


@patch("api.main._tmdb")
def test_suggest_without_tmdb_returns_empty(mock_tmdb):
    mock_tmdb.configured = False
    body = client.get("/v1/suggest", params={"q": "inception"}).json()
    assert body["query"] == "inception"
    assert body["suggestions"] == []
    assert body["tmdb_configured"] is False


@patch("api.main._tmdb")
def test_suggest_returns_tmdb_titles(mock_tmdb):
    mock_tmdb.configured = True
    mock_tmdb.search_movies.return_value = [
        TmdbDiscoverMovie(
            tmdb_id=1,
            title="Inception",
            year=2010,
            poster_url="https://image.tmdb.org/t/p/w92/x.jpg",
        )
    ]
    response = client.get("/v1/suggest", params={"q": "ince", "limit": 5})
    assert response.status_code == 200
    body = response.json()
    assert body["suggestions"][0]["title"] == "Inception"
    assert body["suggestions"][0]["year"] == 2010
    mock_tmdb.search_movies.assert_called_once_with("ince", limit=5)


@patch("api.main._tmdb")
def test_suggest_short_query_returns_empty_without_calling_tmdb(mock_tmdb):
    body = client.get("/v1/suggest", params={"q": "a"}).json()
    assert body["suggestions"] == []
    mock_tmdb.search_movies.assert_not_called()
