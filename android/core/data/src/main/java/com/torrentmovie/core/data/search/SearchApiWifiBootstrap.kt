package com.torrentmovie.core.data.search

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Re-run Search API bind on Wi-Fi up/down, SSID/IPv4 change, or cellular (5G) (FR-040).
 */
class SearchApiWifiBootstrap(
    context: Context,
    private val onNetworkChanged: () -> Unit,
    private val shouldKeepListening: () -> Boolean,
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var registered = false
    private val notifyRunnable = Runnable {
        if (!shouldKeepListening()) {
            unregister()
            return@Runnable
        }
        onNetworkChanged()
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scheduleAdapt()
        }

        override fun onLost(network: Network) {
            scheduleAdapt()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            scheduleAdapt()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            scheduleAdapt()
        }
    }

    fun register() {
        if (registered) return
        val cm = connectivityManager ?: return
        try {
            // Default network covers Wi-Fi, Ethernet, and cellular/5G.
            cm.registerDefaultNetworkCallback(callback, mainHandler)
            registered = true
            if (shouldKeepListening()) {
                scheduleAdapt()
            }
        } catch (_: Throwable) {
            registered = false
        }
    }

    private fun scheduleAdapt() {
        if (!shouldKeepListening()) {
            unregister()
            return
        }
        mainHandler.removeCallbacks(notifyRunnable)
        mainHandler.postDelayed(notifyRunnable, NETWORK_ADAPT_DEBOUNCE_MS)
    }

    fun unregister() {
        if (!registered) return
        mainHandler.removeCallbacks(notifyRunnable)
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (_: Throwable) {
            // Already unregistered by the system or OEM ConnectivityManager stub.
        }
        registered = false
    }

    companion object {
        internal const val NETWORK_ADAPT_DEBOUNCE_MS = 800L
    }
}
