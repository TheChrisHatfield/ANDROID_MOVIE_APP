package com.torrentmovie.core.data

class SearchException(
    message: String,
    val httpCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
