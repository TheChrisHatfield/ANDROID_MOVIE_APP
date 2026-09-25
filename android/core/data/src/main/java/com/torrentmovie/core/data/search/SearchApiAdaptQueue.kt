package com.torrentmovie.core.data.search

/** Serialize Search API re-bind so an SSID/cellular change is not dropped mid-scan. */
internal class SearchApiAdaptQueue {
    private val lock = Any()
    private var running = false
    private var queued = false

    fun tryStart(): Boolean {
        synchronized(lock) {
            if (running) {
                queued = true
                return false
            }
            running = true
            queued = false
            return true
        }
    }

    fun consumeQueued(): Boolean {
        synchronized(lock) {
            if (!queued) return false
            queued = false
            return true
        }
    }

    fun finish(): Boolean {
        synchronized(lock) {
            running = false
            val restart = queued
            queued = false
            return restart
        }
    }
}
