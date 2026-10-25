# MovieTogether experimental acceptance checklist

PR #4 must remain experimental until the scenarios below are confirmed on real Android devices.

- [ ] Two phones on same Wi-Fi: host picks an MP4, guest enters invite, playback starts.
- [ ] Guest taps Pause all; both pause and display **Paused by <guest display name>**.
- [ ] Host taps Play all; both resume and display **Resumed by <host display name>**.
- [ ] Host +/-10s seeking updates the host player and guest playback position.
- [ ] Hold playback for 5+ minutes and verify drift stays acceptably low; test slow Wi-Fi/buffering.
- [ ] Guest opens the floating chat bubble, sends a message, closes it, then sees unread count for a new incoming message.
- [ ] Guest leaves, taps **Rejoin last room**, and receives current playback position. **Forget** clears its stored invitation.
- [ ] Briefly disconnect guest Wi-Fi; reconnect within retry window and verify automatic guest reconnection + playback recovery.
- [ ] Leave during reconnection; verify there is no ghost reconnection.
- [ ] Invalid/revoked invite and mismatched TLS certificate: show clear errors and do not retry forever.
- [ ] Two devices on different networks via independently configured private VPN: check reachability on TCP 8787.
- [ ] Two devices via real HTTPS/WSS reverse proxy: verify certificate validation and both streams. Ensure no insecure public fallback.
- [ ] Public HTTP/WS direct invitation is rejected by guest UI.
- [ ] Quit while hosting: foreground service, socket clients, Firebase invitation and playback all stop.
- [ ] Host device background/power-management behavior, process death and network loss.
- [ ] Latest CI compile/unit tests/lint green and experimental APK installs successfully.

**Not supported automatically:** arbitrary internet hosting behind CGNAT without an externally deployed reachable private tunnel, reverse proxy or relay.
