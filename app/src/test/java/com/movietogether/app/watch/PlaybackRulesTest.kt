package com.movietogether.app.watch

import org.junit.Assert.*
import org.junit.Test

class PlaybackRulesTest {
    @Test fun anyParticipantMayPauseWithAttribution() {
        val playing = PlaybackSnapshot(positionMs = 3200L, playing = true, revision = 7L)
        val paused = requireNotNull(PlaybackRules.control(playing, false, "  Sam  ", 3650L))
        assertFalse(paused.playing)
        assertEquals("Sam", paused.changedBy)
        assertEquals("paused", paused.change)
        assertEquals(3650L, paused.positionMs)
        assertEquals(8L, paused.revision)
    }

    @Test fun anotherParticipantMayResumeWithTheirName() {
        val paused = PlaybackSnapshot(positionMs = 5400L, playing = false, revision = 11L,
            changedBy = "Sam", change = "paused")
        val playing = requireNotNull(PlaybackRules.control(paused, true, "Alex", paused.positionMs))
        assertTrue(playing.playing)
        assertEquals("Alex", playing.changedBy)
        assertEquals("resumed", playing.change)
    }

    @Test fun duplicatesDoNotReplaceWhoPaused() {
        val paused = PlaybackSnapshot(100L, false, 3L, "Sam", "paused")
        assertNull(PlaybackRules.control(paused, false, "Alex", 110L))
    }

    @Test fun seekingKeepsAttributionAndClampsNegativePositions() {
        val paused = PlaybackSnapshot(5400L, false, 4L, "Sam", "paused")
        val seeked = requireNotNull(PlaybackRules.seek(paused, -100L))
        assertEquals(0L, seeked.positionMs)
        assertEquals("Sam", seeked.changedBy)
        assertEquals("paused", seeked.change)
        assertEquals(5L, seeked.revision)
    }

    @Test fun namesAreTrimmedBoundedAndNotTrustedIdentity() {
        assertEquals("Guest", PlaybackRules.displayName("   "))
        assertEquals(30, PlaybackRules.displayName("A".repeat(60)).length)
    }
}
