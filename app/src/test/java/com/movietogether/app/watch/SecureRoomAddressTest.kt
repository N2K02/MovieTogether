package com.movietogether.app.watch

import org.junit.Assert.*
import org.junit.Test

class SecureRoomAddressTest {
    private val token = "A".repeat(32)
    @Test fun httpsInvitationUsesWss() {
        val address = RoomAddress("watch.example.org", 443, token, secure = true)
        assertTrue(address.mediaUrl.startsWith("https://"))
        assertTrue(address.wsUrl.startsWith("wss://"))
        assertEquals(address, RoomAddress.parse(address.browserUrl))
        assertEquals(address, RoomAddress.parse(address.appLink))
    }
    @Test fun plainLocalModeIsExplicit() {
        val local = RoomAddress("192.168.1.50", 8787, token)
        assertTrue(local.mediaUrl.startsWith("http://"))
        assertTrue(local.wsUrl.startsWith("ws://"))
        assertEquals(local, RoomAddress.parse(local.appLink))
    }
    @Test fun rejectsCredentialsInLink() {
        assertThrows(IllegalArgumentException::class.java) {
            RoomAddress.parse("https://user:pass@watch.example.org/join/$token")
        }
    }
}
