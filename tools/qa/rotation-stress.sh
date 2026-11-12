#!/usr/bin/env bash
# Rotation stress test for Chronova on a connected Android phone.
#
# Sets the app up in a given state (through its UI), turns the phone between portrait and
# landscape many times, and checks it survived:
#   - the app process didn't crash or restart, no ANR, no native abort;
#   - the 3D orb's crash guard (chronova_orb/starting_version) didn't trip;
#   - after the rotations, one activity is alive and memory is back near where it started;
#   - the scenario's state (an open dialog, typed text, the assistant listening...) is still there.
#
# Usage: tools/qa/rotation-stress.sh <scenario|all|list|instrumented|restore> [cycles=30] [interval=1.0]
#   OUT=dir to keep the results (screenshots, dumps, summary); a temporary folder otherwise.
#   instrumented: installs the built debug and androidTest APKs and runs the rotation instrumented tests.
#   restore: puts back the app data an interrupted run left in its backup on the phone.
#
# Needs one phone over adb with a debug build signed in against a reachable server (for the
# assistant scenarios). The app's data is backed up first and restored at the end, and the phone's
# rotation settings are put back, whatever happens. Nothing here touches the server's data except
# what the app itself does (a throwaway project is created on the phone only; no sync is started).

set -u
PKG=noel.chronova.app
SCENARIO=${1:-list}
CYCLES=${2:-30}
INTERVAL=${3:-1.0}
BURST=${BURST:-50}
BURST_INTERVAL=${BURST_INTERVAL:-0.3}
PSS_GROWTH_LIMIT=${PSS_GROWTH_LIMIT:-15}
WARMUP=${WARMUP:-3}
OUT=${OUT:-$(mktemp -d -t chronova-rotation)}
mkdir -p "$OUT"
SUMMARY="$OUT/summary.txt"
QA_PROJECT="QA rotation"

log() { echo "$(date +%T) $*" | tee -a "$OUT/run.log" >&2; }

# Every adb call gets a deadline: when the phone drops off USB, adb can otherwise wait forever.
ADB_BIN=$(command -v adb) || { echo "adb not found"; exit 2; }
adb() { perl -e 'alarm shift; exec @ARGV' "${ADB_TIMEOUT:-90}" "$ADB_BIN" "$@"; }
connected() { [ "$("$ADB_BIN" get-state 2>/dev/null)" = device ]; }
# wait_for_phone <seconds>: true once the phone is back
wait_for_phone() { local i; for i in $(seq 1 "$1"); do connected && return 0; sleep 1; done; return 1; }

# ---------- the phone: rotation settings and app data, restored on exit ----------

has_backup() { adb shell run-as $PKG ls files/qa-backup/databases >/dev/null 2>&1; }

# Puts back the data a run backed up. Reports failure rather than claiming success, and leaves the
# backup in place unless the copy back worked.
restore_data() {
    adb shell am force-stop $PKG
    adb shell "run-as $PKG sh -c 'rm -rf databases shared_prefs && cp -R files/qa-backup/databases databases && cp -R files/qa-backup/shared_prefs shared_prefs && rm -rf files/qa-backup && echo ok'" | grep -q ok
}

if [ "$SCENARIO" = restore ]; then # put back a backup that an interrupted run left on the phone
    connected || { echo "no phone connected"; exit 2; }
    has_backup || { echo "no leftover backup on the phone: nothing to restore"; exit 0; }
    restore_data && { echo "app data restored from the leftover backup"; exit 0; }
    echo "restore FAILED: the backup is still on the phone (files/qa-backup)"; exit 1
fi

connected || { echo "no phone connected"; exit 2; }
ORIG_ACCEL=$(adb shell settings get system accelerometer_rotation | tr -d '\r')
ORIG_ROT=$(adb shell settings get system user_rotation | tr -d '\r')
BACKED_UP=0

# Rotation goes through the window manager (`wm set-user-rotation`): some phones (ColorOS) refuse
# `settings put system` from adb. Back to auto-rotate if that was on, else the original fixed rotation.
restore() {
    trap - EXIT INT TERM
    if ! connected; then
        log "the phone is disconnected: waiting up to 2 minutes for it to come back to restore it"
        wait_for_phone 120 || {
            log "NOT RESTORED: the phone never came back. Reconnect it, then run: $0 restore ; adb shell wm set-user-rotation free"
            exit 3
        }
    fi
    if [ "$ORIG_ACCEL" = 1 ]; then adb shell wm set-user-rotation free >/dev/null 2>&1; else adb shell wm set-user-rotation lock "$ORIG_ROT" >/dev/null 2>&1; fi
    log "rotation settings restored (accelerometer_rotation=$(adb shell settings get system accelerometer_rotation | tr -d '\r'), was $ORIG_ACCEL)"
    if [ "$BACKED_UP" = 1 ]; then
        if restore_data; then log "app data restored"; BACKED_UP=0
        else log "APP DATA NOT RESTORED: the backup is still on the phone (files/qa-backup); run: $0 restore"; exit 3; fi
    fi
}
trap restore EXIT
trap 'restore; exit 130' INT TERM # stop for good: never carry on after the data is put back

