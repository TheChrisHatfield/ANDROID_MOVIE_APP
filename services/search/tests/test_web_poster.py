import io
import time
from pathlib import Path
from unittest.mock import patch

from PIL import Image
from fastapi.testclient import TestClient

from metadata.web_poster import (
    POSTER_HEIGHT,
    POSTER_WIDTH,
    PosterCandidate,
    build_poster_queries,
    coerce_year,
    encode_poster_jpeg,
    fill_missing_posters,
    fit_poster_image,
    is_usable_poster_url,
    overview_hint_tokens,
    poster_id_for,
    poster_public_path,
    resolve_web_poster,
    score_candidate,
    wiki_page_image_urls,
)
from metadata import web_poster as web_poster_mod
from metadata.grouping import build_movie_groups


def _rgb_image(width: int, height: int, color=(20, 80, 160)) -> Image.Image:
    return Image.new("RGB", (width, height), color)


def test_usable_poster_url_rejects_html_pages():
    assert is_usable_poster_url("https://yts.rs/images/superman.jpg")
    assert is_usable_poster_url("/v1/posters/abc123.jpg")
    assert not is_usable_poster_url("https://1337x.to/torrent/1/movie/")
    assert not is_usable_poster_url("https://example.com/cover.php")
    assert not is_usable_poster_url("/relative.jpg")


def test_fill_replaces_html_indexer_poster_with_web_art():
    buckets = [
        {
            "title": "Obscure Film",
            "year": 2020,
            "poster_url": "https://1337x.to/torrent/1/obscure/",
            "releases": [],
        },
    ]

    def fake_resolve(title, year, **kwargs):
        return "/v1/posters/obscure.jpg"

    with patch("metadata.web_poster.resolve_web_poster", side_effect=fake_resolve):
        assert fill_missing_posters(buckets) == 1
    assert buckets[0]["poster_url"] == "/v1/posters/obscure.jpg"


def test_queries_include_title_year_and_overview_tokens():
    queries = build_poster_queries(
        "Inception",
        2010,
        overview="A thief who steals corporate secrets through dream-sharing.",
        torrent_names=["Inception.2010.1080p.BluRay"],
        date="2010-07-16",
    )
    blob = " | ".join(queries).lower()
    assert "inception" in blob
    assert "2010" in blob
    assert "poster" in blob
    assert "thief" in blob or "steals" in blob or "corporate" in blob


def test_overview_hint_skips_stopwords():
    tokens = overview_hint_tokens("The thief who steals corporate secrets")
    assert "the" not in tokens
    assert "who" not in tokens
    assert "thief" in tokens
    assert "steals" in tokens


def test_score_prefers_wikipedia_title_year_match():
    good = PosterCandidate(
        url="https://upload.wikimedia.org/wikipedia/en/thumb/inception.jpg",
        title="Inception",
        snippet="2010 science fiction film",
        source="wikipedia",
    )
    bad = PosterCandidate(
        url="https://example.com/wallpaper-collage.jpg",
        title="Random wallpaper",
        snippet="desktop wallpaper",
        source="google",
    )
    assert score_candidate(good, title="Inception", year=2010, overview="dreams") > 4
    assert score_candidate(bad, title="Inception", year=2010) < 0


def test_fit_poster_is_tmdb_w342_size():
    fitted = fit_poster_image(_rgb_image(800, 400))
    assert fitted.size == (POSTER_WIDTH, POSTER_HEIGHT)
    jpeg = encode_poster_jpeg(_rgb_image(100, 200))
    reopened = Image.open(io.BytesIO(jpeg))
    assert reopened.size == (POSTER_WIDTH, POSTER_HEIGHT)
    assert reopened.format == "JPEG"


def test_resolve_uses_cache_without_network(tmp_path, monkeypatch):
    monkeypatch.setenv("POSTER_CACHE_DIR", str(tmp_path))
    poster_id = poster_id_for("Inception", 2010)
    dest = Path(tmp_path) / f"{poster_id}.jpg"
    dest.write_bytes(encode_poster_jpeg(_rgb_image(60, 90)))
    with patch("metadata.web_poster._wikipedia_candidates", side_effect=AssertionError("network")):
        url = resolve_web_poster("Inception", 2010)
    assert url == poster_public_path(poster_id)


