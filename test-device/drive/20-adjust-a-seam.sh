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

step "drag the upper half of the seam picture down: the page above follows the finger, so it ends sooner"
ui_scroll_to_id "adjust-0" "20-scroll-2"
ui_tap_id "adjust-0" "20-adjust-2"
ui_wait_id "seam-picture" 10 "20-picture" || fail "no seam picture"
dump="$(ui_dump "20-picture-bounds")"
read -r left top right bottom < <(python3 "$DRIVE_DIR/ui.py" "$dump" --resource-id "seam-picture" --bounds)
x=$(((left + right) / 2))
# the picture is as wide as the page, so one screen pixel is one page row; a slow drag down
# by 200 px in the upper half moves the upper edge up by about 200 rows, less the touch slop
# (some twenty pixels) that a drag gesture swallows before it starts to count
y_from=$((top + (bottom - top) / 4))
y_to=$((y_from + 200))
"${ADB[@]}" shell input swipe "$x" "$y_from" "$x" "$y_to" 600
sleep 1
screenshot "20-dragged"
ui_tap_id "adjust-done" "20-done-2"
sleep 1
if third="$(save_and_name "20-save-3")"; then
    read -r w3 h3 < <(png_size "/sdcard/Pictures/ScreenShot-Dachshund/$third")
    note "after the drag ${w3}x${h3} (was $h2)"
    shrunk=$((h2 - h3))
    if [ "$shrunk" -ge 160 ] && [ "$shrunk" -le 205 ]; then
        pass "about two hundred rows shorter: the content followed the finger"
    else
        fail "expected about 200 rows shorter, got $shrunk"
    fi
else
    fail "nothing was saved after the drag"
fi

refute_log 'AndroidRuntime' "the app did not crash"
logcat_dump "20-end" > /dev/null
finish
