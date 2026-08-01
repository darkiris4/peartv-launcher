# PearTV Launcher

A custom Android TV home launcher targeting tvOS-grade D-pad responsiveness, built for the NVIDIA Shield TV Pro. See [`PRODUCT_SPEC.md`](./PRODUCT_SPEC.md) for the full product/technical spec — this README only covers building, installing, and developing against this scaffold.

**Status:** Phase 3, device-verified. `./gradlew :app:assembleDebug` and `:app:assembleRelease` (R8/minified) both succeed end-to-end, and the app has been installed and run repeatedly on an actual Shield TV Pro — not just built. Core navigation (dock, grid, folders, edit mode), the three-tier hero content model (real Home Screen Channels, TMDB-curated backdrops, static fallback), settings, and a full-screen auto-advancing content carousel are all built and confirmed working on-device. See [`PRODUCT_SPEC.md`](./PRODUCT_SPEC.md)'s Decisions Log and closing status note for the detailed history, and [Known Gaps](#known-gaps) below for what's still open.

## Project layout

```
app/src/main/java/com/peartv/launcher/
  domain/            # TvApp/AppChannel/ChannelProgram models, repository/launcher interfaces, use cases — no Android framework deps beyond android.graphics.Bitmap
  data/              # PackageManager repository impl, AppLauncher impl, package-change receiver, ChannelsRepositoryImpl (raw TvContract Cursor access, §2.4), Drawable→HARDWARE-bitmap decode
  ui/
    motion/          # TvSprings — canonical spring specs (PRODUCT_SPEC.md §1.1), referenced everywhere, defined nowhere else
    focus/           # Modifier.tvOSFocusable() + D-pad direction tracking (§1.2)
    theme/           # tv-material3 theme wrapper
    launcher/        # LauncherScreen, TopShelfRow, AppGrid, AppTile (§3.1); HeroBanner (Tier 1/2) + ContentCarousel (Tier 3, §3.1.2 Template 1, media3-based trailer playback); BackdropBlur.kt (dock/pill translucency, §3.1.1 "Dock/pill backdrop blur")
  MainActivity.kt
  PearTvLauncherApplication.kt   # manual DI root, Coil ImageLoaderFactory, package-change debounce
```

## Building

The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) is checked in and pinned to Gradle 8.9, matching AGP 8.7.3. If you have a JDK 17+ and the Android SDK set up already, this is all you need:

```bash
./gradlew assembleDebug
./gradlew installDebug   # requires a connected device/emulator
```

`local.properties` (gitignored) needs an `sdk.dir=` line pointing at your Android SDK if Android Studio hasn't already generated one for you.

### Environment this was actually verified against

No JDK, Android SDK, or Gradle were preinstalled when this scaffold was built, so the build was verified from scratch via Homebrew rather than left as an untested assumption:

```bash
brew install openjdk@17
brew install --cask android-commandlinetools
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

With that in place, `./gradlew :app:assembleDebug` and `./gradlew :app:assembleRelease` both build clean. Two real bugs surfaced and were fixed during this pass, not just cosmetic ones:

1. **`androidx.tv:tv-foundation:1.0.0` ships none of `TvLazyRow`/`TvLazyVerticalGrid`/`TvGridCells`/`Modifier.focusGroup()`.** That alpha-era TV-specific lazy-layout API surface was dropped before the 1.0.0 stable release, once equivalent D-pad focus-scroll support landed in mainline Compose Foundation. `ui/launcher/AppGrid.kt` and `TopShelfRow.kt` now use plain `androidx.compose.foundation.lazy.grid.LazyVerticalGrid`/`androidx.compose.foundation.lazy.LazyRow`, and the `tv-foundation` dependency was removed entirely (verified empty of anything else used here — see `app/build.gradle.kts`'s comment). `PRODUCT_SPEC.md`'s Decisions Log and §1.3 were corrected to match — the original Phase 1 draft named `focusGroup()`, which doesn't exist; `Modifier.focusRestorer()` covers the "remember last-focused child" behavior instead.
2. **The three placeholder vector drawables had a typo'd XML namespace** (`.../apk/res/vector` instead of `.../apk/res/android`), which silently made every `android:*` attribute on them unresolvable and failed AAPT2 resource linking. Fixed in all three files.

If you're building against a different AGP/Kotlin/Compose BOM combination than what's pinned in `gradle/libs.versions.toml`, treat any resolution differences as real signal, not scaffold error — but the versions currently pinned are confirmed to actually resolve and compile together, not just plausible-looking numbers.

**`androidx.media3` is pinned to `1.9.4`, not the latest 1.10.x** — `media3-common`/`media3-ui` 1.10+ require compiling against API 36, and this project's AGP (8.7.3) caps out at 35 (`compileSdk = 35` in `app/build.gradle.kts`). Don't bump Media3 past the 1.9.x line without bumping AGP/`compileSdk` together, deliberately, not as a side effect of an unrelated dependency update.

## Setting as the default launcher (ADB)

```bash
# Install (if not already installed via installDebug)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Prompt the standard "select home app" chooser
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME

