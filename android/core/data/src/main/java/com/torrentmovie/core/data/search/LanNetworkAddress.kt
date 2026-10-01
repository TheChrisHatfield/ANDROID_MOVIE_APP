package com.torrentmovie.core.data.search

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities

/** Wi‑Fi IPv4 helpers for LAN Search API discovery (FR-040). */
object LanNetworkAddress {
    fun wifiIpv4(context: Context): String? {
        return try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return null
            ipv4FromProperties(linkPropertiesForWifi(cm))
        } catch (_: Throwable) {
            null
        }
    }

    fun wifiAvailable(context: Context): Boolean {
        return try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return false
            linkPropertiesForWifi(cm) != null ||
                cm.allNetworks.any { net -> hasWifiTransport(cm, net) }
        } catch (_: Throwable) {
            false
        }
    }

    private fun linkPropertiesForWifi(cm: ConnectivityManager): LinkProperties? {
        val active = cm.activeNetwork
        if (active != null && hasWifiTransport(cm, active)) {
            cm.getLinkProperties(active)?.let { return it }
        }
        val wifiNetwork = cm.allNetworks.firstOrNull { net -> hasWifiTransport(cm, net) }
            ?: return null
        return cm.getLinkProperties(wifiNetwork)
    }

    private fun hasWifiTransport(cm: ConnectivityManager, network: Network): Boolean {
        return try {
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun ipv4FromProperties(props: LinkProperties?): String? {
        if (props == null) return null
        for (addr in props.linkAddresses) {
            val host = addr.address.hostAddress ?: continue
            if (host.contains(':')) continue
            if (isUsableLanPrefixLength(addr.prefixLength)) return host
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

    /** Some OEM Wi-Fi stacks report /32 for a DHCP IPv4; still use it to guess the /24. */
    internal fun isUsableLanPrefixLength(prefixLength: Int): Boolean = prefixLength in 8..32
}
