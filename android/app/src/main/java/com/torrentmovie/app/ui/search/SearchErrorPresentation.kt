package com.torrentmovie.app.ui.search

internal fun searchEmptyStateMessage(errorCode: Int?, error: String?): String {
    return when (errorCode) {
        503 -> {
            val detail = error?.trim().orEmpty()
            when {
                detail.isEmpty() ||
                    detail.equals("No working indexers", ignoreCase = true) ||
                    detail.equals("No sources available", ignoreCase = true) ->
                    "No sources available. Check the search API and try again."
                else -> detail
            }
        }
        400 -> error?.takeIf { it.isNotBlank() }
            ?: "Invalid search request. Check filters and try again."
        else -> error?.takeIf { it.isNotBlank() }
            ?: "No results found. Try another title or adjust filters."
    }
}

internal fun shouldSnackbarSearchError(hasVisibleResults: Boolean): Boolean = hasVisibleResults
