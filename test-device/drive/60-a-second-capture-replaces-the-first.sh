#!/usr/bin/env bash
# A second capture while the edit screen is still open (#31): the edit screen has to show the
# new capture, not stay on the old one. What the screen is editing is read from its log, since
# two captures of the same settings look alike.
#
# Both captures start from the quick-settings tile, the way the user starts them. That leaves
# the edit screen as the root of the app's task, which is the shape in which the bug showed:
# started from the home screen instead, the second edit screen simply opened on top.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_emulator

step "install the debug build, switch the service on, add the tile"
install_app
add_tile
export CAPTURE_FROM=tile

capture_the_settings "60-first"
first="$(app_log | rg -o 'finished: \w+, [0-9]+ pages, (capture-[0-9]+)' -r '$1' | tail -n 1)"
note "first capture: ${first:-?}"
if log_matches "editing $first"; then
    pass "the edit screen is editing the first capture"
else
    fail "the edit screen did not say it is editing $first"
fi

step "leave the edit screen as it is and take another capture"
# capture_the_settings resets the log, so the second capture's lines are the only ones left
capture_the_settings "60-second"
second="$(app_log | rg -o 'finished: \w+, [0-9]+ pages, (capture-[0-9]+)' -r '$1' | tail -n 1)"
note "second capture: ${second:-?}"
if [ -n "$second" ] && [ "$second" != "$first" ]; then
    pass "a different capture finished"
else
    fail "no second capture finished (got '$second')"
fi
if log_matches "editing $second"; then
    pass "the edit screen switched to the second capture"
else
    fail "the edit screen did not switch to $second"
fi
screenshot "60-end"

refute_log 'AndroidRuntime' "the app did not crash"
logcat_dump "60-end" > /dev/null
finish
