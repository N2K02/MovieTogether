package com.movietogether.app

/**
 * Best-effort ordered cleanup for an explicit Quit action.
 * A failed cleanup step must not prevent the rest or closing the activity.
 */
internal class AppShutdownCoordinator(
    private val cleanupActions: List<() -> Unit>,
    private val finishActivity: () -> Unit,
) {
    private var quitRequested = false

    @Synchronized
    fun quit() {
        if (quitRequested) return
        quitRequested = true
        cleanupActions.forEach { action -> runCatching { action() } }
        finishActivity()
    }
}
