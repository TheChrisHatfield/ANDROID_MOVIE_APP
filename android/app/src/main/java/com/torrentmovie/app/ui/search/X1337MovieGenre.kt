package com.torrentmovie.app.ui.search

enum class X1337MovieGenre(val id: String, val label: String, val buttonLabel: String) {
    ACTION("action", "Action", "Action"),
    ADVENTURE("adventure", "Adventure", "Adventure"),
    ANIMATION("animation", "Animation", "Animation"),
    COMEDY("comedy", "Comedy", "Comedy"),
    CRIME("crime", "Crime", "Crime"),
    DRAMA("drama", "Drama", "Drama"),
    FANTASY("fantasy", "Fantasy", "Fantasy"),
    HORROR("horror", "Horror", "Horror"),
    MYSTERY("mystery", "Mystery", "Mystery"),
    ROMANCE("romance", "Romance", "Romance"),
    SCI_FI("sci-fi", "Sci-Fi", "Sci-Fi"),
    THRILLER("thriller", "Thriller", "Thriller"),
    ;

    companion object {
        val entriesList: List<X1337MovieGenre> = entries

        fun fromId(id: String?): X1337MovieGenre? = entries.firstOrNull { it.id == id }
    }
}
