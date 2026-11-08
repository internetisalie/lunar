#!/usr/bin/env python3
# Each mutant of unit_gate.sh must turn test_unit_gate red.
import shutil, subprocess, sys, tempfile
from pathlib import Path
SRC = Path(__file__).resolve().parent
TEXT = (SRC / "unit_gate.sh").read_text()
M = [
 ("red marker task name", "'^> There were failing tests'", "\"^Execution failed for task ':test'\""),
 ("red marker BUILD FAILED", "'^> There were failing tests'", "'^BUILD FAILED'"),
 ("no rm of document", 'rm -f "$dir/$name.ctrf.json" "$dir/$name.head"', 'rm -f "$dir/$name.head"'),
 ("results rsync ignored", '"$dir/results/" || exit "$STOP"', '"$dir/results/" || true'),
 ("assemble ignored", '--out "$dir/$name.ctrf.json" || exit "$STOP"', '--out "$dir/$name.ctrf.json" || true'),
 ("no lock in suite", 'mkdir -p "$dir/results"\n  lock "$backend"', 'mkdir -p "$dir/results"'),
 ("fd inherited", ' 2>&1 9>&- || true', ' 2>&1 || true'),
 ("lock freed before copy", '  rsync -az --delete', '  flock -u 9\n  rsync -az --delete'),
 ("no EXECUTED check", "  grep -qP '^EXECUTED\\t[1-9]' \"$lines\" || refuse \"diff printed no EXECUTED line with tests in it\"\n", ""),
 ("zero tests allowed", "EXECUTED\\t[1-9]", "EXECUTED\\t[0-9]"),
 ("head not checked", '  [ "$(cat "$head")" = "$(git rev-parse HEAD)" ] || refuse "$after was measured at $(cat "$head"), not HEAD"\n', ""),
 ("no NEWLY_FAILING", "NEWLY_FAILING\\t|", ""),
 ("no ADDED failed", "ADDED\\tfailed\\t|", ""),
 ("no OUT_OF_SET after failed", "|OUT_OF_SET\\tafter\\tfailed\\t", ""),
 ("no REMOVED", "/^(REMOVED|", "/^("),
 ("no NEWLY_SKIPPED", "|NEWLY_SKIPPED|", "|"),
 ("no STATUS", "|STATUS\\t[a-z]+->skipped|", "|"),
 ("OUT_OF_SET either side", "|OUT_OF_SET\\tbefore)", "|OUT_OF_SET)"),
 ("substring match", "index($NF,p)==1", "index($NF,p)>0"),
 ("prefix without dot", 'p~/\\.$/&&', ''),
 ("cold flag dropped", ' --rerun-tasks --no-build-cache', ''),
 ("integration set dropped", 'testClasses integrationTestClasses', 'testClasses'),
 ("test task allowed", '  if grep -qE "$TEST_TASK" "$dir/warnings.log"; then stop "a test task ran"; fi\n', ''),
 ("ktlint broken tolerated", ' || grep -qE "$VIOLATION" "$dir/ktlint.log" || stop "$dir/ktlint.log"', ' || true'),
 ("integration without rerun", 'integrationTest --rerun redisIntegrationTest --rerun --no-build-cache', 'integrationTest'),
 ("integration red tolerated", 'grep -q "$OK_SUCCESS" "$dir/integration.log" || stop "$dir/integration.log"', 'true'),
 ("suite without rerun", 'test --rerun --no-build-cache "$@"', 'test "$@"'),
 ("dead builder reads idle", '2>&1)" || stop "builder unreachable"', '2>&1)" || return 0'),
 ("strange status reads idle", '*) stop "unrecognised gradle --status output" ;;', '*) return 0 ;;'),
 ("busy ignored", '      *BUSY*) ;;\n', ''),
 ("deadline dropped", '[ "$SECONDS" -lt "$deadline" ] || stop "a Gradle build is still running"', 'true'),
 ("fresh ignores merge-base", ' \\\n    && [ "$(cat "$1/base_commit")" = "$(git merge-base HEAD origin/main)" ]', ''),
 ("fresh ignores record", '[ -f "$1/before.ctrf.json" ] && ', ''),
 ("RSH not required", '  [ -n "${RSH:-}" ] || refuse "RSH is not set"\n', ''),
 ("BLD not required", '  [ -n "${BLD:-}" ] || refuse "BLD is not set"\n', ''),
 ("missing list not refused", '  need_file "$planned"\n', ''),
 ("head not recorded", '  git rev-parse HEAD > "$dir/$name.head"\n', ''),
 ("failures lists everything", 'if test["status"] == "failed":', 'if True:'),
 ("dirty tree allowed", '  [ -z "$(git status --porcelain)" ] || refuse "the working tree is dirty; the builder would measure what no commit holds"\n', ''),
 ("untracked files allowed", 'git status --porcelain', 'git status --porcelain --untracked-files=no'),
 ("ignored files counted", 'git status --porcelain', 'git status --porcelain --ignored'),
 ("class missing log not refused", 'need_args $# 1\n  need_file "$1"\n  if grep', 'need_args $# 1\n  if grep'),
]
bad = 0
for name, old, new in M:
    if old not in TEXT:
        print("NOT APPLIED", name); bad += 1; continue
    with tempfile.TemporaryDirectory(dir=SRC) as t:
        d = Path(t)
        for f in ("junit_ctrf.py", "loom_store.py", "test_unit_gate.py"):
            shutil.copy(SRC / f, d)
        (d / "unit_gate.sh").write_text(TEXT.replace(old, new, 1)); (d / "unit_gate.sh").chmod(0o755)
        try:
            r = subprocess.run([sys.executable, "-m", "unittest", "test_unit_gate"], cwd=d, capture_output=True, text=True, timeout=60)
            red = r.returncode != 0
            names = [l.split()[1] for l in r.stderr.splitlines() if l.startswith(("FAIL:", "ERROR:"))]
        except subprocess.TimeoutExpired:
            red, names = True, ["timeout"]
    print(("RED  " if red else "SURVIVED ") + name, ",".join(n.replace("test_", "") for n in names)[:80])
    bad += not red
print("bad", bad, "of", len(M))
