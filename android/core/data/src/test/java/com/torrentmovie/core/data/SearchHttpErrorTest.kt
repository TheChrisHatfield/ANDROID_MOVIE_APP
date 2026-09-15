package com.torrentmovie.core.data

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SearchHttpErrorTest {
    @Test
    fun preserves400DetailFromApiBody() {
        val body = """{"detail":"min_seeds cannot exceed max_seeds"}"""
            .toResponseBody("application/json".toMediaType())
        val response = Response.error<Any>(400, body)
        val exception = mapSearchHttpError(HttpException(response), Gson())
        assertEquals("min_seeds cannot exceed max_seeds", exception.message)
        assertEquals(400, exception.httpCode)
    }

    @Test
    fun preserves503DetailFromApiBody() {
        val body = """{"detail":"No movie indexers available"}"""
            .toResponseBody("application/json".toMediaType())
        val response = Response.error<Any>(503, body)
        val exception = mapSearchHttpError(HttpException(response), Gson())
        assertEquals("No movie indexers available", exception.message)
        assertEquals(503, exception.httpCode)
    }
}
