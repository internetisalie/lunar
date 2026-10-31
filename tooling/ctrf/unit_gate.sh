#!/usr/bin/env bash
# MAINT-50: the supervise-implement gate's builder steps. Run from the root of a lunar checkout.
# Exit codes: 0 success or PASS; 1 FAIL; 2 refused (usage, a missing input, not comparable);
# 10 red; 20 stop. See docs/features/maint/50-supervise-implement-per-test-gate/design.md.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CTRF="$HERE/junit_ctrf.py"
BUILDER="${GATE_BUILDER:-tooling/gce-builder/gce-builder.sh}"
STATUS_COMMAND='cd lunar && JAVA_HOME=/opt/jdk GRADLE_USER_HOME=/opt/cache/gradle ./gradlew --status'
WARNING_TASKS="assemble testClasses integrationTestClasses redisIntegrationTestClasses lintDocs"
TEST_TASK='^> Task :(test|integrationTest|redisIntegrationTest)( |$)'
VIOLATION='^/[^ ]*\.kts?:[0-9]+:[0-9]+: '
OK_SUCCESS='^BUILD SUCCESSFUL'
REFUSED=2
RED=10
STOP=20

refuse() { echo "REFUSED: $1"; exit "$REFUSED"; }
stop() { echo "STOP: $1"; exit "$STOP"; }
need_args() { [ "$1" -ge "$2" ] || refuse "expected at least $2 arguments, got $1"; }
need_file() { [ -f "$1" ] || refuse "no file $1"; }

lock() {
  exec 9>"$HOME/.cache/claude-scratch/lunar/builder-$1.lock"
  flock 9
}

gradle() {
  local backend="$1" log="$2"
  shift 2
  GCE_BUILDER_BACKEND="$backend" "$BUILDER" run "$@" > "$log" 2>&1 9>&- || true
}

class() {
  need_args $# 1
  need_file "$1"
  if grep -q "$OK_SUCCESS" "$1"; then echo green
  elif grep -q '^> There were failing tests' "$1"; then echo red
  elif ! grep -q 'Running on ' "$1"; then echo infra-transport
  elif grep -q "^Execution failed for task ':compile" "$1"; then echo infra-compile
  else echo infra-other
  fi
}

suite() {
  need_args $# 3
  local backend="$1" dir="$2" name="$3" verdict
  shift 3
  [ -n "${BLD:-}" ] || refuse "BLD is not set"
  [ -n "${RSH:-}" ] || refuse "RSH is not set"
  [ -z "$(git status --porcelain)" ] || refuse "the working tree is dirty; the builder would measure what no commit holds"
  mkdir -p "$dir/results"
  lock "$backend"
  rm -f "$dir/$name.ctrf.json" "$dir/$name.head" "$dir/test-filter.json" "$dir/run.log"
  git rev-parse HEAD > "$dir/$name.head"
  gradle "$backend" "$dir/run.log" test --rerun --no-build-cache "$@"
  verdict="$(class "$dir/run.log")"
  echo "class=$verdict"
  case "$verdict" in green|red) ;; *) exit "$STOP" ;; esac
  rsync -az --delete -e "$RSH" "$BLD"/build/test-results/test/ "$dir/results/" || exit "$STOP"
  rsync -az -e "$RSH" "$BLD"/build/test-results/test-filter.json "$dir/test-filter.json" || exit "$STOP"
  python3 "$CTRF" assemble --results "$dir/results" --filter "$dir/test-filter.json" \
    --out "$dir/$name.ctrf.json" || exit "$STOP"
  [ "$verdict" = green ] || exit "$RED"
}

verdict() {
  need_args $# 2
  local planned="$1" lines="$2" fails losses
  need_file "$planned"
  need_file "$lines"
  grep -qP '^EXECUTED\t[1-9]' "$lines" || refuse "diff printed no EXECUTED line with tests in it"
  fails="$(grep -P '^(NEWLY_FAILING\t|ADDED\tfailed\t|OUT_OF_SET\tafter\tfailed\t)' "$lines" || true)"
  losses="$(awk -F'\t' 'FILENAME==ARGV[1]{ok[$0];next}
    /^(REMOVED|NEWLY_SKIPPED|STATUS\t[a-z]+->skipped|OUT_OF_SET\tbefore)\t/{
      hit=($NF in ok);for(p in ok)if(p~/\.$/&&index($NF,p)==1)hit=1;if(!hit)print}' "$planned" "$lines")"
  if [ -z "$fails$losses" ]; then echo PASS; exit 0; fi
  printf 'FAIL\n%s\n%s\n' "$fails" "$losses"
  exit 1
}

