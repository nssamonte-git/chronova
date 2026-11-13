#!/bin/sh
# The full check for the Android app: what CI and the pre-push hook run.
#
#   tools/check.sh             unit tests, app build and device-test APKs, lint, the module-graph and
#                              UI-model checks, every screenshot suite (the security gate runs at preBuild)
#   tools/check.sh --devices   also every module's device tests on the managed emulator (pixel6api35);
#                              nothing is installed on a phone
#   tools/check.sh --devices-only   only the device tests on the emulator (CI's Linux job)
#
# Extra Gradle arguments: CHECK_GRADLE_ARGS (for example the emulator's GPU mode in CI).
# Exit code 0 only when everything passes. The Gradle log goes to build/reports/check.log.
set -u
root=$(cd "$(dirname "$0")/.." && pwd)
app="$root/Chronova - Android App"
cd "$app" || { echo "check: Android project not found at $app" >&2; exit 2; }

devices=false
checks=true
for arg in "$@"; do
  case "$arg" in
    --devices) devices=true ;;
    --devices-only) devices=true; checks=false ;;
    *) echo "usage: tools/check.sh [--devices | --devices-only]" >&2; exit 2 ;;
  esac
done

tasks=""
$checks && tasks="test :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest verifyModuleGraph verifyUiModels testDebugScreenshotTestDefaultTestSuite"
$devices && tasks="$tasks pixel6api35DebugAndroidTest"

mkdir -p build/reports
log=build/reports/check.log
# Stale results would make the summary below lie.
find . -path ./build-logic -prune -o \( -path '*/build/test-results' -o -path '*/build/outputs/androidTest-results' \) -type d -prune -exec rm -rf {} + 2>/dev/null

echo "check: running $tasks"
# shellcheck disable=SC2086
./gradlew $tasks ${CHECK_GRADLE_ARGS:-} --continue > "$log" 2>&1
code=$?

python3 - "$code" "$devices" "$checks" <<'PY'
import glob, re, sys
code, devices, checks = int(sys.argv[1]), sys.argv[2] == "true", sys.argv[3] == "true"

def count(pattern):
    tests = failed = 0
    for path in glob.glob(pattern, recursive=True):
        suite = re.search(r"<testsuite\b[^>]*>", open(path, errors="replace").read(4000))
        if not suite:
            continue
        # Attribute order differs between test tasks, so read each one on its own.
        attr = lambda name: int((re.search(rf'\b{name}="(\d+)"', suite[0]) or [0, 0])[1])
        tests += attr("tests"); failed += attr("failures") + attr("errors")
    return tests, failed

def first_line(path, default):
    try:
        return open(path).read().strip().splitlines()[-1]
    except (OSError, IndexError):
        return default

if checks:
    unit = [count("**/build/test-results/test/*.xml"), count("**/build/test-results/testDebugUnitTest/*.xml")]
    print(f"unit tests:   {unit[0][0] + unit[1][0]} run, {unit[0][1] + unit[1][1]} failed")
    # The screenshot suites keep their JUnit results under intermediates (kept while the task is up to date).
    shots = count("**/build/intermediates/test_suite_results/debug/testDebugScreenshotTestDefaultTestSuite/*.xml")
    print(f"screenshots:  {shots[0]} checked, {shots[1]} failed")
    print("lint:        ", first_line("app/build/reports/lint-results-debug.txt", "no report (lint didn't run)"))
    print("module graph:", first_line("build/reports/module-graph.txt", "no report"))
    print("UI models:   ", first_line("build/reports/ui-models.txt", "no report"))
if devices:
    dev = count("**/build/outputs/androidTest-results/managedDevice/**/*.xml")
    print(f"device tests: {dev[0]} run, {dev[1]} failed (managed emulator)")
print("result:       PASS" if code == 0 else f"result:       FAIL (Gradle exit {code}; see build/reports/check.log)")
PY
if [ "$code" -ne 0 ]; then
  grep -E '^e: |FAILED|What went wrong' -A3 "$log" | head -30
fi
exit "$code"
