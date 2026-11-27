# PixelBuddy plan

PixelBuddy is a minimalist, Niagara-inspired Android launcher with a retro-game / pixel-art look.
This document records the agreed plan. The [MVP tracking issue](https://github.com/nobuddyorg/PixelBuddy/issues/1)
is the source of truth for progress; each sub-issue is one small, independently tested PR with green CI.

## Decisions

| Topic | Decision |
|---|---|
| Name / applicationId | PixelBuddy / `io.github.nobuddyorg.pixelbuddy` |
| License | MIT (bundled fonts ship with their OFL text) |
| Primary device | Galaxy S24, One UI 9.0 (Android 17, API 37) |
| SDK levels | minSdk 29, targetSdk 37, compileSdk 37.2 (Compose 1.13 needs 37.1 or newer) |
| Toolchain | AGP 9.5 with built-in Kotlin, Gradle 9.9, Kotlin 2.4, Compose BOM 2026.11, Java/Kotlin target 17 (exact versions in `gradle/libs.versions.toml`) |
| Languages | English (default) and German |
| Font | Departure Mono (OFL), mixed case; big clock drawn from custom pixel digit glyphs |
| Alphabet rail | Niagara-style (★, A–Z, #) with stepped wave/magnification |
| Default icon preset | 16-bit, pixel-rounded mask, 1 px outline (outline icons from #29 are presets too; the default is revisited after testing them on the S24) |
| Lock screen | Wallpaper only (no clock/lock-screen replacement) |
| Weather | Open-Meteo, no key, no account; city search or approximate location; no background work |
| Updates | GitHub Releases, Obtainium-friendly; no in-app updater |
| Diagnostics | Local crash log only, shared manually; no telemetry |
| Priority | Good looking over the concept screenshot |

## Visual direction

Niagara's layout and whitespace, styled like a retro game: pixel clock with 1 px drop shadow, info line
`Fri 06 Nov  ▮▮▮▯ 58%  ☾ 4°` (battery as HP bar with charging state), navy→purple→orange dithered gradient,
optional faint CRT scanlines (off by default), `▶` press cursor, pixel speech-bubble on the rail. Configurable
accent colour (orange default). Icons: pixel presets (#6) and automatic neon outline icons (#29).

## Architecture

```
:app                  HOME + settings activities, manual wiring (AppContainer)
:feature:home         clock header, favorites/app list, alphabet rail, search, long-press menu
:feature:settings     settings screens, icon style editor, first-start setup
:core:data            LauncherApps discovery/launch, package events, DataStore, icon loading + cache, export/import
:core:weather         Open-Meteo client, location, refresh policy (only module that uses the network)
:core:design-system   colors, font, pixel clock glyphs, dithered gradient, components
:core:pixel-engine    pure Kotlin/JVM: downscale, alpha threshold, outline, palettes, quantization, dithering, outline-icon geometry
:core:search          pure Kotlin/JVM: app search matching and ranking (accents, prefix, word starts, initials)
:baseline-profile     startup macrobenchmark and baseline profile generator for :app (runs on a device or emulator)
```

No DI framework, no navigation library, no Room. One typed DataStore with versioned JSON (kotlinx.serialization).

Shared build configuration lives in `build-logic` as convention plugins:

| Plugin | Used by |
|---|---|
| `pixelbuddy.android.application` | `:app` |
| `pixelbuddy.android.library` | all Android library modules |
| `pixelbuddy.android.compose` | modules with Compose UI (applied next to one of the above) |
| `pixelbuddy.jvm.library` | `:core:pixel-engine`, `:core:search` |

## Permissions

`INTERNET` (weather only), `ACCESS_COARSE_LOCATION` (only if chosen for weather), `REQUEST_DELETE_PACKAGES`,
`SET_WALLPAPER`, `EXPAND_STATUS_BAR`. No services, no background work.

## Quality

- Tests: JUnit + coroutines-test + Turbine, Robolectric, Compose UI tests, Roborazzi screenshot tests, emulator
  smoke tests (API 29/33/37).
- Coverage: Kover (pixel engine ≥ 90 %, others ≥ 80 %), Pitest nightly on the engine and sort/search logic.
- Static checks: Android Lint (warnings as errors), ktlint via Spotless, Compose lint rules, CodeQL.
- Supply chain: Dependabot, Gradle dependency verification, Actions pinned by SHA.
- Budgets: startup time and APK size checked in CI.

## Order

1. #2 Project foundations, together with #19 (localization setup), #22 (crash log) and #23 (open-source basics)
2. #3 Release pipeline
3. #4 App discovery, sorting and launching
4. #5 Design system and home header
5. #6 Pixel engine and icon presets
6. #7 Icon pipeline
7. #29 Automatic outline icons
8. #8 Favorites, settings storage and long-press menu
9. #9 Alphabet rail
10. #10 Search and gestures
11. #11 Weather
12. #12 Settings screens and wallpaper
13. #20 First-start setup
14. #13 Icon style editor
15. #14 Per-app icon overrides
16. #21 Settings export and import
17. #15 Polish: performance, accessibility, emulator tests

## Known constraints

- A launcher cannot replace or draw over the secure lock screen.
- Swipe-down-for-notifications uses a non-public API; verified on the S24 with fallback to search.
- Samsung Auto Blocker blocks sideloading by default; install docs cover turning it off or using ADB.
- Google developer verification for sideloaded apps rolls out globally in 2027 (ADB exempt), see #18.

## Definition of done (MVP)

Installs from a GitHub Release on the S24, can be set as home app, finds and launches all apps,
favorites/rail/search feel smooth at 120 Hz, icons pixelized consistently, outline icons available, icon editor
works, weather shows, UI available in German and English, settings survive restarts and can be exported/imported,
CI green.

## After the MVP

#16 double-tap to lock · #17 app shortcuts · #18 developer verification · #24 notification dots/preview ·
#25 media controls · #26 widget slot · #27 next calendar event · #28 time-of-day sky gradient
