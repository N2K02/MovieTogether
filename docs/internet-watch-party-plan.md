# MovieTogether — APK-hosted peer-to-peer internet watch-party plan

Tracking issue: https://github.com/N2K02/MovieTogether/issues/3

## Non-negotiable design
The **host Android APK** owns the source movie, movie byte-range service, watch-party session and UI. No VPS, rented video server, Debian VM, or movie cloud upload. Guests also install MovieTogether; future versions update both roles through the existing N2K02 updater. Work in feature/internet-watch-party and submit one integrated PR when complete.

## Physical network constraint
Generating a new room URL does not yield a public network route to a phone behind cellular carrier-grade NAT, changing IPv4 or a restrictive firewall. A truly global link needs a publicly reachable discovery/signaling component and, where direct ICE fails, a TURN transport relay. This can be a managed service; it does **not** require the developer to administer their own VPS. A completely infrastructure-free solution is best-effort P2P only and cannot guarantee all pairs of users connect.

## Network topology
1. Host picks one local file with Storage Access Framework; foreground host session stays in the APK.
2. APK creates random opaque room ID and authorized guest invite using managed signaling (recommended no-VPS default: Firebase Realtime Database Spark, within quotas).
3. Host and joiners exchange WebRTC SDP and ICE candidates through signaling only; no media bytes in Firebase.
4. Use STUN-assisted WebRTC direct data channels for encrypted, authenticated room control/chat and file byte ranges.
5. Where direct ICE fails, optionally use a managed TURN relay (data usage/possible charges); without one show an actionable connection failure.
6. Android Media3 uses a custom DataSource to request byte ranges through a peer channel; use backpressure, bounded caches and cancellation. Supports seek.
7. Host publishes authoritative playback (positionMs, playing, revision, timestamp), heartbeat, presence and chat.
8. Room closes or expires when hosting ends. Guest link never embeds the host's current IP.

## Code boundaries
- app/feature/host: SAF scoped reader, WebRTC host network manager, host foreground session, owner controls.
- app/feature/viewer: join, room state, Media3 peer-backed DataSource, playback sync, chat.
- app/core/room: immutable room state, monotonically increasing revisions, roles, membership, permissions, reconnection logic.
- app/core/protocol: versioned control messages and binary video byte-range requests/chunks.
- app/core/signaling: interface and optional Firebase implementation; credentials/settings injected through build config, not committed secrets.
- app/core/transport: WebRTC ICE/data channel adapter with optional secure short-lived TURN credentials.
- app/core/security: invitation tokens, peer authorization, expiry and revoke.
- app/feature/ui: M3 Expressive Create, Join, Watch, Chat, Members, Settings, Failure/Retry.

## Suggested protocol
Protocol v1: create, join, offer, answer, ICE candidate, heartbeat, peer-left, playback-state, chat-message, video-request, video-chunk, video-end, video-error, cancel-request. For video include unique request ID, offset and count and bounded chunk size. Use encrypted WebRTC transport and session-level authorization, but treat invite possession as access to the shared content. Avoid leaking invites in logs.

## Performance and compatibility
- First release: progressive MP4/H.264/AAC, selected file only, <=5 viewers; no transcoding and no DRM bypass.
- Estimated host upstream capacity must cover approximate movie bitrate multiplied by concurrent viewers for one-to-one P2P.
- Screen-lock/background operation follows Android foreground service limits; explicitly test on Pixel 8 Pro.
- ICE candidate network handoff and peer disconnection must trigger safe recovery, not indefinite stalls.

## Integration and release gates
- Android Gradle build/lint/unit tests pass; networking component tests pass.
- 2+ physical devices on different Wi-Fi/mobile networks, including restrictive NAT fallback or explicit failure without TURN.
- Video seeking and ranged delivery verify content and size; synchronization, late join and chat pass.
- Invites revoked/expired and unauthorized byte-range queries rejected.
- Shareable HTTPS link and App Links trust anchored to a configured public HTTPS origin, including assetlinks.json; fallback join code when unconfigured.
- Avoid triggering the pull-request workflow until all first-release work is ready; then one final PR and experimental APK.

## Blocking external input
Ask owner to approve minimal managed signaling, e.g. Firebase free Spark plan. Firebase configuration and security rules must be provisioned before cross-internet link creation works. To guarantee connections on restrictive NAT, additionally decide on managed TURN service; it carries media traffic and may have charges. If the owner rejects all managed networking infrastructure, limit claims to local-network or best-effort direct P2P.