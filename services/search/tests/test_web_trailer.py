import time
from unittest.mock import patch

from metadata.grouping import build_movie_groups
from metadata.web_trailer import (
    TrailerCandidate,
    build_trailer_queries,
    extract_youtube_id,
    fill_missing_trailers,
    is_usable_trailer_key,
    resolve_web_trailer,
    score_trailer_candidate,
    trailer_id_for,
)
from metadata import web_trailer as web_trailer_mod


def test_extracts_youtube_ids_from_urls_and_bare_keys():
    assert extract_youtube_id("dQw4w9WgXcQ") == "dQw4w9WgXcQ"
    assert extract_youtube_id("https://www.youtube.com/watch?v=dQw4w9WgXcQ") == "dQw4w9WgXcQ"
    assert extract_youtube_id("https://youtu.be/dQw4w9WgXcQ") == "dQw4w9WgXcQ"
    assert extract_youtube_id("https://www.youtube.com/embed/dQw4w9WgXcQ") == "dQw4w9WgXcQ"
    assert extract_youtube_id("abc123") is None
    assert extract_youtube_id("yt_trailer_code") is None
    assert not is_usable_trailer_key("abc123")
    assert is_usable_trailer_key("dQw4w9WgXcQ")


def test_trailer_queries_include_title_year_and_overview_tokens():
    queries = build_trailer_queries(
        "Inception",
        2010,
        overview="A thief who steals corporate secrets through dream-sharing.",
        torrent_names=["Inception.2010.1080p.BluRay"],
        date="2010-07-16",
    )
    blob = " | ".join(queries).lower()
    assert "inception" in blob
    assert "2010" in blob
    assert "official trailer" in blob
    assert "thief" in blob or "steals" in blob or "corporate" in blob


def test_score_prefers_official_trailer_and_rejects_reviews():
    official = TrailerCandidate(
        video_id="dQw4w9WgXcQ",
        title="Inception Official Trailer",
        snippet="2010 Christopher Nolan",
        source="youtube",
    )
    review = TrailerCandidate(
        video_id="aaaaaaaaaaa",
        title="Inception ending explained review",
        snippet="reaction recap",
        source="youtube",
    )
    assert score_trailer_candidate(official, title="Inception", year=2010) >= 5.0
    assert score_trailer_candidate(review, title="Inception", year=2010) < 0


def test_wikidata_score_does_not_invent_official_trailer():
    entity_video = TrailerCandidate(
        video_id="dQw4w9WgXcQ",
        title="Inception",
        snippet="2010 science fiction film",
        source="wikidata",
    )
    blob_score = score_trailer_candidate(entity_video, title="Inception", year=2010)
    assert blob_score >= 5.0
    fake = TrailerCandidate(
        video_id="dQw4w9WgXcQ",
        title="Inception official trailer",
        snippet="2010 science fiction film",
        source="wikidata",
    )
    assert score_trailer_candidate(fake, title="Inception", year=2010) > blob_score


def test_fill_replaces_invalid_indexer_trailer():
    buckets = [
        {
            "title": "Obscure Film",
            "year": 2020,
            "trailer_youtube_key": "abc123",
            "releases": [],
        },
    ]

    def fake_resolve(title, year, **kwargs):
        return "dQw4w9WgXcQ"

    with patch("metadata.web_trailer.resolve_web_trailer", side_effect=fake_resolve):
        assert fill_missing_trailers(buckets) == 1
    assert buckets[0]["trailer_youtube_key"] == "dQw4w9WgXcQ"


def test_fill_skips_valid_existing_trailer():
    buckets = [
        {
            "title": "Inception",
            "year": 2010,
            "trailer_youtube_key": "dQw4w9WgXcQ",
            "releases": [],
        },
    ]
    with patch("metadata.web_trailer.resolve_web_trailer", side_effect=AssertionError("network")):
        assert fill_missing_trailers(buckets) == 0
    assert buckets[0]["trailer_youtube_key"] == "dQw4w9WgXcQ"


def test_fill_uses_memory_hit_without_network():
    cache_id = trailer_id_for("Cached Film", 1999)
    web_trailer_mod._memory_hit[cache_id] = "dQw4w9WgXcQ"
    buckets = [{"title": "Cached Film", "year": 1999, "trailer_youtube_key": None, "releases": []}]
    try:
        with patch("metadata.web_trailer.resolve_web_trailer", side_effect=AssertionError("network")):
            assert fill_missing_trailers(buckets) == 1
        assert buckets[0]["trailer_youtube_key"] == "dQw4w9WgXcQ"
    finally:
        web_trailer_mod._memory_hit.pop(cache_id, None)


