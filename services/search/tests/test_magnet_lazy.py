from unittest.mock import MagicMock, patch
from uuid import UUID, uuid4

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
    mock_site.get_magnet_link.assert_called_once_with(row["detail_url"], quality=None)


def test_magnet_endpoint_refetches_when_cached_magnet_blank():
    row = {
        "name": "Test",
        "site": "1337x",
        "size": "1 GB",
        "seeds": "10",
        "leeches": "1",
        "date": "2024",
        "magnet": "   ",
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
    mock_site.get_magnet_link.assert_called_once_with(row["detail_url"], quality=None)


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


def test_magnet_endpoint_unknown_id_returns_not_found_or_expired():
    response = client.get(f"/v1/results/{uuid4()}/magnet")
    assert response.status_code == 404
    assert response.json()["detail"] == "Result not found or expired"


def test_magnet_resolve_returns_stable_cache_id_not_client_result_id():
    client_id = uuid4()
    mock_site = MagicMock()
    mock_site.name = "1337x"
    mock_site.get_magnet_link.return_value = "magnet:?xt=urn:btih:deadbeef"

    with patch.object(_searcher, "sites", [mock_site]):
        response = client.get(
            "/v1/magnet/resolve",
            params={
                "site": "1337x",
                "detail_url": "https://1337x.to/torrent/42/stable-id/",
                "name": "Stable Id",
                "result_id": str(client_id),
            },
        )

    assert response.status_code == 200
    body = response.json()
    assert body["magnet"].startswith("magnet:")
    assert body["id"] != str(client_id)
    cached = _result_cache.get(UUID(body["id"]))
    assert cached is not None
    assert cached["detail_url"].endswith("/torrent/42/stable-id/")


def test_magnet_resolve_by_detail_url_without_cache():
    mock_site = MagicMock()
    mock_site.name = "1337x"
    mock_site.get_magnet_link.return_value = "magnet:?xt=urn:btih:deadbeef"

    with patch.object(_searcher, "sites", [mock_site]):
        response = client.get(
            "/v1/magnet/resolve",
            params={
                "site": "1337x",
                "detail_url": "https://1337x.to/torrent/999/cache-miss/",
                "name": "Cache Miss",
            },
        )

    assert response.status_code == 200
    assert response.json()["magnet"].startswith("magnet:")
    mock_site.get_magnet_link.assert_called_once_with(
        "https://1337x.to/torrent/999/cache-miss/",
        quality=None,
    )
