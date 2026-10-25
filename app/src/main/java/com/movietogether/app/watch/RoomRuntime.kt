package com.movietogether.app.watch

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import javax.net.ssl.SSLHandshakeException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.TimeUnit

/** No cloud room discovery: invite URLs identify an address reachable from the guest. */
object RoomRuntime {
    const val PORT = 8787
    val host = MutableStateFlow(HostStatus())
    val guest = MutableStateFlow(GuestStatus())
    val playback = MutableStateFlow(PlaybackSnapshot())
    val messages = MutableStateFlow<List<ChatEntry>>(emptyList())

    @Volatile private var server: RoomHttpServer? = null
    @Volatile private var guestSocket: WebSocket? = null
    private val networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null
    private var activeRoom: RoomAddress? = null
    private var socketGeneration = 0L
    private var retryAttempt = 0
    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(5, TimeUnit.SECONDS)
        .build()

    fun availableIPv4(): List<String> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .map { it.hostAddress.orEmpty() }
            .distinct()
    }.getOrDefault(emptyList())

    @Synchronized fun startServer(context: Context, uri: Uri, title: String) {
        stopServer()
        val token = RoomAddress.newToken()
        val networkAddress = availableIPv4().firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.") }
            ?: availableIPv4().firstOrNull() ?: "127.0.0.1"
        try {
            val built = RoomHttpServer(context.applicationContext, uri, token, title)
            built.start(10_000, false)
            server = built
            playback.value = PlaybackSnapshot()
            messages.value = emptyList()
            host.value = HostStatus(
                running = true, title = title,
                address = RoomAddress(networkAddress, PORT, token),
            )
        } catch (t: Exception) {
            Log.e("MovieTogether", "Host server failed", t)
            host.value = HostStatus(error = t.message ?: "Server failed")
            throw t
        }
    }

    @Synchronized fun stopServer() {
        server?.stop()
        server = null
        host.value = HostStatus()
        playback.value = PlaybackSnapshot()
        messages.value = emptyList()
    }

    fun onViewerCount(count: Int) {
        host.value = host.value.copy(viewers = count)
    }

    /**
     * Host-only seek controls; all viewers receive the new authoritative position.
     * Seeking retains the last pause/resume attribution.
     */
    @Synchronized fun hostSeekTo(positionMs: Long) {
        if (!host.value.running) return
        PlaybackRules.seek(playback.value, positionMs)?.let(::publishPlayback)
    }

    /** Anyone can request play/pause. Guests send a request; host applies and broadcasts it. */
    @Synchronized fun requestPlayback(playing: Boolean, name: String, hostPositionMs: Long? = null) {
        if (host.value.running) {
            applyPlaybackControl(playing, name, hostPositionMs)
        } else if (guest.value.connected) {
            guestSocket?.send(JSONObject()
                .put("type", "playback_control")
                .put("playing", playing)
                .put("name", PlaybackRules.displayName(name)).toString())
        }
    }

    /** Called only by a token-authenticated room WebSocket. */
    @Synchronized fun receiveGuestPlaybackControl(playing: Boolean, name: String) {
        if (host.value.running) {
            val current = playback.value
            applyPlaybackControl(playing, name,
                PlaybackSyncPolicy.targetPosition(current, SystemClock.elapsedRealtime()))
        }
    }

    private fun applyPlaybackControl(playing: Boolean, name: String, positionMs: Long?) {
        val previous = playback.value
        PlaybackRules.control(previous, playing, name, positionMs ?: previous.positionMs)
            ?.let(::publishPlayback)
    }

    /** Periodic host position updates never change play/pause or its attribution. */
    @Synchronized fun hostPlayback(positionMs: Long) {
        if (!host.value.running) return
        val current = playback.value
        if (!current.playing) return
        val nextPosition = positionMs.coerceAtLeast(0)
        if (kotlin.math.abs(nextPosition - current.positionMs) < 300L) return
        publishPlayback(current.copy(positionMs = nextPosition, revision = current.revision + 1))
    }

    private fun publishPlayback(snapshot: PlaybackSnapshot) {
        playback.value = snapshot.copy(observedAtElapsedMs = SystemClock.elapsedRealtime())
        server?.broadcast(playbackWire())
    }

    fun sendChat(name: String, body: String) {
        val cleaned = body.trim().take(500)
        if (cleaned.isEmpty()) return
        val message = JSONObject().put("type", "chat")
            .put("name", name.trim().take(30).ifBlank { "Guest" })
            .put("text", cleaned)
        if (host.value.running) server?.receiveHostChat(message)
        else guestSocket?.send(message.toString())
    }

    fun recordChat(sender: String, body: String, timestamp: Long = System.currentTimeMillis()) {
        val entry = ChatEntry(sender.take(30), body.take(500), timestamp)
        messages.value = (messages.value + entry).takeLast(100)
        com.movietogether.app.RoomChatBubbleNotifications.onNewMessage(entry)
    }

    /**
     * Manual join replaces any previous connection and cancels its pending retries.
     * Automatic retries retain the same invitation and are cancelled by Leave or Quit.
     */
    @Synchronized fun joinRoom(address: RoomAddress) {
        require(RoomTransportPolicy.mayConnect(address)) { RoomTransportPolicy.TLS_REQUIRED_MESSAGE }
        leaveRoom()
        activeRoom = address
        val generation = ++socketGeneration
        retryAttempt = 0
        guest.value = GuestStatus(error = "Connecting to room…")
        openSocket(address, generation)
    }

    @Synchronized private fun openSocket(address: RoomAddress, generation: Long) {
        if (socketGeneration != generation || activeRoom != address) return
        guest.value = GuestStatus(error = if (retryAttempt == 0) "Connecting to room…"
            else "Reconnecting to room (attempt $retryAttempt)…")
        val request = Request.Builder().url(address.wsUrl)
            .header("Authorization", "Bearer ${address.token}").build()
        guestSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(this@RoomRuntime) {
                    if (!isCurrent(webSocket, generation)) {
                        webSocket.close(1000, "Superseded")
                        return
                    }
                    retryAttempt = 0
                    reconnectJob?.cancel()
                    reconnectJob = null
                    guest.value = GuestStatus(connected = true)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                synchronized(this@RoomRuntime) {
                    if (!isCurrent(webSocket, generation)) return
                    try {
                        val event = JSONObject(text)
                        when (event.optString("type")) {
                            "playback" -> {
                                val revision = event.optLong("revision", 0)
                                if (revision > playback.value.revision) {
                                    playback.value = PlaybackSnapshot(
                                        event.optLong("positionMs", 0L).coerceAtLeast(0L),
                                        event.optBoolean("playing", false),
                                        revision,
                                        event.optString("changedBy").takeIf { it.isNotBlank() },
                                        event.optString("change").takeIf { it.isNotBlank() },
                                        SystemClock.elapsedRealtime(),
                                    )
                                }
                            }
                            "chat" -> recordChat(event.optString("name", "Guest"),
                                event.optString("text"),
                                event.optLong("timeMs", System.currentTimeMillis()))
                        }
                    } catch (_: Exception) { /* Ignore malformed events. */ }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val roomFull = code == 1008 && reason.contains("full", ignoreCase = true)
                connectionLost(webSocket, generation,
                    if (roomFull) "Room is full" else "Connection lost", terminal = roomFull)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val invalid = response?.code == 401 || response?.code == 403
                val tlsError = t is SSLHandshakeException || t.cause is SSLHandshakeException
                val error = when {
                    invalid -> "Invitation expired or invalid"
                    tlsError -> "Secure connection failed: check the HTTPS certificate"
                    else -> "Host unreachable: checking connection and retrying"
                }
                connectionLost(webSocket, generation, error, terminal = invalid || tlsError)
            }
        })
    }

    private fun isCurrent(webSocket: WebSocket, generation: Long): Boolean =
        generation == socketGeneration && guestSocket === webSocket && activeRoom != null

    @Synchronized private fun connectionLost(
        webSocket: WebSocket, generation: Long, reason: String, terminal: Boolean,
    ) {
        if (!isCurrent(webSocket, generation)) return
        guestSocket = null
        guest.value = GuestStatus(error = reason)
        // A room close, explicit leave, revoked link or TLS trust failure must not spin.
        if (terminal) return
        val room = activeRoom ?: return
        val attempt = ++retryAttempt
        reconnectJob?.cancel()
        reconnectJob = networkScope.launch {
            delay(PlaybackSyncPolicy.reconnectDelayMs(attempt))
            synchronized(this@RoomRuntime) {
                if (generation == socketGeneration && activeRoom == room && guestSocket == null) {
                    openSocket(room, generation)
                }
            }
        }
    }

    @Synchronized fun leaveRoom() {
        socketGeneration++
        reconnectJob?.cancel()
        reconnectJob = null
        activeRoom = null
        retryAttempt = 0
        val previous = guestSocket
        guestSocket = null
        previous?.close(1000, "Leaving")
        guest.value = GuestStatus()
        if (!host.value.running) {
            playback.value = PlaybackSnapshot()
            messages.value = emptyList()
        }
    }

    fun playbackWire(): String {
        val snapshot = playback.value
        return JSONObject().put("type", "playback")
            .put("positionMs", snapshot.positionMs)
            .put("playing", snapshot.playing)
            .put("revision", snapshot.revision)
            .put("changedBy", snapshot.changedBy ?: "")
            .put("change", snapshot.change ?: "")
            .toString()
    }
}