# Or set it directly without the chooser UI (requires the package to already
# declare CATEGORY_HOME, which AndroidManifest.xml does):
adb shell cmd package set-home-activity com.peartv.launcher/.MainActivity
```

To revert to the Shield's stock launcher:

```bash
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
```

(Adjust the package name if the Shield's factory launcher differs from `com.google.android.tvlauncher` on your firmware — check with `adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME` before switching, since overwriting the wrong package name will leave you unable to revert via the same command.)

To uninstall:

```bash
adb uninstall com.peartv.launcher
```

## Baseline Profile generation (setup only — not yet scaffolded)

PRODUCT_SPEC.md §2.3 calls for a Baseline Profile covering cold start, D-pad grid traversal, and app-launch transitions, generated via `androidx.benchmark.macro.junit4.BaselineProfileRule` and packaged with the `androidx.baselineprofile` Gradle plugin. That requires a **separate macrobenchmark module** (typically `:baselineprofile`) that instruments the app on a real device — this scaffold does not include that module yet, since it needs an actual device/emulator to author and run against, which wasn't available here.

To add it later:

1. In Android Studio: **File → New → Module → Baseline Profile Generator**, targeting `:app`. This scaffolds the `:baselineprofile` module and wires the `androidx.baselineprofile` plugin into `app/build.gradle.kts` for you.
2. Write a `BaselineProfileRule` test that drives the three journeys from §2.3 (cold start → top-shelf render, full D-pad sweep across the grid, app-launch transition) — `ui/launcher/LauncherScreen.kt`'s D-pad key handling and `TopShelfRow`/`AppGrid` structure is what that test will be navigating.
3. Run it on-device (§5's open question: Shield TV Pro on-device vs. emulator macrobenchmark — on-device is more representative of real dex2oat behavior and is the recommended default absent a reason to prefer CI).
4. `./gradlew :app:generateBaselineProfile` produces `app/src/main/generated/baselineProfiles/baseline-prof.txt`, which subsequent release builds pick up automatically.

## Known Gaps

- **Carousel auto-advance stops after a few minutes** — user-reported, real, not yet root-caused. The system screensaver (confirmed disabled via `adb shell settings get secure screensaver_enabled`) and screen-off timeout (confirmed 2 hours via `adb shell settings get system screen_off_timeout`) were both checked on-device and ruled out, not assumed. Diagnostic logging is in place (`ContentCarousel`'s `Log.d` calls, tag `ContentCarousel`) so the next occurrence should pin down the actual cause. See `PRODUCT_SPEC.md` §5, open question #6.
- **Content Carousel trailer playback is built but currently inert** — `ContentCarousel` (the Tier 3 full-screen carousel, `PRODUCT_SPEC.md` §3.1.2 Template 1) plays a program's trailer via `media3`/`ExoPlayer` after its poster hold, reading `TvContract.PreviewPrograms.COLUMN_PREVIEW_VIDEO_URI`. Confirmed via diagnostic logging: none of the apps on the reference device (Plex, Hulu, Apple TV) currently publish that column, so this code path has never actually run against real video on real hardware — the §0 frame-budget risk it reopens (video decode contention during D-pad nav) is unverified, not cleared.
- **No Tier 2 fallback treatment** (`PRODUCT_SPEC.md` §3.1.2's Template 4 — blurred banner + centered logo) — tried once (banner blurred behind a `Fit`-scaled copy of itself) and reverted: an arbitrary installed app's banner has no guarantee it reads correctly as a small centered mark over its own blurred self. No fallback currently attempted; Tier 2 apps show their raw banner as the hero backdrop.
- **No Baseline Profile module** — `PRODUCT_SPEC.md` §2.3's frame-pacing strategy depends on one existing; authoring it needs the dedicated `:baselineprofile` module and a real on-device benchmark run, neither of which exist yet.
- **D-pad only, no touch/click fallback** — `Modifier.tvOSFocusable()` handles `DPAD_CENTER`/`Enter` key events, not `Modifier.clickable()`. This matches the `touchscreen required=false` manifest declaration and the Shield's actual input model, but means a mouse click in an emulator won't launch a tile — use arrow keys + Enter, or an actual D-pad-emulating input source.
- **Streaming provider package names in `PRODUCT_SPEC.md` §3.3.1 are still marked unverified** for several rows (Peacock, Paramount+) — confirm against `adb shell pm list packages` before relying on them as launch targets for a provider not already confirmed working.
