# Releasing PixelBuddy

Releases are published on [GitHub Releases](https://github.com/nobuddyorg/PixelBuddy/releases) by
`.github/workflows/release.yml` when a tag `vX.Y.Z` is pushed. Each release has exactly one APK named
`PixelBuddy-vX.Y.Z.apk`, a `SHA256SUMS` file and (for the public repository) a build-provenance attestation, so
Obtainium and manual installs both work.

## How the workflow works

| Job | Runs | Secrets |
|---|---|---|
| **build** | Format check, lint, unit and screenshot tests, coverage, unsigned release APK (R8 + resource shrinking), APK size budget | none |
| **sign** | `apksigner` with APK signature scheme v2 + v3, verification of both schemes, `SHA256SUMS` | keystore, in the protected `release` environment |
| **publish** | Checksum check, build-provenance attestation, GitHub Release with generated notes | none (`GITHUB_TOKEN` only) |

Gradle and all third-party build code run without access to the keystore; the signing job runs no project code.

Version numbers come from the tag: `versionName = X.Y.Z`, `versionCode = X·1,000,000 + Y·1,000 + Z`
(X ≤ 2099, Y and Z < 1000). Tags that don't match `vX.Y.Z` fail the build job.

## One-time setup (maintainers)

### 1. Create the signing key

```sh
keytool -genkeypair -keystore pixelbuddy-release.keystore -storetype PKCS12 \
  -alias pixelbuddy -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=PixelBuddy"
```

> **Back up the keystore and both passwords offline** (e.g. a password manager plus an encrypted copy). Every
> future update must be signed with the same key. If it is lost, installed apps can't be updated and users have to
> uninstall first. Never commit the keystore; `.gitignore` excludes `*.jks` and `*.keystore`.

### 2. Create the protected `release` environment

Repository **Settings → Environments → New environment** `release`:

- **Required reviewers:** add yourself, so every release waits for approval before the key is used.
- **Deployment branches and tags:** "Selected branches and tags", add the tag rule `v*`.
- **Environment secrets:**

  | Secret | Value |
  |---|---|
  | `KEYSTORE_BASE64` | `base64 -w0 pixelbuddy-release.keystore` (macOS: `base64 -i pixelbuddy-release.keystore`) |
  | `KEYSTORE_PASSWORD` | keystore password |
  | `KEY_ALIAS` | `pixelbuddy` |
  | `KEY_PASSWORD` | key password (for PKCS12 the same as the keystore password) |

### 3. Note the certificate fingerprint

Publish the SHA-256 fingerprint of the signing certificate in the README so users can check it:

```sh
keytool -list -v -keystore pixelbuddy-release.keystore -alias pixelbuddy | grep SHA256
```

## Making a release

1. Make sure `main` is green.
2. Tag and push:

   ```sh
   git tag -a v1.2.3 -m "PixelBuddy v1.2.3"
   git push origin v1.2.3
   ```

3. Approve the `sign` job when GitHub asks (environment `release`).
4. Check the release page: one APK, `SHA256SUMS`, generated notes. Stable releases are never marked as pre-release.
5. Install the APK on the test device over the previous release to confirm the update path.

## APK size budget

`.github/apk-size-budget` holds the maximum release APK size in bytes; CI and the release workflow fail above it.
After the first release, set it to the measured size plus about 20 % headroom, and raise it only on purpose
(in its own commit with the reason).
