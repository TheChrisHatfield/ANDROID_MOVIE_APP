from unittest.mock import MagicMock, patch

from fastapi.testclient import TestClient

from api.main import _quality_from_result_name, app, _searcher
from torrtux_core.searcher import SearchOutcome


client = TestClient(app)


@patch.object(_searcher, "test_sites", return_value=True)
@patch.object(_searcher, "search")
def test_search_keeps_movie_profile_when_sites_filtered(mock_search, _mock_test):
    mock_search.return_value = SearchOutcome(results=[], failed_sites=[])
    _searcher.working_sites = [MagicMock(name="YTS")]

    client.get("/v1/search", params={"q": "inception", "sites": "YTS", "movie_profile": True})
    mock_search.assert_called_once()
    assert mock_search.call_args.kwargs["movie_profile"] is True


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
    assert body["groups"][0]["releases"][0]["name"] == "Inception 2010"
    assert body["groups"][0]["releases"][0]["id"]
    assert body["failed_sites"] == ["EZTV"]


@patch("api.main._tmdb")
def test_health(mock_tmdb):
    mock_tmdb.configured = False
    assert client.get("/v1/health").json() == {"status": "ok", "tmdb_configured": False}
    mock_tmdb.configured = True
    assert client.get("/v1/health").json() == {"status": "ok", "tmdb_configured": True}


@patch.object(_searcher, "test_sites", return_value=True)
@patch.object(_searcher, "search")
def test_search_preserves_release_poster_url(mock_search, _mock_test):
    mock_search.return_value = SearchOutcome(
        results=[
            {
                "name": "Superman 2025 1080p",
                "site": "YTS",
                "seeds": "10",
                "poster_url": "https://yts.rs/images/superman.jpg",
            }
        ],
        failed_sites=[],
    )
    _searcher.working_sites = [MagicMock(name="YTS")]

    response = client.get("/v1/search", params={"q": "superman", "enrich": False})
    assert response.status_code == 200
    release = response.json()["groups"][0]["releases"][0]
    assert release["poster_url"] == "https://yts.rs/images/superman.jpg"


@patch.object(_searcher, "test_sites", return_value=True)
@patch.object(_searcher, "search")
def test_search_all_sources_failed_returns_503(mock_search, mock_test):
    mock_search.return_value = SearchOutcome(results=[], failed_sites=["YTS"], all_sources_failed=True)
    _searcher.working_sites = [MagicMock(name="YTS")]

    response = client.get("/v1/search", params={"q": "inception"})
    assert response.status_code == 503
    mock_test.assert_called()


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


@patch("api.main.build_movie_groups")
@patch.object(_searcher, "test_sites", return_value=True)
@patch.object(_searcher, "search")
def test_search_passes_tmdb_api_key_override(mock_search, _mock_test, mock_group):
    mock_search.return_value = SearchOutcome(
        results=[
            {"name": "Inception 2010 1080p", "site": "YTS", "seeds": "1"},
            {"name": "Inception.2010.720p", "site": "TPB", "seeds": "2"},
        ],
        failed_sites=[],
    )
    _searcher.working_sites = [MagicMock(name="YTS")]
    mock_group.return_value = ([], [], False)

    with patch.object(
        __import__("metadata.tmdb_client", fromlist=["TmdbClient"]).TmdbClient,
        "validate_key",
        return_value=True,
    ):
        client.get(
            "/v1/search",
            params={"q": "inception", "tmdb_api_key": "test-key-123", "group": True},
        )

    assert mock_group.called
    _, kwargs = mock_group.call_args
    assert kwargs["tmdb"].api_key == "test-key-123"


def test_quality_from_result_name_bracketed_and_loose():
    assert _quality_from_result_name("Movie [1080p] BluRay") == "1080p"
    assert _quality_from_result_name("Movie 720p WEB") == "720p"


@patch.object(_searcher, "test_sites", return_value=True)
def test_whitespace_only_query_returns_400(_mock_test):
    _searcher.working_sites = [MagicMock(name="YTS")]
    response = client.get("/v1/search", params={"q": "   "})
    assert response.status_code == 400
    assert response.json()["detail"] == "Query cannot be empty"


@patch.object(_searcher, "test_sites", return_value=True)
def test_min_seeds_greater_than_max_seeds_returns_400(_mock_test):
    _searcher.working_sites = [MagicMock(name="YTS")]
    response = client.get(
        "/v1/search",
        params={"q": "inception", "min_seeds": 50, "max_seeds": 10},
    )
    assert response.status_code == 400
    assert response.json()["detail"] == "min_seeds cannot exceed max_seeds"


@patch.object(_searcher, "test_sites", return_value=True)
@patch.object(_searcher, "search")
def test_search_serializes_branch_key_for_android(mock_search, _mock_test):
    mock_search.return_value = SearchOutcome(
        results=[
            {
                "name": "Insidious 2015 1080p",
                "site": "YTS",
                "seeds": "10",
                "_branch_key": "insidious|2015",
            }
        ],
        failed_sites=[],
    )
    _searcher.working_sites = [MagicMock(name="YTS")]

    response = client.get("/v1/search", params={"q": "insidious", "enrich": False})
    assert response.status_code == 200
    release = response.json()["groups"][0]["releases"][0]
    assert "branch_key" in release
    assert "_branch_key" not in release
    assert release["branch_key"] == "insidious|2015"
