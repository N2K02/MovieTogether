package com.movietogether.app.watch

import org.junit.Assert.*
import org.junit.Test

class RoomTransportPolicyTest {
    private val token = "A".repeat(32)

    @Test fun allowsPrivateAndVpnAddressesWithoutTls() {
        listOf("192.168.1.8", "10.0.0.5", "172.16.0.2", "172.31.1.10",
            "100.100.22.3", "localhost", "device.local", "::1", "fd00::1"
        ).forEach { host ->
            assertTrue("Should allow $host", RoomTransportPolicy.mayConnect(RoomAddress(host, 8787, token)))
        }
    }

    @Test fun blocksPublicPlaintextButAllowsVerifiedTlsScheme() {
        listOf("8.8.8.8", "watch.example.org", "172.32.0.1", "100.128.0.1"
        ).forEach { host ->
            assertFalse("Should block insecure public $host",
                RoomTransportPolicy.mayConnect(RoomAddress(host, 8787, token)))
            assertTrue(RoomTransportPolicy.mayConnect(RoomAddress(host, 443, token, secure = true)))
        }
    }
}
