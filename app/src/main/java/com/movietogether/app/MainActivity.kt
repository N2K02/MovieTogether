package com.movietogether.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.movietogether.app.watch.*
import dev.n2k02.updater.N2K02UpdateScreen

class MainActivity : ComponentActivity() {
    private val incomingInvitation = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveUpdater.init(this)
        incomingInvitation.value = intent?.dataString
        enableEdgeToEdge()
        setContent { MovieTogetherTheme { MovieTogetherApp(incomingInvitation.value) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingInvitation.value = intent.dataString
    }

    override fun onResume() {
        super.onResume()
        LiveUpdater.onResume(this)
    }
}

private enum class Screen { HOME, HOST, GUEST, SETTINGS, UPDATES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovieTogetherApp(incomingInvitation: String?) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(Screen.HOME) }
    var joinText by rememberSaveable { mutableStateOf("") }
    var advertisedHost by rememberSaveable { mutableStateOf("") }
    var selectedFileString by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedFile = selectedFileString?.let(Uri::parse)
    var currentGuestString by rememberSaveable { mutableStateOf<String?>(null) }
    val currentGuest = remember(currentGuestString) {
        currentGuestString?.let { runCatching { RoomAddress.parse(it) }.getOrNull() }
    }
    var joinError by remember { mutableStateOf<String?>(null) }
    var displayName by rememberSaveable { mutableStateOf("Guest") }

    val host by RoomRuntime.host.collectAsState()
    val guest by RoomRuntime.guest.collectAsState()
    val updaterState by LiveUpdater.controller.status.collectAsState()
    val cloudInvitation by FirebaseRoomDirectory.invitation.collectAsState()
    val cloudStatus by FirebaseRoomDirectory.status.collectAsState()

