package com.movietogether.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Isolated behavioral tests for Quit, including failed cleanups and repeated taps. */
class AppShutdownCoordinatorTest {
    @Test fun cleanupIsOrderedAndFinishesEvenWhenAnActionFails() {
        val events = mutableListOf<String>()
        val coordinator = AppShutdownCoordinator(
            cleanupActions = listOf(
                { events += "leave guest" },
                { events += "revoke invitation"; error("Firebase not reachable") },
                { events += "stop server" },
                { events += "stop foreground service" },
                { events += "cancel updater" },
            ),
            finishActivity = { events += "finish" },
        )

        coordinator.quit()

        assertEquals(
            listOf("leave guest", "revoke invitation", "stop server",
                "stop foreground service", "cancel updater", "finish"),
            events,
        )
    }

    @Test fun multipleQuitTapsDoNotRepeatCleanup() {
        var stopped = 0
        var finished = 0
        val coordinator = AppShutdownCoordinator(
            cleanupActions = listOf({ stopped++ }),
            finishActivity = { finished++ },
        )
        coordinator.quit()
        coordinator.quit()
        assertEquals(1, stopped)
        assertEquals(1, finished)
    }
}
