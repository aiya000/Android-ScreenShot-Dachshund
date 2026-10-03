#!/usr/bin/env bash
# The bits every driving script needs: a device it is allowed to touch, a way to press things, and
# a way to say what it expected.
#
# The safety rail at the top is the point of the whole directory. These scripts switch an
# accessibility service on, tap wherever they like and scroll whatever is on screen, so a script
# that ran against the phone the maintainer is holding would fight them for it. Every entry point
# asks require_emulator first.

set -euo pipefail

DRIVE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DEVICE_DIR="$(dirname "$DRIVE_DIR")"
PROJECT_DIR="$(dirname "$TEST_DEVICE_DIR")"

PACKAGE="io.github.aiya000.screenshotdachshund.debug"
SERVICE="$PACKAGE/io.github.aiya000.screenshotdachshund.service.DachshundService"
APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

# where screenshots and dumps of a run are kept, so a failure can be looked at afterwards
RUN_DIR="${RUN_DIR:-$TEST_DEVICE_DIR/runs/$(date +%Y-%m-%d-%H%M%S)}"
mkdir -p "$RUN_DIR"

ADB=(adb)
if [ -n "${ANDROID_SERIAL:-}" ]; then
    ADB=(adb -s "$ANDROID_SERIAL")
fi

failures=0

step() { echo; echo "== $*"; }
note() { echo "   $*"; }
pass() { echo "   ok: $*"; }

fail() {
    echo "   FAILED: $*" >&2
    failures=$((failures + 1))
}

finish() {
    echo
    if [ "$failures" -gt 0 ]; then
        echo "$failures check(s) failed; screenshots and dumps are in $RUN_DIR"
        exit 1
    fi
    echo "all checks passed; screenshots and dumps are in $RUN_DIR"
}

# An emulator, or a device the caller has said out loud is safe to drive. Nothing below this line
# is safe to point at a phone somebody is using
require_emulator() {
    local serial
    serial="$("${ADB[@]}" get-serialno)"
    if [ "${DACHSHUND_ALLOW_REAL_DEVICE:-0}" = "1" ]; then
        note "running against $serial because DACHSHUND_ALLOW_REAL_DEVICE=1"
        return 0
    fi

    case "$serial" in
        emulator-*) return 0 ;;
    esac
    if "${ADB[@]}" shell getprop ro.build.characteristics | tr -d '\r' | rg -q emulator; then
        return 0
    fi

    echo "refusing to drive $serial: it is not an emulator." >&2
    echo "These scripts take over the screen. Start an emulator, or set" >&2
    echo "DACHSHUND_ALLOW_REAL_DEVICE=1 if this really is a device nobody is using." >&2
    exit 1
}

# The debug build, freshly installed, with the service switched on the way the user would in
# the accessibility settings. Other enabled services are kept.
#
# The app is stopped BEFORE the service is switched on, never after: a force-stop makes Android
# drop the package's accessibility services from the enabled ones, so a script that stopped the
# app afterwards would see the service off and the "Start a capture" button greyed out
install_app() {
    "${ADB[@]}" install -r -g "$APK" > /dev/null
    app_stop
    sleep 1
    local enabled
    enabled="$("${ADB[@]}" shell settings get secure enabled_accessibility_services | tr -d '\r')"
    case "$enabled" in
        null | "") enabled="$SERVICE" ;;
        *"$SERVICE"*) ;;
        *) enabled="$enabled:$SERVICE" ;;
    esac
    "${ADB[@]}" shell settings put secure enabled_accessibility_services "$enabled"
    "${ADB[@]}" shell settings put secure accessibility_enabled 1
    # the system binds the service a moment later
    sleep 2
}

app_stop() { "${ADB[@]}" shell am force-stop "$PACKAGE"; }

# The app's own log, from this moment on, taken by its pid.
logcat_reset() { "${ADB[@]}" logcat -c; }

app_pid() { "${ADB[@]}" shell pidof "$PACKAGE" | tr -d '\r' | awk '{print $1}'; }

app_log() {
    local pid
    pid="$(app_pid)"
    if [ -n "$pid" ]; then
        "${ADB[@]}" logcat -d --pid="$pid" | tr -d '\r'
    else
        "${ADB[@]}" logcat -d -s Dachshund:* AndroidRuntime:E | tr -d '\r'
    fi
}

logcat_dump() {
    app_log > "$RUN_DIR/$1.log"
    echo "$RUN_DIR/$1.log"
}

# Whether the app's log matches, right now.
#
# The log is taken into a variable first and searched afterwards, never piped straight into
# `rg -q`: `rg -q` closes the pipe the moment it has its match, adb dies of the broken pipe, and
# with pipefail the whole pipeline is then a failure -- a found match that reads as "not found"
log_matches() {
    local log
    log="$(app_log)"
    rg -q -- "$1" <<< "$log"
}

