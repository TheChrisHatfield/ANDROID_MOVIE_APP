package com.torrentmovie.core.data.search

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Wi‑Fi IPv4 helpers for LAN Search API discovery (FR-040). */
object LanNetworkAddress {
    fun wifiIpv4(context: Context): String? {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = cm.allNetworks.firstOrNull { net ->
            cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return null
        val props = cm.getLinkProperties(network) ?: return null
        for (addr in props.linkAddresses) {
            val host = addr.address.hostAddress ?: continue
            if (host.contains(':')) continue
            if (addr.prefixLength in 8..30) return host
        }
        return null
    }

    /** Host order tuned for home LAN: self, common gateways, then rest of /24. */
    fun candidateHosts(wifiIpv4: String): List<String> {
        val parts = wifiIpv4.split('.')
        if (parts.size != 4) return emptyList()
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        val self = parts[3].toIntOrNull()
        val order = mutableListOf<Int>()
        if (self != null) order += self
        order += listOf(1, 2, 100, 254)
        order += (1..254).filter { it !in order }
        return order.distinct().map { "$prefix.$it" }
    }
}
