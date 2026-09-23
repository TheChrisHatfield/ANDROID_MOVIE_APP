from unittest.mock import patch

from metadata.tmdb_client import TmdbClient


@patch("metadata.tmdb_client.requests.get")
def test_search_movies_parses_results(mock_get):
    mock_get.return_value.status_code = 200
    mock_get.return_value.json.return_value = {
        "results": [
            {
                "id": 27205,
                "title": "Inception",
                "release_date": "2010-07-16",
                "poster_path": "/inception.jpg",
                "popularity": 50.0,
                "vote_average": 8.8,
            },
            {
                "id": 999,
                "title": "Inception Documentary",
                "release_date": "2012-01-01",
                "poster_path": None,
            },
        ]
    }
    client = TmdbClient(api_key="test-key")
    movies = client.search_movies("incep", limit=5)
    assert len(movies) == 2
    assert movies[0].title == "Inception"
    assert movies[0].year == 2010
    assert movies[0].poster_url == "https://image.tmdb.org/t/p/w92/inception.jpg"


@patch("metadata.tmdb_client.requests.get")
def test_search_movies_caches_repeat_calls(mock_get):
    mock_get.return_value.status_code = 200
    mock_get.return_value.json.return_value = {"results": []}
    client = TmdbClient(api_key="test-key")
    client.search_movies("dune", limit=3)
    client.search_movies("dune", limit=3)
    assert mock_get.call_count == 1
