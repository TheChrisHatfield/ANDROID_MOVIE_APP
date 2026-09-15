from torrtux_core.filters import seed_count
from torrtux_core.sites.providers import LimeTorrents


LIME_HTML = b"""
<table class="table2">
<tr><th>Name</th><th>Added</th><th>Size</th><th>Seed</th><th>Leech</th><th></th></tr>
<tr><td><a href="http://itorrents.net/torrent/x.torrent"></a>
<a href="/Inception-2010-torrent.html">Inception 2010 DVDRip</a></td>
<td>1 Year+ - in Movies</td><td>755.53 MB</td><td>1,474</td><td>1,210</td><td></td></tr>
</table>
"""


def test_limetorrents_parses_class_table2():
    site = LimeTorrents()
    site.working_url = "https://www.limetorrents.lol"
    rows = site.parse_results(LIME_HTML, "inception")
    assert len(rows) == 1
    assert "Inception" in rows[0]["name"]
    assert rows[0]["seeds"] == "1474"
    assert rows[0]["detail_url"].startswith("https://www.limetorrents.lol/")


def test_seed_count_strips_commas():
    assert seed_count("1,474") == 1474
