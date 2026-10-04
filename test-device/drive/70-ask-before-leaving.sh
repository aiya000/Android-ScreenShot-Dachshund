#!/usr/bin/env bash
# Leaving the edit screen (#32): back on an unsaved capture asks first, Cancel keeps the screen,
# Leave closes it; back on the adjust screen only closes the adjust screen; and once the capture
# is saved, back closes the edit screen at once.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_emulator

back() { "${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 1; }

# whether the edit screen's Save button is on screen right now
edit_screen_is_up() {
    local dump
    dump="$(ui_dump "$1")"
    python3 "$DRIVE_DIR/ui.py" "$dump" --resource-id "save" > /dev/null
}

step "install the debug build and switch the service on"
install_app

capture_the_settings "70"

step "back on an unsaved capture asks first"
back
if ui_wait_id "leave-confirm" 10 "70-ask"; then
    pass "the question is up"
else
    fail "no question before leaving"
fi
screenshot "70-ask"

step "Cancel keeps the screen"
ui_tap_id "leave-cancel" "70-cancel"
sleep 1
if edit_screen_is_up "70-after-cancel"; then
    pass "still on the edit screen"
else
    fail "the edit screen went away on Cancel"
fi

step "back on the adjust screen only closes the adjust screen"
ui_scroll_to_id "adjust-0" "70-scroll"
ui_tap_id "adjust-0" "70-adjust"
ui_wait_id "adjust-done" 10 "70-adjust-screen" || fail "no adjust screen"
back
if edit_screen_is_up "70-after-adjust-back"; then
    pass "back from the adjust screen lands on the edit screen"
else
    fail "back from the adjust screen did not return to the edit screen"
fi

step "back, then Leave, closes the screen"
back
ui_wait_id "leave-confirm" 10 "70-ask-2" || fail "no question the second time"
ui_tap_id "leave-confirm" "70-leave"
sleep 2
if edit_screen_is_up "70-after-leave"; then
    fail "the edit screen is still up after Leave"
else
    pass "the edit screen is gone"
fi

step "a saved capture closes at once"
capture_the_settings "70-second"
if save_and_name "70-save" > /dev/null; then
    pass "saved"
else
    fail "nothing was saved"
fi
back
sleep 1
dump="$(ui_dump "70-after-saved-back")"
if python3 "$DRIVE_DIR/ui.py" "$dump" --resource-id "leave-confirm" > /dev/null; then
    fail "a saved capture was still asked about"
elif python3 "$DRIVE_DIR/ui.py" "$dump" --resource-id "save" > /dev/null; then
    fail "the edit screen is still up after back on a saved capture"
else
    pass "closed without asking"
fi

refute_log 'AndroidRuntime' "the app did not crash"
logcat_dump "70-end" > /dev/null
finish
