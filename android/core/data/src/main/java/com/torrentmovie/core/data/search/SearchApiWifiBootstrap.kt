package com.torrentmovie.core.data.search

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/** Re-run Search API bootstrap when Wi‑Fi becomes available after cold start (FR-040). */
class SearchApiWifiBootstrap(
    context: Context,
    private val onWifiReady: () -> Unit,
    private val shouldKeepListening: () -> Boolean,
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val cm = connectivityManager ?: return
            val onWifi = try {
                cm.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } catch (_: Throwable) {
                false
            }
            if (!onWifi) return
            if (!shouldKeepListening()) {
                unregister()
                return
            }
            onWifiReady()
            if (!shouldKeepListening()) {
                unregister()
            }
        }
    }

    fun register() {
        if (registered) return
        val cm = connectivityManager ?: return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            // Handler form is API 26+ (our minSdk) and avoids binder-thread OEM crashes.
            cm.registerNetworkCallback(request, callback, mainHandler)
            registered = true
            if (shouldKeepListening() && isOnWifi(cm)) {
                onWifiReady()
            }
        } catch (_: Throwable) {
            registered = false
        }
    }

    private fun isOnWifi(cm: ConnectivityManager): Boolean {
        return try {
            val active = cm.activeNetwork
            if (active != null &&
                cm.getNetworkCapabilities(active)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            ) {
                return true
            }
            cm.allNetworks.any { network ->
                cm.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun unregister() {
        if (!registered) return
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (_: Throwable) {
            // Already unregistered by the system or OEM ConnectivityManager stub.
        }
        registered = false
    }
}
