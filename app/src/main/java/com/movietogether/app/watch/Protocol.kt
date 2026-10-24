package com.movietogether.app.watch

import java.net.URI
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Base64

/** External infrastructure is deliberately not used. These addresses are NOT universal internet links. */
data class RoomAddress(val host: String, val port: Int, val token: String) {
    init {
        require(host.matches(Regex("[a-zA-Z0-9.:-]{1,253}"))) { "Invalid host" }
        require(port in 1..65535) { "Invalid port" }
        require(token.matches(Regex("[A-Za-z0-9_-]{24,128}"))) { "Invalid room key" }
    }
    val baseUrl: String get() = "http://" + if (host.contains(':')) "[$host]:$port" else "$host:$port"
    val mediaUrl: String get() = "$baseUrl/media"
    val wsUrl: String get() = baseUrl.replaceFirst("http:", "ws:") + "/ws"
    val browserUrl: String get() = "$baseUrl/join/$token"
    val appLink: String get() = "movietogether://join?host=$host&port=$port&key=$token"

    companion object {
        fun parse(text: String): RoomAddress {
            val uri = URI(text.trim())
            return when (uri.scheme?.lowercase()) {
                "movietogether" -> {
                    require(uri.host == "join") { "Invalid invitation" }
                    val pairs = uri.rawQuery.orEmpty().split('&').mapNotNull {
                        val ix = it.indexOf('=')
                        if (ix <= 0) null else it.substring(0, ix) to URLDecoder.decode(it.substring(ix + 1), "UTF-8")
                    }.toMap()
                    RoomAddress(pairs.getValue("host"), pairs["port"]?.toIntOrNull() ?: 8787, pairs.getValue("key"))
                }
                "http" -> {
                    require(uri.userInfo == null) { "Invalid invitation" }
                    val part = uri.path?.split('/') ?: emptyList()
                    require(part.size == 3 && part[1] == "join") { "Not a MovieTogether invitation" }
                    RoomAddress(requireNotNull(uri.host), if (uri.port > 0) uri.port else 80, part[2])
                }
                else -> error("Use a MovieTogether invitation or direct HTTP room link")
            }
        }
        fun newToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}

data class PlaybackSnapshot(
    val positionMs: Long = 0,
    val playing: Boolean = false,
    val revision: Long = 0,
)

data class ChatEntry(
    val sender: String,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

data class HostStatus(
    val running: Boolean = false,
    val title: String = "",
    val address: RoomAddress? = null,
    val viewers: Int = 0,
    val error: String? = null,
)

data class GuestStatus(
    val connected: Boolean = false,
    val error: String? = null,
)