# Waits until the log says something, or gives up. Nothing here is a fixed sleep: a capture of a
# long page takes as long as it takes
wait_for_log() {
    local pattern="$1" seconds="${2:-120}" label="${3:-wait}"
    local waited=0
    while [ "$waited" -lt "$seconds" ]; do
        if log_matches "$pattern"; then
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
    done

    logcat_dump "$label" > /dev/null
    return 1
}

expect_log() {
    local pattern="$1" label="$2"
    if log_matches "$pattern"; then
        pass "$label"
    else
        fail "$label (nothing in the log matched: $pattern)"
    fi
}

refute_log() {
    local pattern="$1" label="$2"
    if log_matches "$pattern"; then
        fail "$label (the log matched what it must not: $pattern)"
    else
        pass "$label"
    fi
}

screenshot() {
    local name="$1"
    "${ADB[@]}" exec-out screencap -p > "$RUN_DIR/$name.png"
}

# the view tree as the device sees it, saved beside the screenshots
ui_dump() {
    local name="${1:-dump}"
    "${ADB[@]}" shell uiautomator dump /sdcard/window_dump.xml > /dev/null
    "${ADB[@]}" pull /sdcard/window_dump.xml "$RUN_DIR/$name.xml" > /dev/null
    echo "$RUN_DIR/$name.xml"
}

# Presses whatever shows this text, located by text rather than by coordinates so that a layout
# change does not kill the script.
#
# The pause between the dump and the tap is for the service: a uiautomator dump unbinds every
# accessibility service while it runs (see overlay_frame), and ours is bound again about a second
# later. A tap that needs the service -- "Start a capture" on the home screen -- would otherwise
# land while it is gone and do nothing
ui_tap_text() {
    local text="$1" name="${2:-tap}"
    local dump point
    dump="$(ui_dump "$name")"
    if ! point="$(python3 "$DRIVE_DIR/ui.py" "$dump" --text "$text")"; then
        fail "nothing on screen says '$text' (view tree in $dump)"
        return 1
    fi
    sleep 2

    # shellcheck disable=SC2086
    "${ADB[@]}" shell input tap $point
}

ui_wait_text() {
    local text="$1" seconds="${2:-60}" name="${3:-wait}"
    local waited=0 dump
    while [ "$waited" -lt "$seconds" ]; do
        dump="$(ui_dump "$name")"
        if python3 "$DRIVE_DIR/ui.py" "$dump" --text "$text" > /dev/null; then
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
    done
    return 1
}

# The floating bar's frame on screen, "<left> <top> <right> <bottom>", read from the window
# manager.
#
# The bar is an accessibility overlay, and uiautomator must NOT be used to locate it or to tap it:
# a uiautomator dump registers a UiAutomation, and the system unbinds every other accessibility
# service for as long as that runs. The service then drops the bar and whatever capture is in
# flight, and is bound again about a second later, with nothing on screen. So the bar is located
# through the window manager and pressed with a plain `input tap`
overlay_frame() {
    "${ADB[@]}" shell dumpsys window windows | tr -d '\r' | python3 -c '
import re, sys
text = sys.stdin.read()
m = re.search(r"Window #\d+ Window\{\S+ u0 ScreenShot Dachshund\}:.*?frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]", text, re.S)
if not m:
    sys.exit(1)
print(*m.groups())
'
}

wait_for_bar() {
    local seconds="${1:-20}"
    local waited=0
    while [ "$waited" -lt "$seconds" ]; do
        if overlay_frame > /dev/null; then
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done
    return 1
}

# Presses a pill of the bar: "start" (the left pill before a capture), "dismiss" (the right one),
# or "stop" (the only pill during a capture)
tap_bar() {
    local which="$1" frame left top right bottom x y
    if ! frame="$(overlay_frame)"; then
        fail "the floating bar is not on screen"
        return 1
    fi
    read -r left top right bottom <<< "$frame"
    y=$(((top + bottom) / 2))
    case "$which" in
        start) x=$((left + (right - left) * 3 / 10)) ;;
        dismiss) x=$((right - (right - left) / 6)) ;;
        *) x=$(((left + right) / 2)) ;;
    esac
    "${ADB[@]}" shell input tap "$x" "$y"
}

# The size of a PNG on the device, read out of its header: "<width> <height>"
png_size() {
    "${ADB[@]}" exec-out "dd if='$1' bs=24 count=1 2>/dev/null" |
        python3 -c 'import struct,sys; d=sys.stdin.buffer.read(); print(*struct.unpack(">II", d[16:24]))'
}

