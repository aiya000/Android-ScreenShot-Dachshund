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
ANDROID_SERIAL=emulator-5554 test-device/drive/20-adjust-a-seam.sh
ANDROID_SERIAL=emulator-5554 test-device/drive/30-delete-a-page.sh
ANDROID_SERIAL=emulator-5554 test-device/drive/40-prompt-when-the-service-is-off.sh
```

- `10`: a whole capture of the system settings, from the app's start button to a saved PNG that
  is taller than the screen and shorter than the pages laid end to end
- `20`: the same capture, then the first seam's upper edge moved down by fifty rows through the
  Adjust dialog; the image saved afterwards has to be exactly fifty rows taller
- `30`: the same capture, then the first page's Delete: Cancel leaves the saved height as it was,
  Delete makes the next save shorter
- `40`: with the service off, the app asks to switch it on; Later puts it aside, a fresh start asks
  again, and the button lands in the accessibility list on the service's row

Each run keeps its screenshots, view-tree dumps and logs under `test-device/runs/<timestamp>/`,
so a failure can be looked at afterwards. The `runs/` folder is not committed.

## How a script works

- `lib.sh` installs the debug build and switches the service on through the secure settings,
  the way the user does in the accessibility settings, keeping whatever other services are on
- things are tapped by the text they show or by their Compose test tag (`ui.py` reads the
  uiautomator dump; the tags are exposed as resource ids), never by coordinates, so a layout
  change does not kill a script. The one exception is the floating bar, which uiautomator must
  not be used on (`lib.sh`, `overlay_frame`)
- the service's floating bar is a window of its own; `wait_for_window` finds it through the
  window manager
- what the service did is read from its log (`Dachshund` tag): how many pages, and why it
  finished
