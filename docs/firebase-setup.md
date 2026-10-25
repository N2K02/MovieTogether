# Firebase setup — MovieTogether

## Your project

- Firebase project ID: `movietogether-45988`
- Project number: `1073360589291`
- Android application ID / package: `com.movietogether.app`
- No movie files are uploaded to Firebase. It is used only as an anonymous-authenticated directory for opaque invitation codes.

## Firebase Console actions (one time)

1. Open https://console.firebase.google.com/project/movietogether-45988/overview
2. Project settings → General → Your apps → Add Android app, if not registered already.
   Use **exactly** `com.movietogether.app`. Download the app-specific `google-services.json`.
3. Build → Authentication → Sign-in method → Enable **Anonymous** authentication.
4. Build → Realtime Database → Create database (choose a region and record the exact database URL).
5. Open Realtime Database → Rules and paste the contents of `firebase/database.rules.json`, then **Publish**.
   Do **not** use public test-mode rules for production. Anonymous authentication grants an anonymous UID,
   not a privileged admin identity. Random 32-character room codes provide unguessable invitation capabilities.
6. The Firebase Android configuration file must identify this project and this Android package.
   The supplied app-specific `app/google-services.json` has now been validated and committed to this private repository's Firebase feature branch. You do not need to upload it again. This file holds public client identifiers, not an Admin SDK credential.

## CI config

Firebase Android client configuration is tracked at `app/google-services.json`, so the normal Gradle CI build includes it automatically. No GitHub secret is needed for this client config. For an alternate project, the optional existing `MOVIETOGETHER_GOOGLE_SERVICES_JSON_BASE64` Actions secret can replace the file before Gradle runs; CI checks project and Android package identity.

The `google-services.json` client config is not a service-account credential and is not a
substitute for security rules; keeping it out of source control is a repository hygiene choice.
Never share service account JSON or admin private keys.

Existing `N2K02_PACKAGES_TOKEN` and APK signing secrets remain unchanged.

## Behavior

- Host selects a local movie, starts the embedded server, then the app anonymously signs into Firebase.
- Firebase stores an opaque 32-character room code → current host address, port, bearer room token,
  movie title and expiration (four hours). A new room has a new code.
- Invite example: `movietogether://join?room=<32-character-code>`
- The viewer opens the link in MovieTogether; Firebase resolves it into an address for the existing
  streaming HTTP/WebSocket client.
- The room is removed on explicit host shutdown or on Firebase disconnection when possible;
  expired entries are denied by the database rules.

## Network limitation

Firebase only provides **discovery**. The guest still needs to reach the host's TCP port 8787
to watch the movie. No connection is created through CGNAT by Firebase. Same Wi-Fi generally works.
Different networks need a routable host endpoint, a user-managed VPN/port forward, or a future
peer transport and TURN relay. Direct streaming remains unencrypted HTTP/WS and must not be
exposed to untrusted internet connections.

## Testing

- With the Firebase file configured, verify Android build and app startup.
- Create a room on host and confirm a cloud invitation appears.
- Copy that code into a second MovieTogether install signed in anonymously; confirm it resolves.
- Confirm the guest streams when on the same Wi-Fi and cannot stream when the host is blocked by NAT.
- Verify database rules reject writes to a room from a different anonymous UID.
- Verify expired/closed rooms fail to resolve, and no arbitrary database listing is permitted.
- Only after end-to-end testing should the single existing experimental PR include this change.

## Project details needed if setup is not yet complete

- Android `google-services.json`: supplied and integrated, including the database URL `https://movietogether-45988-default-rtdb.firebaseio.com`.
- Still required: confirm Anonymous Auth is enabled and Realtime Database rules have been published via the **Rules** tab (not Data → Import JSON).
- For actual end-to-end testing, the host and viewer must use a mutually reachable network; Firebase discovery does not provide video transport through CGNAT.
