"""Movie genre ids and cross-indexer search queries."""

MOVIE_GENRES: dict[str, str] = {
    "action": "action movie",
    "adventure": "adventure movie",
    "animation": "animation movie",
    "comedy": "comedy movie",
    "crime": "crime movie",
    "drama": "drama movie",
    "fantasy": "fantasy movie",
    "horror": "horror movie",
    "mystery": "mystery movie",
    "romance": "romance movie",
    "sci-fi": "sci-fi movie",
    "thriller": "thriller movie",
}


def genre_search_query(genre_id: str) -> str | None:
    return MOVIE_GENRES.get(genre_id.strip().lower())


def genre_display_label(genre_id: str) -> str:
    return genre_id.strip().lower().replace("-", " ").title()
