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

check_tasks="test :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest verifyModuleGraph verifyUiModels testDebugScreenshotTestDefaultTestSuite"
device_tasks="pixel6api35DebugAndroidTest"

mkdir -p build/reports
log=build/reports/check.log
# Stale results would make the summary below lie.
find . -path ./build-logic -prune -o \( -path '*/build/test-results' -o -path '*/build/outputs/androidTest-results' \) -type d -prune -exec rm -rf {} + 2>/dev/null

: > "$log"
code=0
if $checks; then
  echo "check: running $check_tasks"
  # shellcheck disable=SC2086
  ./gradlew $check_tasks ${CHECK_GRADLE_ARGS:-} --continue >> "$log" 2>&1
  code=$?
fi
if $devices; then
  # One module at a time (--max-workers=1). Gradle otherwise runs several modules' test apps at
  # once on the one emulator, and one of them was killed mid-run ("Process crashed") with none
  # of its tests reported; alone, the same module passes.
  echo "check: running $device_tasks (one module at a time)"
  # shellcheck disable=SC2086
  ./gradlew $device_tasks ${CHECK_GRADLE_ARGS:-} --continue --max-workers=1 >> "$log" 2>&1
  device_code=$?
  [ "$code" -eq 0 ] && code=$device_code
fi

python3 - "$code" "$devices" "$checks" <<'PY'
import glob, re, sys
code, devices, checks = int(sys.argv[1]), sys.argv[2] == "true", sys.argv[3] == "true"

def count(pattern):
    tests = failed = 0
    for path in glob.glob(pattern, recursive=True):
        # The root element: <testsuites> where a file holds several classes (device tests), else
        # its one <testsuite>. Reading only the first class undercounted the device tests.
        suite = re.search(r"<testsuites?\b[^>]*>", open(path, errors="replace").read(4000))
        if not suite:
            continue
        # Attribute order differs between test tasks, so read each one on its own.
        attr = lambda name: int((re.search(rf'\b{name}="(\d+)"', suite[0]) or [0, 0])[1])
        tests += attr("tests"); failed += attr("failures") + attr("errors")
    return tests, failed

def failed_cases(pattern):
    """Each failed test as "Class.test: the first line of its message", so a log (CI's too) names them."""
    out = []
    # A passing test is often self-closing (<testcase .../>), so match that form first.
    case = re.compile(r'<testcase\b([^>]*?)(?:/>|>(.*?)</testcase>)', re.S)
    for path in sorted(glob.glob(pattern, recursive=True)):
        for attrs, body in case.findall(open(path, errors="replace").read()):
            problem = re.search(r'<(failure|error)\b[^>]*?(?:message="([^"]*)")?[^>]*>(.*?)</\1>', body, re.S)
            if not problem:
                continue
            name = (re.search(r'\bname="([^"]*)"', attrs) or [None, "?"])[1]
            cls = (re.search(r'\bclassname="([^"]*)"', attrs) or [None, "?"])[1].rsplit(".", 1)[-1]
            text = (problem[2] or problem[3] or "").replace("&quot;", '"').replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            first = next((l.strip() for l in text.strip().splitlines() if l.strip()), "no message")
            out.append(f"{cls}.{name}: {first[:200]}")
    return out

def print_failed(pattern):
    cases = failed_cases(pattern)
    for line in cases[:20]:
        print(f"  FAILED  {line}")
    if len(cases) > 20:
        print(f"  ... and {len(cases) - 20} more (build/reports/check.log)")

def first_line(path, default):
    try:
        return open(path).read().strip().splitlines()[-1]
    except (OSError, IndexError):
        return default

failures = 0
if checks:
    unit = [count("**/build/test-results/test/*.xml"), count("**/build/test-results/testDebugUnitTest/*.xml")]
    failures += unit[0][1] + unit[1][1]
    print(f"unit tests:   {unit[0][0] + unit[1][0]} run, {unit[0][1] + unit[1][1]} failed")
    if unit[0][1] + unit[1][1]:
        print_failed("**/build/test-results/test/*.xml"); print_failed("**/build/test-results/testDebugUnitTest/*.xml")
    # The screenshot suites keep their JUnit results under intermediates (kept while the task is up to date).
    shots = count("**/build/intermediates/test_suite_results/debug/testDebugScreenshotTestDefaultTestSuite/*.xml")
    failures += shots[1]
    print(f"screenshots:  {shots[0]} checked, {shots[1]} failed")
    if shots[1]:
        print_failed("**/build/intermediates/test_suite_results/debug/testDebugScreenshotTestDefaultTestSuite/*.xml")
    print("lint:        ", first_line("app/build/reports/lint-results-debug.txt", "no report (lint didn't run)"))
    print("module graph:", first_line("build/reports/module-graph.txt", "no report"))
    print("UI models:   ", first_line("build/reports/ui-models.txt", "no report"))
if devices:
    dev = count("**/build/outputs/androidTest-results/managedDevice/**/*.xml")
    failures += dev[1]
    print(f"device tests: {dev[0]} run, {dev[1]} failed (managed emulator)")
    if dev[1]:
        print_failed("**/build/outputs/androidTest-results/managedDevice/**/*.xml")
# A crashed test app is reported as a failed test while Gradle still exits 0: that is not a pass.
if code == 0 and failures == 0:
    print("result:       PASS")
elif code == 0:
    print(f"result:       FAIL ({failures} failed; Gradle exited 0; see build/reports/check.log)")
    sys.exit(1)
else:
    print(f"result:       FAIL (Gradle exit {code}; see build/reports/check.log)")
PY
summary=$?
[ "$code" -eq 0 ] && code=$summary
if [ "$code" -ne 0 ]; then
  grep -E '^e: |FAILED|What went wrong' -A3 "$log" | head -30
fi
exit "$code"
