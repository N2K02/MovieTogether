package com.movietogether.app.watch

/** Public room invitations must use TLS. Plain HTTP/WS is restricted to local/VPN address space. */
object RoomTransportPolicy {
    fun mayConnect(address: RoomAddress): Boolean = address.secure || isPrivateOrVpnHost(address.host)

    fun isPrivateOrVpnHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h.endsWith(".local") || h == "::1") return true
        if (h.contains(':') &&
            (h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80:"))) return true
        val octets = h.split('.').map { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) return false
        val a = octets[0] ?: return false
        val b = octets[1] ?: return false
        return a == 10 || a == 127 || (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) || (a == 100 && b in 64..127) ||
            (a == 169 && b == 254)
    }

    const val TLS_REQUIRED_MESSAGE =
        "Public HTTP/WS rooms are blocked. Use an HTTPS/WSS proxy with a trusted certificate, " +
            "or connect devices through the same private VPN."
}
