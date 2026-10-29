package com.movietogether.app.watch

/** One distinguishable label per authenticated room socket, frozen on first action.
 * A nickname is not proof of identity. The server appends its socket ID so two
 * visitors choosing identical or malicious names never appear identical.
 */
internal class RoomParticipantIdentity(private val connectionNumber: Int) {
    init { require(connectionNumber > 0) }
    private var assigned: String? = null

    @Synchronized fun claim(requested: String): String {
        val existing = assigned
        if (existing != null) return existing
        val safe = PlaybackRules.displayName(requested).take(24)
        return "$safe #$connectionNumber".also { assigned = it }
    }
}
