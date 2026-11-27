# Contributing to PixelBuddy

## No outside contributions

PixelBuddy is a personal hobby project. I don't accept pull requests, issues or feature requests from outside the
project; they are closed without review. That keeps the project small enough to finish and maintain in spare time.

The one exception is security: please report vulnerabilities privately as described in [SECURITY.md](SECURITY.md),
not as a public issue.

You are welcome to fork it. The [MIT License](LICENSE) lets you use, change and redistribute the code in your own
fork without asking. There is no support for forks; use your own `applicationId` and signing key (see
[docs/RELEASING.md](docs/RELEASING.md)) so your builds install next to the official ones.

The rest of this guide describes how the project itself is worked on and applies to a fork just as well: every
change is one focused pull request with green CI, following [docs/PLAN.md](docs/PLAN.md).

## Setup

- JDK 17 or newer (CI uses 21).
- Android SDK with platform `android-37.2` (`sdkmanager "platforms;android-37.2"`). Android Studio installs it on
  first sync; otherwise point `ANDROID_HOME` or `local.properties` (`sdk.dir=...`) at your SDK.

## Commands

| What | Command |
|---|---|
| Everything CI runs | `./gradlew spotlessCheck build :koverVerifyUnit :core:pixel-engine:koverVerify :core:search:koverVerify -Proborazzi.test.verify=true` |
| Debug APK | `./gradlew :app:assembleDebug` (installs next to a release build as `…pixelbuddy.debug`) |
| Unit tests | `./gradlew testDebugUnitTest :core:pixel-engine:test :core:search:test` |
| Lint | `./gradlew :app:lintDebug` (covers all modules) |
| Format | `./gradlew spotlessApply` |
| Coverage report | `./gradlew :koverHtmlReportUnit` → `build/reports/kover/htmlUnit/index.html` |
| Screenshot tests | `./gradlew verifyRoborazziDebug`; after an intended UI change `./gradlew recordRoborazziDebug` |
| Pixel engine golden images | `./gradlew :core:pixel-engine:test`; after an intended change add `-Dpixelengine.record=true` and review `core/pixel-engine/src/test/resources/golden` |
| Outline icon prototype (#29) | `cd tools/icon-outline && python3 -m unittest -v`, see its README |
| Emulator smoke tests (CI: API 29, 33, 37) | `./gradlew :app:connectedDebugAndroidTest` with an emulator or phone connected |
| Baseline profile | `./gradlew :app:generateBaselineProfile` (API 33+ emulator or phone; add `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR` on an emulator) |
| Startup benchmark (CI budget: `.github/startup-budget-ratio`, PixelBuddy's cold start relative to the system Settings app's on the same emulator, the highest ratio of the first CI runs (0.42, 0.51) plus 25 %) | `./gradlew :baseline-profile:connectedBenchmarkReleaseAndroidTest`, `scripts/reference-startup.sh build/reference-startup.txt`, then `scripts/check-startup.py baseline-profile/build/outputs/connected_android_test_additional_output build/reference-startup.txt` |
| Frame timing (#55: scrolling all apps, dragging the rail, typing a search, opening an app's menu; CI budget per test in `.github/frame-budget.json`, the 90th percentile of CPU time per frame scaled by the Settings startup) | Runs with the startup benchmark; `scripts/check-frames.py baseline-profile/build/outputs/connected_android_test_additional_output build/reference-startup.txt`. On a phone: `./gradlew :app:generateBaselineProfile :baseline-profile:connectedBenchmarkReleaseAndroidTest` and read `frameOverrunMs` (negative is good: time to spare per frame) |
| Benchmark check scripts | `python3 -m unittest discover -s scripts` |
| Mutation tests (nightly in CI) | `./gradlew :core:pixel-engine:pitest :core:search:pitest` → `core/<module>/build/reports/pitest/index.html` |

Reference screenshots are recorded on Linux, like CI. Rendering on macOS or Windows can differ slightly; if
`verifyRoborazziDebug` fails locally without a UI change, rely on CI.

## Code style

- Kotlin, formatted by ktlint (`ktlint_official`) through Spotless. Run `spotlessApply` before committing.
- Kotlin warnings and Android Lint warnings fail the build. Fix them instead of suppressing; if a suppression is
  really needed, keep it as narrow as possible and add a comment explaining why.
- `:core:pixel-engine` and `:core:search` are pure Kotlin/JVM with explicit API mode: no Android imports there.
- All user-visible text goes into string resources, in **English and German** (`values/` and `values-de/`).
  Lint fails on hard-coded text and missing translations.
- No DI framework, no navigation library, no Room (see the plan).
- Keep the permission list in [PRIVACY.md](PRIVACY.md) in sync with the manifest.

## Tests

- New logic comes with unit tests; Android behaviour with Robolectric tests; UI with Compose UI tests and, for
  visual changes, Roborazzi screenshots.
- Merged line coverage must stay at 80 % or more (`:koverVerifyUnit`); the pixel engine and search at 90 % or
  more (`:core:pixel-engine:koverVerify`, `:core:search:koverVerify`).

## Dependencies

- Versions live in `gradle/libs.versions.toml`. Add new libraries only when needed, and prefer AndroidX and
  platform APIs.
- Gradle verifies every dependency against `gradle/verification-metadata.xml`. After adding or updating one:

  ```sh
  ./gradlew --no-configuration-cache --write-verification-metadata sha256 spotlessCheck build :koverVerifyUnit :core:pixel-engine:pitest :core:search:pitest
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
