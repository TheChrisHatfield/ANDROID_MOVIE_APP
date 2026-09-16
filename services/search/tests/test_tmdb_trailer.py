from unittest.mock import MagicMock, patch

from metadata.tmdb_client import TmdbClient


@patch("metadata.tmdb_client.requests.get")
def test_trailer_falls_back_to_teaser(mock_get):
    client = TmdbClient(api_key="test-key")
    mock_get.return_value = MagicMock(
        status_code=200,
        raise_for_status=MagicMock(),
    )
    mock_get.return_value.json.return_value = {
        "results": [
            {"site": "YouTube", "type": "Teaser", "key": "teaser123"},
        ],
    }
    assert client._fetch_trailer_key(1) == "teaser123"
