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
}
