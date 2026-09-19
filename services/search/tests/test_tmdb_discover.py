from unittest.mock import patch

from metadata.tmdb_client import TmdbClient


@patch("metadata.tmdb_client.requests.get")
def test_discover_movies_parses_popular_results(mock_get):
    mock_get.return_value.status_code = 200
    mock_get.return_value.json.return_value = {
        "results": [
            {
                "id": 42,
                "title": "Inception",
                "release_date": "2010-07-16",
                "overview": "A mind-bending thriller.",
                "poster_path": "/inception.jpg",
                "popularity": 88.5,
                "vote_average": 8.4,
            }
        ]
    }
    client = TmdbClient(api_key="test-key")
    movies = client.discover_movies(878, limit=5)
    assert len(movies) == 1
    assert movies[0].title == "Inception"
    assert movies[0].year == 2010
    assert movies[0].poster_url.endswith("/inception.jpg")
    assert movies[0].popularity == 88.5
