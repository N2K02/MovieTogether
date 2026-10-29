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

    @Test fun guestNameIsFrozenAcrossChatAndPlaybackEvents() {
        val identity = RoomParticipantIdentity(6)
        val fromChat = identity.claim("Sam")
        val spoofedPlayback = identity.claim("Another guest")
        val spoofedChat = identity.claim("Host")
        assertEquals("Sam #6", fromChat)
        assertEquals(fromChat, spoofedPlayback)
        assertEquals(fromChat, spoofedChat)
    }

    @Test fun chatAndPlaybackHandlersUseTheSameConnectionIdentity() {
        val path = "app/src/main/java/com/movietogether/app/watch/RoomHttpServer.kt"
        val source = listOf(java.io.File(path), java.io.File("../$path"))
            .first { it.isFile }.readText()
        assertEquals(2, Regex("identity\\.claim\\(").findAll(source).count())
        org.junit.Assert.assertTrue(source.contains(
            "\"chat\" -> receiveGuestChat(event, identity.claim("))
        org.junit.Assert.assertTrue(source.contains(
            "identity.claim(event.optString(\"name\", \"Guest\"))"))
    }

    @Test fun fallbackIdentityIsStillDistinguishable() {
        assertNotEquals(RoomParticipantIdentity(4).claim(""),
            RoomParticipantIdentity(5).claim(""))
    }
}
