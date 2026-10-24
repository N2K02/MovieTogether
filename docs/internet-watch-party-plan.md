# MovieTogether — self-contained watch party

Implementation branch: `feature/internet-watch-party`
Tracking: https://github.com/N2K02/MovieTogether/issues/3

## Owner's final networking requirement

**Only Android APK internal hosting.** No VPS, Firebase, STUN/TURN providers, cloud signaling,
public API, registration service, external Linux runtime, or video storage. Both host and
guest install the same APK. One integrated experimental PR only after verification.

## Technically achievable reachability

- Same Wi-Fi (provided peer isolation is off): connect via the host's private IPv4 address.
- Same VPN/subnet or other directly routable network: connect when the host IPv4/port is reachable.
- Across the public Internet: possible **only if** the host has a reachable public endpoint,
  appropriate port forwarding and firewall rules, or a user-managed VPN.
- Carrier NAT/CGNAT and dynamic addresses are **not** bypassed by random room codes,
  fresh links, WebRTC alone, or embedded servers. The share URL is a direct network address;
  it becomes invalid when the host's address changes.
- Since there is no external trust anchor/certificate service, direct LAN connections use
  HTTP and WS. Invitations are bearer capabilities but traffic is **not end-to-end encrypted**:
  use only trusted networks, do not publicly expose this MVP.

## Implementation scope

- [x] Secure random per-session room token and direct invitation parser.
- [x] Android SAF MP4 picker and original-file-only streaming.
- [x] Embedded NanoHTTPD/WebSocket server, bounded concurrent streams, Range/206/416 support.
- [x] HTTP Authorization header enforced on movie and WebSocket event stream.
- [x] Foreground-hosting service with stop action and Android 15+ dataSync timeout handling.
- [x] Kotlin/Compose create, join, host/viewer, chat, settings, updater navigation.
- [x] Media3 host local player, remote authenticated progressive player, periodic playback snapshots.
- [x] JVM tests for room links/tokens/ranges.
- [ ] CI Android build + unit tests + lint (feature-branch validation).
- [ ] Two-phone physical testing over one Wi-Fi network with seek and playback sync.
- [ ] Routed network manual tests (optional; needs routable address/firewall).
- [ ] Verify app updater/signature and single final PR/experimental APK.

## Server API (host phone port 8787)

- `GET /health`: local server health (no secrets).
- `GET /join/{token}`: minimal join page, opens custom scheme in installed app.
- `GET/HEAD /media`: stream selected MP4; Authorization: Bearer {token}; Range header.
- `WS /ws`: Authorization: Bearer {token}; host-only authoritative playback updates,
  chat broadcast, count and late-join state snapshot.

## v1 limits and hardening follow-up

- MP4/H.264/AAC playback only; codecs depend on participant devices.
- Five connected WebSocket viewers and five concurrent range streams.
- Tokens are revoked when hosting stops or a new room is created.
- No remote seek/playback authority (only host controls).
- Chat length capped; ephemeral and limited to last 100 messages on host.
- The app cannot guarantee background streaming indefinitely. Android's dataSync
  foreground services are bounded by a six-hour total time window per 24h while backgrounded
  on Android 15+; active user interaction can reset it. No autostart after reboot.
- Direct HTTP/WS is not safe for internet use without a secured transport. For any real
  internet rollout, add a user-managed encrypted tunnel, TLS or a relay later **only if
  the owner changes this no-services constraint**.

## Release gate

Do not open PR until CI is green and tests pass. A single final PR to main triggers the
existing experimental APK pipeline. Do not describe untested internet streaming as supported.
