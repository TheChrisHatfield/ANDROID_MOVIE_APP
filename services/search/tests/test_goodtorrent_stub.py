from torrtux_core.sites.providers import GoodTorrent


def test_goodtorrent_not_search_capable():
    site = GoodTorrent()
    assert site.test_connection() is False
