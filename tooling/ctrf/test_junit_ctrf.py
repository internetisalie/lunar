"""MAINT-48 TC 2-8: junit_ctrf.py."""
import contextlib
import copy
import hashlib
import io
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import junit_ctrf
import loom_store
from junit_ctrf import read_filter, test_set_identity
from loom_stub import STUB

FILTER = {"schema": 1, "excludePatterns": ["*ParseOracleTest", "*Benchmark*", "*Performance*"],
          "includePatterns": [], "commandLineIncludePatterns": []}
AFTER_FILTER = dict(FILTER, excludePatterns=["*Benchmark*", "*Performance*"])
REVERSED_FILTER = dict(FILTER, excludePatterns=list(reversed(FILTER["excludePatterns"])))

TC2_FILES = {
    "TEST-p.C.xml": [
        '<testcase name="testA" classname="p.C" time="0.012"/>',
        '<testcase name="testB" classname="p.C" time="0.003">'
        '<failure message="expected:&lt;a&gt; but was:&lt;b&gt;">junit.framework.ComparisonFailure</failure>'
        '</testcase>',
        '<testcase name="testC" classname="p.C" time="0.0"><skipped/></testcase>',
        f'<testcase name="testL" classname="p.C" time="0.001"><failure message="{"x" * 1500}"/></testcase>',
    ],
    "TEST-p.D.xml": [
        '<testcase name="testE" classname="p.D" time="1.5"><error message="">'
        'java.lang.IllegalStateException: boom\n\tat p.D.testE(D.kt:3)</error></testcase>',
    ],
}

ALLOWED = {
    "": {"reportFormat", "specVersion", "results"},
    "results": {"tool", "summary", "tests", "environment"},
    "results.tool": {"name"},
    "results.summary": {"tests", "passed", "failed", "skipped", "pending", "other", "start", "stop"},
    "results.environment": {"testEnvironment", "extra"},
    "results.tests[]": {"name", "testId", "status", "duration", "suite", "message", "extra", "rawStatus"},
}


def write_xml(directory: Path, files: dict[str, list[str]], reverse: bool = False) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    for name, cases in files.items():
        ordered = list(reversed(cases)) if reverse else cases
        body = "".join(ordered)
        (directory / name).write_text(f'<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="{name}">{body}'
                                      f'<system-out><![CDATA[]]></system-out></testsuite>\n', encoding="utf-8")
    return directory


def write_filter(path: Path, doc: dict) -> Path:
    path.write_text(json.dumps(doc), encoding="utf-8")
    return path


def keys_outside_extra(value, path=""):
    """(path, key) for every object key that is not inside an `extra` object."""
    found = []
    if isinstance(value, dict):
        for key, child in value.items():
            found.append((path, key))
            if key != "extra":
                found.extend(keys_outside_extra(child, f"{path}.{key}" if path else key))
    elif isinstance(value, list):
        for child in value:
            found.extend(keys_outside_extra(child, f"{path}[]"))
    return found


def quietly(argv: list[str]) -> tuple[int, str, str]:
    stdout, stderr = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
        code = junit_ctrf.main(argv)
    return code, stdout.getvalue(), stderr.getvalue()


