package com.torrentmovie.core.data.search

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Re-run Search API bind on Wi-Fi up/down, SSID/IPv4 change, or cellular (5G) (FR-040).
 */
class SearchApiWifiBootstrap(
    context: Context,
    private val onNetworkChanged: () -> Unit,
    private val shouldKeepListening: () -> Boolean,
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
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
            val internet = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val wifi = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            val cellular = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()
            cm.registerNetworkCallback(internet, callback, mainHandler)
            // Same callback cannot be registered twice; use extra callbacks for SSID vs 5G.
            cm.registerNetworkCallback(wifi, wifiCallback, mainHandler)
            cm.registerNetworkCallback(cellular, cellCallback, mainHandler)
            registered = true
            if (shouldKeepListening()) {
                scheduleAdapt()
            }
        } catch (_: Throwable) {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Throwable) {
            }
            try {
                cm.unregisterNetworkCallback(wifiCallback)
            } catch (_: Throwable) {
            }
            try {
                cm.unregisterNetworkCallback(cellCallback)
            } catch (_: Throwable) {
            }
            registered = false
        }
    }

    private val wifiCallback = forwardingCallback()
    private val cellCallback = forwardingCallback()

    private fun forwardingCallback() = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleAdapt()
        override fun onLost(network: Network) = scheduleAdapt()
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
            scheduleAdapt()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
            scheduleAdapt()
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
        }
        try {
            connectivityManager?.unregisterNetworkCallback(wifiCallback)
        } catch (_: Throwable) {
        }
        try {
            connectivityManager?.unregisterNetworkCallback(cellCallback)
        } catch (_: Throwable) {
        }
        registered = false
    }

    companion object {
        internal const val NETWORK_ADAPT_DEBOUNCE_MS = 800L
    }
}
