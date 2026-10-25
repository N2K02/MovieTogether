package com.movietogether.app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression source contract for Android PiP and conversation notifications.
 * These do not replace on-device PiP, bubble-permission, or background playback tests.
 */
class PipAndRoomBubbleContractTest {
    private fun source(relative: String): String =
        listOf(File(relative), File("../$relative"))
            .firstOrNull { it.isFile }?.readText() ?: error("Missing source: $relative")

    @Test fun mainActivityOnlyAutoEntersWhileWatchingAndPlaying() {
        val ui = source("app/src/main/java/com/movietogether/app/MainActivity.kt")
        assertTrue(ui.contains("watchOnScreen && playerState.playing"))
        assertTrue(ui.contains("setAutoEnterEnabled(eligible)"))
        assertTrue(ui.contains("override fun onUserLeaveHint()"))
        assertTrue(ui.contains("enterPictureInPictureMode("))
        assertTrue(ui.contains("onPictureInPictureModeChanged("))
        assertTrue(ui.contains("RoomChatBubbleNotifications.showRoomBubble()"))
    }

    @Test fun pipKeepsPlayerMountedWhileHidingControls() {
        val ui = source("app/src/main/java/com/movietogether/app/MainActivity.kt")
        val player = source("app/src/main/java/com/movietogether/app/watch/WatchPlayer.kt")
        assertTrue(ui.contains("WatchPlayer(selectedFile!!, true, null, displayName, inPictureInPicture)"))
        assertTrue(ui.contains("displayName, inPictureInPicture)"))
        assertTrue(player.contains("if (inPictureInPicture) Modifier.fillMaxSize()"))
        assertTrue(player.contains("if (!inPictureInPicture) Row("))
        assertTrue(ui.contains("if (!inPictureInPicture) NavigationBar"))
    }

    @Test fun manifestSupportsPipAndEmbeddableBubbleActivity() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:supportsPictureInPicture=\"true\""))
        assertTrue(manifest.contains("android:configChanges="))
        assertTrue(manifest.contains("android:name=\".ChatBubbleActivity\""))
        assertTrue(manifest.contains("android:allowEmbedded=\"true\""))
        assertTrue(manifest.contains("android:resizeableActivity=\"true\""))
    }

    @Test fun chatBubbleUsesConversationAndPermissions() {
        val bubble = source("app/src/main/java/com/movietogether/app/RoomChatBubbleNotifications.kt")
        val runtime = source("app/src/main/java/com/movietogether/app/watch/RoomRuntime.kt")
        assertTrue(bubble.contains("Notification.MessagingStyle("))
        assertTrue(bubble.contains("Notification.BubbleMetadata.Builder(SHORTCUT_ID)"))
        assertTrue(bubble.contains("setLongLived(true)"))
        assertTrue(bubble.contains("POST_NOTIFICATIONS"))
        assertTrue(bubble.contains("if (appForeground ||"))
        assertTrue(runtime.contains("RoomChatBubbleNotifications.onNewMessage(entry)"))
        assertFalse("No system-wide overlay permission needed",
            source("app/src/main/AndroidManifest.xml").contains("SYSTEM_ALERT_WINDOW"))
    }

    @Test fun leaveAndQuitDismissRoomBubble() {
        val source = source("app/src/main/java/com/movietogether/app/MainActivity.kt")
        assertTrue(source.contains("RoomChatBubbleNotifications.dismiss()"))
        assertTrue(source.contains("HostService.stop(context)"))
        assertTrue(source.contains("RoomRuntime.leaveRoom()"))
    }
}
