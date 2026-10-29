package com.movietogether.app.watch

import org.junit.Assert.*
import org.junit.Test

class FirebaseRoomDirectoryTest {
    private val code = "abcdefghijklmnopqrstuvwx12345678"

    @Test fun cloudCodeAccepted() {
        assertEquals(code, FirebaseRoomDirectory.extractCode(code))
        assertEquals(code, FirebaseRoomDirectory.extractCode("movietogether://join?room=$code"))
    }

    @Test fun directLinkIsNotCloudCode() {
        assertNull(FirebaseRoomDirectory.extractCode("movietogether://join?host=192.168.1.12&port=8787&key=some-token"))
        assertNull(FirebaseRoomDirectory.extractCode("http://192.168.1.12:8787/join/abcd"))
    }

    @Test fun invalidCodesAreIgnored() {
        assertNull(FirebaseRoomDirectory.extractCode("too-short"))
        assertNull(FirebaseRoomDirectory.extractCode("movietogether://join?room=bad"))
        assertNull(FirebaseRoomDirectory.extractCode("https://example.com/?room=$code"))
    }
    @Test fun expiryRulesRejectMissingAndExpiredRooms() {
        val now = 1_000_000L
        assertTrue(FirebaseRoomDirectory.roomExpired(false, now + 60_000L, now))
        assertTrue(FirebaseRoomDirectory.roomExpired(true, now - 1, now))
        assertFalse(FirebaseRoomDirectory.roomExpired(true, now, now))
        assertFalse(FirebaseRoomDirectory.roomExpired(true, now + 60_000L, now))
    }

    @Test fun eventCodesAreFixedAndSensitiveInformationIsNotInterpolated() {
        val source = sequenceOf(
            java.io.File("app/src/main/java/com/movietogether/app/watch/FirebaseRoomDirectory.kt"),
            java.io.File("../app/src/main/java/com/movietogether/app/watch/FirebaseRoomDirectory.kt"),
        ).first { it.isFile }.readText()
        listOf("AUTH_FAILED", "ROOM_PUBLISH_FAILED", "ROOM_RESOLVE_FAILED", "ROOM_EXPIRED")
            .forEach { assertTrue("Missing diagnostic event: $it", source.contains("event(\"$it\")")) }
        assertTrue(source.contains("AppLog.add(\"FIREBASE\", code)"))
        assertFalse("Never log exception messages", source.contains("event(it.localizedMessage)"))
        assertFalse("Never log invitation codes", source.contains("event(code)"))
    }

}
