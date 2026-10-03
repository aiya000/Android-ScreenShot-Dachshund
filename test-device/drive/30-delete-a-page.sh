#!/usr/bin/env bash
# Deleting a page (#3): after a capture, the first page's Delete asks first -- Cancel leaves the
# image exactly as it was -- and Delete takes the page out, so the image saved afterwards is
# shorter than the one saved before.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_emulator

step "install the debug build and switch the service on"
install_app

capture_the_settings "30"
if [ "${CAPTURED_PAGES:-0}" -ge 2 ]; then
    pass "there is a page to spare"
else
    fail "expected at least two pages, got ${CAPTURED_PAGES:-none}"
fi

step "save it as it is"
if first="$(save_and_name "30-save-1")"; then
    pass "saved before: $first"
else
    first=""
    fail "nothing was saved before deleting"
fi

step "ask to delete the first page, then think better of it"
ui_tap_id "delete-0" "30-delete"
if ui_wait_id "delete-confirm" 10 "30-dialog"; then
    pass "the question is asked first"
else
    fail "no confirmation dialog"
fi
screenshot "30-dialog"
ui_tap_id "delete-cancel" "30-cancel"
sleep 1
if ui_wait_id "delete-0" 10 "30-after-cancel"; then
    pass "the page is still there after Cancel"
else
    fail "the page went away on Cancel"
fi

step "save it again: nothing has changed"
if second="$(save_and_name "30-save-2")"; then
    pass "saved after cancel: $second"
else
    second=""
    fail "nothing was saved after cancel"
fi

step "delete the first page for real"
ui_tap_id "delete-0" "30-delete-2"
ui_wait_id "delete-confirm" 10 "30-dialog-2" || fail "no confirmation dialog the second time"
ui_tap_id "delete-confirm" "30-confirm"
sleep 1
screenshot "30-deleted"

step "save it once more: shorter now"
if third="$(save_and_name "30-save-3")"; then
    pass "saved after delete: $third"
else
    third=""
    fail "nothing was saved after delete"
fi

step "the heights say what happened"
if [ -n "$first" ] && [ -n "$second" ] && [ -n "$third" ]; then
    read -r w1 h1 < <(png_size "/sdcard/Pictures/ScreenShot-Dachshund/$first")
    read -r w2 h2 < <(png_size "/sdcard/Pictures/ScreenShot-Dachshund/$second")
    read -r w3 h3 < <(png_size "/sdcard/Pictures/ScreenShot-Dachshund/$third")
    note "before ${w1}x${h1}, after cancel ${w2}x${h2}, after delete ${w3}x${h3}"
    "${ADB[@]}" pull "/sdcard/Pictures/ScreenShot-Dachshund/$third" "$RUN_DIR/saved-deleted.png" > /dev/null
    if [ "$h2" -eq "$h1" ]; then
        pass "cancel changed nothing"
    else
        fail "cancel changed the height from $h1 to $h2"
    fi
    if [ "$h3" -lt "$h1" ]; then
        pass "a page's worth shorter"
    else
        fail "expected fewer than $h1 rows, got $h3"
    fi
fi

refute_log 'AndroidRuntime' "the app did not crash"
logcat_dump "30-end" > /dev/null
finish
