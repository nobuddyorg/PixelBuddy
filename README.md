# PixelBuddy

A minimalist Android launcher with a retro pixel-art look. Work in progress, see the
[MVP tracking issue](https://github.com/nobuddyorg/PixelBuddy/issues/1).

## Build

Requirements: JDK 17 or newer and the Android SDK (platform `android-37.2`).

```sh
./gradlew build                                  # assemble, lint, unit tests
./gradlew spotlessCheck                          # formatting (spotlessApply fixes it)
./gradlew :koverHtmlReportUnit :koverVerifyUnit  # merged coverage report and minimum
```

Dependencies are checked against `gradle/verification-metadata.xml`. After adding or updating a
dependency, refresh the checksums and review the diff:

```sh
./gradlew --write-verification-metadata sha256 spotlessCheck build koverVerifyUnit
```

`aapt2` ships one jar per OS. Gradle only records the one for the machine it runs on, so keep the
macOS and Windows entries when the AGP version changes.
