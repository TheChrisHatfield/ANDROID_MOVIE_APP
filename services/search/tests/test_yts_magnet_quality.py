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
