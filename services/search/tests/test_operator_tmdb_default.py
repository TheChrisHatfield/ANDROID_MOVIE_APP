import os

from metadata.tmdb_client import TmdbClient
from operator_defaults import BUNDLED_TMDB_API_KEY


def test_tmdb_client_uses_bundled_key_without_env(monkeypatch):
    monkeypatch.delenv("TMDB_API_KEY", raising=False)
    client = TmdbClient()
    assert client.api_key == BUNDLED_TMDB_API_KEY
    assert client.configured


def test_env_overrides_bundled_default(monkeypatch):
    monkeypatch.setenv("TMDB_API_KEY", "override-key")
    client = TmdbClient()
    assert client.api_key == "override-key"


def test_health_endpoint_reports_operator_tmdb_configured():
    from fastapi.testclient import TestClient

    from api.main import app

    response = TestClient(app).get("/v1/health")
    assert response.status_code == 200
    assert response.json()["tmdb_configured"] is True
