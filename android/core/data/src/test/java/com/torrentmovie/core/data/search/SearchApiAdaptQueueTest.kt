package com.torrentmovie.core.data.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchApiAdaptQueueTest {
    @Test
    fun overlappingRequestIsQueuedThenRestarted() {
        val queue = SearchApiAdaptQueue()
        assertTrue(queue.tryStart())
        assertFalse(queue.tryStart())
        assertTrue(queue.consumeQueued())
        assertFalse(queue.consumeQueued())
        assertFalse(queue.finish())
    }

    @Test
    fun queuedDuringFinishRestarts() {
        val queue = SearchApiAdaptQueue()
        assertTrue(queue.tryStart())
        assertFalse(queue.tryStart())
        assertTrue(queue.finish())
        assertTrue(queue.tryStart())
    }
}
