package com.movietogether.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUpdater.init(this, "N2K02/MovieTogether", "MovieTogether")
        setContent { MovieTogetherTheme { MovieTogetherApp() } }
    }
    override fun onResume() { super.onResume(); AppUpdater.onResume(this) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovieTogetherApp() {
    var page by remember { mutableStateOf(0) }
    var settingsPage by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = page == 1 && settingsPage) { settingsPage = false }
    val updaterState by AppUpdater.state.collectAsState()
    val updateAvailable = updaterState.hasPendingUpdate()
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (page == 0) "MovieTogether" else if (settingsPage) "About & updates" else "Settings") },
            navigationIcon = { if (page == 1 && settingsPage) IconButton(onClick = { settingsPage = false }) { Icon(Icons.Default.ArrowBack, "Back to settings") } }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = page == 0, onClick = { page = 0; settingsPage = false }, icon = { Icon(Icons.Default.Movie, null) }, label = { Text("Home") })
                NavigationBarItem(selected = page == 1, onClick = { page = 1 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (page == 0) {
                Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Watch together", style = MaterialTheme.typography.headlineMedium)
                Text("Your shared movie night starts here. Explore the MovieTogether sample and manage updates from Settings.")
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Movie room", style = MaterialTheme.typography.titleLarge)
                        Text("Room creation, invitations and synchronized playback will be added in the next phase.")
                    }
                }
            } else if (!settingsPage) {
                ListItem(
                    headlineContent = { Text("About & updates") },
                    supportingContent = { Text(if (updateAvailable) "A new update is available" else "App version and update checks") },
                    leadingContent = { Icon(Icons.Default.Info, null) },
                    trailingContent = { if (updateAvailable) UpdateAvailableBadge() },
                    modifier = Modifier.clickable { settingsPage = true },
                )
            } else {
                ListItem(
                    headlineContent = { Text("MovieTogether") },
                    supportingContent = { Text("Version ${AppUpdater.installedVersionName}") },
                    leadingContent = { Icon(Icons.Default.Settings, null) },
                )
                val experimental by AppUpdater.experimentalEnabled.collectAsState()
                ListItem(
                    headlineContent = { Text("Experimental updates") },
                    supportingContent = { Text("Offer signed APKs from successful pull request builds. Test builds may have bugs.") },
                    trailingContent = { Switch(checked = experimental, onCheckedChange = AppUpdater::setExperimentalEnabled) },
                    modifier = Modifier.clickable { AppUpdater.setExperimentalEnabled(!experimental) },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Check for updates") },
                    supportingContent = { Text(if (updateAvailable) "Update available — review below" else if (experimental) "Check latest green PR build and official releases" else "Check official GitHub releases") },
                    leadingContent = { Icon(Icons.Default.SystemUpdate, null) },
                    trailingContent = { if (updateAvailable) UpdateAvailableBadge() },
                    modifier = Modifier.clickable { AppUpdater.check(manual = true) },
                )
                UpdateCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}
