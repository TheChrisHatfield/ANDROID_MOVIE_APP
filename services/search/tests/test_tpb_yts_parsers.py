import json
from pathlib import Path

from torrtux_core.sites.providers import PirateBay, YTS

FIXTURES = Path(__file__).parent / "fixtures"


def test_yts_parses_next_data_fixture():
    site = YTS()
    site.working_url = "https://yts.rs"
    html = (FIXTURES / "yts_search.html").read_bytes()
    rows = site.parse_results(html, "inception")
    assert len(rows) == 3
    assert any("Inception" in row["name"] for row in rows)
    assert all(row["site"] == "YTS" for row in rows)
    qualities = {row["name"] for row in rows}
    assert any("[720p]" in name for name in qualities)
    assert any("[1080p]" in name for name in qualities)
    assert all(row["magnet"].startswith("magnet:?xt=urn:btih:") for row in rows)
    assert all("inception-2010" in row["detail_url"] for row in rows)


def test_tpb_skips_apibay_no_results_placeholder():
    site = PirateBay()
    site.working_url = "https://tpb.party"
    site._apibay_mode = True
    payload = [
        {
            "id": "0",
            "name": "No results returned",
            "info_hash": "0000000000000000000000000000000000000000",
            "seeders": "0",
            "leechers": "0",
            "size": "0",
        }
    ]
    rows = site.parse_results(json.dumps(payload).encode(), "zzznomatchzzz")
    assert rows == []


def test_tpb_parses_apibay_json():
    site = PirateBay()
    site.working_url = "https://tpb.party"
    site._apibay_mode = True
    payload = [
        {
            "id": "1",
            "name": "Inception (2010) 1080p",
            "info_hash": "224BF45881252643DFC2E71ABC7B2660A21C68C4",
            "seeders": "812",
            "leechers": "162",
            "size": "1991613584",
        }
    ]
    rows = site.parse_results(json.dumps(payload).encode(), "inception")
    assert len(rows) == 1
    assert "Inception" in rows[0]["name"]
    assert rows[0]["seeds"] == "812"
    assert rows[0]["magnet"].startswith("magnet:?xt=urn:btih:224BF458")


def test_tpb_html_fixture_if_present():
    fixture = FIXTURES / "tpb_party_search.html"
    if not fixture.exists():
        return
    site = PirateBay()
    site.working_url = "https://tpb.party"
    site._apibay_mode = False
    rows = site.parse_results(fixture.read_bytes(), "inception")
    assert rows
    assert any("inception" in row["name"].lower() for row in rows)
    assert rows[0]["seeds"] != "1.85 GiB"