judge() {
  need_args $# 4
  local before="$1" after="$2" planned="$3" output="$4" head="${2%.ctrf.json}.head" code=0
  need_file "$before"
  need_file "$after"
  need_file "$head"
  [ "$(cat "$head")" = "$(git rev-parse HEAD)" ] || refuse "$after was measured at $(cat "$head"), not HEAD"
  rm -f "$output"
  python3 "$CTRF" diff "$before" "$after" > "$output" || code=$?
  case "$code" in 0|1|4) ;; *) refuse "diff exit $code" ;; esac
  verdict "$planned" "$output"
}

count() {
  need_args $# 2
  local backend="$1" dir="$2"
  mkdir -p "$dir"
  lock "$backend"
  rm -f "$dir/warnings.log" "$dir/ktlint.log"
  # shellcheck disable=SC2086
  gradle "$backend" "$dir/warnings.log" $WARNING_TASKS --rerun-tasks --no-build-cache
  gradle "$backend" "$dir/ktlint.log" ktlintCheck
  grep -q "$OK_SUCCESS" "$dir/warnings.log" || stop "$dir/warnings.log"
  if grep -qE "$TEST_TASK" "$dir/warnings.log"; then stop "a test task ran"; fi
  grep -q "$OK_SUCCESS" "$dir/ktlint.log" || grep -qE "$VIOLATION" "$dir/ktlint.log" || stop "$dir/ktlint.log"
  echo "warnings=$(grep -c '^w: ' "$dir/warnings.log" || true)"
  echo "violations=$(grep -cE "$VIOLATION" "$dir/ktlint.log" || true)"
}

integration() {
  need_args $# 2
  local backend="$1" dir="$2"
  mkdir -p "$dir"
  lock "$backend"
  rm -f "$dir/integration.log"
  # --rerun binds to the task before it, so each task needs its own.
  gradle "$backend" "$dir/integration.log" integrationTest --rerun redisIntegrationTest --rerun --no-build-cache
  grep -q "$OK_SUCCESS" "$dir/integration.log" || stop "$dir/integration.log"
}

failures() {
  need_args $# 1
  need_file "$1"
  python3 -c 'import json, sys
for test in json.load(open(sys.argv[1]))["results"]["tests"]:
    if test["status"] == "failed":
        print(test["testId"])' "$1"
}

fresh() {
  need_args $# 1
  [ -f "$1/before.ctrf.json" ] && [ -f "$1/base_commit" ] \
    && [ "$(cat "$1/base_commit")" = "$(git merge-base HEAD origin/main)" ]
}

wait_for_vm() {
  need_args $# 1
  local backend="$1" deadline=$((SECONDS + ${GATE_WAIT_LIMIT:-1500})) status
  while :; do
    status="$(GCE_BUILDER_BACKEND="$backend" "$BUILDER" shell "$STATUS_COMMAND" 2>&1)" || stop "builder unreachable"
    case "$status" in
      *BUSY*) ;;
      *IDLE*|*STOPPED*|*"No Gradle daemons are running"*) return 0 ;;
      *) stop "unrecognised gradle --status output" ;;
    esac
    [ "$SECONDS" -lt "$deadline" ] || stop "a Gradle build is still running"
    sleep "${GATE_WAIT_POLL:-20}"
  done
}

need_args $# 1
command="$1"
shift
case "$command" in
  suite) suite "$@" ;;
  judge) judge "$@" ;;
  verdict) verdict "$@" ;;
  class) class "$@" ;;
  count) count "$@" ;;
  integration) integration "$@" ;;
  failures) failures "$@" ;;
  fresh) fresh "$@" ;;
  wait) wait_for_vm "$@" ;;
  *) refuse "unknown command $command" ;;
esac
