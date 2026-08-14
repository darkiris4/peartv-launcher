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
git tag v0.5.0
git push origin v0.5.0
```

Pushing a `v*` tag triggers `.github/workflows/release.yml`, which builds
`assembleRelease`, signs it with the secrets above, and publishes a GitHub
Release named after the tag with `PearTV-v0.5.0.apk` attached.

## Installing on a device

On the Shield TV Pro (or any Android TV/Fire TV device):

1. Install **Downloader** from the device's app store.
2. Open it and enter the URL of the `.apk` asset from the
   [Releases page](https://github.com/darkiris4/peartv-launcher/releases).
3. Confirm the install prompt (enable "install from unknown sources" for
   Downloader if asked, once).

A QR code pointing at the same URL (Downloader can generate one, or any QR
tool) makes remote-typing the link on a TV easier.
