# MovieTogether

Android watch-party application: **one APK on every device**. The host picks a local movie and serves it directly from their phone. Guests can join through a shared direct-network invitation, stream the movie, follow host-controlled play/pause/seek, and chat in real time.

## Internet limitation — read this first

This edition runs **entirely inside the Android APK** without any Firebase, server hosting, TURN/STUN, registration service, or external gateway.

- **Works:** Devices on the same Wi-Fi (unless client isolation is enabled), or connected through a mutually routable private/VPN network.
- **Sometimes works:** Different networks when the host has a reachable public address and manual port forwarding/firewall configuration.
- **Does not work automatically:** Carrier-grade NAT, changing private IP addresses, or strict firewalls. Making a new invitation link does **not** bypass those restrictions.
- **Security:** HTTP/WS is unencrypted. Use trusted networks and do not expose private watch rooms over the public internet without a secure tunnel.

## How to host

1. Install the same version of the MovieTogether APK on the host phone and guest devices (Android 8.0+).
2. Connect both devices to the same Wi-Fi network.
3. In MovieTogether, choose **Create a room** and select a progressive MP4 file (H.264/AAC is recommended).
4. When the host server starts, tap **Share invitation**. Send the direct URL or custom app link.
5. On a guest phone, open the invitation in MovieTogether or paste the invite link into **Join a room**.
6. The host controls video playback; guests can watch and exchange text messages.
7. Tap **End room** or the **Stop sharing** notification action to stop serving the selected file.

For a network that is already routable (manual VPN/port forwarding), the host may enter an optional public/VPN address in the room screen. The host listens on TCP port **8787**.

## Architecture

- Jetpack Compose and shared `dev.n2k02:theme` / `dev.n2k02:updater`.
- Android foreground `HostService` manages a single locally embedded NanoHTTPD server.
- The embedded server only exposes the SAF-selected MP4 over authenticated GET/HEAD byte-range requests.
- The guest uses a Media3 ExoPlayer authenticated HTTP DataSource.
- Authenticated WebSocket broadcasts playback snapshots and text chat.
- Rooms use a 256-bit random invitation token. When hosting ends, the room closes and its token is revoked.

## Limitations

- No transcoding: guests need compatible codecs; the initial format is MP4/H.264/AAC.
- Maximum five concurrent viewer/stream connections.
- No guarantee the host server stays alive indefinitely after Android enforces foreground-service and power-management rules.
- Host must stay online and reachable; MovieTogether does not include a routing/discovery service.
- Media streaming and signaling are not encrypted under direct HTTP/WS, even though access is token-guarded.
- Only stream media you have permission to share.

## Development / experimental builds

Branch `feature/internet-watch-party` has a checks-only GitHub Actions workflow which runs Android Debug assemble, JVM tests, and lint; it **does not produce a public release**. One final PR to `main` triggers the repository's existing signed experimental APK release workflow after integrated verification.

Full technical plan: [docs/internet-watch-party-plan.md](docs/internet-watch-party-plan.md).

Gradle needs `N2K02_PACKAGES_TOKEN` for private theme and updater GitHub Packages dependencies.
