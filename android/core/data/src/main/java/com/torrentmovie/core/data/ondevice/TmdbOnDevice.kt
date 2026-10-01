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
    private val apiBase: String = BASE,
) {
    val configured: Boolean get() = apiKey.isNotBlank()
    @Volatile var keyRejected: Boolean = false
        private set

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
        val url = "$apiBase/search/movie?api_key=$apiKey&query=$q"
        val fetched = http.fetch(url)
        if (fetched.code == 401 || fetched.code == 403) {
            keyRejected = true
            return emptyList()
        }
        val body = fetched.body.takeIf { fetched.code in 200..299 } ?: return emptyList()
        return parseMovieList(body, limit)
    }

    fun discover(genreTmdbId: Int, page: Int, limit: Int): List<TmdbMovie> {
        if (!configured) return emptyList()
        val url = "$apiBase/discover/movie?api_key=$apiKey&with_genres=$genreTmdbId" +
            "&sort_by=popularity.desc&page=$page"
        val fetched = http.fetch(url)
        if (fetched.code == 401 || fetched.code == 403) {
            keyRejected = true
            return emptyList()
        }
        val body = fetched.body.takeIf { fetched.code in 200..299 } ?: return emptyList()
        return parseMovieList(body, limit)
    }

    fun lookup(title: String, year: Int?, fetchTrailer: Boolean = false): TmdbMovie? {
        val hits = searchMovies(title, 5)
        if (hits.isEmpty()) return null
        val match = if (year != null) {
            hits.firstOrNull { it.year == year } ?: hits.first()
        } else {
            hits.first()
        }
        if (!fetchTrailer) return match
        return match.copy(trailerKey = trailer(match.tmdbId) ?: match.trailerKey)
    }

    private fun trailer(tmdbId: Int): String? {
        val url = "$apiBase/movie/$tmdbId/videos?api_key=$apiKey"
        val fetched = http.fetch(url)
        if (fetched.code == 401 || fetched.code == 403) {
            keyRejected = true
            return null
        }
        val body = fetched.body.takeIf { fetched.code in 200..299 } ?: return null
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
            val resultsEl = JsonParser.parseString(body).asJsonObject.get("results")
            if (resultsEl == null || resultsEl.isJsonNull || !resultsEl.isJsonArray) return emptyList()
            val results = resultsEl.asJsonArray
            results.take(limit).mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    val title = obj.get("title")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: return@mapNotNull null
                    val dateEl = obj.get("release_date")
                    val date = if (dateEl != null && dateEl.isJsonPrimitive) dateEl.asString else ""
                    val year = date.take(4).toIntOrNull()?.takeIf { it in 1900..2100 }
                    val posterEl = obj.get("poster_path")
                    val posterPath = if (posterEl != null && posterEl.isJsonPrimitive && !posterEl.isJsonNull) {
                        posterEl.asString.takeIf { it.isNotBlank() && it != "null" }
                    } else {
                        null
                    }
                    val id = MovieIndexers.jsonPrimitiveString(obj, "id")?.toIntOrNull()
                        ?: return@mapNotNull null
                    val overviewEl = obj.get("overview")
                    val overview = if (overviewEl != null && overviewEl.isJsonPrimitive) {
                        overviewEl.asString.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                    TmdbMovie(
                        tmdbId = id,
                        title = title,
                        year = year,
                        overview = overview,
                        posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                        popularity = obj.get("popularity")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0,
                    )
                } catch (_: Exception) {
                    null
                }
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
