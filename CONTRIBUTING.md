# Contributing to PixelBuddy

Thanks for helping. PixelBuddy is built in small steps: every change is one focused pull request with green CI.
The plan and the order of work are in [docs/PLAN.md](docs/PLAN.md) and the
[MVP tracking issue](https://github.com/nobuddyorg/PixelBuddy/issues/1).

## Before you start

- For anything larger than a small fix, open or comment on an issue first so we agree on the approach.
- Security problems: do not open an issue, follow [SECURITY.md](SECURITY.md).

## Setup

- JDK 17 or newer (CI uses 21).
- Android SDK with platform `android-37.2` (`sdkmanager "platforms;android-37.2"`). Android Studio installs it on
  first sync; otherwise point `ANDROID_HOME` or `local.properties` (`sdk.dir=...`) at your SDK.

## Commands

| What | Command |
|---|---|
| Everything CI runs | `./gradlew spotlessCheck build :koverVerifyUnit :core:pixel-engine:koverVerify -Proborazzi.test.verify=true` |
| Debug APK | `./gradlew :app:assembleDebug` (installs next to a release build as `…pixelbuddy.debug`) |
| Unit tests | `./gradlew testDebugUnitTest :core:pixel-engine:test` |
| Lint | `./gradlew :app:lintDebug` (covers all modules) |
| Format | `./gradlew spotlessApply` |
| Coverage report | `./gradlew :koverHtmlReportUnit` → `build/reports/kover/htmlUnit/index.html` |
| Screenshot tests | `./gradlew verifyRoborazziDebug`; after an intended UI change `./gradlew recordRoborazziDebug` |
| Pixel engine golden images | `./gradlew :core:pixel-engine:test`; after an intended change add `-Dpixelengine.record=true` and review `core/pixel-engine/src/test/resources/golden` |
| Outline icon prototype (#29) | `cd tools/icon-outline && python3 -m unittest -v`, see its README |
| Mutation tests (nightly in CI) | `./gradlew :core:pixel-engine:pitest` → `core/pixel-engine/build/reports/pitest/index.html` |

Reference screenshots are recorded on Linux, like CI. Rendering on macOS or Windows can differ slightly; if
`verifyRoborazziDebug` fails locally without a UI change, rely on CI.

## Code style

- Kotlin, formatted by ktlint (`ktlint_official`) through Spotless. Run `spotlessApply` before committing.
- Kotlin warnings and Android Lint warnings fail the build. Fix them instead of suppressing; if a suppression is
  really needed, keep it as narrow as possible and add a comment explaining why.
- `:core:pixel-engine` is pure Kotlin/JVM with explicit API mode: no Android imports there.
- All user-visible text goes into string resources, in **English and German** (`values/` and `values-de/`).
  Lint fails on hard-coded text and missing translations.
- No DI framework, no navigation library, no Room (see the plan).
- Keep the permission list in [PRIVACY.md](PRIVACY.md) in sync with the manifest.

## Tests

- New logic comes with unit tests; Android behaviour with Robolectric tests; UI with Compose UI tests and, for
  visual changes, Roborazzi screenshots.
- Merged line coverage must stay at 80 % or more (`:koverVerifyUnit`); the pixel engine at 90 % or more
  (`:core:pixel-engine:koverVerify`).

## Dependencies

- Versions live in `gradle/libs.versions.toml`. Add new libraries only when needed, and prefer AndroidX and
  platform APIs.
- Gradle verifies every dependency against `gradle/verification-metadata.xml`. After adding or updating one:

  ```sh
  ./gradlew --no-configuration-cache --write-verification-metadata sha256 spotlessCheck build :koverVerifyUnit :core:pixel-engine:pitest
  ```

  Review the diff: only the expected artifacts should change. When AGP changes, keep the macOS and Windows `aapt2`
  entries (Gradle only records the one for your OS).
- Dependabot opens weekly update PRs. For Gradle updates, a workflow regenerates the checksums and pushes them to
  the Dependabot branch; review that commit like any other change.

## Pull requests

- Branch from `main`, keep the PR focused on one issue, and link it (`Closes #123`).
- Fill in the PR template. Describe what you tested, on which device or emulator if relevant.
- CI must be green: formatting, lint, unit and screenshot tests, coverage and CodeQL.
- Commits: short imperative subject line (`Add alphabet rail drag handling`), details in the body when useful.

By contributing you agree that your contributions are licensed under the [MIT License](LICENSE).
