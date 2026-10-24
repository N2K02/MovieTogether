package com.movietogether.app

import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.n2k02.updater.UpdateStatus

/** Stable and experimental updates share the same pending-update indicator. */
internal fun UpdateStatus.hasPendingUpdate(): Boolean = when (this) {
    is UpdateStatus.Available,
    is UpdateStatus.Downloading,
    is UpdateStatus.Ready,
    is UpdateStatus.NeedsInstallPermission -> true
    else -> false
}

/** Visible text makes the state clear for TalkBack and does not rely on colour alone. */
@Composable
internal fun UpdateAvailableBadge() {
    Badge { Text("New") }
}
