# ScreenShot Dachshund

An Android app that takes a scrolling (full-page) screenshot of apps that do not support one themselves,
such as Firefox.

## Purpose

ScreenShot Dachshund is an open-source replacement for
[LongShot](https://play.google.com/store/apps/details?id=com.leavjenn.longshot).

Among the scrolling-screenshot apps, LongShot has one feature the others do not: it scrolls the app for
you. A single tap captures a whole page, instead of the user scrolling and capturing by hand, page after
page. But LongShot is no longer maintained, and it has a fatal bug: depending on the moment the stop
button is tapped, the capture fails and nothing is saved.

This app exists to keep that feature and to fix those problems, in the open.

## Status

Work in progress, but the whole flow is there: a capture can be taken, joined, shown, adjusted at
each seam, stripped of a page, and saved.

## How it works

1. Switch the app's accessibility service on, once, in the system's accessibility settings
2. Open the app to capture, then tap the "Scrolling screenshot" quick-settings tile (or the
   app's own "Start a capture" button). A small bar floats over the app
3. Tap Start. The service takes a screenshot, scrolls the app, takes another, and goes on until
   you tap Stop or the page scrolls no further
4. The pages are joined into one tall image and shown. At every seam, Adjust lets you move where
   the page above ends and where the page below starts, by dragging or in steps of ten rows.
   Delete page takes a page out, after asking, and joins its neighbours to each other
5. Save writes the result as a PNG into `Pictures/ScreenShot-Dachshund/`

The screenshots come from `AccessibilityService.takeScreenshot()`, so there is no screen-capture
consent dialog, and the scrolling from `dispatchGesture()` of the same service. The service reads
nothing from the screen: it only takes screenshots, scrolls, and listens for scroll events while a
capture runs.

## Layout

- Language: Kotlin
- UI: Jetpack Compose (the floating bar is plain views, since it lives in a service window)
- applicationId: `io.github.aiya000.screenshotdachshund` (`.debug` is appended to the debug build)
- minSdk 30 / targetSdk 35 / compileSdk 35

```
app/src/main/kotlin/io/github/aiya000/screenshotdachshund/
├── MainActivity.kt          -- the front door: service status, settings, a start button
├── capture/CaptureSession   -- the capture as a state machine, with no Android in it
├── image/                   -- rows of pixels: hashes, stored pages, a streaming PNG writer
├── join/                    -- fixed edges, overlap search, the joined image
├── service/                 -- the accessibility service, its floating bar, the tile
├── storage/                 -- the capture's files, and saving to the shared pictures
└── ui/                      -- the edit screen, the seam adjuster, the joining, the tile's invisible activity
```

## Testing

- The logic that needs no device lives in `app/src/test/` and runs on the JVM:
  `./gradlew :app:testDebugUnitTest`
- A capture driven end to end on an emulator lives in `test-device/` (see its README)

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