backup() {
    # A backup left by an interrupted run holds the real data: never overwrite it with test data.
    if has_backup; then
        echo "A backup from an interrupted run is still on the phone. Run '$0 restore' first."; trap - EXIT; exit 2
    fi
    adb shell am force-stop $PKG
    adb shell "run-as $PKG sh -c 'mkdir -p files/qa-backup && cp -R databases shared_prefs files/qa-backup/ && echo ok'" | grep -q ok || { echo "backup failed: is a debug build installed?"; exit 1; }
    BACKED_UP=1
    log "app data backed up on the phone"
}

portrait() { adb shell wm set-user-rotation lock 0; }
landscape() { adb shell wm set-user-rotation lock 1; }

# ---------- UI helpers (uiautomator) ----------

dump() { adb exec-out uiautomator dump /dev/tty 2>/dev/null | sed 's#UI hierchary dumped to: /dev/tty##' > "$OUT/ui.xml"; }
# center <regex on a node's attributes> → "x y" of the first matching node
center() {
    dump
    python3 - "$OUT/ui.xml" "$1" <<'PY'
import re, sys
xml, pattern = open(sys.argv[1]).read(), re.compile(sys.argv[2])
for node in re.findall(r'<node [^>]*>', xml):
    if pattern.search(node):
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
        print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
}
tap() { local xy; xy=$(center "$1"); [ -n "$xy" ] || { log "not on screen: $1"; return 1; }; adb shell input tap $xy; }
# The floating buttons don't show up in uiautomator dumps; in portrait they sit at the bottom right (720x1600 phone).
new_project_button() { adb shell input tap 540 1424; }
add_task_button() { adb shell input tap 560 1424; }
has() { dump; grep -qE "$1" "$OUT/ui.xml"; }
text_of() { dump; python3 -c "import re,sys; m=re.search(sys.argv[2], open(sys.argv[1]).read()); print(m.group(1) if m else '')" "$OUT/ui.xml" "$1"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
back() { adb shell input keyevent 4; sleep 1; }

launch() { adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 6; }
home_list() { for _ in 1 2 3 4; do has 'content-desc="Account and sync"' && return 0; back; done; launch; }
open_project() { home_list; tap "text=\"$1\"" && sleep 3; }
close_orb() { has 'Tap outside the orb to stop' && adb shell input tap 360 1500 && sleep 1.5; return 0; }

# ---------- health ----------

pid() { adb shell pidof $PKG | tr -d '\r'; }
pss() { adb shell dumpsys meminfo $PKG | awk '/TOTAL PSS:/ {print $3; exit} /^ *TOTAL / {print $2; exit}'; }
activities() { adb shell dumpsys meminfo $PKG | awk '/Activities:/ {print $NF; exit}'; }
guard_tripped() { adb shell run-as $PKG cat shared_prefs/chronova_orb.xml 2>/dev/null | grep -q starting_version; }
# The screen's rotation right now, in degrees: 0 = portrait, 90 or 270 = landscape.
screen_rotation() { adb shell dumpsys window displays | grep -oE 'mCurrentRotation=ROTATION_[0-9]+' | head -1 | sed 's/.*_//'; }
crashes_since() { # count crash/ANR/abort lines for the app since the logs were cleared
    { adb logcat -d -b crash; adb logcat -d -b events | grep -E 'am_crash|am_anr'; adb logcat -d | grep -E 'FATAL EXCEPTION|Abort message'; } 2>/dev/null | grep -c "$PKG\|chronova" ; }

# ---------- one run: rotate CYCLES times (then a fast burst) and judge ----------

# run_rotations <name> <check-regex or ""> [during-command]
# HOLD_WHILE=<regex>: whenever that is on screen after a turn (the assistant is open), the screen
# must not have turned with the phone.
run_rotations() {
    local name=$1 check=$2 during=${3:-} held=0 turned=0
    # A few turns first: the first rotations fill caches (the landscape layout, fonts, the orb's
    # renderer), which isn't a leak. The soak scenario is the check for memory that keeps growing.
    local w; for w in $(seq 1 "$WARMUP"); do landscape; sleep 0.5; portrait; sleep 0.5; done; sleep 1
    adb logcat -c; adb logcat -b crash -c 2>/dev/null; adb logcat -b events -c 2>/dev/null
    local pid0 pss0; pid0=$(pid); pss0=$(pss)
    shot "$name-start"
    [ -n "$during" ] && eval "$during" &
    local i
    for i in $(seq 1 "$CYCLES"); do
        landscape; sleep "$INTERVAL"
        if [ -n "${HOLD_WHILE:-}" ] && has "$HOLD_WHILE"; then
            held=$((held + 1)); [ "$(screen_rotation)" = 0 ] || turned=$((turned + 1))
        fi
        [ "$i" = $((CYCLES / 2 + 1)) ] && shot "$name-landscape"
        portrait; sleep "$INTERVAL"
    done
    wait
    local state_ok=yes
    if [ -n "$check" ]; then has "$check" || state_ok=no; fi
    for i in $(seq 1 "$BURST"); do landscape; sleep "$BURST_INTERVAL"; portrait; sleep "$BURST_INTERVAL"; done
    sleep 3
    adb shell am send-trim-memory $PKG RUNNING_LOW >/dev/null 2>&1; sleep 2
    shot "$name-end"
    local pid1 pss1 acts crashes growth verdict reasons=""
    if ! connected; then printf '%-22s ABORTED  the phone disconnected mid-run\n' "$name" | tee -a "$SUMMARY"; return 1; fi
    pid1=$(pid); pss1=$(pss); acts=$(activities); crashes=$(crashes_since)
    growth=$(python3 -c "print(round(100*($pss1-$pss0)/max($pss0,1)))" 2>/dev/null || echo "?")
    [ "$pid0" = "$pid1" ] || reasons+=" process restarted ($pid0→$pid1);"
    [ "${crashes:-0}" = 0 ] || reasons+=" $crashes crash/ANR/abort log lines;"
    guard_tripped && reasons+=" orb crash guard tripped;"
    [ "${acts:-1}" -le 1 ] 2>/dev/null || reasons+=" $acts activities alive;"
    [ "$growth" != "?" ] && [ "$growth" -gt "$PSS_GROWTH_LIMIT" ] && reasons+=" memory +$growth%;"
    [ "$state_ok" = yes ] || reasons+=" state lost (wanted: $check);"
    local note=""
    if [ -n "${HOLD_WHILE:-}" ]; then
        if [ "$held" = 0 ]; then reasons+=" the assistant was never open after a turn (nothing tested);"
        elif [ "$turned" -gt 0 ]; then reasons+=" the screen turned $turned of $held times while the assistant was open;"
        else note=", screen held at all $held turns while the assistant was open"; fi
    fi
    if [ -z "$reasons" ]; then verdict=PASS; else verdict=FAIL; fi
    printf '%-22s %s  pss %s→%s KB (%s%%), activities %s%s%s\n' "$name" "$verdict" "$pss0" "$pss1" "$growth" "$acts" "$note" "${reasons:+ —$reasons}" | tee -a "$SUMMARY"
    adb logcat -d > "$OUT/$name-logcat.txt" 2>/dev/null
}

# ---------- setup: a throwaway project with one task ----------

setup_project() {
    home_list
    if has "text=\"$QA_PROJECT\""; then return 0; fi
    new_project_button
    sleep 1.5; tap 'text="Project name"'; sleep 0.6; adb shell input text "QA%srotation"; sleep 0.6
    tap 'text="Create"'; sleep 3
    close_orb # the first-open introduction
    add_task_button; sleep 1.5; tap 'text="Task title"'; sleep 0.6; adb shell input text "Rotate%sme"; sleep 0.6
    tap 'text="Add"'; sleep 2
    has 'text="Rotate me"' || log "SETUP FAILED: the task didn't appear"
    log "set up project \"$QA_PROJECT\" with task \"Rotate me\""
}

# ---------- scenarios ----------

sc_projects() { home_list; run_rotations projects 'text="QA rotation"'; }

sc_timeline() { open_project "$QA_PROJECT"; close_orb; run_rotations timeline 'text="Rotate me"'; }

sc_scroll() { # a round trip should come back to the same time range
    open_project "$QA_PROJECT"; close_orb; tap 'text="Days"'; sleep 1
    adb shell input swipe 600 900 200 900 400; sleep 1
    local before; before=$(text_of 'text="([0-9]{1,2} [A-Z][a-z]{2}|[A-Z][a-z]{2} [0-9]{1,2} [A-Z][a-z]{2})"')
    run_rotations scroll "text=\"$before\""
    tap 'text="Hours"'
}

sc_selected() { # tapping a bar opens its editor sheet
    open_project "$QA_PROJECT"; close_orb; tap 'text="Rotate me"'; sleep 1.5
    run_rotations selected 'text="Edit task"'
    back; back
}

sc_editor() {
    open_project "$QA_PROJECT"; close_orb; add_task_button; sleep 1.5; tap 'text="Task title"'; sleep 0.6
    adb shell input text "Typed%sbefore%srotating"; sleep 0.5 # keyboard left up: Escape would close the sheet
    run_rotations editor 'Typed before rotating'
    back; back
}

sc_drag() { # hold a drag on the bar across the first rotations
    open_project "$QA_PROJECT"; close_orb
    local xy; xy=$(center 'text="Rotate me"'); local x=${xy% *} y=${xy#* }
    run_rotations drag 'text="Rotate me"' "adb shell input swipe $x $y $((x + 250)) $y 3000"
}

sc_new_project() {
    home_list; new_project_button; sleep 1.5
    tap 'text="Project name"'; sleep 0.6; adb shell input text "Half%styped"; sleep 0.5
    run_rotations new-project 'Half typed'
    back; back
}

sc_delete_dialog() {
    home_list; tap "content-desc=\"Actions for $QA_PROJECT\""; sleep 1; tap 'text="Delete"'; sleep 1
    run_rotations delete-dialog 'text="Cancel"'
    tap 'text="Cancel"'; back
}

sc_notifications() {
    home_list; tap 'content-desc="Notifications'; sleep 2
    tap 'text="Unread"' ; sleep 1
    run_rotations notifications 'Unread'
    back
}

sc_assistant_speaking() { # rotate while the orb greets
    open_project "$QA_PROJECT"; close_orb; tap 'content-desc="Assistant"'; sleep 1
    HOLD_WHILE='Tap outside the orb to stop' WARMUP=0 run_rotations assistant-speaking ""
    close_orb
}

sc_assistant_listening() { # rotate while it listens (the Mac keeps quiet)
    open_project "$QA_PROJECT"; close_orb; tap 'content-desc="Assistant"'
    for _ in $(seq 1 20); do has 'Chronova assistant, listening' && break; sleep 0.5; done
    HOLD_WHILE='Tap outside the orb to stop' WARMUP=0 run_rotations assistant-listening ""
    close_orb
}

sc_assistant_talking() {
    # The assistant is mid-answer when the phone suddenly turns: the screen stays as it is and the
    # assistant keeps talking. Once it is closed, the screen follows the phone again.
    open_project "$QA_PROJECT"; close_orb; tap 'content-desc="Assistant"'
    for _ in $(seq 1 30); do has 'Chronova assistant, listening' && break; sleep 0.5; done
    say -v Samantha "Push rotate me by one hour." 2>/dev/null || true
    local spoke=no
    for _ in $(seq 1 60); do has 'Chronova assistant, speaking' && { spoke=yes; break; }; sleep 0.3; done
    if [ "$spoke" = yes ]; then
        landscape; sleep 1.2
        local rot open=no; rot=$(screen_rotation); has 'Tap outside the orb to stop' && open=yes
        shot assistant-talking-turned
        if [ "$open" = yes ] && [ "$rot" = 0 ]; then echo "assistant-talking-turn  PASS  phone turned while it talked: screen stayed portrait, still talking" | tee -a "$SUMMARY"
        else echo "assistant-talking-turn  FAIL  phone turned while it talked: screen rotation $rot, assistant open: $open" | tee -a "$SUMMARY"; fi
        # Once it is closed, the screen follows the phone again. (Android undoes a fixed rotation set from
        # adb while the app holds its orientation, so the phone is turned again after closing.)
        close_orb; landscape; sleep 2.5; rot=$(screen_rotation); shot assistant-talking-released
        if [ "$rot" = 90 ] || [ "$rot" = 270 ]; then echo "assistant-released       PASS  once closed, the screen turned to landscape with the phone" | tee -a "$SUMMARY"
        else echo "assistant-released       FAIL  once closed, the screen stayed at rotation $rot" | tee -a "$SUMMARY"; fi
        portrait; sleep 1.5
    else
        echo "assistant-talking-turn  SKIP  never heard it start talking" | tee -a "$SUMMARY"
    fi
    # Then many turns while it is open (it keeps one orientation), checked for crashes and memory.
    tap 'content-desc="Assistant"'; sleep 1
    HOLD_WHILE='Tap outside the orb to stop' WARMUP=0 run_rotations assistant-talking ""
    close_orb
}

sc_soak() { # memory over a long run: should level off, not keep climbing
    open_project "$QA_PROJECT"; close_orb
    local samples=() i j
    for i in $(seq 1 10); do
        for j in $(seq 1 20); do landscape; sleep 0.4; portrait; sleep 0.4; done
        sleep 2; samples+=("$(pss)")
    done
    local first=${samples[2]} last=${samples[9]}
    local growth; growth=$(python3 -c "print(round(100*($last-$first)/max($first,1)))")
    local verdict=PASS; [ "$growth" -gt 5 ] && verdict=FAIL
    echo "soak                   $verdict  pss every 20 rotations: ${samples[*]} KB; growth after warm-up (40→200): $growth%" | tee -a "$SUMMARY"
}

sc_intro() { # a fresh project's first-open introduction, rotated mid-way
    home_list; new_project_button; sleep 1.5
    tap 'text="Project name"'; sleep 0.6; adb shell input text "QA%sintro"; sleep 0.6; tap 'text="Create"'; sleep 2
    HOLD_WHILE='Tap outside the orb to stop' WARMUP=0 run_rotations intro ""
    close_orb
}

sc_notice() { # the one-time data notice, with its flag cleared for the test
    adb shell am force-stop $PKG
    adb shell "run-as $PKG sh -c 'sed -i \"/voice_notice_accepted/d\" shared_prefs/chronova_voice.xml'"
    launch; open_project "$QA_PROJECT"; close_orb; tap 'content-desc="Assistant"'; sleep 1.5
    run_rotations notice 'Talk to Chronova'
    tap 'text="Cancel"'
}

sc_burst() { open_project "$QA_PROJECT"; close_orb; CYCLES=100 INTERVAL=0.3 run_rotations burst 'text="Rotate me"'; }

ALL="projects timeline scroll selected editor drag new_project delete_dialog notifications assistant_speaking assistant_listening assistant_talking intro notice burst soak"

# The instrumented tests (app/src/androidTest/.../rotation): installs the debug app over the current one
# (data kept) and the test APK, runs them with `am instrument`, and restores the data. Never use
# connectedAndroidTest for these: it uninstalls the app, and the data with it.
APP_DIR="$(cd "$(dirname "$0")/../.." && pwd)/Chronova - Android App"
APP_APK=${APP_APK:-$APP_DIR/app/build/outputs/apk/debug/app-debug.apk}
TEST_APK=${TEST_APK:-$APP_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk}
TEST_CLASSES=${TEST_CLASSES:-$PKG.rotation.RotationStateTest,$PKG.rotation.RotationStressTest}
run_instrumented() {
    [ -f "$APP_APK" ] && [ -f "$TEST_APK" ] || { log "build first: ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest"; return 1; }
    adb install -r "$APP_APK" >/dev/null && adb install -r -t "$TEST_APK" >/dev/null || { log "install failed"; return 1; }
    # Screen on: with it off, some phones (ColorOS) freeze the test process before its first activity shows.
    adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard >/dev/null 2>&1; sleep 1
    ADB_TIMEOUT=900 adb shell am instrument -w -e class "$TEST_CLASSES" $PKG.test/androidx.test.runner.AndroidJUnitRunner | tee "$OUT/instrumented.txt"
    if grep -q '^OK (' "$OUT/instrumented.txt"; then echo "instrumented           PASS  $(grep '^OK (' "$OUT/instrumented.txt")" | tee -a "$SUMMARY"
    else echo "instrumented           FAIL  see $OUT/instrumented.txt" | tee -a "$SUMMARY"; fi
}

case "$SCENARIO" in
    list) echo "scenarios: $ALL all instrumented restore"; trap - EXIT; exit 0 ;;
    instrumented)
        log "results in $OUT"; backup; run_instrumented
        echo; echo "== summary ($OUT) =="; cat "$SUMMARY"
        grep -q FAIL "$SUMMARY" && exit 1 || exit 0 ;;
esac

log "results in $OUT"
backup
portrait
launch
setup_project
for sc in $( [ "$SCENARIO" = all ] && echo "$ALL" || echo "$SCENARIO" ); do
    declare -F "sc_$sc" >/dev/null || { echo "unknown scenario: $sc"; continue; }
    connected || { log "the phone disconnected: stopping before scenario $sc"; echo "$sc  ABORTED  phone disconnected" >> "$SUMMARY"; break; }
    log "scenario $sc"
    "sc_$sc"
done
echo; echo "== summary ($OUT) =="; cat "$SUMMARY"
grep -q FAIL "$SUMMARY" && exit 1 || exit 0
