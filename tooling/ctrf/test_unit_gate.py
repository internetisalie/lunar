"""MAINT-50: unit_gate.sh, against a fake builder and a fake rsync in a throwaway repository."""
import json
import os
import shutil
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

GATE = Path(__file__).resolve().parent / "unit_gate.sh"
XML = '<testsuite><testcase name="t" classname="p.A" time="0.1"/></testsuite>'
SIDECAR = {"schema": 1, "excludePatterns": [], "includePatterns": [], "commandLineIncludePatterns": []}
GREEN = "Running on x\nBUILD SUCCESSFUL in 1s\n"
RED_LOG = "Running on x\n> There were failing tests. See the report at: file:///x\nBUILD FAILED in 1s\n"
COMPILE = "Running on x\nExecution failed for task ':compileTestKotlin' (registered by plugin 'k').\nBUILD FAILED in 2s\n"
CRASH = ("Running on x\nExecution failed for task ':test' (registered by plugin 'g').\n"
         "> Test process encountered an unexpected problem.\nBUILD FAILED in 23s\n")
KILLED = "Running on x\n> Task :test\n"
TRANSPORT = "rsync error: unexplained error (code 255) at io.c(232) [sender=3.4.1]\n"
VIOLATION_LOG = "/w/a.kt:8:48: Unnecessary long whitespace (standard:no-multi-spaces)\nBUILD FAILED in 18s\n"

FAKE_BUILDER = """#!/bin/bash
if [ "$1" = shell ]; then
  n=$(cat "$F/polls" 2>/dev/null || echo 0); echo $((n + 1)) > "$F/polls"
  [ -e "$F/shell-dead" ] && exit 255
  if [ "$n" -lt "$(cat "$F/busy-polls")" ]; then echo "99 BUSY 9.7.1"; else cat "$F/status"; fi
  exit 0
fi
echo "$*" >> "$F/runs"
[ -e /proc/self/fd/9 ] && echo inherited >> "$F/fd9"
flock -n "$HOME/.cache/claude-scratch/lunar/builder-libvirt.lock" true && echo free >> "$F/lock-run"
n=$(cat "$F/run-count" 2>/dev/null || echo 0); echo $((n + 1)) > "$F/run-count"
cat "$F/log$n" 2>/dev/null || cat "$F/log"
"""

FAKE_RSYNC = """#!/bin/bash
flock -n "$HOME/.cache/claude-scratch/lunar/builder-libvirt.lock" true && echo free >> "$F/lock-copy"
src="${@: -2:1}"; dst="${@: -1}"
[ -e "$F/rsync-dead" ] && [ -d "$src" ] && exit 255
[ -e "$F/rsync-dead-file" ] && [ -f "$src" ] && exit 255
if [ -d "$src" ]; then rm -rf "$dst"; cp -r "$src" "$dst"; else cp "$src" "$dst"; fi
"""


def write_tool(path: Path, text: str) -> None:
    path.write_text(text)
    path.chmod(path.stat().st_mode | stat.S_IEXEC)


def document(tests: dict[str, str]) -> dict:
    return {"results": {"tool": {"name": "lunar-unit-tests"}, "environment": {"extra": {"filter": SIDECAR}},
                        "tests": [{"testId": i, "name": i, "suite": ["p.A"], "status": s} for i, s in tests.items()]}}


