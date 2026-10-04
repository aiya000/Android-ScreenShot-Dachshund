#!/usr/bin/env bash
# The question asked while the service is off (#8): with the service switched off, opening the
# app shows a dialog, and its button lands in the accessibility settings -- on the service's own
# page where the system allows it.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_emulator

step "install the debug build, with the service OFF"
"${ADB[@]}" install -r -g "$APK" > /dev/null
app_stop
enabled="$("${ADB[@]}" shell settings get secure enabled_accessibility_services | tr -d '\r')"
without="$(tr ':' '\n' <<< "$enabled" | rg -v -F "$SERVICE" | rg -v '^null$' | paste -sd ':' - || true)"
if [ -z "$without" ]; then
    "${ADB[@]}" shell settings delete secure enabled_accessibility_services
else
    "${ADB[@]}" shell settings put secure enabled_accessibility_services "$without"
fi
sleep 1

step "open the app"
"${ADB[@]}" shell am start -W -n "$PACKAGE/io.github.aiya000.screenshotdachshund.MainActivity" > /dev/null
sleep 2
screenshot "40-home"
if ui_wait_id "prompt-open" 10 "40-prompt"; then
    pass "the app asks to switch the service on"
else
    fail "no dialog about the service being off"
fi

step "Later puts the question aside, and the home screen says the service is off"
ui_tap_id "prompt-later" "40-later"
sleep 1
dump="$(ui_dump "40-after-later")"
if python3 "$DRIVE_DIR/ui.py" "$dump" --resource-id "prompt-open" > /dev/null; then
    fail "the dialog is still there after Later"
else
    pass "the dialog is gone"
fi
if python3 "$DRIVE_DIR/ui.py" "$dump" --text "is off" > /dev/null; then
    pass "the home screen still says the service is off"
else
    fail "the home screen does not say the service is off (view tree in $dump)"
fi

step "reopened, the app asks again, and the button opens the accessibility settings"
"${ADB[@]}" shell am force-stop "$PACKAGE"
"${ADB[@]}" shell am start -W -n "$PACKAGE/io.github.aiya000.screenshotdachshund.MainActivity" > /dev/null
sleep 2
if ui_wait_id "prompt-open" 10 "40-prompt-2"; then
    pass "the question is asked again on a fresh start"
else
    fail "no dialog on the second start"
fi
ui_tap_id "prompt-open" "40-open"
sleep 3
screenshot "40-settings"
focus="$("${ADB[@]}" shell dumpsys window | tr -d '\r' | rg -o 'mCurrentFocus=.*' | head -n 1)"
note "$focus"
if rg -q 'com.android.settings' <<< "$focus"; then
    pass "the settings app is in front"
else
    fail "the settings app is not in front"
fi
dump="$(ui_dump "40-settings")"
if python3 "$DRIVE_DIR/ui.py" "$dump" --text "Dachshund debug" > /dev/null; then
    pass "the list shows the service, landed on its row"
else
    fail "the service's row is not in view (view tree in $dump)"
fi

step "switch it on there, the way the user would, and come back"
"${ADB[@]}" shell input keyevent KEYCODE_BACK
sleep 1
"${ADB[@]}" shell input keyevent KEYCODE_BACK
install_app
"${ADB[@]}" shell am start -W -n "$PACKAGE/io.github.aiya000.screenshotdachshund.MainActivity" > /dev/null
sleep 3
dump="$(ui_dump "40-on")"
if python3 "$DRIVE_DIR/ui.py" "$dump" --resource-id "prompt-open" > /dev/null; then
    fail "the dialog is shown although the service is on"
else
    pass "no dialog once the service is on"
fi

refute_log 'AndroidRuntime' "the app did not crash"
logcat_dump "40-end" > /dev/null
finish
