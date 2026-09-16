from unittest.mock import patch

from api.main import _tmdb, _tmdb_for_request
from metadata.tmdb_client import TmdbClient


def test_invalid_client_key_falls_back_to_server_env():
    server = TmdbClient(api_key="server-valid-key")
    assert server.configured

    with (
        patch("api.main._tmdb", server),
        patch.object(TmdbClient, "validate_key", return_value=False),
    ):
        client, rejected = _tmdb_for_request("bad-user-key")
    assert client.api_key == server.api_key
    assert rejected is True


def test_valid_client_key_used_when_different_from_server():
    server = TmdbClient(api_key="server-valid-key")
    with (
        patch("api.main._tmdb", server),
        patch.object(TmdbClient, "validate_key", return_value=True),
    ):
        client, rejected = _tmdb_for_request("user-valid-key")
    assert client.api_key == "user-valid-key"
    assert rejected is False


def test_blank_client_key_uses_server():
    server = TmdbClient(api_key="server-valid-key")
    with patch("api.main._tmdb", server):
        client, rejected = _tmdb_for_request(None)
        assert client.api_key == server.api_key
        assert rejected is False
        client2, rejected2 = _tmdb_for_request("   ")
        assert client2.api_key == server.api_key
        assert rejected2 is False