def test_fill_attaches_disk_cache_then_searches_uncached_titles(tmp_path, monkeypatch):
    monkeypatch.setenv("POSTER_CACHE_DIR", str(tmp_path))
    buckets = [
        {"title": f"Cached {i}", "year": 2000 + i, "poster_url": None, "releases": []}
        for i in range(8)
    ] + [
        {
            "title": "Needs Network",
            "year": 2011,
            "poster_url": None,
            "releases": [{"name": "Needs.Network.2011.1080p"}],
        },
    ]
    for i in range(8):
        poster_id = poster_id_for(f"Cached {i}", 2000 + i)
        dest = Path(tmp_path) / f"{poster_id}.jpg"
        dest.write_bytes(encode_poster_jpeg(_rgb_image(40, 60)))

    searched: list[str] = []

    def fake_resolve(title, year, **kwargs):
        searched.append(title)
        return "/v1/posters/newtitle.jpg"

    with patch("metadata.web_poster.resolve_web_poster", side_effect=fake_resolve):
        filled = fill_missing_posters(buckets)
    assert filled == 9
    assert searched == ["Needs Network"]
    assert buckets[0]["poster_url"].startswith("/v1/posters/")
    assert buckets[8]["poster_url"] == "/v1/posters/newtitle.jpg"


def test_fill_skips_recent_misses_so_later_titles_can_search(monkeypatch):
    from metadata import web_poster as web_poster_mod

    miss_id = poster_id_for("Known Miss", 1999)
    web_poster_mod._memory_miss[miss_id] = time.time()
    buckets = [
        {"title": "Known Miss", "year": 1999, "poster_url": None, "releases": []},
        {"title": "Later Title", "year": 2012, "poster_url": None, "releases": []},
    ]
    searched: list[str] = []

    def fake_resolve(title, year, **kwargs):
        searched.append(title)
        return "/v1/posters/later.jpg"

    with patch("metadata.web_poster.resolve_web_poster", side_effect=fake_resolve):
        fill_missing_posters(buckets)
    assert searched == ["Later Title"]
    assert buckets[0].get("poster_url") is None
    assert buckets[1]["poster_url"] == "/v1/posters/later.jpg"
    web_poster_mod._memory_miss.pop(miss_id, None)


def test_fill_missing_posters_skips_existing_and_fills_blank(tmp_path, monkeypatch):
    monkeypatch.setenv("POSTER_CACHE_DIR", str(tmp_path))
    buckets = [
        {"title": "Has Poster", "year": 2020, "poster_url": "https://yts.rs/p.jpg", "releases": []},
        {"title": "Needs Poster", "year": 2011, "poster_url": None, "overview": "A sailor returns home.", "releases": [{"name": "Needs.Poster.2011.1080p"}]},
    ]

    def fake_resolve(title, year, **kwargs):
        if title == "Needs Poster":
            return "/v1/posters/abc123.jpg"
        raise AssertionError("should not search for existing poster")

    with patch("metadata.web_poster.resolve_web_poster", side_effect=fake_resolve):
        filled = fill_missing_posters(buckets)
    assert filled == 1
    assert buckets[0]["poster_url"] == "https://yts.rs/p.jpg"
    assert buckets[1]["poster_url"] == "/v1/posters/abc123.jpg"


def test_grouping_copies_web_poster_onto_releases():
    rows = [
        {"id": "1", "name": "Obscure Film 2020 1080p", "site": "1337x"},
    ]
    with patch("metadata.grouping.fill_missing_posters") as mock_fill, patch(
        "metadata.grouping.fill_missing_trailers", return_value=0,
    ):
        def _fill(buckets, **kwargs):
            for bucket in buckets:
                bucket["poster_url"] = "/v1/posters/deadbeef.jpg"
            return len(buckets)

        mock_fill.side_effect = _fill
        groups, _, _ = build_movie_groups(rows, tmdb=None, enrich_metadata=True)
    assert groups[0]["poster_url"] == "/v1/posters/deadbeef.jpg"
    assert groups[0]["releases"][0]["poster_url"] == "/v1/posters/deadbeef.jpg"


