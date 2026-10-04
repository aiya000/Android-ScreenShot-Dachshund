#!/usr/bin/env bash
# Trimming the ends of the image (#26): after a capture, "Start here" moves where the image
# starts fifty rows down the first page, and "End here" moves where it ends fifty rows up the
# last page; each save afterwards has to be exactly fifty rows shorter than the one before.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_emulator

step "install the debug build and switch the service on"
install_app

capture_the_settings "50"

step "save it as it is"
if first="$(save_and_name "50-save-1")"; then
    pass "saved before trimming: $first"
else
    first=""
    fail "nothing was saved before trimming"
fi

step "open the start of the image: the button level with its top"
ui_tap_id "trim-start" "50-start"
if ui_wait_id "adjust-done" 10 "50-start-screen"; then
    pass "the trim screen is up"
else
    fail "no trim screen"
fi
screenshot "50-start-screen"
if python3 "$DRIVE_DIR/ui.py" "$(ui_dump "50-start-sides")" --resource-id "upper-down" > /dev/null; then
    fail "the start has buttons for an upper edge, which it has not"
else
    pass "only the lower edge has buttons"
fi

step "start fifty rows further down"
for i in 1 2 3 4 5; do
    ui_tap_id "lower-down" "50-lower-down-$i"
done
screenshot "50-start-trimmed"
ui_tap_id "adjust-done" "50-start-done"
sleep 1

step "save it again"
if second="$(save_and_name "50-save-2")"; then
    pass "saved after trimming the start: $second"
else
    second=""
    fail "nothing was saved after trimming the start"
fi

step "open the end of the image: the button level with its bottom"
ui_scroll_to_id "trim-end" "50-scroll"
ui_tap_id "trim-end" "50-end"
if ui_wait_id "adjust-done" 10 "50-end-screen"; then
    pass "the trim screen is up"
else
    fail "no trim screen"
fi
screenshot "50-end-screen"
if python3 "$DRIVE_DIR/ui.py" "$(ui_dump "50-end-sides")" --resource-id "lower-down" > /dev/null; then
    fail "the end has buttons for a lower edge, which it has not"
else
    pass "only the upper edge has buttons"
fi

step "end fifty rows further up"
for i in 1 2 3 4 5; do
    ui_tap_id "upper-up" "50-upper-up-$i"
done
screenshot "50-end-trimmed"
ui_tap_id "adjust-done" "50-end-done"
sleep 1

step "save it once more"
if third="$(save_and_name "50-save-3")"; then
    pass "saved after trimming the end: $third"
else
    third=""
    fail "nothing was saved after trimming the end"
fi

step "each image is fifty rows shorter than the one before"
if [ -n "$first" ] && [ -n "$second" ] && [ -n "$third" ]; then
    read -r w1 h1 < <(png_size "/sdcard/Pictures/ScreenShot-Dachshund/$first")
    read -r w2 h2 < <(png_size "/sdcard/Pictures/ScreenShot-Dachshund/$second")
    read -r w3 h3 < <(png_size "/sdcard/Pictures/ScreenShot-Dachshund/$third")
    note "before ${w1}x${h1}, start trimmed ${w2}x${h2}, end trimmed ${w3}x${h3}"
    "${ADB[@]}" pull "/sdcard/Pictures/ScreenShot-Dachshund/$third" "$RUN_DIR/saved-trimmed.png" > /dev/null
    if [ "$h2" -eq $((h1 - 50)) ]; then
        pass "fifty rows shorter after the start"
    else
        fail "expected $((h1 - 50)) rows after the start, got $h2"
    fi
    if [ "$h3" -eq $((h2 - 50)) ]; then
        pass "fifty rows shorter again after the end"
    else
        fail "expected $((h2 - 50)) rows after the end, got $h3"
    fi
fi

refute_log 'AndroidRuntime' "the app did not crash"
logcat_dump "50-end" > /dev/null
finish
