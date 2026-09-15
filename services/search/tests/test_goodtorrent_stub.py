from torrtux_core.sites.providers import GoodTorrent


def test_goodtorrent_not_search_capable():
    site = GoodTorrent()
    assert site.test_connection() is False


def test_goodtorrent_pagination_url():
    site = GoodTorrent()
    site.working_url = "https://good-torrent.com"
    assert site.build_search_url("matrix", page=0).endswith("/search/matrix")
    assert site.build_search_url("matrix", page=1).endswith("/search/matrix/page/2")
