# MovieTogether internet watch-party delivery plan

Tracking issue: https://github.com/N2K02/MovieTogether/issues/3

## Release strategy
- One cohesive implementation branch: feature/internet-watch-party
- No pull request until end-to-end features are ready and tested.
- Preserve existing experimental build/updater workflow; open one PR for the integrated feature.
- Do not report internet access working until a public gateway is configured and tested.

## Roles
- Host: selects one SAF-accessible local video, starts a watch-party session, distributes its invitation, and controls playback.
- Viewer: opens HTTPS invitation in same Android APK, joins the room, streams from the host, chats, and follows authoritative playback state.
- Gateway: public HTTPS/WSS service that maps short-lived room invitations to the host's outbound connection; bridges stream requests and control messages.

## Why a gateway is required
Room links are location-independent identifiers, not a replacement for network reachability.
Dynamic mobile IPs and CGNAT make incoming connection to an Android phone unreliable.
Use an outbound authenticated WSS connection from the host and a public relay reachable by viewers.
No router port forwarding or static IP is necessary for clients.

## Protocol v1
- Use protocolVersion=1 and requestId in all transport messages.
- Role-scoped room tokens, renewal and expiry; room IDs opaque and unguessable.
- Host publishes authoritative play/pause/seek state: positionMs, playing, updatedAt, revision.
- Use server timestamps/RTT estimation, late-join snapshot and bounded drift correction.
- Chat messages: event ID, author ID, sequence, time, body length limit; no unbounded history.
- Video byte range: gateway GET /v1/rooms/{room}/video with Range and bearer token;
  gateway relays request to host via outbound WSS, with streaming binary chunks, bounded queues,
  flow control, cancellation, 206/416 handling, Accept-Ranges and Content-Range.
- Heartbeat, room terminated, host offline, reconnect with exponential backoff.
- Host-only playback controls by default; server validates authorization.

## Proposed modules
app/
  core/protocol/
  core/network/
  core/player/
  feature/room/
  feature/chat/
  feature/host/
  feature/viewer/
  feature/settings/
gateway/
  src/rooms/
  src/security/
  src/signaling/
  src/stream/
  test/
  deployment/

## First integrated release
- MP4/H.264/AAC progressive stream, initially one host and <= 5 viewers.
- Media3 player, SAF picker, Material 3 Expressive room/chat UI.
- Share button with HTTPS App Link and manual join code as fallback.
- Embedded Android host service with user-visible notification and stop controls.
- No DRM extraction, transcoding, automatic movie upload, or browser-only viewer.
- Host must remain online: Android process lifecycle restrictions still apply.

## Security
- TLS at gateway, WSS tunnel to host, HTTPS viewer delivery.
- Invitation tokens scoped to room; short-lived participant tokens, disconnect/revoke support.
- Room host device is sole media source; never allow arbitrary filesystem paths.
- Gateway throttles joins/chat/streams and enforces max participants and concurrent byte ranges.
- Never log bearer tokens, full invitations, private media bytes or chat bodies.
- End sessions and clear ephemeral room data on host disconnect/expiry.

## Test and acceptance gate
- Host and two Android viewers on different carrier/Wi-Fi networks.
- Range requests and seeks return correct bytes; offline host fails closed.
- Cross-device pause/play/seek and late join align with room state; periodic drift correction.
- Chat ordering, presence, guest joins, reconnect, stale revision drop.
- Auth negative cases and revocations covered by automated tests.
- Android build, lint and tests pass; gateway integration tests pass.
- App Links verify against public domain and correct APK signing fingerprints.
- Publicly reachable gateway deployed and tested before final experimental APK.

## Required decision
Choose gateway hosting: a VPS/domain already owned, a cloud/Cloudflare account, or a new inexpensive VPS. Configuration requires a hostname, TLS, and deployment credentials; do not store them in Git. Public video relay bandwidth scales with bitrate times viewer count.
