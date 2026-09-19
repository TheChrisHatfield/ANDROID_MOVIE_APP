package com.torrentmovie.core.data

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

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
    fun parses422ValidationArrayDetail() {
        val body = """{"detail":[{"type":"less_than_equal","loc":["query","limit"],"msg":"Input should be less than or equal to 200"}]}"""
            .toResponseBody("application/json".toMediaType())
        val response = Response.error<Any>(422, body)
        val exception = mapSearchHttpError(HttpException(response), Gson())
        assertEquals(
            "Input should be less than or equal to 200",
            exception.message,
        )
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

class CanceledNetworkTest {
    @Test
    fun okHttpCanceledIsTreatedAsCancellation() {
        assertTrue(isCanceledNetwork(IOException("Canceled")))
        assertTrue(isCanceledNetwork(IOException("call canceled")))
        assertFalse(isCanceledNetwork(IOException("Cannot connect to search API")))
        assertFalse(isCanceledNetwork(IOException("timeout")))
    }
}
