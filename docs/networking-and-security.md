# MovieTogether network and security deployment

## What is implemented

The embedded Android host server serves the selected MP4 and a WebSocket over **HTTP/WS on TCP 8787**. Video stays on the host phone. Firebase securely publishes/retrieves room metadata only; it does not relay media.

The Android guest supports `https://` video streaming and `wss://` signaling with standard Android certificate validation, and `movietogether://join?host=...&port=443&key=...&secure=1` invitations. The host may advertise a different endpoint from its embedded server. This is useful **only when that endpoint has already been provisioned**.

Public plain HTTP/WS links are rejected by the guest; local/private/VPN IPs and .local names may still use HTTP/WS for trusted network testing. This is a transport policy, not a substitute for an application-level identity or authorization system.

## Same Wi-Fi or private VPN

1. Connect both phones to the same trusted Wi-Fi network, or use a private VPN that gives them routable addresses (e.g. WireGuard/Tailscale configured independently).
2. Host selects an MP4 and starts sharing. Copy the direct local/VPN app invitation.
3. Guest joins and streams from the host's TCP 8787. Check whether the Wi-Fi/router blocks client-to-client traffic.
4. Avoid exposing the unencrypted phone port directly to the public internet.

## Secure public DNS endpoint (external infrastructure required)

This is an **example**, not an automatic setup. Provision a DNS name `watch.example.com` pointing to your HTTPS reverse proxy (Caddy/Nginx or equivalent) and a publicly reachable TCP 443 endpoint. The proxy must be able to reach the Android phone's TCP 8787 through a **private LAN/VPN route**. NAT/firewall and changing host IPs must be addressed separately.

Example Caddyfile on a server with automatic trusted TLS:

```caddyfile
watch.example.com {
    reverse_proxy 192.168.1.50:8787
}
```

Caddy forwards `/media`, `/ws` (WebSocket upgrade), and `/join/<room-token>`. Never forward to an arbitrary phone address supplied by a guest. Ensure the proxy-to-phone route is authenticated/private: **TLS ends at Caddy**, and this example uses unencrypted HTTP to the phone behind it. Use a VPN between them if crossing untrusted networks. Confirm Android trusts the proxy certificate; do not disable certificate validation.

In the host room screen, enter `watch.example.com`, enable **HTTPS / WSS reverse proxy**, and leave the port blank for 443 (or enter the actual TLS port). Choose **Update Firebase invitation to this address** before sharing a Firebase room code. The app's Share action prefers the secure direct invitation when configured.

If clients cannot reach the proxy, playback will not work regardless of Firebase invitation success. There is **no automatic CGNAT bypass, TURN, NAT hole-punching, serverless video relay or universal internet hosting** in this PR.

## Security and privacy

- Each room uses a random capability token. Do not put invite links in public channels or logs. Guest Android headers use `Authorization: Bearer` for both media and WebSocket.
- The local HTTP server still allows unencrypted trusted-LAN traffic. Do **not** port-forward it to the internet.
- WSS/HTTPS connections require a trusted certificate and never intentionally fall back to HTTP.
- Display names are self-declared, so "Paused by Alex" is not cryptographic identity attribution.
- Leaving cancels automatic reconnects; revoked or invalid invitations do not keep retrying. Previously saved invitation links can be deleted with **Forget**.
- Firebase is not a secure relay. Keep anonymous-auth security rules deployed and validate guest transport separately.
- A secure proxy is **not end-to-end media encryption** if it forwards plain HTTP to the phone.

See [acceptance checklist](watch-party-acceptance.md) for tests before merging to stable.
