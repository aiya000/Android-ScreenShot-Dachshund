#!/usr/bin/env bash
# Adjusting a seam (#2): after a capture, the first seam's "where the page above ends" is moved
# down by five steps of ten rows, and the image saved afterwards has to be exactly fifty rows
# taller than the one saved before.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_emulator

step "install the debug build and switch the service on"
install_app

capture_the_settings "20"
if [ "${CAPTURED_PAGES:-0}" -ge 2 ]; then
    pass "there is a seam to adjust"
else
    fail "expected at least two pages, got ${CAPTURED_PAGES:-none}"
fi

step "save it as it is"
if first="$(save_and_name "20-save-1")"; then
    pass "saved before adjusting: $first"
else
    first=""
    fail "nothing was saved before adjusting"
fi

step "open the first seam"
ui_scroll_to_id "adjust-0" "20-scroll"
ui_tap_id "adjust-0" "20-adjust"
if ui_wait_id "adjust-done" 10 "20-dialog"; then
    pass "the seam dialog is up"
else
    fail "no seam dialog"
fi
screenshot "20-dialog"

step "keep fifty more rows of the page above the seam"
for i in 1 2 3 4 5; do
    ui_tap_id "upper-down" "20-upper-down-$i"
done
screenshot "20-adjusted"
ui_tap_id "adjust-done" "20-done"
sleep 1
screenshot "20-after-dialog"

step "save it again"
if second="$(save_and_name "20-save-2")"; then
    pass "saved after adjusting: $second"
else
    second=""
    fail "nothing was saved after adjusting"
fi

step "the second image is fifty rows taller"
if [ -n "$first" ] && [ -n "$second" ]; then
    read -r w1 h1 < <(png_size "/sdcard/Pictures/ScreenShot-Dachshund/$first")
    read -r w2 h2 < <(png_size "/sdcard/Pictures/ScreenShot-Dachshund/$second")
    note "before ${w1}x${h1}, after ${w2}x${h2}"
    "${ADB[@]}" pull "/sdcard/Pictures/ScreenShot-Dachshund/$second" "$RUN_DIR/saved-adjusted.png" > /dev/null
    if [ "$h2" -eq $((h1 + 50)) ]; then
        pass "fifty rows taller"
    else
        fail "expected $((h1 + 50)) rows, got $h2"
    fi
fi

refute_log 'AndroidRuntime' "the app did not crash"
logcat_dump "20-end" > /dev/null
finish
