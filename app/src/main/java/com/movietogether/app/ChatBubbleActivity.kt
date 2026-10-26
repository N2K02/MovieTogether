package com.movietogether.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movietogether.app.watch.RoomRuntime

/** Lightweight Compose surface displayed by Android as an expanded conversation bubble. */
@OptIn(ExperimentalMaterial3Api::class)
class ChatBubbleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MovieTogetherTheme {
                val profile = remember {
                    getSharedPreferences("room_profile", MODE_PRIVATE)
                }
                var displayName by rememberSaveable {
                    mutableStateOf(RoomProfile.getOrCreate(this@ChatBubbleActivity))
                }
                val hosting by RoomRuntime.host.collectAsState()
                val viewing by RoomRuntime.guest.collectAsState()
                LaunchedEffect(displayName) {
                    profile.edit().putString("displayName", displayName).apply()
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("MovieTogether chat") },
                            actions = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Close chat bubble")
                                }
                            }
                        )
                    }
                ) { inner ->
                    Column(Modifier.fillMaxSize().padding(inner)) {
                        if (hosting.running || viewing.connected) {
                            ChatPanel(displayName, { displayName = it })
                        } else {
                            Text(
                                "No room connected. Rejoin through MovieTogether to chat.",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        RoomChatBubbleNotifications.setAppForeground(true)
    }

    override fun onStop() {
        RoomChatBubbleNotifications.setAppForeground(false)
        super.onStop()
    }
}
