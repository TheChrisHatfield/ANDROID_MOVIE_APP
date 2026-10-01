package com.torrentmovie.core.data.ondevice

import com.google.gson.JsonParser
import com.torrentmovie.core.data.MovieSearchSuggestion
import java.net.URLEncoder

internal data class TmdbMovie(
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    val overview: String?,
    val posterUrl: String?,
    val trailerKey: String? = null,
    val popularity: Double = 0.0,
)

internal class TmdbOnDevice(
    private val http: IndexerHttp,
    private val apiKey: String,
) {
    val configured: Boolean get() = apiKey.isNotBlank()

    fun suggest(query: String, limit: Int): List<MovieSearchSuggestion> {
        if (!configured || query.trim().length < 2) return emptyList()
        return searchMovies(query, limit).map {
            MovieSearchSuggestion(
                tmdbId = it.tmdbId,
                title = it.title,
                year = it.year,
                posterUrl = it.posterUrl,
            )
        }
    }

    fun searchMovies(query: String, limit: Int): List<TmdbMovie> {
        if (!configured) return emptyList()
        val q = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val url = "$BASE/search/movie?api_key=$apiKey&query=$q"
        val body = http.getText(url) ?: return emptyList()
        return parseMovieList(body, limit)
    }

    fun discover(genreTmdbId: Int, page: Int, limit: Int): List<TmdbMovie> {
        if (!configured) return emptyList()
        val url = "$BASE/discover/movie?api_key=$apiKey&with_genres=$genreTmdbId" +
            "&sort_by=popularity.desc&page=$page"
        val body = http.getText(url) ?: return emptyList()
        return parseMovieList(body, limit)
    }

    fun lookup(title: String, year: Int?): TmdbMovie? {
        val hits = searchMovies(title, 5)
        if (hits.isEmpty()) return null
        val match = if (year != null) {
            hits.firstOrNull { it.year == year } ?: hits.first()
        } else {
            hits.first()
        }
        return match.copy(trailerKey = trailer(match.tmdbId) ?: match.trailerKey)
    }

    private fun trailer(tmdbId: Int): String? {
        val url = "$BASE/movie/$tmdbId/videos?api_key=$apiKey"
        val body = http.getText(url) ?: return null
        return try {
            val results = JsonParser.parseString(body).asJsonObject.getAsJsonArray("results") ?: return null
            results.map { it.asJsonObject }
                .firstOrNull { obj ->
                    obj.get("site")?.asString.equals("YouTube", true) &&
                        obj.get("type")?.asString.equals("Trailer", true)
                }
                ?.get("key")?.asString
                ?.takeIf { it.length == 11 }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMovieList(body: String, limit: Int): List<TmdbMovie> {
        return try {
            val results = JsonParser.parseString(body).asJsonObject.getAsJsonArray("results") ?: return emptyList()
            results.take(limit).mapNotNull { el ->
                val obj = el.asJsonObject
                val title = obj.get("title")?.asString ?: return@mapNotNull null
                val date = obj.get("release_date")?.asString.orEmpty()
                val year = date.take(4).toIntOrNull()?.takeIf { it in 1900..2100 }
                val posterPath = obj.get("poster_path")?.asString
                TmdbMovie(
                    tmdbId = obj.get("id")?.asInt ?: return@mapNotNull null,
                    title = title,
                    year = year,
                    overview = obj.get("overview")?.asString?.takeIf { it.isNotBlank() },
                    posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                    popularity = obj.get("popularity")?.asDouble ?: 0.0,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val BASE = "https://api.themoviedb.org/3"
        val GENRE_TMDB_IDS = mapOf(
            "action" to 28,
            "adventure" to 12,
            "animation" to 16,
            "comedy" to 35,
            "crime" to 80,
            "drama" to 18,
            "fantasy" to 14,
            "horror" to 27,
            "mystery" to 9648,
            "romance" to 10749,
            "sci-fi" to 878,
            "thriller" to 53,
        )
        val GENRE_QUERIES = mapOf(
            "action" to "action",
            "adventure" to "adventure",
            "animation" to "animation",
            "comedy" to "comedy",
            "crime" to "crime",
            "drama" to "drama",
            "fantasy" to "fantasy",
            "horror" to "horror",
            "mystery" to "mystery",
            "romance" to "romance",
            "sci-fi" to "science fiction",
            "thriller" to "thriller",
        )
    }
}