    fun connectInvitation(text: String) {
        if (host.running) {
            joinError = "End your hosted room before joining another"
            return
        }
        fun connect(address: RoomAddress) {
            RoomRuntime.leaveRoom()
            currentGuestString = address.appLink
            selectedFileString = null
            RoomRuntime.joinRoom(address)
            page = Screen.GUEST
            joinError = null
        }
        val code = FirebaseRoomDirectory.extractCode(text)
        if (code != null) {
            joinError = "Looking up Firebase room…"
            FirebaseRoomDirectory.resolve(context, code) { resolved ->
                resolved.onSuccess(::connect)
                    .onFailure { joinError = it.message ?: "Cloud room unavailable" }
            }
        } else {
            runCatching { RoomAddress.parse(text) }
                .onSuccess(::connect)
                .onFailure { joinError = "Invalid or unsupported room invitation" }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val title = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
            }.getOrNull() ?: "Selected movie"
            RoomRuntime.leaveRoom()
            HostService.start(context, uri, title)
            selectedFileString = uri.toString()
            page = Screen.HOST
        }
    }


    LaunchedEffect(incomingInvitation) {
        if (incomingInvitation != null) {
            joinText = incomingInvitation
            connectInvitation(incomingInvitation)
        }
    }
    BackHandler(enabled = page == Screen.UPDATES) { page = Screen.SETTINGS }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (page == Screen.UPDATES) {
                        dev.n2k02.updater.N2K02UpdateTitle()
                    } else Text(when (page) {
                        Screen.HOME -> "MovieTogether"
                        Screen.HOST -> "Host watch party"
                        Screen.GUEST -> "Watch together"
                        Screen.SETTINGS -> "Settings"
                        Screen.UPDATES -> "About & updates"
                    })
                },
                navigationIcon = {
                    if (page == Screen.UPDATES) {
                        IconButton(onClick = { page = Screen.SETTINGS }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = page in listOf(Screen.HOME, Screen.HOST, Screen.GUEST),
                    onClick = { page = if (host.running) Screen.HOST
                        else if (currentGuest != null) Screen.GUEST else Screen.HOME },
                    icon = { Icon(Icons.Rounded.Movie, null) },
                    label = { Text("Watch") },
                )
                NavigationBarItem(
                    selected = page in listOf(Screen.SETTINGS, Screen.UPDATES),
                    onClick = { page = Screen.SETTINGS },
                    icon = {
                        dev.n2k02.updater.N2K02UpdateBadgedIcon(updaterState) {
                            Icon(Icons.Rounded.Settings, "Settings")
                        }
                    },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (page) {
                Screen.HOME -> {
                    Icon(Icons.Rounded.Groups, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Watch together", style = MaterialTheme.typography.headlineMedium)
                    Text("Host a movie from your phone. Everyone installs MovieTogether. No cloud uploads or accounts.")
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create a room", style = MaterialTheme.typography.titleLarge)
                            Text("Select an MP4 video stored on this device.")
                            Button(onClick = { picker.launch(arrayOf("video/mp4")) }) {
                                Icon(Icons.Rounded.FolderOpen, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Choose movie & host")
                            }
                        }
                    }
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Join a room", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(
                                value = joinText, onValueChange = { joinText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Paste invite link") },
                                singleLine = true,
                            )
                            if (joinError != null) Text(joinError!!, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { connectInvitation(joinText) }) { Text("Join watch party") }
                        }
                    }
                    Text(
                        "Firebase room codes are available when configured. They find a room, but video still needs " +
                            "a directly reachable host. Carrier NAT and router firewalls may block playback.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Screen.HOST -> {
                    if (host.running) {
                        Text(host.title, style = MaterialTheme.typography.titleLarge)
                        Text("Server running · ${host.viewers} connected viewers")
                        val address = host.address
                        if (address != null) {
                            OutlinedTextField(
                                value = advertisedHost, onValueChange = { advertisedHost = it.trim() },
                                label = { Text("Public or VPN host address (optional)") },
                                supportingText = { Text("Use only a reachable public IP/DNS or VPN address with port 8787 forwarded; a new link cannot bypass NAT.") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                            )
                            val sharedAddress = if (advertisedHost.isBlank()) address
                                else runCatching { RoomAddress(advertisedHost, address.port, address.token) }.getOrNull()
                            if (sharedAddress == null) Text("Invalid host address", color = MaterialTheme.colorScheme.error)
                            Text("Direct link: ${sharedAddress?.browserUrl ?: "(invalid host)"}", style = MaterialTheme.typography.bodySmall)
                            Text("Firebase: $cloudStatus", style = MaterialTheme.typography.bodySmall)
                            if (cloudInvitation != null) {
                                Text("Cloud invitation: $cloudInvitation", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(enabled = sharedAddress != null || cloudInvitation != null, onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    val invite = cloudInvitation ?: sharedAddress?.appLink.orEmpty()
                                    putExtra(Intent.EXTRA_TEXT,
                                        "Watch with me on MovieTogether:\n$invite\n\nDirect network address: ${sharedAddress?.browserUrl.orEmpty()}")
                                }
                                context.startActivity(Intent.createChooser(share, "Share room"))
                            }) {
                                Icon(Icons.Rounded.Share, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Share invitation")
                            }
                            if (sharedAddress != null && FirebaseRoomDirectory.isAvailable(context)) {
                                TextButton(onClick = {
                                    FirebaseRoomDirectory.publish(context, sharedAddress, host.title)
                                }) { Text("Refresh Firebase room address") }
                            }
                            if (address.host == "127.0.0.1") {
                                Text("No network IP available. Connect to Wi-Fi first.", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (selectedFile != null) WatchPlayer(selectedFile!!, true, null)
                        ChatPanel(displayName, { displayName = it })
                        OutlinedButton(onClick = {
                            HostService.stop(context)
                            selectedFileString = null
                            page = Screen.HOME
                        }) {
                            Icon(Icons.Rounded.Stop, null)
                            Spacer(Modifier.width(8.dp))
                            Text("End room")
                        }
                    } else {
                        Text(host.error ?: "Starting server…")
                        OutlinedButton(onClick = { page = Screen.HOME }) { Text("Back") }
                    }
                }
                Screen.GUEST -> {
                    if (currentGuest != null) {
                        if (guest.connected) {
                            WatchPlayer(Uri.parse(currentGuest!!.mediaUrl), false, currentGuest)
                            ChatPanel(displayName, { displayName = it })
                        } else {
                            Text(guest.error ?: "Connecting…")
                            Button(onClick = { RoomRuntime.joinRoom(currentGuest!!) }) { Text("Retry connection") }
                        }
                        OutlinedButton(onClick = {
                            RoomRuntime.leaveRoom()
                            currentGuestString = null
                            page = Screen.HOME
                        }) { Text("Leave room") }
                    }
                }
                Screen.SETTINGS -> {
                    ListItem(
                        headlineContent = { Text("About & updates") },
                        supportingContent = { Text(if (updaterState.hasPendingUpdate())
                            "Update available" else "Version and update checks") },
                        leadingContent = { Icon(Icons.Rounded.Info, null) },
                        trailingContent = { dev.n2k02.updater.N2K02UpdateBadge(updaterState) },
                        modifier = Modifier.clickable { page = Screen.UPDATES },
                    )
                    ListItem(
                        headlineContent = { Text("Hosting and connectivity") },
                        supportingContent = { Text("Port ${RoomRuntime.PORT} · IPv4: ${RoomRuntime.availableIPv4().joinToString().ifBlank { "unavailable" }}") },
                        leadingContent = { Icon(Icons.Rounded.Wifi, null) },
                    )
                    Text("Firebase project: movietogether-45988")
                    Text("Firebase status: $cloudStatus")
                    Text("Firebase makes invitations easier; it does not relay videos. Direct HTTP/WS has no TLS—only share movies on trusted networks.")
                }
                Screen.UPDATES -> {
                    val experimental by LiveUpdater.experimentalEnabled.collectAsState()
                    N2K02UpdateScreen(
                        controller = LiveUpdater.controller,
                        installGuard = LiveUpdater.installGuard,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        experimentalEnabled = experimental,
                        onExperimentalEnabledChange = LiveUpdater::setExperimentalEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatPanel(displayName: String, setDisplayName: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val messages by RoomRuntime.messages.collectAsState()
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Room chat", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = displayName, onValueChange = { setDisplayName(it.take(30)) },
                label = { Text("Your name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (messages.isEmpty()) Text("No messages yet")
            messages.takeLast(25).forEach { message ->
                Text("${message.sender}: ${message.text}", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = draft, onValueChange = { draft = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") }, maxLines = 3,
            )
            Button(onClick = {
                RoomRuntime.sendChat(displayName, draft)
                draft = ""
            }, enabled = draft.isNotBlank()) { Text("Send message") }
        }
    }
}
