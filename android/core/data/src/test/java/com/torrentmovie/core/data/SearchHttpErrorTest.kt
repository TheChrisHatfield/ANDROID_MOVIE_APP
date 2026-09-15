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
    fun preserves503DetailFromApiBody() {
        val body = """{"detail":"No movie indexers available"}"""
            .toResponseBody("application/json".toMediaType())
        val response = Response.error<Any>(503, body)
        val exception = mapSearchHttpError(HttpException(response), Gson())
        assertEquals("No movie indexers available", exception.message)
        assertEquals(503, exception.httpCode)
    }
}
