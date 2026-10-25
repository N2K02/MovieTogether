package com.movietogether.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import dev.n2k02.updater.hasPendingUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.n2k02.updater.N2K02UpdateScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveUpdater.init(this)
        enableEdgeToEdge()
        setContent { MovieTogetherTheme { MovieTogetherApp() } }
    }

    override fun onResume() {
        super.onResume()
        LiveUpdater.onResume(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovieTogetherApp() {
    var page by remember { mutableStateOf(0) }
    var settingsPage by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = page == 1 && settingsPage) { settingsPage = false }

    val updaterState by LiveUpdater.controller.status.collectAsState()
    val updateAvailable = updaterState.hasPendingUpdate()
    val activity = androidx.compose.ui.platform.LocalContext.current as? ComponentActivity

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            when {
                                page == 0 -> Icons.Rounded.Movie
                                settingsPage -> Icons.Rounded.Info
                                else -> Icons.Rounded.Settings
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(when {
                            page == 0 -> "Home"
                            settingsPage -> "About & updates"
                            else -> "Settings"
                        }, maxLines = 1)
                    }
                },
                actions = {
                    dev.n2k02.updater.N2K02AppIcon(Modifier.size(28.dp))
                    Text("MovieTogether", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 6.dp, end = 12.dp))
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = page == 0,
                    onClick = { page = 0; settingsPage = false },
                    icon = { Icon(Icons.Rounded.Movie, null) },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = page == 1,
                    onClick = { page = 1 },
                    icon = {
                        dev.n2k02.updater.N2K02UpdateBadgedIcon(updaterState) {
                            Icon(Icons.Rounded.Settings, "Settings")
                        }
                    },
                    label = { Text("Settings") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        LiveUpdater.controller.cancel()
                        activity?.finishAndRemoveTask()
                    },
                    icon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Quit") },
                    label = { Text("Quit") },
                    alwaysShowLabel = true,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (page == 0) {
                Icon(
                    Icons.Rounded.Groups,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Watch together",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Text(
                    "Your shared movie night starts here. Explore the MovieTogether sample and manage updates from Settings.",
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                ElevatedCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Movie room", style = MaterialTheme.typography.titleLarge)
                        Text("Room creation, invitations and synchronized playback will be added in the next phase.")
                    }
                }
            } else if (!settingsPage) {
                Text(
                    "App information",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 12.dp),
                )
                ListItem(
                    headlineContent = { Text("About & updates") },
                    supportingContent = {
                        Text(if (updateAvailable) "A new update is available" else "App version and update checks")
                    },
                    leadingContent = { Icon(Icons.Rounded.Info, null) },
                    trailingContent = { dev.n2k02.updater.N2K02UpdateBadge(updaterState) },
                    modifier = Modifier.clickable { settingsPage = true },
                )
            } else {
                val experimental by LiveUpdater.experimentalEnabled.collectAsState()
                N2K02UpdateScreen(
                    controller = LiveUpdater.controller,
                    installGuard = LiveUpdater.installGuard,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    experimentalEnabled = experimental,
                    onExperimentalEnabledChange = LiveUpdater::setExperimentalEnabled,
                )
            }
        }
    }
}
