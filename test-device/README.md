# Driving the app on a device

The checks that need a screen: a capture started, scrolled, stopped, joined and saved, on an
emulator that nobody is holding. The plain logic -- the capture state machine, the fixed-edge
detection, the overlap search, the PNG writer -- needs no device and lives in `app/src/test/`.

## What it needs

- an **emulator**. Every script refuses a phone unless `DACHSHUND_ALLOW_REAL_DEVICE=1` is set,
  because it switches an accessibility service on and taps and scrolls whatever is on screen
    - starting one headless (any AVD on API 30 or later will do):

      ```sh
      sg kvm -c "$HOME/Android/Sdk/emulator/emulator -avd <name> -no-window -no-audio -no-snapshot -gpu swiftshader_indirect"
      ```

    - with a phone connected as well, set `ANDROID_SERIAL=emulator-5554` so a script cannot pick
      the wrong one
- the **debug APK** built: `./gradlew :app:assembleDebug`
- **python3** and **rg**

## Running

```sh
ANDROID_SERIAL=emulator-5554 test-device/drive/10-capture-the-settings-and-save.sh
```

Each run keeps its screenshots, view-tree dumps and logs under `test-device/runs/<timestamp>/`,
so a failure can be looked at afterwards. The `runs/` folder is not committed.

## How a script works

- `lib.sh` installs the debug build and switches the service on through the secure settings,
  the way the user does in the accessibility settings, keeping whatever other services are on
- things are tapped by the text they show (`ui.py` reads the uiautomator dump), never by
  coordinates, so a layout change does not kill a script
- the service's floating bar is a window of its own; `wait_for_window` finds it through the
  window manager
- what the service did is read from its log (`Dachshund` tag): how many pages, and why it
  finished
