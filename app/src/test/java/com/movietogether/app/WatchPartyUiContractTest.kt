package com.movietogether.app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source guards for room navigation and the in-player chat entry point. */
class WatchPartyUiContractTest {
    private fun source(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText()
            ?: error("Missing $path")

    @Test fun playerExposesSharedControlsAndParticipantAttribution() {
        val code = source("app/src/main/java/com/movietogether/app/watch/WatchPlayer.kt")
        assertTrue(code.contains("RoomRuntime.requestPlayback("))
        assertTrue(code.contains("Paused by"))
        assertTrue(code.contains("Resumed by"))
    }

    @Test fun roomChatUsesCollapsibleInAppBubble() {
        val code = source("app/src/main/java/com/movietogether/app/MainActivity.kt")
        assertTrue(code.contains("FloatingActionButton("))
        assertTrue(code.contains("ModalBottomSheet("))
        assertTrue(code.contains("lastSeenMessageCount"))
        assertTrue(code.contains("RoomRuntime.sendChat("))
    }

    @Test fun leaveAndRejoinPersistInvitationAndAllowForgetting() {
        val code = source("app/src/main/java/com/movietogether/app/MainActivity.kt")
        assertTrue(code.contains("lastJoinedLink"))
        assertTrue(code.contains("Rejoin last room"))
        assertTrue(code.contains("remove(\"lastJoinedLink\")"))
        assertTrue(code.contains("RoomRuntime.leaveRoom()"))
        assertTrue(code.contains("label = { Text(\"Quit\") }"))
    }

    @Test fun onlyAuthenticatedRoomSocketsProcessPlaybackRequests() {
        val code = source("app/src/main/java/com/movietogether/app/watch/RoomHttpServer.kt")
        assertTrue(code.contains("!authorized(session)"))
        assertTrue(code.contains("receiveGuestPlaybackControl"))
    }
}
