from metadata.grouping import build_movie_groups


def test_groups_duplicate_movies():
    rows = [
        {"id": "1", "name": "Inception 2010 1080p BluRay", "site": "YTS"},
        {"id": "2", "name": "Inception.2010.720p.WEB-DL", "site": "TPB"},
        {"id": "3", "name": "Interstellar 2014 1080p", "site": "YTS"},
    ]
    groups, ungrouped, _ = build_movie_groups(rows, tmdb=None, enrich_metadata=False)
    assert len(groups) == 2
    inception = next(g for g in groups if g["title"] == "Inception")
    assert inception["release_count"] == 2
    interstellar = next(g for g in groups if g["title"] == "Interstellar")
    assert interstellar["release_count"] == 1
    assert ungrouped == []


def test_yearless_duplicates_group_by_title():
    rows = [
        {"id": "1", "name": "Avatar 1080p BluRay", "site": "YTS"},
        {"id": "2", "name": "Avatar 720p WEB-DL", "site": "TPB"},
    ]
    groups, ungrouped, _ = build_movie_groups(rows, enrich_metadata=False)
    assert len(groups) == 1
    assert groups[0]["title"] == "Avatar"
    assert groups[0]["release_count"] == 2
    assert ungrouped == []


def test_single_release_becomes_group_card():
    rows = [{"id": "1", "name": "Obscure Film 2020 1080p", "site": "YTS"}]
    groups, ungrouped, _ = build_movie_groups(rows, enrich_metadata=False)
    assert len(groups) == 1
    assert groups[0]["release_count"] == 1
    assert ungrouped == []


def test_max_groups_still_groups_overflow_without_tmdb():
    rows = [
        {"id": str(i), "name": f"Film {i} 2020 1080p", "site": "YTS"}
        for i in range(35)
    ]
    groups, ungrouped, capped = build_movie_groups(rows, tmdb=None, enrich_metadata=False, max_groups=30)
    assert len(groups) == 35
    assert ungrouped == []
    assert capped is False


def test_enrichment_capped_when_overflow_with_tmdb(monkeypatch):
    rows = [
        {"id": str(i), "name": f"Film {i} 2020 1080p", "site": "YTS"}
        for i in range(55)
    ]

    class FakeTmdb:
        configured = True

        def lookup(self, title, year=None):
            return None

    groups, ungrouped, capped = build_movie_groups(
        rows,
        tmdb=FakeTmdb(),
        enrich_metadata=True,
        max_groups=50,
    )
    assert len(groups) == 55
    assert ungrouped == []
    assert capped is True


def test_enrichment_capped_when_overflow_without_tmdb(monkeypatch):
    monkeypatch.setattr("metadata.grouping.fill_missing_posters", lambda buckets, **kwargs: 0)
    rows = [
        {"id": str(i), "name": f"Film {i} 2020 1080p", "site": "YTS"}
        for i in range(12)
    ]
    groups, ungrouped, capped = build_movie_groups(
        rows,
        tmdb=None,
        enrich_metadata=True,
        max_groups=8,
    )
    assert len(groups) == 12
    assert capped is True


def test_untitled_rows_do_not_consume_tmdb_enrichment_slots():
    rows = [
        {"id": "u1", "name": "", "site": "1337x"},
        {"id": "u2", "name": "   ", "site": "1337x"},
        {"id": "m1", "name": "Named Film 2020 1080p", "site": "YTS"},
    ]
    looked_up: list[str] = []

    class FakeTmdb:
        configured = True

        def lookup(self, title, year=None):
            looked_up.append(title)
            return None

    groups, ungrouped, capped = build_movie_groups(
        rows,
        tmdb=FakeTmdb(),
        enrich_metadata=True,
        max_groups=1,
    )
    assert any(g["title"] == "Named Film" for g in groups)
    assert looked_up == ["Named Film"]
    assert len(ungrouped) == 2
    assert capped is False
