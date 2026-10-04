#!/usr/bin/env bash
# The cache (#35): a capture that starts sweeps the folders of earlier captures, except the ones an
# edit screen still shows. So one capture leaves exactly one folder, a capture started over an
# open edit screen leaves two, and once both edit screens are left, the next capture leaves one.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_emulator

capture_folders() {
    "${ADB[@]}" shell "run-as $PACKAGE ls cache/captures" 2>/dev/null | tr -d '\r' | rg -c '^capture-' || echo 0
}

expect_folders() {
    local want="$1" label="$2" got
    got="$(capture_folders)"
    if [ "$got" -eq "$want" ]; then
        pass "$label ($got folder(s))"
    else
        fail "$label: expected $want folder(s), found $got"
    fi
}

# Leaves every edit screen that is up: back, then Leave at the question, as long as a Save
# button is on screen. How many screens there are depends on where the capture was started
# from (an edit screen reused through onNewIntent is one screen)
leave_edit_screens() {
    local attempt dump
    for attempt in 1 2 3; do
        dump="$(ui_dump "$1-$attempt")"
        if ! python3 "$DRIVE_DIR/ui.py" "$dump" --resource-id "save" > /dev/null; then
            return 0
        fi
        "${ADB[@]}" shell input keyevent KEYCODE_BACK
        sleep 1
        if ui_wait_id "leave-confirm" 10 "$1-$attempt-ask"; then
            ui_tap_id "leave-confirm" "$1-$attempt-leave"
            sleep 2
        else
            fail "no question before leaving ($1, screen $attempt)"
            return 1
        fi
    done
}

step "install the debug build, switch the service on, add the tile"
install_app
add_tile
# From the tile, the way the user starts a capture: an edit screen that is open stays open
# underneath. From the app's home screen it would not -- the home activity is singleTask and
# clears the task when it comes up -- so there would be nothing being edited to keep
export CAPTURE_FROM=tile

step "one capture leaves one folder, whatever was there before"
capture_the_settings "80-first"
expect_folders 1 "only this capture's folder is in the cache"

step "a capture started over an open edit screen keeps that screen's folder"
capture_the_settings "80-second"
expect_folders 2 "the first capture's folder stayed, since its edit screen is still open"

step "leave the edit screen(s), then capture again: only the new folder is left"
leave_edit_screens "80-leave"
capture_the_settings "80-third"
expect_folders 1 "the folders of the captures nobody is editing are gone"

refute_log 'AndroidRuntime' "the app did not crash"
logcat_dump "80-end" > /dev/null
finish
