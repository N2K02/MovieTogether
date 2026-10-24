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
}
