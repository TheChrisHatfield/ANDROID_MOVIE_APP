from torrtux_core.sites.providers import RARBG


def test_rarbg_not_search_capable():
    site = RARBG()
    assert site.test_connection() is False
