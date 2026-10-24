package com.movietogether.app.watch

import org.junit.Assert.*
import org.junit.Test

class RoomAddressTest {
    @Test fun roundTrip() {
        val original = RoomAddress("192.168.1.10", 8787, RoomAddress.newToken())
        assertEquals(original, RoomAddress.parse(original.appLink))
        assertEquals(original, RoomAddress.parse(original.browserUrl))
    }
    @Test fun rejectArbitrarySite() {
        try { RoomAddress.parse("https://example.com/join/abcd"); fail("should reject") }
        catch (_: IllegalArgumentException) {}
        catch (_: IllegalStateException) {}
    }
    @Test fun tokensAreUnique() = assertNotEquals(RoomAddress.newToken(), RoomAddress.newToken())
}
