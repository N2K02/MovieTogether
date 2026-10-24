package com.movietogether.app.watch

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import fi.iki.elonen.NanoWSD.WebSocketFrame
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore

/** HTTP Range video server + authenticated WebSocket for a single selected SAF movie. */
internal class RoomHttpServer(
    private val context: Context,
    private val uri: Uri,
    private val token: String,
    private val title: String,
) : NanoWSD(RoomRuntime.PORT) {
    private val viewers = CopyOnWriteArrayList<RoomSocket>()
    private val rangeSlots = Semaphore(5)
    private val size: Long = determineSize()
    private val mime = "video/mp4"

    init { require(size > 0) { "The selected video must have a readable size" } }

    private fun determineSize(): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) {
                val size = it.getLong(0)
                if (size > 0) return size
            }
        }
        return context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    }

    private fun authorized(session: IHTTPSession): Boolean =
        session.headers["authorization"] == "Bearer $token"

    override fun serve(session: IHTTPSession): Response {
        // nanohttpd 2.3.1 processes WebSocket upgrades inside NanoWSD.serve.
        if (isWebsocketRequested(session)) {
            if (session.uri != "/ws" || !authorized(session)) {
                return NanoHTTPD.newFixedLengthResponse(Status.UNAUTHORIZED, NanoHTTPD.MIME_PLAINTEXT, "Invalid invitation")
            }
            return super.serve(session)
        }
        if (session.uri == "/health" && session.method == Method.GET) {
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", "{\"ok\":true}")
        }
        if (session.uri.startsWith("/join/") && session.method == Method.GET) {
            if (session.uri.removePrefix("/join/") != token)
                return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Room closed")
            val safeTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            val host = session.headers["host"]?.takeIf { it.matches(Regex("[A-Za-z0-9.:-]{1,260}")) }
                ?: "127.0.0.1:${RoomRuntime.PORT}"
            val hostIp = host.substringBefore(':')
            val appLink = RoomAddress(hostIp, RoomRuntime.PORT, token).appLink
            val html = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><h2>MovieTogether</h2><p>Join: $safeTitle</p><p><a href="$appLink">Open MovieTogether app</a></p><p>Install the same MovieTogether APK on both phones. The host must be reachable over the network.</p></body></html>"""
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html; charset=utf-8", html)
                .apply { addHeader("Cache-Control", "no-store") }
        }
        if (session.uri != "/media") {
            return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not found")
        }
        if (!authorized(session)) {
            return NanoHTTPD.newFixedLengthResponse(Status.UNAUTHORIZED, NanoHTTPD.MIME_PLAINTEXT, "Not authorized")
        }
        if (session.method != Method.GET && session.method != Method.HEAD) {
            return NanoHTTPD.newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, NanoHTTPD.MIME_PLAINTEXT, "GET or HEAD only")
        }
        val rangeHeader = session.headers["range"]
        val range = RangeParser.parse(rangeHeader, size)
            ?: return NanoHTTPD.newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "")
                .apply { addHeader("Content-Range", "bytes */$size") }
        val code = if (rangeHeader.isNullOrBlank()) Status.OK else Status.PARTIAL_CONTENT
        if (session.method == Method.HEAD) {
            return NanoHTTPD.newFixedLengthResponse(code, mime, "")
                .apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Content-Length", range.length.toString())
                    if (code == Status.PARTIAL_CONTENT) addHeader("Content-Range", "bytes ${range.start}-${range.endInclusive}/$size")
                }
        }
        if (!rangeSlots.tryAcquire()) {
            return NanoHTTPD.newFixedLengthResponse(Status.SERVICE_UNAVAILABLE, NanoHTTPD.MIME_PLAINTEXT, "Too many streams")
        }
        return try {
            val stream = openRange(range)
            NanoHTTPD.newFixedLengthResponse(code, mime, stream, range.length).apply {
                addHeader("Accept-Ranges", "bytes")
                addHeader("Cache-Control", "private, no-store")
                if (code == Status.PARTIAL_CONTENT) addHeader("Content-Range", "bytes ${range.start}-${range.endInclusive}/$size")
            }
        } catch (e: Exception) {
            rangeSlots.release()
            NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Selected file cannot be streamed")
        }
    }

    private fun openRange(range: ByteRange): InputStream {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Unable to open file")
        try {
            val source = FileInputStream(descriptor.fileDescriptor)
            try {
                source.channel.position(range.start) // Some SAF providers do not expose seekable FDs.
            } catch (error: Exception) {
                source.close()
                throw error
            }
            return object : FilterInputStream(source) {
                private var left = range.length
                private var closed = false
                override fun read(): Int {
                    if (left <= 0) return -1
                    val byte = super.read()
                    if (byte >= 0) left--
                    return byte
                }
                override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                    if (left <= 0) return -1
                    val n = super.read(buffer, off, minOf(len.toLong(), left).toInt())
                    if (n > 0) left -= n
                    return n
                }
                override fun close() {
                    if (closed) return
                    closed = true
                    try { super.close() } finally {
                        descriptor.close()
                        rangeSlots.release()
                    }
                }
            }
        } catch (e: Exception) {
            descriptor.close()
            throw e
        }
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = RoomSocket(handshake)

    inner class RoomSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        override fun onOpen() {
            if (viewers.size >= 5) {
                close(WebSocketFrame.CloseCode.PolicyViolation, "Room full", false)
                return
            }
            viewers.add(this)
            RoomRuntime.onViewerCount(viewers.size)
            send(RoomRuntime.playbackWire())
        }

        override fun onMessage(message: WebSocketFrame) {
            val text = runCatching { message.textPayload }.getOrNull() ?: return
            if (text.length > 2048) return
            val event = runCatching { JSONObject(text) }.getOrNull() ?: return
            if (event.optString("type") == "chat") receiveGuestChat(event)
        }

        override fun onPong(pong: WebSocketFrame) {}
        override fun onException(exception: IOException) { viewers.remove(this); RoomRuntime.onViewerCount(viewers.size) }
        override fun onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
            viewers.remove(this)
            RoomRuntime.onViewerCount(viewers.size)
        }
    }

    fun receiveHostChat(event: JSONObject) = receiveGuestChat(event)

    private fun receiveGuestChat(event: JSONObject) {
        val name = event.optString("name", "Guest").trim().take(30).ifBlank { "Guest" }
        val text = event.optString("text").trim().take(500)
        if (text.isEmpty()) return
        val timestamp = System.currentTimeMillis()
        RoomRuntime.recordChat(name, text, timestamp)
        broadcast(JSONObject().put("type", "chat").put("name", name)
            .put("text", text).put("timeMs", timestamp).toString())
    }

    fun broadcast(payload: String) {
        viewers.forEach { viewer ->
            runCatching { if (viewer.isOpen) viewer.send(payload) }
                .onFailure { viewers.remove(viewer) }
        }
        RoomRuntime.onViewerCount(viewers.size)
    }
}
