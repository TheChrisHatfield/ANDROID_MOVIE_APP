"""Integration contract: ruTorrent addtorrent.php shape (mirrors Android RuTorrentClient)."""
from urllib.parse import urlencode

import httpx


def test_addtorrent_form_body_shape():
    magnet = "magnet:?xt=urn:btih:abc"
    directory = "/home5/chris82/downloads/MOVIES/"
    body = urlencode({"url": magnet, "dir_edit": directory})
    assert "url=magnet" in body
    assert "dir_edit=" in body


def test_rutorrent_mock_server_success():
    class Handler(httpx.BaseTransport):
        def handle_request(self, request):
            assert request.url.path.endswith("/php/addtorrent.php")
            assert b"url=magnet" in request.content
            assert b"dir_edit=" in request.content
            return httpx.Response(200, text="status=Success")

    client = httpx.Client(transport=Handler())
    response = client.post(
        "https://seedbox.example/rutorrent/php/addtorrent.php",
        data={"url": "magnet:?xt=urn:btih:abc", "dir_edit": "/movies/"},
        auth=("user", "pass"),
    )
    assert response.status_code == 200
    assert "Success" in response.text
