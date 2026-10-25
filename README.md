# MovieTogether

Android watch-party application: **one APK on every device**. The host picks a local movie and serves it directly from their phone. Guests can join through a shared direct-network invitation, stream the movie, share play/pause controls with named attribution, follow host-only seeking, and exchange messages in a collapsible in-app chat bubble.

## Internet limitation — read this first

This edition keeps **movie streaming inside the host Android APK**. Firebase Realtime Database and anonymous authentication optionally provide room-code discovery; there is still no video relay or external movie storage.

- **Works:** Devices on the same Wi-Fi (unless client isolation is enabled), or connected through a mutually routable private/VPN network.
- **Sometimes works:** Different networks when the host has a reachable public address and a correctly configured trusted HTTPS/WSS reverse proxy or a shared private VPN.
- **Does not work automatically:** Carrier-grade NAT, changing private IP addresses, or strict firewalls. Firebase codes can point to a new host IP when updated, but cannot create a network path through NAT.
- **Security:** Plain HTTP/WS links are limited by the guest app to local/private/VPN addresses. Public links must use a valid HTTPS/WSS certificate and reachable reverse proxy. TLS terminates at the proxy, so also protect its connection to the phone with a VPN or trusted local network. There is still no built-in universal relay or automatic NAT traversal.

## How to host

1. Install the same version of the MovieTogether APK on the host phone and guest devices (Android 8.0+).
2. Connect both devices to the same Wi-Fi network.
3. In MovieTogether, choose **Create a room** and select a progressive MP4 file (H.264/AAC is recommended). If Firebase is configured, a cloud room code is also registered.
4. When the host server starts, tap **Share invitation**. Send the Firebase room code link (if configured) or the direct app link.
5. On a guest phone, open the invitation in MovieTogether or paste the invite link into **Join a room**.
6. Everyone can tap **Pause all** or **Play all**; all devices follow the host-owned state and show who paused/resumed (display names are self-declared). The host can seek ±10 seconds. Use the floating chat bubble for messages and unread counts. Guest playback uses drift correction, and dropped WebSockets retry with capped backoff; invalid links and TLS trust failures do not retry automatically.
7. Guests can tap **Leave room** and later **Rejoin last room** (or **Forget** the saved invitation); **Quit** closes the app. Hosts tap **End room** or the **Stop sharing** notification action to stop serving the selected file.

For a private/VPN network, the host may enter an optional reachable private IP/port in the room screen. For public DNS, use the **HTTPS/WSS reverse proxy** toggle with the actual hostname and public TLS port (443 by default), and update the Firebase invitation. The embedded phone server itself still listens on plain HTTP/WS TCP **8787**. See [Networking and security](docs/networking-and-security.md) for the required setup.

## Architecture

- Jetpack Compose and shared `dev.n2k02:theme` / `dev.n2k02:updater`.
- Android foreground `HostService` manages a single locally embedded NanoHTTPD server.
- The embedded server only exposes the SAF-selected MP4 over authenticated GET/HEAD byte-range requests.
- The guest uses a Media3 ExoPlayer authenticated HTTP/HTTPS DataSource, with local monotonic-clock target prediction and periodic drift correction.
- Authenticated WebSocket (or WSS behind TLS reverse proxy) broadcasts host-authoritative playback snapshots, participant pause/resume requests with self-entered names, and text chat. Unexpected disconnects trigger capped retries that are cancelled on Leave/Quit.
- Rooms use a 256-bit random invitation token. When hosting ends, the room closes and its token is revoked.

## Limitations

- No transcoding: guests need compatible codecs; the initial format is MP4/H.264/AAC.
- Maximum five concurrent viewer/stream connections.
- No guarantee the host server stays alive indefinitely after Android enforces foreground-service and power-management rules.
- Saved rejoin links contain a room token in app-private preferences; **Forget** clears the saved link, and ending hosting revokes its token.
- Participant display names are not verified identities. Treat pause/resume attribution as informational, not security evidence.
- Host must stay online and reachable; MovieTogether does not include a routing/discovery service.
- Local HTTP/WS media streaming and signaling remain unencrypted even though access is token-guarded. HTTPS/WSS relies on an external trusted TLS endpoint; MovieTogether does not provision certificates or host a relay.
- Only stream media you have permission to share.

## Development / experimental builds

Branch `feature/internet-watch-party` has a checks-only GitHub Actions workflow which runs Android Debug assemble, JVM tests, and lint; it **does not produce a public release**. One final PR to `main` triggers the repository's existing signed experimental APK release workflow after integrated verification.

Full technical plan: [docs/internet-watch-party-plan.md](docs/internet-watch-party-plan.md).
Network and security deployment: [docs/networking-and-security.md](docs/networking-and-security.md).
Manual two-device acceptance plan: [docs/watch-party-acceptance.md](docs/watch-party-acceptance.md).

Gradle needs `N2K02_PACKAGES_TOKEN` for private theme and updater GitHub Packages dependencies.

## Firebase room directory

- Firebase project: `movietogether-45988` (Android package `com.movietogether.app`).
- A validated `app/google-services.json` with the Realtime Database URL is part of this private project.
- The host publishes an ephemeral, 32-character random cloud room code; viewers resolve it using Firebase Anonymous Authentication and Realtime Database.
- Firebase does **not** carry video data, enable NAT traversal or guarantee remote playback; guests still access TCP port `8787` on the host's reachable IP/port. Direct HTTP/WS is unencrypted.
- Firebase Console setup is required: enable anonymous sign-in and publish `firebase/database.rules.json` in **Realtime Database → Rules**. Do not import it under Data.
- See [Firebase setup](docs/firebase-setup.md).
