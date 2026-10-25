package com.movietogether.app.watch

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
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
        if (host.value.running) applyPlaybackControl(playing, name, null)
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
        playback.value = snapshot
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
        messages.value = (messages.value + ChatEntry(sender.take(30), body.take(500), timestamp)).takeLast(100)
    }

    fun joinRoom(address: RoomAddress) {
        leaveRoom()
        guest.value = GuestStatus(error = "Connecting to ${address.host}…")
        val request = Request.Builder().url(address.wsUrl)
            .header("Authorization", "Bearer ${address.token}").build()
        guestSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (guestSocket !== webSocket) {
                    webSocket.close(1000, "Superseded")
                    return
                }
                guest.value = GuestStatus(connected = true)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (guestSocket !== webSocket) return
                try {
                    val event = JSONObject(text)
                    when (event.optString("type")) {
                        "playback" -> {
                            val revision = event.optLong("revision", 0)
                            if (revision > playback.value.revision) {
                                playback.value = PlaybackSnapshot(
                                    event.optLong("positionMs", 0),
                                    event.optBoolean("playing", false),
                                    revision,
                                    event.optString("changedBy").takeIf { it.isNotBlank() },
                                    event.optString("change").takeIf { it.isNotBlank() },
                                )
                            }
                        }
                        "chat" -> recordChat(event.optString("name", "Guest"),
                            event.optString("text"), event.optLong("timeMs", System.currentTimeMillis()))
                    }
                } catch (_: Exception) { /* Ignore malformed events. */ }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (guestSocket === webSocket) guest.value = GuestStatus(error = "Host disconnected")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (guestSocket === webSocket) guest.value = GuestStatus(
                    error = if (response?.code == 401) "Invitation expired or invalid"
                    else "Host unreachable: check Wi-Fi, IP address and router/firewall.",
                )
            }
        })
    }

    fun leaveRoom() {
        val old = guestSocket
        guestSocket = null
        old?.close(1000, "Leaving")
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
