package com.movietogether.app.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoomParticipantIdentityTest {
    @Test fun sameNicknameFromDifferentGuestsHasDistinctAttribution() {
        val first = RoomParticipantIdentity(1).claim("Alex")
        val second = RoomParticipantIdentity(2).claim("Alex")
        assertNotEquals(first, second)
    }

    @Test fun aGuestCannotChangeTheirClaimedIdentityDuringPlayback() {
        val identity = RoomParticipantIdentity(3)
        assertEquals("Alex #3", identity.claim("Alex"))
        assertEquals("Alex #3", identity.claim("Other user"))
        assertEquals("Alex #3", identity.claim(""))
    }

    @Test fun fallbackIdentityIsStillDistinguishable() {
        assertNotEquals(RoomParticipantIdentity(4).claim(""),
            RoomParticipantIdentity(5).claim(""))
    }
}
