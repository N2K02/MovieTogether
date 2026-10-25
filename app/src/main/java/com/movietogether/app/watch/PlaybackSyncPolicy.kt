package com.movietogether.app.watch

/**
 * Estimate current host playback without assuming the host and viewer wall clocks agree.
 * Host snapshots are captured against each device's local monotonic clock.
 */
object PlaybackSyncPolicy {
    const val DRIFT_TOLERANCE_MS = 900L
    private const val MAX_PROGRESS_MS = 15_000L

    fun targetPosition(snapshot: PlaybackSnapshot, nowElapsedMs: Long): Long {
        if (!snapshot.playing || snapshot.observedAtElapsedMs <= 0L) return snapshot.positionMs.coerceAtLeast(0L)
        val elapsed = (nowElapsedMs - snapshot.observedAtElapsedMs).coerceIn(0L, MAX_PROGRESS_MS)
        return (snapshot.positionMs + elapsed).coerceAtLeast(0L)
    }

    fun shouldSeek(playerPositionMs: Long, targetMs: Long, ready: Boolean): Boolean =
        ready && kotlin.math.abs(playerPositionMs - targetMs) >= DRIFT_TOLERANCE_MS

    /** Capped automatic retry; never retries revoked/invalid room invitations. */
    fun reconnectDelayMs(attempt: Int): Long =
        when {
            attempt <= 1 -> 1_000L
            attempt == 2 -> 2_000L
            attempt == 3 -> 4_000L
            attempt == 4 -> 8_000L
            else -> 15_000L
        }
}
