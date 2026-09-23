package com.torrentmovie.core.data.search

import android.content.Context
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

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val cm = connectivityManager ?: return
            val onWifi = cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
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
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
    }

    fun unregister() {
        connectivityManager?.unregisterNetworkCallback(callback)
    }
}