# Presses whatever carries this resource id; Compose test tags are exposed as resource ids
ui_tap_id() {
    local id="$1" name="${2:-tap}"
    local dump point
    dump="$(ui_dump "$name")"
    if ! point="$(python3 "$DRIVE_DIR/ui.py" "$dump" --resource-id "$id")"; then
        fail "nothing on screen has the id '$id' (view tree in $dump)"
        return 1
    fi
    sleep 2

    # shellcheck disable=SC2086
    "${ADB[@]}" shell input tap $point
}

ui_wait_id() {
    local id="$1" seconds="${2:-60}" name="${3:-wait}"
    local waited=0 dump
    while [ "$waited" -lt "$seconds" ]; do
        dump="$(ui_dump "$name")"
        if python3 "$DRIVE_DIR/ui.py" "$dump" --resource-id "$id" > /dev/null; then
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
    done
    return 1
}

# The files saved so far, newest first
saved_files() {
    "${ADB[@]}" shell ls -t /sdcard/Pictures/ScreenShot-Dachshund 2>/dev/null | tr -d '\r' || true
}

# Presses Save on the edit screen and waits for a new file; prints its name
save_and_name() {
    local before after latest
    before="$(saved_files | wc -l)"
    ui_tap_id "save" "${1:-save}"
    local waited=0
    while [ "$waited" -lt 60 ]; do
        after="$(saved_files | wc -l)"
        if [ "$after" -gt "$before" ]; then
            saved_files | head -n 1
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
    done
    return 1
}

swipes_done() {
    local log
    log="$(app_log)"
    rg -c 'swipe done' <<< "$log" || echo 0
}

# A whole capture of the system settings, from the app's own start button to the edit screen.
# Leaves the number of pages in CAPTURED_PAGES. Each step reports through pass/fail
capture_the_settings() {
    local prefix="${1:-cap}"

    step "open the app and start a capture from its button"
    logcat_reset
    "${ADB[@]}" shell am start -W -n "$PACKAGE/io.github.aiya000.screenshotdachshund.MainActivity" > /dev/null
    sleep 2
    screenshot "$prefix-home"
    if ui_wait_text "Start a capture" 10 "$prefix-home"; then
        pass "the home screen is up"
    else
        fail "the home screen did not come up"
    fi
    ui_tap_text "Start a capture" "$prefix-start"
    sleep 1

    step "the app stepped aside and the floating bar is on screen"
    if wait_for_bar 10; then
        pass "the floating bar is a window of its own"
    else
        fail "no floating bar window"
    fi

    step "bring up something long to scroll: the system settings"
    # stopped first, so that the list opens at its top rather than wherever the last run left it
    "${ADB[@]}" shell am force-stop com.android.settings
    "${ADB[@]}" shell am start -W -a android.settings.SETTINGS > /dev/null
    sleep 3
    screenshot "$prefix-settings-with-bar"

    step "tap Start on the bar"
    tap_bar start
    sleep 7
    screenshot "$prefix-capturing"

    step "tap Stop on the bar, after a few pages"
    # A tap while the service is dispatching its swipe goes to the app underneath together with
    # the swipe, and opens whatever row is under the bar. So the stop is pressed right after the
    # service says a swipe is done, inside the pause before the next screenshot, and pressed
    # again if it was not heard -- unless the page ran out first, which a short list may well do
    local attempt tick seen
    for attempt in 1 2 3 4 5 6; do
        if log_matches 'stop requested|finished:'; then
            break
        fi
        seen="$(swipes_done)"
        for tick in $(seq 1 40); do
            if [ "$(swipes_done)" -gt "$seen" ]; then
                break
            fi
            sleep 0.2
        done
        if overlay_frame > /dev/null; then
            tap_bar stop
        fi
        sleep 0.5
    done

    step "the capture ends and the edit screen opens"
    if wait_for_log 'finished: (Stopped|EndOfContent), [0-9]+ pages' 60 "$prefix-finish"; then
        pass "the service finished the capture"
    else
        fail "the service never finished"
    fi
    CAPTURED_PAGES="$(app_log | rg -o 'finished: \w+, ([0-9]+) pages' -r '$1' | tail -n 1)"
    note "pages: ${CAPTURED_PAGES:-?}"
    if ui_wait_id "save" 60 "$prefix-edit"; then
        pass "the edit screen shows its Save button"
    else
        fail "no edit screen with a Save button"
    fi
    screenshot "$prefix-edit"
}

# Drags the screen up until something with this id is in view, a screenful at a time
ui_scroll_to_id() {
    local id="$1" name="${2:-scroll}"
    local attempt dump
    for attempt in 1 2 3 4 5 6 7 8; do
        dump="$(ui_dump "$name-$attempt")"
        if python3 "$DRIVE_DIR/ui.py" "$dump" --resource-id "$id" > /dev/null; then
            return 0
        fi
        "${ADB[@]}" shell input swipe 540 1700 540 700 400
        sleep 1
    done
    fail "nothing with the id '$id' came into view after scrolling"
    return 1
}
