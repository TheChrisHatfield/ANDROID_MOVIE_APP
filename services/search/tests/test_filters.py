from torrtux_core.filters import (
    apply_filters,
    filter_movie_profile,
    is_likely_movie_release,
    parse_size,
    seed_count,
    sort_by_seeds_desc,
)


def test_parse_size_gb():
    assert parse_size("2 GB") == 2 * 1024**3


def test_parse_size_gib():
    assert abs(parse_size("1.85 GiB") - 1.85 * 1024**3) < 1


def test_invalid_max_size_raises():
    import pytest

    from torrtux_core.filters import filter_size_bytes

    with pytest.raises(ValueError):
        filter_size_bytes("not-a-size")


def test_unitless_max_size_raises():
    import pytest

    from torrtux_core.filters import filter_size_bytes

    with pytest.raises(ValueError):
        filter_size_bytes("500")


def test_yts_unknown_size_kept_with_max_size_filter():
    rows = [{"name": "movie", "site": "YTS", "seeds": "10", "size": "-"}]
    out = apply_filters(rows, max_size="4 GB")
    assert len(out) == 1


def test_yts_unknown_seeds_kept_with_min_seeds_filter():
    rows = [{"name": "movie", "site": "YTS", "seeds": "-", "size": "1 GB"}]
    out = apply_filters(rows, min_seeds=10)
    assert len(out) == 1


def test_max_size_filters_gib_rows():
    rows = [
        {"name": "big", "size": "8.50 GiB", "seeds": "10"},
        {"name": "small", "size": "1.2 GB", "seeds": "5"},
    ]
    out = apply_filters(rows, max_size="2 GB")
    assert len(out) == 1
    assert out[0]["name"] == "small"


def test_min_seeds_zero_keeps_unknown_seed_rows():
    rows = [{"name": "a", "site": "1337x", "seeds": "-", "size": "1 GB"}]
    out = apply_filters(rows, min_seeds=0)
    assert len(out) == 1


def test_max_seeds_keeps_unknown_seed_rows():
    rows = [{"name": "a", "site": "1337x", "seeds": "-", "size": "1 GB"}]
    out = apply_filters(rows, max_seeds=10)
    assert len(out) == 1


def test_apply_min_seeds():
    rows = [
        {"name": "a", "seeds": "5", "size": "1 GB"},
        {"name": "b", "seeds": "20", "size": "1 GB"},
    ]
    out = apply_filters(rows, min_seeds=10)
    assert len(out) == 1
    assert out[0]["name"] == "b"


def test_seed_count_with_commas():
    assert seed_count("1,474") == 1474


def test_parse_size_with_thousands_comma():
    assert parse_size("1,474 MB") == 1474 * 1024**2


def test_sort_by_seeds_desc():
    rows = [{"seeds": "3"}, {"seeds": "99"}, {"seeds": "-"}]
    out = sort_by_seeds_desc(rows)
    assert out[0]["seeds"] == "99"


def test_movie_profile_filters_tv_and_software():
    rows = [
        {"name": "Inception 2010 1080p BluRay"},
        {"name": "Breaking Bad S01E01 1080p"},
        {"name": "Windows 11 Pro x64 ISO"},
    ]
    out = filter_movie_profile(rows)
    assert [row["name"] for row in out] == ["Inception 2010 1080p BluRay"]
    assert is_likely_movie_release("Avatar 2022 2160p")
    assert not is_likely_movie_release("Show Name Season 2 Complete")
