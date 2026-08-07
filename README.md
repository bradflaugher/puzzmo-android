# Puzzmo for Android

An unofficial full-screen Android home for [Puzzmo](https://www.puzzmo.com) — the same idea as the official iOS app: a thin, polished WebView shell around the real product.

Puzzmo only ships an iOS app today. This gives Android the same simple experience.

## Download

Every push to `main` builds a signed APK and **replaces** the single `latest` release (same flow as [free-library-nyt](https://github.com/bradflaugher/free-library-nyt)).

**[Download the latest APK](https://github.com/bradflaugher/puzzmo-android/releases/latest/download/puzzmo.apk)**

1. Open the link on your Android phone  
2. Install (allow “install unknown apps” for your browser if asked)  
3. Launch **Puzzmo** and play  

Requires **Android 16+** (API 36). Older devices are intentionally not supported.

## What you get

- Full-screen WebView of `https://www.puzzmo.com`
- Edge-to-edge layout that respects the notch and gesture bar
- Persistent login cookies
- Back navigates in-site history, then exits
- Offline screen with one-tap retry
- Deep links for `puzzmo.com` / `www.puzzmo.com`
- External links (mailto, store, random sites) open outside the app

## Build locally

Install Android SDK Platform 36, then:

```shell
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Release APK (uses the committed personal keystore under `keystore/`):

```shell
./gradlew assembleRelease
```

## Releases

GitHub Actions on every `main` push:

1. Computes a UTC date version like `2026.08.07.12`
2. Builds a signed release APK
3. Deletes the previous `latest` release/tag
4. Publishes a new `latest` release with `puzzmo.apk`

So the Releases page always has exactly one build.

## License

MIT — see [LICENSE](LICENSE). Not affiliated with Puzzmo / Hearst.
