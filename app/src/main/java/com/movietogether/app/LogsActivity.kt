package com.movietogether.app

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Logs is a Settings subpage of MovieTogetherApp, not a standalone Activity.
 * The parent owns its single branded header, N2K02Theme, navigation and safe
 * insets, so the app's dark or dynamic colors apply here as well.
 */
@Composable
internal fun MovieTogetherLogsPage(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val enabled by AppLog.enabled.collectAsState()
    val version by AppLog.version.collectAsState()
    val logText = remember(version) { AppLog.all() }

    BoxWithConstraints(modifier) {
        // If a display is short (landscape or split screen), let the entire
        // page scroll so logging controls and all actions remain accessible.
        val compactHeight = maxHeight < 430.dp
        Column(
            modifier = Modifier.fillMaxSize()
                .then(if (compactHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ListItem(
                headlineContent = { Text("Enable detailed logging") },
                supportingContent = {
                    Text("Optional diagnostics; fatal crashes are always recorded.")
                },
                trailingContent = {
                    Switch(checked = enabled, onCheckedChange = AppLog::setEnabled)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
            Text(
                "Private storage · 7 days · fatal crashes always captured",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier.fillMaxWidth().then(
                    if (compactHeight) Modifier.height(160.dp) else Modifier.weight(1f)
                ),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    logText.ifBlank { "No saved diagnostic events." },
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("MovieTogether diagnostic logs", AppLog.all()))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Copy") }
                OutlinedButton(
                    onClick = AppLog::clear,
                    modifier = Modifier.weight(1f),
                ) { Text("Clear") }
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) { Text("Back") }
            }
        }
    }
}