def test_poster_endpoint_serves_cached_jpeg(tmp_path, monkeypatch):
    monkeypatch.setenv("POSTER_CACHE_DIR", str(tmp_path))
    from api.main import app

    poster_id = poster_id_for("Cache Film", 1999)
    dest = Path(tmp_path) / f"{poster_id}.jpg"
    dest.write_bytes(encode_poster_jpeg(_rgb_image(40, 60, (200, 10, 10))))
    client = TestClient(app)
    response = client.get(f"/v1/posters/{poster_id}.jpg")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("image/jpeg")
    image = Image.open(io.BytesIO(response.content))
    assert image.size == (POSTER_WIDTH, POSTER_HEIGHT)


def test_poster_endpoint_rejects_bad_id():
    from api.main import app

    client = TestClient(app)
    assert client.get("/v1/posters/not-a-hash.jpg").status_code == 404


def test_wiki_urls_prefer_thumbnail_before_original():
    urls = wiki_page_image_urls(
        {
            "original": {"source": "https://upload.wikimedia.org/wikipedia/en/huge-original.jpg"},
            "thumbnail": {
                "source": "https://upload.wikimedia.org/wikipedia/en/thumb/poster.jpg/600px-poster.jpg"
            },
        }
    )
    assert urls[0].endswith("600px-poster.jpg")
    assert urls[1].endswith("huge-original.jpg")


def test_score_prefers_wikimedia_thumb_over_original():
    thumb = PosterCandidate(
        url="https://upload.wikimedia.org/wikipedia/en/thumb/inception.jpg/600px-inception.jpg",
        title="Inception",
        snippet="2010 science fiction film",
        source="wikipedia",
    )
    original = PosterCandidate(
        url="https://upload.wikimedia.org/wikipedia/en/inception.jpg",
        title="Inception",
        snippet="2010 science fiction film",
        source="wikipedia",
    )
    assert score_candidate(thumb, title="Inception", year=2010) > score_candidate(
        original, title="Inception", year=2010
    )


def test_coerce_year_accepts_digit_strings():
    assert coerce_year("2010") == 2010
    assert coerce_year("nope") is None
    assert coerce_year(True) is None


def test_extra_name_query_is_capped():
    long_name = "X." * 200
    queries = build_poster_queries("Film", 2020, torrent_names=[long_name])
    assert all(len(query) < 200 for query in queries)


def test_recent_miss_skips_network(tmp_path, monkeypatch):
    monkeypatch.setenv("POSTER_CACHE_DIR", str(tmp_path))
    poster_id = poster_id_for("Ghost Film", 1990)
    web_poster_mod._memory_miss[poster_id] = time.time()
    with patch(
        "metadata.web_poster._wikipedia_candidates",
        side_effect=AssertionError("network"),
    ):
        assert resolve_web_poster("Ghost Film", 1990) is None
    web_poster_mod._memory_miss.pop(poster_id, None)


def test_stale_miss_retries_network(tmp_path, monkeypatch):
    monkeypatch.setenv("POSTER_CACHE_DIR", str(tmp_path))
    poster_id = poster_id_for("Ghost Film", 1990)
    web_poster_mod._memory_miss[poster_id] = time.time() - 400
    called = {"wiki": False}

    def fake_wiki(*_args, **_kwargs):
        called["wiki"] = True
        return []

    with patch("metadata.web_poster._wikipedia_candidates", side_effect=fake_wiki), patch(
        "metadata.web_poster._google_image_candidates", return_value=[]
    ):
        assert resolve_web_poster("Ghost Film", 1990) is None
    assert called["wiki"] is True
    web_poster_mod._memory_miss.pop(poster_id, None)


def test_fill_missing_posters_respects_wall_clock_budget(monkeypatch):
    monkeypatch.setattr(web_poster_mod, "_FILL_BUDGET_SEC", 0.05)
    buckets = [
        {"title": f"Need Poster {i}", "year": 2011, "poster_url": None, "releases": []}
        for i in range(8)
    ]

    def slow_resolve(*_args, **_kwargs):
        time.sleep(1.5)
        return "/v1/posters/abc.jpg"

    start = time.time()
    with patch("metadata.web_poster.resolve_web_poster", side_effect=slow_resolve):
        fill_missing_posters(buckets)
    assert time.time() - start < 1.0
