# Releasing PearTV Launcher

Cutting a release builds a signed APK via GitHub Actions and attaches it to
a GitHub Release, giving users a stable direct-download link to sideload
with the [Downloader](https://amzn.to/downloaderapp) app on Shield/Fire TV.

## One-time setup

1. **Generate a release keystore** (do this once, on your own machine — never commit it):

   ```bash
   keytool -genkeypair -v -keystore peartv-release.jks -alias peartv \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

   Back this file up somewhere durable outside the repo. If it's lost, you
   can't sign updates that install over existing installs — every user
   would have to uninstall first.

2. **Add repo secrets** at `Settings → Secrets and variables → Actions` on
   `github.com/darkiris4/peartv-launcher`:

   | Secret | Value |
   |---|---|
   | `PEARTV_KEYSTORE_BASE64` | `base64 -i peartv-release.jks \| pbcopy` (macOS), then paste |
   | `PEARTV_KEYSTORE_PASSWORD` | the keystore password you set above |
   | `PEARTV_KEY_ALIAS` | `peartv` (or whatever alias you chose) |
   | `PEARTV_KEY_PASSWORD` | the key password you set above |

3. **(Optional) Build signed release APKs locally** by adding the same
   values to `local.properties` (already gitignored):

   ```properties
   release.storeFile=/absolute/path/to/peartv-release.jks
   release.storePassword=...
   release.keyAlias=peartv
   release.keyPassword=...
   ```

   Without these, `./gradlew assembleRelease` still succeeds — it just
   produces an unsigned APK, same as before this setup existed.

## Cutting a release

```bash
git tag v0.5.1
git push origin v0.5.1
```

The tag **must** be `vMAJOR.MINOR.PATCH` (each part 0-99) — the workflow
parses it to derive both the Android `versionName` (`0.5.1`) and
`versionCode` (`major*10000 + minor*100 + patch`, e.g. `501`), and fails
with a clear error if the tag doesn't match. This means you never need to
hand-edit `versionCode`/`versionName` in `app/build.gradle.kts`: cutting a
correctly-formatted tag is the only thing that has to happen, and the
scheme guarantees each release's versionCode is higher than the last as
long as tags themselves only go up — which is what makes sideloaded
updates install in place instead of erroring as a downgrade.

Pushing the tag triggers `.github/workflows/release.yml`, which builds
`assembleRelease`, signs it with the secrets above, and publishes a GitHub
Release named after the tag with `PearTV-v0.5.1.apk` attached.

## Installing on a device

See the [README's "Installing (no Play Store)" section](../README.md#installing-no-play-store)
for end-user sideload instructions (Downloader app, short code, QR code).
Updating is the same flow — Android installs a newer same-signature APK
in place, no uninstall needed.
