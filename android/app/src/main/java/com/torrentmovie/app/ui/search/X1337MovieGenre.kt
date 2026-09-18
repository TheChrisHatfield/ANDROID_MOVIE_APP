package com.torrentmovie.app.ui.search

enum class X1337MovieGenre(val id: String, val label: String, val buttonLabel: String) {
    ACTION("action", "1337x Action", "Action"),
    ADVENTURE("adventure", "1337x Adventure", "Adventure"),
    ANIMATION("animation", "1337x Animation", "Animation"),
    COMEDY("comedy", "1337x Comedy", "Comedy"),
    CRIME("crime", "1337x Crime", "Crime"),
    DRAMA("drama", "1337x Drama", "Drama"),
    FANTASY("fantasy", "1337x Fantasy", "Fantasy"),
    HORROR("horror", "1337x Horror", "Horror"),
    MYSTERY("mystery", "1337x Mystery", "Mystery"),
    ROMANCE("romance", "1337x Romance", "Romance"),
    SCI_FI("sci-fi", "1337x Sci-Fi", "Sci-Fi"),
    THRILLER("thriller", "1337x Thriller", "Thriller"),
    ;

    companion object {
        val entriesList: List<X1337MovieGenre> = entries

        fun fromId(id: String?): X1337MovieGenre? = entries.firstOrNull { it.id == id }
    }
}
