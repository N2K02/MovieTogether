# MovieTogether

Android sample application based on the MRadio project structure, Material 3 theme and updater.

- Android 8.0+ (API 26)
- Package: `com.movietogether.app`
- Custom purple clapperboard adaptive launcher icon
- Home, Settings and About & updates
- MRadio update state machine and update card, pointed to this repository
- GitHub Actions PR experimental APK build and artifact

## Experimental build
Open a pull request to `main`. Download the `MovieTogether-experimental-*` artifact from Actions or the prerelease APK from Releases.

**Signing:** experimental APKs are debug-signed by CI. Production signing and stable release distribution require a new MovieTogether signing key and dedicated workflow. Never reuse MRadio signing keys. The updater verifies package name and signing identity; debug builds are only mutually update-compatible if their signing key is stable.

This is a working starter UI; synchronized playback and movie-room backend are not implemented yet.