class Workspace(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.filter = write_filter(self.tmp / "test-filter.json", FILTER)

    def assemble(self, results: Path, name: str, sidecar: Path | None = None) -> tuple[int, bytes, str]:
        out = self.tmp / "out" / f"{name}.json"
        code, _, refused = quietly(["assemble", "--results", str(results), "--filter", str(sidecar or self.filter),
                                    "--out", str(out)])
        return code, out.read_bytes() if code == 0 else b"", refused


class AssembleTest(Workspace):
    def test_TC_2_document_is_deterministic_canonical_ctrf(self):
        code_a, out_a, _ = self.assemble(write_xml(self.tmp / "a", TC2_FILES), "a")
        code_b, out_b, _ = self.assemble(write_xml(self.tmp / "b", TC2_FILES, reverse=True), "b")
        self.assertEqual((0, 0), (code_a, code_b))
        self.assertEqual(out_a, out_b)
        canonical = json.dumps(json.loads(out_a), sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        self.assertEqual(canonical.encode() + b"\n", out_a)
        doc = json.loads(out_a)
        self.assertEqual(("CTRF", "0.2.0"), (doc["reportFormat"], doc["specVersion"]))
        self.assertEqual("lunar-unit-tests", doc["results"]["tool"]["name"])
        for path, key in keys_outside_extra(doc):
            self.assertIn(key, ALLOWED[path], f"{key!r} at {path!r}")

    def test_TC_2_status_duration_and_message(self):
        _, out, _ = self.assemble(write_xml(self.tmp / "a", TC2_FILES), "a")
        tests = {test["testId"]: test for test in json.loads(out)["results"]["tests"]}
        ids = ["p.C.testA", "p.C.testB", "p.C.testC", "p.C.testL", "p.D.testE"]
        self.assertEqual(ids, list(tests))
        self.assertEqual(["passed", "failed", "skipped", "failed", "failed"], [tests[i]["status"] for i in ids])
        self.assertEqual([None, "failure", "skipped", "failure", "error"], [tests[i].get("rawStatus") for i in ids])
        self.assertEqual((12, 1500), (tests["p.C.testA"]["duration"], tests["p.D.testE"]["duration"]))
        self.assertEqual("expected:<a> but was:<b>", tests["p.C.testB"]["message"])
        self.assertEqual("java.lang.IllegalStateException: boom", tests["p.D.testE"]["message"])
        self.assertEqual("x" * 1000, tests["p.C.testL"]["message"])
        self.assertNotIn("message", tests["p.C.testA"])
        self.assertEqual(["p.D"], tests["p.D.testE"]["suite"])

    def test_TC_13_multi_line_message_is_one_line(self):
        multi = ('<testcase name="testM" classname="p.C" time="0.001"><failure message="junit.framework.'
                 'ComparisonFailure: expected:&lt;[a&#10;  b]&gt; but was:&lt;[a&#13;&#10;&#10;c]&gt;"/></testcase>')
        _, out, _ = self.assemble(write_xml(self.tmp / "m", one_file(multi)), "m")
        message = json.loads(out)["results"]["tests"][0]["message"]
        self.assertEqual("junit.framework.ComparisonFailure: expected:<[a b]> but was:<[a c]>", message)

    def test_TC_2_summary(self):
        _, out, _ = self.assemble(write_xml(self.tmp / "a", TC2_FILES), "a")
        self.assertEqual({"tests": 5, "passed": 1, "failed": 3, "skipped": 1, "pending": 0, "other": 0, "start": 0,
                          "stop": 0}, json.loads(out)["results"]["summary"])


TESTX_FAILED = '<testcase name="testX" classname="p.C" time="0.002"><failure message="m"/></testcase>'
TESTX_PASSED = '<testcase name="testX" classname="p.C" time="0.001"/>'
BRACKETED = '<testcase name="test[1]" classname="p.C" time="0.001"/>'


def one_file(*cases: str) -> dict[str, list[str]]:
    return {"TEST-a.xml": list(cases)}


class IdentityTest(Workspace):
    def by_case(self, out: bytes) -> list[tuple[str, str, str]]:
        return [(test["testId"], test["name"], test["status"]) for test in json.loads(out)["results"]["tests"]]

    def test_TC_3_ordinals_follow_the_cases_not_the_file(self):
        _, first, _ = self.assemble(write_xml(self.tmp / "a", one_file(TESTX_FAILED, TESTX_PASSED, BRACKETED)), "a")
        self.assertEqual([("p.C.testX", "p.C.testX", "passed"), ("p.C.testX#1", "p.C.testX", "failed"),
                          ("p.C.test[1]", "p.C.test[1]", "passed")], self.by_case(first))
        _, second, _ = self.assemble(write_xml(self.tmp / "b", one_file(BRACKETED, TESTX_PASSED, TESTX_FAILED)), "b")
        self.assertEqual(first, second)

    def test_TC_3_duration_does_not_move_an_id(self):
        slower = TESTX_PASSED.replace('time="0.001"', 'time="9.0"')
        _, first, _ = self.assemble(write_xml(self.tmp / "a", one_file(TESTX_FAILED, TESTX_PASSED, BRACKETED)), "a")
        _, third, _ = self.assemble(write_xml(self.tmp / "c", one_file(TESTX_FAILED, slower, BRACKETED)), "c")
        self.assertEqual(self.by_case(first), self.by_case(third))

    def test_TC_3_a_literal_ordinal_collision_is_refused(self):
        cases = ['<testcase name="testY" classname="p.C" time="0.001"/>',
                 '<testcase name="testY" classname="p.C" time="0.002"/>',
                 '<testcase name="testY#1" classname="p.C" time="0.001"/>']
        code, _, refused = self.assemble(write_xml(self.tmp / "d", one_file(*cases)), "d")
        self.assertEqual(2, code)
        self.assertIn("duplicate testId p.C.testY#1", refused)


class TestSetTest(Workspace):
    def test_TC_4_identity_is_derived_from_the_sidecar(self):
        s1 = read_filter(self.filter)
        s2 = read_filter(write_filter(self.tmp / "s2.json", AFTER_FILTER))
        reversed_s1 = write_filter(self.tmp / "r.json", REVERSED_FILTER)
        self.assertNotEqual(test_set_identity(s1), test_set_identity(s2))
        self.assertEqual(test_set_identity(s1), test_set_identity(read_filter(reversed_s1)))
        self.assertRegex(test_set_identity(s1), r"^set-[0-9a-f]{16}$")

    def test_TC_4_document_carries_the_sorted_filter(self):
        results = write_xml(self.tmp / "a", TC2_FILES)
        reversed_s1 = write_filter(self.tmp / "r.json", REVERSED_FILTER)
        _, out, _ = self.assemble(results, "r", reversed_s1)
        environment = json.loads(out)["results"]["environment"]
        self.assertEqual({"excludePatterns": ["*Benchmark*", "*ParseOracleTest", "*Performance*"],
                          "includePatterns": [], "commandLineIncludePatterns": []}, environment["extra"]["filter"])
        self.assertEqual(test_set_identity(read_filter(self.filter)), environment["testEnvironment"])
        _, from_s1, _ = self.assemble(results, "s1")
        self.assertEqual(from_s1, out)

    def test_TC_4_unknown_schema_and_missing_sidecar_are_refused(self):
        results = write_xml(self.tmp / "a", TC2_FILES)
        code, _, _ = self.assemble(results, "s", write_filter(self.tmp / "s.json", dict(FILTER, schema=2)))
        self.assertEqual(2, code)
        missing = self.tmp / "absent" / "test-filter.json"
        code, _, refused = self.assemble(results, "m", missing)
        self.assertEqual(2, code)
        self.assertIn(f"no test-filter.json at {missing}", refused)


class StubbedLoomTest(Workspace):
    """Runs junit_ctrf against the MAINT-40 design §4.3 stub loom-cli, selected through LOOM_CLI."""

    def setUp(self):
        super().setUp()
        stub = self.tmp / "loom-cli"
        stub.write_text(STUB, encoding="utf-8")
        stub.chmod(0o755)
        self.scenario = self.tmp / "scenario.json"
        patcher = mock.patch.object(loom_store, "LOOM_CLI", str(stub))
        patcher.start()
        self.addCleanup(patcher.stop)
        env = mock.patch.dict(os.environ, {"STUB_LOOM": str(self.scenario)})
        env.start()
        self.addCleanup(env.stop)

    def stage(self, scenario: dict):
        self.scenario.write_text(json.dumps(scenario), encoding="utf-8")

    def calls(self) -> list[list[str]]:
        path = Path(f"{self.scenario}.calls")
        if not path.exists():
            return []
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


def measured(key: str, sha256: str = "0" * 64) -> dict:
    return {"exit": 0, "json": {"commit": "x", "checks": [{"check": "a3", "status": "measured",
                                                          "record": {"key": key, "loom-sha256": sha256}}]}}


class StoreTest(StubbedLoomTest):
    def setUp(self):
        super().setUp()
        self.results = write_xml(self.tmp / "xml", TC2_FILES)
        self.out = self.tmp / "unit-ctrf" / "unit.ctrf.json"

    def store(self, results: Path | None = None, sidecar: Path | None = None) -> tuple[int, str]:
        code, stdout, stderr = quietly(["store", "--results", str(results or self.results), "--filter",
                                        str(sidecar or self.filter), "--out", str(self.out), "--repo", "."])
        return code, stdout + stderr

    def expected_sha256(self) -> str:
        filter_doc = read_filter(self.filter)
        return hashlib.sha256(junit_ctrf.ctrf_document(junit_ctrf.read_cases(self.results), filter_doc)).hexdigest()

    def puts(self) -> list[list[str]]:
        return [argv for argv in self.calls() if argv[1] == "put"]

    def test_TC_5_unmeasured_head_is_put_once_without_a_suite(self):
        self.stage({})
        code, printed = self.store()
        self.assertEqual(0, code, printed)
        self.assertEqual([["artifact", "put", "conformance", str(self.out), "--commit", "HEAD", "--check", "a3",
                           "--no-item", "--repo", "."]], self.puts())
        self.assertNotIn("--suite", self.calls()[0])

    def test_TC_5_identical_record_is_not_put_again(self):
        self.stage({"resolve": {"HEAD": measured("k", self.expected_sha256())}})
        code, printed = self.store()
        self.assertEqual((0, []), (code, self.puts()))
        self.assertIn("store: already stored, identical", printed)

    def test_TC_5_different_record_is_kept_with_a_warning(self):
        self.stage({"resolve": {"HEAD": measured("k", "f" * 64)}})
        code, printed = self.store()
        self.assertEqual((0, []), (code, self.puts()))
        self.assertIn("WARNING store: HEAD already has a different a3 unit-test record; kept the first", printed)

    def test_TC_5_failing_resolve_or_put_fails_the_store(self):
        self.stage({"resolve": {"HEAD": {"exit": 1}}})
        self.assertEqual(1, self.store()[0])
        self.stage({"put": {"exit": 1}})
        self.assertEqual(1, self.store()[0])

    def test_TC_5_partial_run_is_refused_before_any_loom_call(self):
        self.stage({})
        partial = write_filter(self.tmp / "partial.json", dict(FILTER, commandLineIncludePatterns=["*X*"]))
        code, printed = self.store(sidecar=partial)
        self.assertEqual((2, []), (code, self.calls()))
        self.assertIn("refusing to store a partial run: --tests *X*", printed)

    def test_TC_5_empty_results_are_refused_before_any_loom_call(self):
        self.stage({})
        empty = self.tmp / "empty"
        empty.mkdir()
        code, _ = self.store(results=empty)
        self.assertEqual((2, []), (code, self.calls()))


def git(repo: Path, *args: str) -> str:
    env = dict(os.environ, GIT_AUTHOR_NAME="t", GIT_AUTHOR_EMAIL="t@example.invalid", GIT_COMMITTER_NAME="t",
               GIT_COMMITTER_EMAIL="t@example.invalid", GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM="1")
    return subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True, text=True,
                          env=env).stdout.strip()


def commit_file(repo: Path, path: str, content: str) -> str:
    target = repo / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")
    git(repo, "add", path)
    git(repo, "commit", "-q", "-m", f"edit {path}")
    return git(repo, "rev-parse", "HEAD")


def unit_document(tests=(), tool: str = junit_ctrf.TOOL_NAME, filter_doc: dict | None = None) -> dict:
    lists = {key: (filter_doc or FILTER)[key] for key in junit_ctrf.FILTER_LISTS}
    return {"reportFormat": "CTRF", "specVersion": "0.2.0", "results": {
        "tool": {"name": tool}, "summary": {}, "tests": list(tests),
        "environment": {"testEnvironment": test_set_identity(lists), "extra": {"filter": lists}}}}


class FetchTest(StubbedLoomTest):
    def setUp(self):
        super().setUp()
        self.repo = self.tmp / "repo"
        self.repo.mkdir()
        git(self.repo, "init", "-q", "-b", "main")
        self.c1 = commit_file(self.repo, "src/a.kt", "one")
        self.c2 = commit_file(self.repo, "docs/x.md", "doc")
        self.c3 = commit_file(self.repo, "README.md", "readme")
        self.c4 = commit_file(self.repo, "src/a.kt", "two")
        unit, corpus = self.tmp / "unit.json", self.tmp / "corpus.json"
        unit.write_text(json.dumps(unit_document()), encoding="utf-8")
        corpus.write_text(json.dumps(unit_document(tool="lunar-corpus-sweep")), encoding="utf-8")
        self.stage({"resolve": {self.c1: measured("k-c1"), self.c2: measured("k-c2")},
                    "get": {"k-c1": str(unit), "k-c2": str(corpus)}})
        self.out = self.tmp / "missing-dir" / "before.ctrf.json"

    def fetch(self, commit: str, *extra: str) -> tuple[int, str]:
        argv = ["fetch", "--commit", commit, "--out", str(self.out), "--repo", str(self.repo), *extra]
        code, stdout, _ = quietly(argv)
        return code, stdout

    def test_TC_6_nearest_code_identical_ancestor_with_a_unit_record(self):
        code, printed = self.fetch(self.c3)
        self.assertEqual(0, code, printed)
        self.assertIn(f"fetched {self.c1} identity={test_set_identity(FILTER)} distance=2", printed)
        self.assertEqual(junit_ctrf.TOOL_NAME, json.loads(self.out.read_text())["results"]["tool"]["name"])
        self.assertFalse(Path(f"{self.out}.part").exists())

    def test_TC_6_an_ancestor_with_other_code_is_not_a_baseline(self):
        code, printed = self.fetch(self.c4)
        self.assertEqual(3, code)
        self.assertIn(f"no unit-test record for a code-identical first-parent ancestor of {self.c4} (searched 1)",
                      printed)

    def test_TC_6_depth_bound(self):
        self.assertEqual(3, self.fetch(self.c3, "--max-depth", "2")[0])

    def test_TC_14_a_move_from_code_to_docs_is_a_code_change(self):
        source = "\n".join(f"line {n}" for n in range(40))
        moved_from = commit_file(self.repo, "src/b.kt", source)
        git(self.repo, "mv", "src/b.kt", "docs/b.md")
        git(self.repo, "commit", "-q", "-m", "move src/b.kt to docs/b.md")
        moved = git(self.repo, "rev-parse", "HEAD")
        self.stage({"resolve": {moved_from: measured("k-c1")}, "get": {"k-c1": str(self.tmp / "unit.json")}})
        self.assertEqual(3, self.fetch(moved)[0])

    def test_TC_6_no_resolve_carries_a_suite(self):
        self.fetch(self.c3)
        resolves = [argv for argv in self.calls() if argv[1] == "resolve"]
        self.assertEqual(3, len(resolves))
        for argv in resolves:
            self.assertNotIn("--suite", argv)


def unit_test(test_id: str, status: str, message: str = "") -> dict:
    classname = test_id.rsplit(".", 1)[0]
    test = {"name": test_id, "testId": test_id, "status": status, "duration": 1, "suite": [classname]}
    if message:
        test["message"] = message
    return test


def before_doc() -> dict:
    return unit_document([unit_test("p.A.t1", "passed"), unit_test("p.A.t2", "failed", "was broken"),
                          unit_test("p.A.t3", "passed"), unit_test("p.A.t4", "passed"),
                          unit_test("p.S.t5", "skipped")])


def after_doc(t1: str = "failed", tx: str = "passed", t3: str = "passed") -> dict:
    return unit_document([unit_test("p.A.t1", t1, "boom" if t1 == "failed" else ""), unit_test("p.A.t2", "passed"),
                          unit_test("p.A.t3", t3), unit_test("p.S.t5", "passed"), unit_test("p.A.t6", "passed"),
                          unit_test("p.ParseOracleTest.tx", tx)], filter_doc=AFTER_FILTER)


class DiffTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)

    def run_diff(self, before: dict, after: dict) -> tuple[int, list[str], str]:
        paths = []
        for name, doc in (("before", before), ("after", after)):
            path = self.tmp / f"{name}.json"
            path.write_text(json.dumps(doc), encoding="utf-8")
            paths.append(str(path))
        code, stdout, stderr = quietly(["diff", *paths])
        return code, stdout.splitlines(), stderr

    def test_TC_7_test_level_delta_respects_each_side_set(self):
        code, lines, _ = self.run_diff(before_doc(), after_doc())
        self.assertEqual(["ADDED\tpassed\tp.A.t6", "NEWLY_FAILING\tp.A.t1\tboom", "NEWLY_PASSING\tp.A.t2",
                          "OUT_OF_SET\tafter\tpassed\tp.ParseOracleTest.tx", "REMOVED\tpassed\tp.A.t4",
                          "STATUS\tskipped->passed\tp.S.t5", "EXECUTED\t4\t5"], lines)
        self.assertEqual(1, code)

    def test_TC_8_b_no_new_failure(self):
        code, lines, _ = self.run_diff(before_doc(), after_doc(t1="passed"))
        self.assertEqual(0, code, lines)
        self.assertIn("REMOVED\tpassed\tp.A.t4", lines)

    def test_TC_8_c_executed_drop(self):
        after = unit_document([unit_test("p.A.t1", "passed"), unit_test("p.ParseOracleTest.tx", "passed")],
                              filter_doc=AFTER_FILTER)
        code, lines, _ = self.run_diff(before_doc(), after)
        self.assertEqual(["EXECUTED\t4\t1", "EXECUTED_DROP\t3"], lines[-2:])
        self.assertEqual(4, code)

    def test_TC_8_d_out_of_set_failure_counts(self):
        code, lines, _ = self.run_diff(before_doc(), after_doc(t1="passed", tx="failed"))
        self.assertEqual(1, code, lines)

    def test_TC_8_e_foreign_tool_is_not_comparable(self):
        other = after_doc()
        other["results"]["tool"]["name"] = "other"
        code, lines, refused = self.run_diff(before_doc(), other)
        self.assertEqual((2, []), (code, lines))
        self.assertTrue(refused.startswith("NOT COMPARABLE: "), refused)

    def test_TC_8_f_passed_now_skipped_counts(self):
        code, lines, _ = self.run_diff(before_doc(), after_doc(t1="passed", t3="skipped"))
        self.assertIn("NEWLY_SKIPPED\tp.A.t3", lines)
        self.assertEqual("EXECUTED\t4\t4", lines[-1])
        self.assertEqual(1, code)

    def test_TC_8_g_baseline_skipped_test_now_failing_counts(self):
        after = after_doc(t1="passed")
        after["results"]["tests"][3] = unit_test("p.S.t5", "failed", "boom")
        code, lines, _ = self.run_diff(before_doc(), after)
        self.assertIn("NEWLY_FAILING\tp.S.t5\tboom", lines)
        self.assertEqual(1, code)

    def test_TC_8_h_added_failing_test_counts(self):
        after = after_doc(t1="passed")
        after["results"]["tests"][4] = unit_test("p.A.t6", "failed", "new")
        code, lines, _ = self.run_diff(before_doc(), after)
        self.assertIn("ADDED\tfailed\tp.A.t6", lines)
        self.assertEqual(1, code)

    def test_TC_8_i_test_the_after_set_excludes_is_out_of_set_before(self):
        _, lines, _ = self.run_diff(after_doc(t1="passed"), before_doc())
        self.assertIn("OUT_OF_SET\tbefore\tpassed\tp.ParseOracleTest.tx", lines)
        self.assertNotIn("REMOVED\tpassed\tp.ParseOracleTest.tx", lines)

    def test_identical_documents(self):
        doc = after_doc(t1="passed")
        self.assertEqual((0, ["EXECUTED\t6\t6"], ""), self.run_diff(doc, copy.deepcopy(doc)))


if __name__ == "__main__":
    unittest.main()
