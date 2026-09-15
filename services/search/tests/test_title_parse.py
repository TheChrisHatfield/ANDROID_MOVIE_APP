from metadata.title_parse import group_key_for, parse_torrent_movie_title


def test_parse_inception_release():
    title, year = parse_torrent_movie_title("Inception 2010 1080p BluRay x264 YIFY")
    assert title == "Inception"
    assert year == 2010


def test_parse_parenthetical_year():
    title, year = parse_torrent_movie_title("The Matrix (1999) 1080p WEB-DL")
    assert "Matrix" in title
    assert year == 1999


def test_group_key_includes_year():
    assert group_key_for("Inception", 2010) == "inception-2010"


def test_prefers_release_year_over_title_digits():
    title, year = parse_torrent_movie_title("Blade Runner 2049 2017 1080p BluRay")
    assert title == "Blade Runner 2049"
    assert year == 2017


def test_title_only_year_in_name():
    title, year = parse_torrent_movie_title("1917 2019 1080p")
    assert title == "1917"
    assert year == 2019
