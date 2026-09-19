"""Movie genre ids, TMDB discover mapping, and keyword fallback queries."""

# TMDB movie genre ids (https://developer.themoviedb.org/reference/genre-movie-list)
TMDB_GENRE_IDS: dict[str, int] = {
    "action": 28,
    "adventure": 12,
    "animation": 16,
    "comedy": 35,
    "crime": 80,
    "drama": 18,
    "fantasy": 14,
    "horror": 27,
    "mystery": 9648,
    "romance": 10749,
    "sci-fi": 878,
    "thriller": 53,
}

# Keyword fallback when TMDB discover is unavailable.
MOVIE_GENRES: dict[str, str] = {
    "action": "action",
    "adventure": "adventure",
    "animation": "animation",
    "comedy": "comedy",
    "crime": "crime",
    "drama": "drama",
    "fantasy": "fantasy",
    "horror": "horror",
    "mystery": "mystery",
    "romance": "romance",
    "sci-fi": "science fiction",
    "thriller": "thriller",
}


def genre_search_query(genre_id: str) -> str | None:
    return MOVIE_GENRES.get(genre_id.strip().lower())


def tmdb_genre_id(genre_id: str) -> int | None:
    return TMDB_GENRE_IDS.get(genre_id.strip().lower())


def genre_display_label(genre_id: str) -> str:
    return genre_id.strip().lower().replace("-", " ").title()
