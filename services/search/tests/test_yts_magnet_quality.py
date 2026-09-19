import json

from torrtux_core.sites.providers import YTS


def test_yts_magnet_payload_respects_quality():
    site = YTS()
    payload = {
        "props": {
            "pageProps": {
                "movie": {
                    "title": "Inception",
                    "torrents": [
                        {"hash": "CE9156EB497762F8B7577B71C0647A4B0C3423E1", "quality": "720p", "seeds": 83},
                        {"hash": "224BF45881252643DFC2E71ABC7B2660A21C68C4", "quality": "1080p", "seeds": 100},
                    ],
                }
            }
        }
    }
    magnet_720 = site._magnet_from_movie_payload(payload, quality="720p")
    assert "CE9156EB" in magnet_720
    magnet_1080 = site._magnet_from_movie_payload(payload, quality="1080p")
    assert "224BF458" in magnet_1080


def test_yts_magnet_fetch_uses_spec_timeout():
    from unittest.mock import MagicMock, patch

    site = YTS()
    site.working_url = "https://yts.rs"
    response = MagicMock(status_code=404, content=b"")
    with patch("torrtux_core.sites.providers.http_get", return_value=response) as mock_get:
        site.get_magnet_link("https://yts.rs/movie/inception-2010")
    mock_get.assert_called_once()
    assert mock_get.call_args.kwargs["timeout"] == 15
