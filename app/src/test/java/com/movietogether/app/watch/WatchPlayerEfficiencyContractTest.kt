package com.movietogether.app.watch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WatchPlayerEfficiencyContractTest {
    @Test fun timersOnlyRunWhileMediaIsPlaying() {
        val candidates = listOf(
            File("src/main/java/com/movietogether/app/watch/WatchPlayer.kt"),
            File("app/src/main/java/com/movietogether/app/watch/WatchPlayer.kt")
        )
        val code = candidates.first { it.exists() }.readText()
        assertTrue(code.contains("if (isHost && snapshot.playing)"))
        assertTrue(code.contains("if (!isHost && snapshot.playing)"))
        assertTrue(code.contains("LaunchedEffect(player, snapshot.revision)"))
        assertTrue(code.contains("player.release()"))
        assertTrue(code.contains("if (it.player !== player) it.player = player"))
    }
}
