# ScreenShot Dachshund

An Android app that takes a scrolling (full-page) screenshot of apps that do not support one themselves,
such as Firefox.

It is meant as an open-source replacement for LongShot, which has not been maintained for a long time and
sometimes stops responding without producing an image.

## Status

Work in progress. Nothing is captured yet.

## Layout

- Language: Kotlin
- UI: Jetpack Compose
- applicationId: `io.github.aiya000.screenshotdachshund` (`.debug` is appended to the debug build)
- minSdk 26 / targetSdk 35 / compileSdk 35

```
app/src/main/kotlin/io/github/aiya000/screenshotdachshund/
└── MainActivity.kt     -- the entry point
```

## Building

Android Studio is not needed. An Android SDK (platform 35, build-tools) and JDK 17 are enough.

Point `local.properties` at the SDK.

```properties
sdk.dir=/path/to/Android/Sdk
```

With JDK 17 on `PATH`:

```console
$ ./gradlew :app:assembleDebug
```

This produces `app/build/outputs/apk/debug/app-debug.apk`.

When the JDK is managed by mise:

```console
$ mise exec java@17 -- ./gradlew :app:assembleDebug
```

## Installing

```console
$ adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build uses its own application id, so it installs next to the release build. It is the one with the
orange launcher icon, labelled `Dachshund debug`.

The release build is unsigned unless a signing key is configured in `~/.gradle/gradle.properties`
(see `app/build.gradle.kts`).

## License

[MIT License](LICENSE)
