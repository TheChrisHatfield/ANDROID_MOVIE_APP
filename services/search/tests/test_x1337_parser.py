from unittest.mock import MagicMock, patch

from torrtux_core.sites.providers import X1337

SAMPLE_HTML = """
<html><body>
<table class="table-list">
<tr><th>Name</th></tr>
<tr>
  <td class="coll-1">1</td>
  <td class="name"><a href="/sub/cat/1/">Cat</a><a href="/torrent/123/inception-2010/">Inception 2010 1080p</a></td>
  <td class="coll-date">Apr. 1st '24</td>
  <td class="size">2.1 GB</td>
  <td class="seeds">120</td>
  <td class="leeches">4</td>
</tr>
</table>
</body></html>
"""


def test_x1337_parse_results_extracts_torrent_row():
    site = X1337()
    site.working_url = "https://1337xx.to"
    rows = site.parse_results(SAMPLE_HTML.encode(), "inception")
    assert len(rows) == 1
    assert rows[0]["name"] == "Inception 2010 1080p"
    assert rows[0]["seeds"] == "120"
    assert rows[0]["site"] == "1337x"
    assert rows[0]["detail_url"] == "https://1337xx.to/torrent/123/inception-2010/"


def test_x1337_mirror_list_prioritizes_working_domains():
    site = X1337()
    assert site.base_urls[0] == "https://1337xx.to"


def test_x1337_magnet_parser_finds_detail_page_link():
    site = X1337()
    site.working_url = "https://1337xx.to"
    html = """
    <html><body>
    <a class="download-torrent" href="magnet:?xt=urn:btih:abc123">Magnet</a>
    </body></html>
    """
    mock_response = MagicMock(status_code=200, content=html.encode())
    with patch("torrtux_core.http_client.http_get", return_value=mock_response):
        assert site.get_magnet_link("https://1337xx.to/torrent/1/test/") == "magnet:?xt=urn:btih:abc123"
