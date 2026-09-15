from unittest.mock import patch

from fastapi.testclient import TestClient

from api.main import _searcher, app
from torrtux_core.searcher import SearchOutcome

client = TestClient(app)


@patch.object(_searcher, "test_sites", return_value=True)
@patch.object(_searcher, "search")
def test_search_does_not_drop_working_indexers_on_transient_failure(mock_search, _mock_test):
    failing = type("Site", (), {"name": "BrokenIndexer"})()
    ok = type("Site", (), {"name": "GoodIndexer"})()
    _searcher.working_sites = [failing, ok]
    mock_search.return_value = SearchOutcome(
        results=[{"name": "test", "site": "GoodIndexer", "seeds": "1"}],
        failed_sites=["BrokenIndexer"],
    )

    response = client.get("/v1/search", params={"q": "test"})
    assert response.status_code == 200
    assert {site.name for site in _searcher.working_sites} == {"BrokenIndexer", "GoodIndexer"}


@patch.object(_searcher, "test_sites", return_value=True)
@patch.object(_searcher, "search")
def test_search_returns_503_when_requested_indexers_unavailable(mock_search, _mock_test):
    mock_search.return_value = SearchOutcome([], [], indexers_unavailable=True)
    response = client.get("/v1/search", params={"q": "test", "sites": "MissingIndexer"})
    assert response.status_code == 503
    assert response.json()["detail"] == "Requested indexers unavailable"
