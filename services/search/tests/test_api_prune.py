from unittest.mock import MagicMock, patch

from fastapi.testclient import TestClient

from api.main import _searcher, app
from torrtux_core.searcher import SearchOutcome

client = TestClient(app)


@patch.object(_searcher, "test_sites", return_value=True)
@patch.object(_searcher, "search")
def test_search_prunes_failed_sites_from_working_pool(mock_search, _mock_test):
    failing = MagicMock()
    failing.name = "BrokenIndexer"
    ok = MagicMock()
    ok.name = "GoodIndexer"
    _searcher.working_sites = [failing, ok]
    mock_search.return_value = SearchOutcome(
        results=[{"name": "test", "site": "GoodIndexer", "seeds": "1"}],
        failed_sites=["BrokenIndexer"],
    )

    response = client.get("/v1/search", params={"q": "test"})
    assert response.status_code == 200
    assert all(site.name != "BrokenIndexer" for site in _searcher.working_sites)
