package com.movietogether.app.watch

import org.junit.Assert.*
import org.junit.Test

class PlaybackSyncPolicyTest {
    @Test fun pausedPositionDoesNotAdvance() {
        val snapshot = PlaybackSnapshot(positionMs = 5000, playing = false, observedAtElapsedMs = 1000)
        assertEquals(5000L, PlaybackSyncPolicy.targetPosition(snapshot, 10000))
    }

    @Test fun playingSnapshotUsesLocalMonotonicTime() {
        val snapshot = PlaybackSnapshot(positionMs = 5000, playing = true, observedAtElapsedMs = 1000)
        assertEquals(6500L, PlaybackSyncPolicy.targetPosition(snapshot, 2500))
    }

    @Test fun negativeTimeAndLargeJumpDoNotCorruptSeekTarget() {
        val snapshot = PlaybackSnapshot(positionMs = 5000, playing = true, observedAtElapsedMs = 1000)
        assertEquals(5000L, PlaybackSyncPolicy.targetPosition(snapshot, 900))
        assertEquals(20000L, PlaybackSyncPolicy.targetPosition(snapshot, 100000))
    }

    @Test fun seeksOnlyWhenReadyAndOutOfSync() {
        assertFalse(PlaybackSyncPolicy.shouldSeek(0, 10_000, ready = false))
        assertFalse(PlaybackSyncPolicy.shouldSeek(5000, 5500, ready = true))
        assertTrue(PlaybackSyncPolicy.shouldSeek(5000, 6000, ready = true))
    }

    @Test fun retryBackoffIsBounded() {
        assertEquals(1000L, PlaybackSyncPolicy.reconnectDelayMs(1))
        assertEquals(4000L, PlaybackSyncPolicy.reconnectDelayMs(3))
        assertEquals(15000L, PlaybackSyncPolicy.reconnectDelayMs(100))
    }
}