class Gate(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)
        self.fake = self.root / "fake"
        self.repo = self.root / "repo"
        self.bin = self.root / "bin"
        for path in (self.fake, self.repo, self.bin, self.root / ".cache/claude-scratch/lunar"):
            path.mkdir(parents=True)
        write_tool(self.bin / "rsync", FAKE_RSYNC)
        write_tool(self.fake / "builder", FAKE_BUILDER)
        (self.fake / "log").write_text(GREEN)
        (self.fake / "busy-polls").write_text("0")
        (self.fake / "status").write_text("No Gradle daemons are running.\n")
        self.remote = self.root / "remote"
        (self.remote / "build/test-results/test").mkdir(parents=True)
        (self.remote / "build/test-results/test/TEST-p.A.xml").write_text(XML)
        (self.remote / "build/test-results/test-filter.json").write_text(json.dumps(SIDECAR))
        self.git("init", "-q")
        self.git("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "c1")
        self.git("update-ref", "refs/remotes/origin/main", "HEAD")
        self.d = self.root / "d"
        self.d.mkdir()
        self.planned = self.write("planned.txt", "")

    def git(self, *args) -> str:
        return subprocess.run(["git", *args], cwd=self.repo, check=True, capture_output=True, text=True).stdout.strip()

    def write(self, name: str, text: str) -> Path:
        path = self.root / name
        path.write_text(text)
        return path

    def gate(self, *args, **env) -> tuple[int, str]:
        full = dict(os.environ, HOME=str(self.root), PATH=f"{self.bin}:{os.environ['PATH']}", F=str(self.fake),
                    GATE_BUILDER=str(self.fake / "builder"), BLD=str(self.remote), RSH="unused", GATE_WAIT_POLL="0")
        full.update(env)
        done = subprocess.run([str(GATE), *map(str, args)], cwd=self.repo, env=full, capture_output=True, text=True)
        return done.returncode, done.stdout + done.stderr

    def lines(self, *body: str) -> Path:
        return self.write("diff.txt", "".join(f"{line}\n" for line in body) + "EXECUTED\t1\t1\n")

    def documents(self, before: dict, after: dict) -> tuple[Path, Path]:
        before_path = self.write("before.ctrf.json", json.dumps(before))
        after_path = self.write("after.ctrf.json", json.dumps(after))
        self.write("after.head", self.git("rev-parse", "HEAD") + "\n")
        return before_path, after_path

    def test_tc03_class_markers_in_order(self):
        cases = {GREEN: "green", RED_LOG: "red", COMPILE: "infra-compile", CRASH: "infra-other", KILLED: "infra-other",
                 TRANSPORT: "infra-transport", "": "infra-transport"}
        for text, expected in cases.items():
            self.assertEqual((0, expected + "\n"), self.gate("class", self.write("log", text)), text)

    def test_tc04_failure_lines_fail(self):
        for line in ("NEWLY_FAILING\tp.A.t\tboom", "ADDED\tfailed\tp.A.t", "OUT_OF_SET\tafter\tfailed\tp.A.t"):
            code, out = self.gate("verdict", self.planned, self.lines(line))
            self.assertEqual(1, code, line)
            self.assertIn(line, out)
        self.assertEqual(0, self.gate("verdict", self.planned, self.lines("ADDED\tpassed\tp.A.t", "NEWLY_PASSING\tp.A.u"))[0])

    def test_tc05_loss_lines_fail_unless_planned(self):
        for line in ("REMOVED\tpassed\tp.A.t", "NEWLY_SKIPPED\tp.A.t", "STATUS\tfailed->skipped\tp.A.t",
                     "OUT_OF_SET\tbefore\tpassed\tp.A.t"):
            self.assertEqual(1, self.gate("verdict", self.planned, self.lines(line))[0], line)
        self.assertEqual(0, self.gate("verdict", self.planned, self.lines("OUT_OF_SET\tafter\tpassed\tp.A.t"))[0])

    def test_tc06_planned_removals_accumulate(self):
        diff = self.lines("NEWLY_SKIPPED\tp.A.keep", "REMOVED\tpassed\tp.B.one", "REMOVED\tpassed\tp.C.two")
        first = self.write("p1", "p.A.keep\np.B.\n")
        both = self.write("p2", "p.A.keep\np.B.\np.C.two\n")
        near = self.write("p3", "p.A.kee\np.B.\np.C.two\n")
        inner = self.write("p4", "p.A.keep\nB.\np.C.two\n")
        code, out = self.gate("verdict", first, diff)
        self.assertEqual(1, code)
        self.assertIn("p.C.two", out)
        self.assertNotIn("p.B.one", out)
        self.assertEqual(0, self.gate("verdict", both, diff)[0])
        code, out = self.gate("verdict", near, diff)
        self.assertEqual(1, code)
        self.assertIn("p.A.keep", out)
        code, out = self.gate("verdict", inner, diff)
        self.assertEqual(1, code)
        self.assertIn("p.B.one", out)

    def test_tc07_judge_refuses_what_diff_cannot_read(self):
        good = {"p.A.t": "passed"}
        before, after = self.documents(document(good), document(good))
        self.assertEqual(0, self.gate("judge", before, after, self.planned, self.d / "diff.txt")[0])
        after.write_text(after.read_text()[:40])
        self.assertEqual(2, self.gate("judge", before, after, self.planned, self.d / "diff.txt")[0])
        doc = document(good)
        del doc["results"]["environment"]
        after.write_text(json.dumps(doc))
        self.assertEqual(2, self.gate("judge", before, after, self.planned, self.d / "diff.txt")[0])
        after.unlink()
        self.assertEqual(2, self.gate("judge", before, after, self.planned, self.d / "diff.txt")[0])

    def test_tc07_zero_tests_and_missing_list_are_refused(self):
        before, after = self.documents(document({}), document({}))
        self.assertEqual(2, self.gate("judge", before, after, self.planned, self.d / "diff.txt")[0])
        before, after = self.documents(document({"p.A.t": "passed"}), document({"p.A.t": "passed"}))
        code, out = self.gate("judge", before, after, self.root / "nope", self.d / "diff.txt")
        self.assertEqual(2, code)
        self.assertIn("no file", out)

    def test_tc08_judge_refuses_a_stale_after(self):
        before, after = self.documents(document({"p.A.t": "passed"}), document({"p.A.t": "passed"}))
        self.git("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "c2")
        code, out = self.gate("judge", before, after, self.planned, self.d / "diff.txt")
        self.assertEqual(2, code)
        self.assertIn("not HEAD", out)

    def test_tc09_green_and_red_write_the_document_and_head(self):
        self.assertEqual(0, self.gate("suite", "libvirt", self.d, "before")[0])
        doc = json.loads((self.d / "before.ctrf.json").read_text())
        self.assertEqual("lunar-unit-tests", doc["results"]["tool"]["name"])
        self.assertEqual(self.git("rev-parse", "HEAD"), (self.d / "before.head").read_text().strip())
        (self.fake / "log1").write_text(RED_LOG)
        self.assertEqual(10, self.gate("suite", "libvirt", self.d, "before")[0])
        self.assertTrue((self.d / "before.ctrf.json").exists())

    def test_tc09_gradle_arguments(self):
        self.gate("suite", "libvirt", self.d, "before", "--tests", "*X*")
        self.assertEqual("run test --rerun --no-build-cache --tests *X*\n", (self.fake / "runs").read_text())

    def test_tc10_no_older_document_survives_a_failed_step(self):
        def stale(log: str):
            (self.d / "before.ctrf.json").write_text("old")
            (self.fake / "log").write_text(log)
            (self.fake / "run-count").write_text("99")
            code, _ = self.gate("suite", "libvirt", self.d, "before")
            self.assertEqual(20, code, log)
            self.assertFalse((self.d / "before.ctrf.json").exists(), log)
        stale(COMPILE)
        stale(TRANSPORT)
        stale(KILLED)
        (self.fake / "log").write_text(GREEN)
        self.assertEqual(0, self.gate("suite", "libvirt", self.d, "before")[0])
        (self.fake / "rsync-dead").touch()
        stale(GREEN)
        (self.fake / "rsync-dead").unlink()
        (self.fake / "rsync-dead-file").touch()
        stale(GREEN)
        (self.fake / "rsync-dead-file").unlink()
        (self.remote / "build/test-results/test/TEST-p.A.xml").unlink()
        stale(GREEN)

    def test_tc11_lock_covers_run_and_copy_back_and_is_not_inherited(self):
        self.gate("suite", "libvirt", self.d, "before")
        self.assertTrue((self.fake / "runs").exists())
        self.assertFalse((self.fake / "lock-run").exists())
        self.assertFalse((self.fake / "lock-copy").exists())
        self.assertFalse((self.fake / "fd9").exists())

    def test_tc12_wait(self):
        self.assertEqual(0, self.gate("wait", "libvirt")[0])
        (self.fake / "polls").write_text("0")
        (self.fake / "busy-polls").write_text("2")
        self.assertEqual(0, self.gate("wait", "libvirt")[0])
        self.assertEqual("3", (self.fake / "polls").read_text().strip())

    def test_tc12_wait_stops_on_a_dead_builder_or_strange_output_or_timeout(self):
        (self.fake / "shell-dead").touch()
        self.assertEqual(20, self.gate("wait", "libvirt")[0])
        (self.fake / "shell-dead").unlink()
        (self.fake / "status").write_text("something else\n")
        self.assertEqual(20, self.gate("wait", "libvirt")[0])
        (self.fake / "polls").write_text("0")
        (self.fake / "busy-polls").write_text("9999999")
        self.assertEqual(20, self.gate("wait", "libvirt", GATE_WAIT_LIMIT="0")[0])

    def test_tc13_count(self):
        (self.fake / "log0").write_text("w: file:///a.kt:1:1 x\nw: file:///b.kt:1:1 y\nBUILD SUCCESSFUL in 1s\n")
        (self.fake / "log1").write_text(GREEN)
        code, out = self.gate("count", "libvirt", self.d)
        self.assertEqual((0, "warnings=2\nviolations=0\n"), (code, out))
        self.assertIn("--rerun-tasks --no-build-cache", (self.fake / "runs").read_text().splitlines()[0])
        self.assertIn("integrationTestClasses redisIntegrationTestClasses", (self.fake / "runs").read_text())

    def test_tc13_count_stops_on_a_test_task(self):
        (self.fake / "log0").write_text("> Task :test\nBUILD SUCCESSFUL in 1s\n")
        (self.fake / "log1").write_text(GREEN)
        self.assertEqual(20, self.gate("count", "libvirt", self.d)[0])

    def test_tc14_integration_is_plain_gradle_success(self):
        self.assertEqual(0, self.gate("integration", "libvirt", self.d)[0])
        self.assertEqual("run integrationTest --rerun --no-build-cache\n", (self.fake / "runs").read_text())
        for log in (RED_LOG, KILLED, TRANSPORT):
            (self.fake / "log").write_text(log)
            (self.fake / "run-count").write_text("99")
            self.assertEqual(20, self.gate("integration", "libvirt", self.d)[0], log)

    def test_tc15_ktlint(self):
        (self.fake / "log1").write_text(VIOLATION_LOG)
        (self.fake / "log0").write_text(GREEN)
        code, out = self.gate("count", "libvirt", self.d)
        self.assertEqual((0, "warnings=0\nviolations=1\n"), (code, out))
        (self.fake / "run-count").write_text("0")
        (self.fake / "log1").write_text("BUILD FAILED in 18s\n")
        self.assertEqual(20, self.gate("count", "libvirt", self.d)[0])

    def test_tc16_dispute_lookup_is_by_test_name(self):
        path = self.write("class.ctrf.json", json.dumps(document({"p.A.other": "failed", "p.A.disputed": "passed"})))
        code, out = self.gate("failures", path)
        self.assertEqual((0, "p.A.other\n"), (code, out))
        self.assertNotIn("p.A.disputed", out.splitlines())

    def test_tc17_fresh_baseline_follows_the_merge_base(self):
        base = self.root / "base"
        base.mkdir()
        self.assertEqual(1, self.gate("fresh", base)[0])
        (base / "base_commit").write_text(self.git("merge-base", "HEAD", "origin/main") + "\n")
        self.assertEqual(1, self.gate("fresh", base)[0])
        (base / "before.ctrf.json").write_text("{}")
        self.assertEqual(0, self.gate("fresh", base)[0])
        self.git("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "c2")
        self.git("update-ref", "refs/remotes/origin/main", "HEAD")
        self.assertEqual(1, self.gate("fresh", base)[0])

    def test_tc18_refusals_have_one_exit_code(self):
        self.assertEqual(2, self.gate()[0])
        self.assertEqual(2, self.gate("nonsense")[0])
        self.assertEqual(2, self.gate("suite", "libvirt")[0])
        self.assertEqual(2, self.gate("suite", "libvirt", self.d, "x", RSH="")[0])
        self.assertEqual(2, self.gate("suite", "libvirt", self.d, "x", BLD="")[0])
        self.assertEqual(2, self.gate("failures", self.root / "nope")[0])
        self.assertEqual(2, self.gate("class", self.root / "nope")[0])

    def test_tc23_suite_refuses_a_dirty_tree(self):
        def refused():
            code, out = self.gate("suite", "libvirt", self.d, "after")
            self.assertEqual(2, code)
            self.assertIn("REFUSED:", out)
            self.assertFalse((self.fake / "runs").exists())
        tracked = self.repo / "tracked"
        tracked.write_text("one\n")
        (self.repo / ".gitignore").write_text("ignored\n")
        self.git("add", ".")
        self.git("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", "c2")
        tracked.write_text("two\n")
        refused()
        self.git("add", ".")
        refused()
        self.git("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", "c3")
        (self.repo / "untracked").write_text("x\n")
        refused()
        (self.repo / "untracked").unlink()
        (self.repo / "ignored").write_text("x\n")
        self.assertEqual(0, self.gate("suite", "libvirt", self.d, "after")[0])


if __name__ == "__main__":
    unittest.main()
