# PearTV Launcher

A custom Android TV home launcher built for tvOS-grade D-pad responsiveness — smooth spring-based motion, real Home Screen Channels content, and a full-screen auto-advancing content carousel, all running on an NVIDIA Shield TV Pro.

## Features

- **Fluid, tvOS-style navigation** — spring-physics focus animations, spatial parallax tilt, and D-pad-first input across the dock, app grid, and folders.
- **Live hero content** — a three-tier hero backdrop: real per-app Home Screen Channels data, TMDB-curated artwork, and a static fallback, so the top of the screen is never just an empty banner.
- **Full-screen content carousel** — an auto-advancing, trailer-capable carousel (tvOS Top Shelf-style) built on real `TvContract` channel data, with D-pad Left/Right/Center support.
- **App management** — organize apps into folders, edit-mode reordering, and package-change-aware refresh.
- **Deep-link launching** — direct intents into supported streaming providers instead of generic app launch.
- **Settings** — theme toggle, TMDB API key, and a first-launch prompt for enabling Channels content.

## Status

Phase 3, device-verified: both debug and R8-minified release builds succeed, and the app has been installed and run repeatedly on real Shield TV Pro hardware — not just built.

See [`PRODUCT_SPEC.md`](./PRODUCT_SPEC.md) for the full product/technical spec, the Decisions Log (every substantive change with on-device verification evidence), and the current list of open questions and known gaps (§5).

## Building

Requires JDK 17+ and the Android SDK. The Gradle wrapper is checked in and pinned to Gradle 8.9 / AGP 8.7.3.

```bash
./gradlew assembleDebug
./gradlew installDebug   # requires a connected device/emulator
```

`local.properties` (gitignored) needs an `sdk.dir=` line pointing at your Android SDK if Android Studio hasn't already generated one.

## Setting as the default launcher

```bash
adb shell cmd package set-home-activity com.peartv.launcher/.MainActivity
```

## Support the project

If you enjoy using PearTV Launcher, consider buying me a coffee.

[<img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" height="41" width="174">](https://www.buymeacoffee.com/darkiris4)

## License

MIT — see [LICENSE](./LICENSE).
