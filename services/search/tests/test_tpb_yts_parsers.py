import json
from pathlib import Path
from unittest.mock import MagicMock, patch

from torrtux_core.base import TorrentSite
from torrtux_core.sites.providers import LimeTorrents, PirateBay, YTS

FIXTURES = Path(__file__).parent / "fixtures"


def test_yts_peers_field_is_leech_count():
    site = YTS()
    site.working_url = "https://yts.rs"
    payload = {
        "props": {
            "pageProps": {
                "movies": [
                    {
                        "title": "Test Film",
                        "year": 2024,
                        "medium_cover_image": "https://img.yts.rs/poster.jpg",
                        "torrents": [
                            {"hash": "A" * 40, "quality": "1080p", "seeds": 83, "peers": 53, "size": "1 GB"},
                        ],
                    }
                ]
            }
        }
    }
    rows = site._movie_rows_from_payload(payload)
    assert len(rows) == 1
    assert rows[0]["leeches"] == "53"


def test_yts_omits_invalid_trailer_codes():
    site = YTS()
    site.working_url = "https://yts.rs"
    payload = {
        "props": {
            "pageProps": {
                "movies": [
                    {
                        "title": "Junk Trailer",
                        "year": 2024,
                        "yt_trailer_code": "abc123",
                        "torrents": [
                            {"hash": "B" * 40, "quality": "1080p", "seeds": 1, "peers": 0, "size": "1 GB"},
                        ],
                    },
                    {
                        "title": "Real Trailer",
                        "year": 2024,
                        "yt_trailer_code": "dQw4w9WgXcQ",
                        "torrents": [
                            {"hash": "C" * 40, "quality": "1080p", "seeds": 1, "peers": 0, "size": "1 GB"},
                        ],
                    },
                ]
            }
        }
    }
    rows = site._movie_rows_from_payload(payload)
    junk = next(row for row in rows if row["name"].startswith("Junk Trailer"))
    real = next(row for row in rows if row["name"].startswith("Real Trailer"))
    assert junk.get("trailer_youtube_key") is None
    assert real.get("trailer_youtube_key") == "dQw4w9WgXcQ"


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
    assert all(row.get("poster_url") for row in rows)
    assert all("medium_cover_image" in row["poster_url"] for row in rows)
    assert any(row.get("trailer_youtube_key") for row in rows)


def test_yts_current_detail_url_rewrites_stale_mirror_host():
    site = YTS()
    site.working_url = "https://yts.rs"
    stale = "https://old-yts.example/movie/inception-2010_123"
    assert site.current_detail_url(stale) == "https://yts.rs/movie/inception-2010_123"


def test_limetorrents_magnet_rewrites_known_mirror_without_torrent_path():
    site = LimeTorrents()
    site.working_url = "https://www.limetorrents.pro"
    stale = "https://www.limetorrents.lol/Inception-2010-1080p-torrent-12345.html"
    assert (
        site.current_detail_url(stale)
        == "https://www.limetorrents.pro/Inception-2010-1080p-torrent-12345.html"
    )


def test_piratebay_current_detail_url_preserves_query():
    site = PirateBay()
    site.working_url = "https://tpb.party"
    stale = "https://thepiratebay.org/description.php?id=42"
    assert site.current_detail_url(stale) == "https://tpb.party/description.php?id=42"


def test_base_magnet_fetch_uses_rewritten_mirror():
    site = TorrentSite("Demo", ["https://a.example", "https://b.example"])
    site.working_url = "https://b.example"
    html = b"<html><body><a href='magnet:?xt=urn:btih:abcd'>m</a></body></html>"
    seen: list[str] = []

    def fake_get(url, **kwargs):
        seen.append(url)
        return MagicMock(status_code=200, content=html)

    with patch("torrtux_core.base.http_get", side_effect=fake_get):
        magnet = site.get_magnet_link("https://a.example/file/inception.html")
    assert magnet == "magnet:?xt=urn:btih:abcd"
    assert seen == ["https://b.example/file/inception.html"]


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


def test_piratebay_health_probe_uses_spec_timeout():
    from unittest.mock import patch

    site = PirateBay()
    with patch("torrtux_core.sites.providers.http_get", side_effect=OSError("skip")) as mock_get:
        assert site.test_connection() is False
    assert mock_get.call_args_list
    assert all(call.kwargs.get("timeout") == 10 for call in mock_get.call_args_list)
