from torrtux_core.sites.providers import EZTV, LinuxTracker


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
