#!/usr/bin/env bash
# A whole capture, start to saved file (#1): the system settings are a page longer than the
# screen, so the service has something to scroll; the capture is started from the app's own
# button, stopped from the floating bar, and the edit screen's Save has to leave a PNG taller
# than one screen in the shared pictures.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_emulator

step "install the debug build and switch the service on"
install_app

step "open the app and start a capture from its button"
logcat_reset
"${ADB[@]}" shell am start -W -n "$PACKAGE/io.github.aiya000.screenshotdachshund.MainActivity" > /dev/null
sleep 2
screenshot "10-home"
if ui_wait_text "Start a capture" 10 "10-home"; then
    pass "the home screen is up"
else
    fail "the home screen did not come up"
fi
ui_tap_text "Start a capture" "10-start"
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
screenshot "10-settings-with-bar"

step "tap Start on the bar"
tap_bar start
sleep 7
screenshot "10-capturing"

step "tap Stop on the bar, after a few pages"
# A tap while the service is dispatching its swipe goes to the app underneath together with the
# swipe, and opens whatever row is under the bar. So the stop is pressed right after the
# service says a swipe is done, inside the pause before the next screenshot, and pressed again
# if it was not heard -- unless the page ran out first, which a short settings list may well do
swipes_done() {
    local log
    log="$(app_log)"
    rg -c 'swipe done' <<< "$log" || echo 0
}
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
if wait_for_log 'finished: (Stopped|EndOfContent), [0-9]+ pages' 60 "10-finish"; then
    pass "the service finished the capture"
else
    fail "the service never finished"
fi
pages="$(app_log | rg -o 'finished: \w+, ([0-9]+) pages' -r '$1' | tail -n 1)"
note "pages: ${pages:-?}"
if [ "${pages:-0}" -ge 2 ]; then
    pass "more than one page was taken"
else
    fail "expected at least two pages, got ${pages:-none}"
fi
if ui_wait_text "Save" 60 "10-edit"; then
    pass "the edit screen shows its Save button"
else
    fail "no edit screen with a Save button"
fi
screenshot "10-edit"

step "save it"
saved_files() {
    "${ADB[@]}" shell ls -t /sdcard/Pictures/ScreenShot-Dachshund 2>/dev/null | tr -d '\r' || true
}
before="$(saved_files | wc -l)"
ui_tap_text "Save" "10-save"
sleep 6
screenshot "10-saved"
after="$(saved_files | wc -l)"
if [ "$after" -gt "$before" ]; then
    pass "a file appeared in Pictures/ScreenShot-Dachshund"
else
    fail "nothing was written to Pictures/ScreenShot-Dachshund"
fi

step "the saved image is one screen wide and taller than one screen"
latest="$(saved_files | head -n 1)"
if [ -n "$latest" ]; then
    read -r width height < <(png_size "/sdcard/Pictures/ScreenShot-Dachshund/$latest")
    read -r screen_w screen_h < <("${ADB[@]}" shell wm size | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tr x ' ')
    note "saved $latest: ${width}x${height}, screen ${screen_w}x${screen_h}"
    "${ADB[@]}" pull "/sdcard/Pictures/ScreenShot-Dachshund/$latest" "$RUN_DIR/saved.png" > /dev/null
    if [ "$width" -eq "$screen_w" ]; then
        pass "as wide as the screen"
    else
        fail "width $width is not the screen's $screen_w"
    fi
    if [ "$height" -gt "$screen_h" ]; then
        pass "taller than the screen"
    else
        fail "height $height is not taller than the screen's $screen_h"
    fi
    if [ "$height" -lt $((screen_h * pages)) ]; then
        pass "shorter than the pages laid end to end, so an overlap was cut"
    else
        fail "height $height is the pages end to end: no overlap was found"
    fi
fi

refute_log 'AndroidRuntime' "the app did not crash"
logcat_dump "10-end" > /dev/null
finish
