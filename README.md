# PixelBuddy

[![CI](https://github.com/nobuddyorg/PixelBuddy/actions/workflows/ci.yml/badge.svg)](https://github.com/nobuddyorg/PixelBuddy/actions/workflows/ci.yml)
[![CodeQL](https://github.com/nobuddyorg/PixelBuddy/actions/workflows/codeql.yml/badge.svg)](https://github.com/nobuddyorg/PixelBuddy/actions/workflows/codeql.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

A minimalist Android launcher with a retro-game / pixel-art look: Niagara's calm layout, a pixel clock, a battery
HP bar, a dithered navy→purple→orange sky and pixelized app icons.

- **Private by design:** no tracking, no ads, no accounts. The only network access is optional weather.
- **Fast and small:** no background work, no services.
- **English and German.**

> **Status:** early development. The project foundations are in place; the launcher itself is being built issue by
> issue, see the [plan](docs/PLAN.md) and the [MVP tracking issue](https://github.com/nobuddyorg/PixelBuddy/issues/1).

## Install

There is no release yet. Signed APKs will be published on
[GitHub Releases](https://github.com/nobuddyorg/PixelBuddy/releases) (installable and updatable with Obtainium);
the release pipeline is tracked in [#3](https://github.com/nobuddyorg/PixelBuddy/issues/3).

To try a development build, see [Build](#build), then install `app/build/outputs/apk/debug/app-debug.apk` with
`adb install` and pick PixelBuddy as the home app when Android asks (or in Settings → Apps → Default apps).

Requirements: Android 10 (API 29) or newer. The primary test device is a Galaxy S24 with One UI 9.

## Build

Requirements: JDK 17 or newer and the Android SDK with platform `android-37.2`.

```sh
./gradlew :app:assembleDebug                     # debug APK
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
| [LICENSE](LICENSE) | MIT |
