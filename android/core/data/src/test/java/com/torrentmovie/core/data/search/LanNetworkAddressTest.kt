package com.torrentmovie.core.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanNetworkAddressTest {
    @Test
    fun candidateHostsPrioritizesSelfAndCommonGateways() {
        val hosts = LanNetworkAddress.candidateHosts("192.168.1.42")
        assertEquals("192.168.1.42", hosts.first())
        assertTrue(hosts.indexOf("192.168.1.1") < hosts.indexOf("192.168.1.50"))
        assertEquals(254, hosts.size)
    }

    @Test
    fun candidateHostsRejectsNonIpv4() {
        assertTrue(LanNetworkAddress.candidateHosts("fe80::1").isEmpty())
        assertTrue(LanNetworkAddress.candidateHosts("not-an-ip").isEmpty())
        assertTrue(LanNetworkAddress.candidateHosts("").isEmpty())
    }

    @Test
    fun dhcpSlash32PrefixIsStillUsableForLanGuess() {
        assertTrue(LanNetworkAddress.isUsableLanPrefixLength(24))
        assertTrue(LanNetworkAddress.isUsableLanPrefixLength(32))
        assertTrue(!LanNetworkAddress.isUsableLanPrefixLength(7))
        assertTrue(!LanNetworkAddress.isUsableLanPrefixLength(33))
    }
}
