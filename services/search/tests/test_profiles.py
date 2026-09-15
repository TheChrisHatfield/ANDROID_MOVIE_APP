from torrtux_core.profiles import EXCLUDED_FROM_MOVIE_PROFILE, MOVIE_SITE_NAMES


def test_eztv_excluded_from_movie_profile():
    assert "EZTV" not in MOVIE_SITE_NAMES
    assert "EZTV" in EXCLUDED_FROM_MOVIE_PROFILE
