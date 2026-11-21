# PixelBuddy

[![CI](https://github.com/nobuddyorg/PixelBuddy/actions/workflows/ci.yml/badge.svg)](https://github.com/nobuddyorg/PixelBuddy/actions/workflows/ci.yml)
[![CodeQL](https://github.com/nobuddyorg/PixelBuddy/actions/workflows/codeql.yml/badge.svg)](https://github.com/nobuddyorg/PixelBuddy/actions/workflows/codeql.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

A minimalist Android launcher with a retro-game / pixel-art look: Niagara's calm layout, a pixel clock, a battery
HP bar, a dithered navy→purple→orange sky and pixelized app icons.

- **Private by design:** no tracking, no ads, no accounts. The only network access is optional weather.
- **Fast and small:** no background work, no services.
- **English and German.**

> **Status:** early development. The launcher is being built issue by issue, see the [plan](docs/PLAN.md) and the
> [MVP tracking issue](https://github.com/nobuddyorg/PixelBuddy/issues/1).

## Install

PixelBuddy is distributed as a signed APK on [GitHub Releases](https://github.com/nobuddyorg/PixelBuddy/releases).
It needs Android 10 (API 29) or newer; the primary test device is a Galaxy S24 with One UI 9.

### With Obtainium (recommended, gets updates)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Add app → source URL `https://github.com/nobuddyorg/PixelBuddy`.
3. Install. Obtainium checks GitHub Releases for new versions (`PixelBuddy-vX.Y.Z.apk`).

### Manually

1. Download `PixelBuddy-vX.Y.Z.apk` and `SHA256SUMS` from the latest release.
2. Optional, check the download:
   - `sha256sum --check SHA256SUMS` (macOS: `shasum -a 256 -c SHA256SUMS`)
   - provenance: `gh attestation verify PixelBuddy-vX.Y.Z.apk --repo nobuddyorg/PixelBuddy`
3. Open the APK on the phone and allow your browser or file manager to install unknown apps when asked.

**Samsung:** *Auto Blocker* (Settings → Security and privacy → Auto Blocker) blocks apps from outside the Galaxy
Store. Turn it off for the install, or install over USB with `adb install PixelBuddy-vX.Y.Z.apk` (works with Auto
Blocker on).

After installing, press Home and choose PixelBuddy, or set it in Settings → Apps → Choose default apps → Home app.
Updates must come from the same signing key, so always install over the previous release.

Maintainers: see [docs/RELEASING.md](docs/RELEASING.md).

## Build

Requirements: JDK 17 or newer and the Android SDK with platform `android-37.2`.

```sh
./gradlew :app:assembleDebug                     # debug APK (installs next to a release as …pixelbuddy.debug)
./gradlew spotlessCheck build :koverVerifyUnit   # everything CI checks
```

More commands, code style and how to update dependencies: [CONTRIBUTING.md](CONTRIBUTING.md).

## Project documents

| Document | Contents |
|---|---|
| [docs/PLAN.md](docs/PLAN.md) | Decisions, architecture, order of work |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Setup, commands, code style, pull requests |
| [PRIVACY.md](PRIVACY.md) | What data PixelBuddy uses and sends (almost nothing) |
| [SECURITY.md](SECURITY.md) | Supported versions and how to report a vulnerability privately |
| [docs/RELEASING.md](docs/RELEASING.md) | Release workflow, signing key, APK size budget |
| [LICENSE](LICENSE) | MIT |
