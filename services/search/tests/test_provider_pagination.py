from torrtux_core.sites.providers import EZTV, FitGirlRepacks, LinuxTracker, TorrentFunk, Torrentz2


def test_eztv_build_search_url_includes_page():
    site = EZTV()
    site.working_url = "https://eztv.re"
    assert site.build_search_url("test", page=0) == "https://eztv.re/search/test"
    assert site.build_search_url("test", page=1) == "https://eztv.re/search/test/2"


def test_linuxtracker_build_search_url_includes_page():
    site = LinuxTracker()
    site.working_url = "https://linuxtracker.org"
    assert "pages=1" in site.build_search_url("ubuntu", page=0)
    assert "pages=2" in site.build_search_url("ubuntu", page=1)


def test_torrentfunk_build_search_url_includes_page():
    site = TorrentFunk()
    site.working_url = "https://www.torrentfunk.com"
    assert site.build_search_url("test", page=0).endswith("/search/all/test/")
    assert site.build_search_url("test", page=1).endswith("/search/all/test/2/")


def test_torrentz2_build_search_url_includes_page():
    site = Torrentz2()
    site.working_url = "https://torrentz2.nz"
    assert site.build_search_url("test", page=0) == "https://torrentz2.nz/search?f=test&p=0"
    assert site.build_search_url("test", page=1) == "https://torrentz2.nz/search?f=test&p=1"


def test_fitgirl_build_search_url_includes_page():
    site = FitGirlRepacks()
    site.working_url = "https://fitgirl-repacks.site"
    assert site.build_search_url("game", page=0) == "https://fitgirl-repacks.site/?s=game"
    assert site.build_search_url("game", page=1) == "https://fitgirl-repacks.site/page/2/?s=game"


def test_fitgirl_parse_entry_title():
    site = FitGirlRepacks()
    site.working_url = "https://fitgirl-repacks.site"
    html = b"""
    <div class="post">
      <h1 class="entry-title"><a href="/game-repack/">Test Game</a></h1>
    </div>
    """
    rows = site.parse_results(html, "game")
    assert len(rows) == 1
    assert rows[0]["name"] == "Test Game"
    assert rows[0]["detail_url"] == "https://fitgirl-repacks.site/game-repack/"