def test_recent_miss_skips_network():
    cache_id = trailer_id_for("Ghost Film", 1990)
    web_trailer_mod._memory_miss[cache_id] = time.time()
    try:
        with patch(
            "metadata.web_trailer._wikidata_candidates",
            side_effect=AssertionError("network"),
        ):
            assert resolve_web_trailer("Ghost Film", 1990) is None
    finally:
        web_trailer_mod._memory_miss.pop(cache_id, None)


def test_resolve_picks_live_oembed_candidate():
    candidate = TrailerCandidate(
        video_id="dQw4w9WgXcQ",
        title="Inception Official Trailer (2010)",
        snippet="film",
        source="wikipedia",
    )

    with patch("metadata.web_trailer._wikidata_candidates", return_value=[candidate]), patch(
        "metadata.web_trailer._wikipedia_candidates", return_value=[]
    ), patch(
        "metadata.web_trailer._youtube_api_candidates", return_value=[]
    ), patch(
        "metadata.web_trailer._google_cse_youtube_candidates", return_value=[]
    ), patch(
        "metadata.web_trailer._oembed_title", return_value="Inception Official Trailer"
    ):
        web_trailer_mod._memory_hit.pop(trailer_id_for("Inception", 2010), None)
        web_trailer_mod._memory_miss.pop(trailer_id_for("Inception", 2010), None)
        assert resolve_web_trailer("Inception", 2010) == "dQw4w9WgXcQ"
    web_trailer_mod._memory_hit.pop(trailer_id_for("Inception", 2010), None)


def test_empty_oembed_title_skips_candidate():
    candidate = TrailerCandidate(
        video_id="dQw4w9WgXcQ",
        title="Inception Official Trailer (2010)",
        snippet="film",
        source="wikipedia",
    )
    cache_id = trailer_id_for("Inception", 2010)
    web_trailer_mod._memory_hit.pop(cache_id, None)
    web_trailer_mod._memory_miss.pop(cache_id, None)
    with patch("metadata.web_trailer._wikidata_candidates", return_value=[candidate]), patch(
        "metadata.web_trailer._wikipedia_candidates", return_value=[]
    ), patch(
        "metadata.web_trailer._youtube_api_candidates", return_value=[]
    ), patch(
        "metadata.web_trailer._google_cse_youtube_candidates", return_value=[]
    ), patch(
        "metadata.web_trailer._oembed_title", return_value=""
    ):
        assert resolve_web_trailer("Inception", 2010) is None
    web_trailer_mod._memory_miss.pop(cache_id, None)


def test_grouping_attaches_web_trailer_key():
    rows = [{"id": "1", "name": "Obscure Film 2020 1080p", "site": "1337x"}]
    with patch("metadata.grouping.fill_missing_posters", return_value=0), patch(
        "metadata.grouping.fill_missing_trailers"
    ) as mock_fill:
        def _fill(buckets, **kwargs):
            for bucket in buckets:
                bucket["trailer_youtube_key"] = "dQw4w9WgXcQ"
            return len(buckets)

        mock_fill.side_effect = _fill
        groups, _, _ = build_movie_groups(rows, tmdb=None, enrich_metadata=True)
    assert groups[0]["trailer_youtube_key"] == "dQw4w9WgXcQ"


def test_fill_missing_trailers_respects_wall_clock_budget(monkeypatch):
    monkeypatch.setattr(web_trailer_mod, "_FILL_BUDGET_SEC", 0.05)
    buckets = [
        {"title": f"Need Trailer {i}", "year": 2011, "trailer_youtube_key": None, "releases": []}
        for i in range(8)
    ]

    def slow_resolve(*_args, **_kwargs):
        time.sleep(1.5)
        return "dQw4w9WgXcQ"

    start = time.time()
    with patch("metadata.web_trailer.resolve_web_trailer", side_effect=slow_resolve):
        fill_missing_trailers(buckets)
    assert time.time() - start < 1.0


def test_fill_applies_cached_hits_past_pending_limit():
    late_id = trailer_id_for("Later Film", 2001)
    web_trailer_mod._memory_hit[late_id] = "dQw4w9WgXcQ"
    buckets = [
        {"title": "Need Network", "year": 2000, "trailer_youtube_key": None, "releases": []},
        {"title": "Later Film", "year": 2001, "trailer_youtube_key": None, "releases": []},
    ]

    def fake_resolve(title, year, **kwargs):
        assert title == "Need Network"
        return None

    try:
        with patch("metadata.web_trailer.resolve_web_trailer", side_effect=fake_resolve):
            fill_missing_trailers(buckets, limit=1)
        assert buckets[1]["trailer_youtube_key"] == "dQw4w9WgXcQ"
    finally:
        web_trailer_mod._memory_hit.pop(late_id, None)
