package com.movietogether.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (page == 0) "MovieTogether" else "Settings") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = page == 0, onClick = { page = 0 }, icon = { Icon(Icons.Default.Movie, null) }, label = { Text("Home") })
                NavigationBarItem(selected = page == 1, onClick = { page = 1 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (page == 0) {
                Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Watch together", style = MaterialTheme.typography.headlineMedium)
                Text("Your shared movie night starts here. This starter app includes the MRadio-style navigation, theme, and update experience.")
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Movie room", style = MaterialTheme.typography.titleLarge)
                        Text("Room creation, invitations and synchronized playback will be added in the next phase.")
                    }
                }
            } else {
                Text("About & updates", style = MaterialTheme.typography.headlineSmall)
                Text("MovieTogether · v${AppUpdater.installedVersionName}")
                val experimental by AppUpdater.experimentalEnabled.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Experimental updates", modifier = Modifier.weight(1f))
                    Switch(checked = experimental, onCheckedChange = { AppUpdater.setExperimentalEnabled(it) })
                }
                Button(onClick = { AppUpdater.check(manual = true) }, modifier = Modifier.fillMaxWidth()) { Text("Check again") }
                UpdateCard()
            }
        }
    }
}
