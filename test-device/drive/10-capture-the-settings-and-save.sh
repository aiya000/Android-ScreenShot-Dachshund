#!/usr/bin/env bash
# A whole capture, start to saved file (#1): the system settings are a page longer than the
# screen, so the service has something to scroll; the capture is started from the app's own
# button, stopped from the floating bar, and the edit screen's Save has to leave a PNG taller
# than one screen in the shared pictures.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_emulator

step "install the debug build and switch the service on"
install_app

capture_the_settings "10"
pages="${CAPTURED_PAGES:-0}"
if [ "$pages" -ge 2 ]; then
    pass "more than one page was taken"
else
    fail "expected at least two pages, got $pages"
fi

step "save it"
if latest="$(save_and_name "10-save")"; then
    pass "a file appeared in Pictures/ScreenShot-Dachshund"
else
    latest=""
    fail "nothing was written to Pictures/ScreenShot-Dachshund"
fi
screenshot "10-saved"

step "the saved image is one screen wide and taller than one screen"
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
