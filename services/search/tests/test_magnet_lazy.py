from unittest.mock import MagicMock, patch
from uuid import uuid4

from fastapi.testclient import TestClient

from api.cache import ResultCache
from api.main import app, _result_cache, _searcher


client = TestClient(app)


def test_magnet_endpoint_fetches_when_detail_url_present():
    row = {
        "name": "Test",
        "site": "1337x",
        "size": "1 GB",
        "seeds": "10",
        "leeches": "1",
        "date": "2024",
        "magnet": None,
        "detail_url": "https://1337x.to/torrent/123/test/",
    }
    result_id = _result_cache.put_many([row])[0]["id"]

    mock_site = MagicMock()
    mock_site.name = "1337x"
    mock_site.get_magnet_link.return_value = "magnet:?xt=urn:btih:deadbeef"

    with patch.object(_searcher, "sites", [mock_site]):
        response = client.get(f"/v1/results/{result_id}/magnet")

    assert response.status_code == 200
    assert response.json()["magnet"].startswith("magnet:")
    mock_site.get_magnet_link.assert_called_once_with(row["detail_url"])


def test_magnet_endpoint_uses_inline_magnet_without_fetch():
    row = {
        "name": "Inline",
        "site": "The Pirate Bay",
        "magnet": "magnet:?xt=urn:btih:abc123",
        "detail_url": "https://tpb.party/torrent/1",
    }
    result_id = _result_cache.put_many([row])[0]["id"]

    mock_site = MagicMock()
    mock_site.get_magnet_link = MagicMock()

    with patch.object(_searcher, "sites", [mock_site]):
        response = client.get(f"/v1/results/{result_id}/magnet")

    assert response.status_code == 200
    mock_site.get_magnet_link.assert_not_called()
