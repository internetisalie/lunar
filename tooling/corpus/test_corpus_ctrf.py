"""MAINT-40 TC 4-7 and TC 9-10: corpus_ctrf.py."""
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

import corpus_ctrf
import loom_store
from corpus_ctrf import NotComparable, ctrf_document, fingerprints, identity_of, load_runs
from loom_stub import STUB

TOKEN = "11111111-2222-3333-4444-555555555555"

MANIFEST = {
    "$comment": ["test manifest"],
    "corpora": [
        {"name": "m1", "url": "https://example.invalid/m1.git", "commit": "aaaa", "roots": ["src"],
         "luaLevel": "5.1", "definitionLibraries": ["busted"]},
        {"name": "m2", "url": "https://example.invalid/m2.git", "commit": "bbbb", "roots": ["lua"],
         "luaLevel": "5.4", "moduleRoot": "lua", "target": "LUAJIT:2.1"},
    ],
}

M1_FINDINGS = [
    {"rule": "corpus.parseError", "path": "src/a.lua", "startOffset": 812, "endOffset": 812,
     "line": 31, "column": 7, "message": "<expr> expected", "count": 1},
    {"rule": "inspection.LuaUnusedLocal", "path": "src/a.lua", "startOffset": 10, "endOffset": 11,
     "line": 2, "column": 7, "message": "Unused local 'x'", "symbol": "x", "count": 1},
    {"rule": "corpus.unmergedTokens", "path": "src/b.lua", "startOffset": -1, "endOffset": -1,
     "line": 0, "column": 0, "message": "internal long-bracket tokens escaped the merge", "count": 3},
    {"rule": "corpus.unattributedHighlight", "path": "src/b.lua", "startOffset": 0, "endOffset": 4,
     "line": 1, "column": 1, "message": "", "count": 1},
]

ALLOWED = {
    "": {"reportFormat", "specVersion", "results"},
    "results": {"tool", "summary", "tests", "environment"},
    "results.tool": {"name"},
    "results.summary": {"tests", "passed", "failed", "skipped", "pending", "other", "start", "stop"},
    "results.environment": {"testEnvironment", "extra"},
    "results.tests[]": {"name", "testId", "status", "duration", "suite", "message", "extra"},
}


def run_file(member, findings, libraries=None, token=TOKEN, files=3, requires=2):
    return {"schema": 1, "member": member, "jvmToken": token, "files": files, "requires": requires,
            "libraries": libraries if libraries is not None else {}, "findings": findings}


def m1_run():
    return run_file("m1", copy.deepcopy(M1_FINDINGS), libraries={"zeta": "2.0", "alpha": "1.0"})


def m2_run():
    return run_file("m2", [])


def reversed_keys(value):
    """The same JSON value with every object's keys written in reverse order."""
    if isinstance(value, dict):
        return {k: reversed_keys(value[k]) for k in reversed(list(value))}
    if isinstance(value, list):
        return [reversed_keys(v) for v in value]
    return value


def write_runs(directory: Path, runs) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    for run in runs:
        (directory / f"{run['member']}.json").write_text(json.dumps(run), encoding="utf-8")
    return directory


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


def assemble(directory: Path, manifest_path: Path, out: Path) -> bytes:
    code = corpus_ctrf.main(["assemble", "--findings", str(directory), "--manifest", str(manifest_path),
                             "--out", str(out)])
    if code != 0:
        raise AssertionError(f"assemble exited {code}")
    return out.read_bytes()


class AssembleTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.manifest = self.tmp / "corpus.json"
        self.manifest.write_text(json.dumps(MANIFEST), encoding="utf-8")

    def test_TC_4_document_is_deterministic_canonical_ctrf(self):
        first = write_runs(self.tmp / "a", [m1_run(), m2_run()])
        second = write_runs(self.tmp / "b", [reversed_keys(m1_run()), reversed_keys(m2_run())])
        out_a = assemble(first, self.manifest, self.tmp / "a.json")
        out_b = assemble(second, self.manifest, self.tmp / "b.json")
        self.assertEqual(out_a, out_b)
        canonical = json.dumps(json.loads(out_a), sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        self.assertEqual(canonical.encode() + b"\n", out_a)
        doc = json.loads(out_a)
        self.assertEqual("CTRF", doc["reportFormat"])
        self.assertEqual("0.2.0", doc["specVersion"])
        results = doc["results"]
        self.assertEqual(len(M1_FINDINGS), len(results["tests"]))
        for test in results["tests"]:
            self.assertEqual("failed", test["status"])
            self.assertEqual(["m1"], test["suite"])
        summary = results["summary"]
        self.assertEqual((len(M1_FINDINGS), len(M1_FINDINGS)), (summary["tests"], summary["failed"]))
        self.assertEqual((0, 0), (summary["start"], summary["stop"]))
        self.assertEqual({"m1", "m2"}, set(results["environment"]["extra"]["members"]))
        for path, key in keys_outside_extra(doc):
            self.assertIn(key, ALLOWED[path], f"{key!r} at {path!r}")

    def test_TC_4_extra_carries_the_finding(self):
        doc = json.loads(assemble(write_runs(self.tmp / "a", [m1_run(), m2_run()]), self.manifest, self.tmp / "o"))
        by_rule = {t["extra"]["ruleId"]: t for t in doc["results"]["tests"]}
        located = by_rule["inspection.LuaUnusedLocal"]["extra"]
        self.assertEqual({"ruleId": "inspection.LuaUnusedLocal", "uri": "src/a.lua", "startOffset": 10,
                          "endOffset": 11, "line": 2, "column": 7, "message": "Unused local 'x'",
                          "symbol": "x", "count": 1}, located)
        file_level = by_rule["corpus.unmergedTokens"]["extra"]
        self.assertEqual({"ruleId", "uri", "message", "count"}, set(file_level))
        self.assertEqual("corpus.unattributedHighlight", by_rule["corpus.unattributedHighlight"]["message"])
        self.assertTrue(doc["results"]["environment"]["testEnvironment"].startswith("full-"))

    def test_clean_run_has_zero_tests(self):
        doc = json.loads(assemble(write_runs(self.tmp / "a", [m2_run(), run_file("m1", [])]),
                                  self.manifest, self.tmp / "o"))
        self.assertEqual([], doc["results"]["tests"])
        self.assertEqual(0, doc["results"]["summary"]["tests"])


def ids_of(manifest_path, directory, out):
    doc = json.loads(assemble(directory, manifest_path, out))
    return doc, [t["testId"] for t in doc["results"]["tests"]]


class FingerprintTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.manifest = self.tmp / "corpus.json"
        self.manifest.write_text(json.dumps(MANIFEST), encoding="utf-8")
        self.finding = copy.deepcopy(M1_FINDINGS[1])

    def assemble_m1(self, name, m1_findings, m2_findings=()):
        runs = [run_file("m1", m1_findings), run_file("m2", list(m2_findings))]
        return ids_of(self.manifest, write_runs(self.tmp / name, runs), self.tmp / f"{name}.json")

    def test_TC_5_message_is_not_in_the_fingerprint(self):
        reworded = dict(self.finding, message="Local 'x' is never used")
        _, ids_a = self.assemble_m1("a", [self.finding])
        _, ids_b = self.assemble_m1("b", [reworded])
        self.assertEqual(ids_a, ids_b)

    def test_TC_5_identical_findings_get_distinct_ids(self):
        _, ids = self.assemble_m1("c", [self.finding, dict(self.finding)])
        self.assertEqual(2, len(set(ids)))

    def test_TC_5_member_is_in_the_fingerprint(self):
        doc, ids = self.assemble_m1("d", [self.finding], [dict(self.finding)])
        self.assertEqual(2, len(set(ids)))
        for test in doc["results"]["tests"]:
            self.assertTrue(test["name"].endswith(test["testId"]))

    def test_TC_5_ordinals_do_not_depend_on_list_order(self):
        one = dict(self.finding, message="m-a")
        two = dict(self.finding, message="m-b")
        out_e = assemble(write_runs(self.tmp / "e", [run_file("m1", [one, two]), m2_run()]),
                         self.manifest, self.tmp / "e.json")
        out_f = assemble(write_runs(self.tmp / "f", [run_file("m1", [two, one]), m2_run()]),
                         self.manifest, self.tmp / "f.json")
        self.assertEqual(out_e, out_f)

    def test_fingerprints_pairs_each_finding(self):
        pairs = fingerprints("m1", [self.finding])
        self.assertEqual(1, len(pairs))
        self.assertEqual(64, len(pairs[0][0]))
        self.assertIs(self.finding, pairs[0][1])


class IdentityTest(unittest.TestCase):
    def runs(self, manifest=None, *runs):
        return identity_of(list(runs) or [m1_run(), m2_run()], manifest or MANIFEST)

    def manifest_with(self, member, **changes):
        changed = copy.deepcopy(MANIFEST)
        for entry in changed["corpora"]:
            if entry["name"] == member:
                entry.update(changes)
        return changed

    def test_TC_6_identity_moves_with_the_member_pin_and_libraries(self):
        baseline = self.runs()
        self.assertRegex(baseline, r"^full-[0-9a-f]{16}$")
        self.assertEqual(baseline, self.runs())
        self.assertNotEqual(baseline, self.runs(self.manifest_with("m1", commit="cccc")))
        bumped = m1_run()
        bumped["libraries"]["alpha"] = "1.1"
        self.assertNotEqual(baseline, self.runs(None, bumped, m2_run()))

    def test_TC_6_comment_and_url_do_not_split_identity(self):
        edited = self.manifest_with("m1", url="https://elsewhere.invalid/m1.git")
        edited["$comment"] = ["reworded"]
        self.assertEqual(self.runs(), self.runs(edited))

    def test_TC_6_single_member_shape(self):
        self.assertRegex(self.runs(None, m1_run()), r"^member-m1-[0-9a-f]{16}$")

    def test_TC_6_two_jvm_tokens_are_refused(self):
        other = run_file("m2", [], token="another-token")
        with self.assertRaises(NotComparable):
            self.runs(None, m1_run(), other)

    def test_TC_6_partial_run_set_is_refused(self):
        three = copy.deepcopy(MANIFEST)
        three["corpora"].append({"name": "m3", "url": "u", "commit": "dddd", "roots": ["."], "luaLevel": "5.3"})
        with self.assertRaises(NotComparable):
            self.runs(three, m1_run(), m2_run())

    def test_load_runs_sorts_by_member_and_refuses_other_schemas(self):
        tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, tmp, ignore_errors=True)
        write_runs(tmp, [m2_run(), m1_run()])
        self.assertEqual(["m1", "m2"], [r["member"] for r in load_runs(tmp)])
        (tmp / "m3.json").write_text(json.dumps(dict(run_file("m3", []), schema=2)), encoding="utf-8")
        with self.assertRaises(NotComparable):
            load_runs(tmp)


def git(repo: Path, *args: str) -> str:
    env = dict(os.environ, GIT_AUTHOR_NAME="t", GIT_AUTHOR_EMAIL="t@example.invalid",
               GIT_COMMITTER_NAME="t", GIT_COMMITTER_EMAIL="t@example.invalid", GIT_CONFIG_GLOBAL=os.devnull,
               GIT_CONFIG_NOSYSTEM="1")
    return subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True, text=True,
                          env=env).stdout.strip()


def commit(repo: Path, message: str, when: int) -> str:
    stamp = f"{when} +0000"
    os.environ["GIT_AUTHOR_DATE"] = os.environ["GIT_COMMITTER_DATE"] = stamp
    try:
        git(repo, "commit", "--allow-empty", "-q", "-m", message)
    finally:
        del os.environ["GIT_AUTHOR_DATE"], os.environ["GIT_COMMITTER_DATE"]
    return git(repo, "rev-parse", "HEAD")


def measured(key: str) -> dict:
    return {"exit": 0, "json": {"commit": "x", "checks": [{"check": "a3", "status": "measured",
                                                          "record": {"key": key, "loom-sha256": "0" * 64}}]}}


def ctrf(identity: str, tests=(), tool=corpus_ctrf.TOOL_NAME, members=None) -> dict:
    members = members if members is not None else {"m1": {"files": 3, "requires": 2}}
    return {"reportFormat": "CTRF", "specVersion": "0.2.0", "results": {
        "tool": {"name": tool}, "summary": {}, "tests": list(tests),
        "environment": {"testEnvironment": identity, "extra": {"shape": "full", "members": members}}}}


class StubbedLoomTest(unittest.TestCase):
    """Runs corpus_ctrf against the design §4.3 stub loom-cli, selected through LOOM_CLI."""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
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


class FetchTest(StubbedLoomTest):
    def setUp(self):
        super().setUp()
        self.repo = self.tmp / "repo"
        self.repo.mkdir()
        git(self.repo, "init", "-q", "-b", "main")
        self.c1 = commit(self.repo, "C1", 1_700_000_000)
        self.c2 = commit(self.repo, "C2", 1_700_000_100)
        git(self.repo, "checkout", "-q", "-b", "side", self.c1)
        self.s = commit(self.repo, "S", 1_700_000_200)
        git(self.repo, "checkout", "-q", "main")
        os.environ["GIT_AUTHOR_DATE"] = os.environ["GIT_COMMITTER_DATE"] = "1700000300 +0000"
        try:
            git(self.repo, "merge", "-q", "--no-ff", "-m", "C3", "side")
        finally:
            del os.environ["GIT_AUTHOR_DATE"], os.environ["GIT_COMMITTER_DATE"]
        self.c3 = git(self.repo, "rev-parse", "HEAD")
        docs = {name: self.tmp / f"{name}.ctrf.json" for name in ("c1", "c2", "s")}
        docs["c1"].write_text(json.dumps(ctrf("full-c1")), encoding="utf-8")
        docs["s"].write_text(json.dumps(ctrf("full-s")), encoding="utf-8")
        docs["c2"].write_text(json.dumps(ctrf("full-c1", tool="other")), encoding="utf-8")
        self.stage({
            "resolve": {self.c1: measured("k-c1"), self.c2: measured("k-c2"), self.s: measured("k-s")},
            "get": {"k-c1": str(docs["c1"]), "k-c2": str(docs["c2"]), "k-s": str(docs["s"])},
        })
        self.out = self.tmp / "fetched.ctrf.json"

    def fetch(self, *extra: str) -> tuple[int, str]:
        stdout = io.StringIO()
        with contextlib.redirect_stdout(stdout):
            code = corpus_ctrf.main(["fetch", "--commit", self.c3, "--out", str(self.out), "--repo",
                                     str(self.repo), *extra])
        return code, stdout.getvalue()

    def test_TC_9_nearest_first_parent_ancestor_with_a_corpus_document(self):
        code, printed = self.fetch()
        self.assertEqual(0, code)
        self.assertEqual("full-c1", json.loads(self.out.read_text())["results"]["environment"]["testEnvironment"])
        self.assertIn(f"fetched {self.c1} identity=full-c1 distance=2", printed)
        self.assertFalse(Path(f"{self.out}.part").exists())

    def test_TC_9_like_with_another_identity_finds_nothing(self):
        like = self.tmp / "like.json"
        like.write_text(json.dumps(ctrf("full-other")), encoding="utf-8")
        code, _ = self.fetch("--like", str(like))
        self.assertEqual(corpus_ctrf.EXIT_UNMEASURED, code)
        self.assertFalse(self.out.exists())

    def test_TC_9_depth_bound(self):
        code, printed = self.fetch("--max-depth", "2")
        self.assertEqual(corpus_ctrf.EXIT_UNMEASURED, code)
        self.assertIn(f"no stored result within 2 first-parent ancestors of {self.c3}", printed)

    def test_TC_9_every_resolve_carries_the_suite_argv(self):
        self.assertEqual(("--suite", "corpus"), corpus_ctrf.SUITE_ARGV)
        code, _ = self.fetch()
        self.assertEqual(0, code)
        resolves = [argv for argv in self.calls() if argv[1] == "resolve"]
        self.assertEqual(3, len(resolves))
        for argv in resolves:
            self.assertEqual(["--suite", "corpus"], argv[-2:])
            self.assertEqual(["artifact", "resolve", "--commit"], argv[:3])
            self.assertIn("--json", argv)
            self.assertEqual(corpus_ctrf.CHECK, argv[argv.index("--check") + 1])

    def test_a_failing_resolve_is_an_error_not_a_miss(self):
        self.stage({"resolve": {self.c3: {"exit": 1}}})
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            code, _ = self.fetch()
        self.assertEqual(1, code)

    def test_a_failing_get_leaves_no_part_file(self):
        self.stage({"resolve": {self.c3: measured("k-missing")}})
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            code, _ = self.fetch()
        self.assertEqual(1, code)
        self.assertFalse(Path(f"{self.out}.part").exists())


class StoreTest(StubbedLoomTest):
    """TC 7: `store` against the stub loom-cli (design §3.5)."""

    def setUp(self):
        super().setUp()
        self.manifest = self.tmp / "corpus.json"
        self.manifest.write_text(json.dumps(MANIFEST), encoding="utf-8")
        self.full = write_runs(self.tmp / "full", [m1_run(), m2_run()])
        self.out = self.tmp / "ctrf" / "corpus.ctrf.json"
        self.expected = ctrf_document([m1_run(), m2_run()], MANIFEST)

    def store(self, findings: Path) -> tuple[int, str]:
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            code = corpus_ctrf.main(["store", "--findings", str(findings), "--manifest", str(self.manifest),
                                     "--out", str(self.out), "--repo", "."])
        return code, stdout.getvalue() + stderr.getvalue()

    def stored(self, sha256: str) -> dict:
        return {"exit": 0, "json": {"commit": "x", "checks": [{"check": "a3", "status": "measured", "record": {
            "key": "k", "loom-sha256": sha256}}]}}

    def puts(self) -> list[list[str]]:
        return [argv for argv in self.calls() if argv[1] == "put"]

    def test_TC_7_unmeasured_head_is_put_once_under_the_suite(self):
        self.stage({})
        code, printed = self.store(self.full)
        self.assertEqual(0, code, printed)
        self.assertEqual(self.expected, self.out.read_bytes())
        self.assertEqual([["artifact", "put", "conformance", str(self.out), "--commit", "HEAD", "--check", "a3",
                           "--no-item", "--repo", ".", "--suite", "corpus"]], self.puts())
        resolve = self.calls()[0]
        self.assertEqual(["artifact", "resolve", "--commit", "HEAD"], resolve[:4])
        self.assertEqual(["--suite", "corpus"], resolve[-2:])
        self.assertIn("store: ", printed)

    def test_TC_7_identical_record_is_not_put_again(self):
        self.stage({"resolve": {"HEAD": self.stored(hashlib.sha256(self.expected).hexdigest())}})
        code, printed = self.store(self.full)
        self.assertEqual(0, code, printed)
        self.assertEqual([], self.puts())
        self.assertIn("store: already stored, identical", printed)

    def test_TC_7_different_record_is_kept_with_a_warning(self):
        self.stage({"resolve": {"HEAD": self.stored("f" * 64)}})
        code, printed = self.store(self.full)
        self.assertEqual(0, code, printed)
        self.assertEqual([], self.puts())
        self.assertIn("WARNING store: HEAD already has a different a3 corpus record; kept the first", printed)

    def test_TC_7_failing_resolve_fails_the_store(self):
        self.stage({"resolve": {"HEAD": {"exit": 1}}})
        code, printed = self.store(self.full)
        self.assertEqual(1, code, printed)
        self.assertEqual([], self.puts())

    def test_TC_7_failing_put_fails_the_store(self):
        self.stage({"put": {"exit": 1}})
        code, printed = self.store(self.full)
        self.assertEqual(1, code, printed)
        self.assertEqual(1, len(self.puts()))

    def test_TC_7_member_alone_run_is_refused_before_any_loom_call(self):
        self.stage({})
        code, printed = self.store(write_runs(self.tmp / "alone", [m1_run()]))
        self.assertEqual(2, code, printed)
        self.assertEqual([], self.calls())
        self.assertIn("refusing to store a member run: suite corpus holds full runs only", printed)


def finding_test(member: str, test_id: str, rule: str, uri: str, count: int = 1, line: int = 4) -> dict:
    return {"name": f"{member} {rule} {uri} {test_id}", "testId": test_id, "status": "failed", "duration": 0,
            "suite": [member], "message": rule,
            "extra": {"ruleId": rule, "uri": uri, "line": line, "column": 2, "message": rule, "count": count}}


class DiffTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.before = ctrf("full-x", [finding_test("m1", "id-old", "inspection.LuaUnusedLocal", "a.lua"),
                                      finding_test("m1", "id-keep", "corpus.crash", "b.lua")])
        self.after = ctrf("full-x", [finding_test("m1", "id-new", "inspection.LuaUnusedLocal", "c.lua", line=9),
                                     finding_test("m1", "id-keep", "corpus.crash", "b.lua"),
                                     finding_test("m1", "id-pe", "corpus.parseError", "d.lua")])

    def run_main(self, before: dict, after: dict) -> tuple[int, str, str]:
        paths = []
        for name, doc in (("before", before), ("after", after)):
            path = self.tmp / f"{name}.json"
            path.write_text(json.dumps(doc), encoding="utf-8")
            paths.append(str(path))
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            code = corpus_ctrf.main(["diff", *paths])
        return code, stdout.getvalue(), stderr.getvalue()

    def test_TC_10_finding_level_delta(self):
        code, printed, _ = self.run_main(self.before, self.after)
        self.assertEqual(1, code)
        self.assertEqual([
            "m1\tCOUNT\tcorpus.parseError\t0\t1",
            "m1\tNEW\tcorpus.parseError\td.lua:4:2\tcorpus.parseError",
            "m1\tNEW\tinspection.LuaUnusedLocal\tc.lua:9:2\tinspection.LuaUnusedLocal",
            "m1\tRESOLVED\tinspection.LuaUnusedLocal\ta.lua:4:2\tinspection.LuaUnusedLocal",
        ], printed.splitlines())

    def test_TC_10_identical_documents(self):
        self.assertEqual((0, "", ""), self.run_main(self.after, copy.deepcopy(self.after)))

    def test_TC_10_different_identity_is_not_comparable(self):
        other = copy.deepcopy(self.after)
        other["results"]["environment"]["testEnvironment"] = "full-y"
        code, printed, refused = self.run_main(self.before, other)
        self.assertEqual((2, ""), (code, printed))
        self.assertTrue(refused.startswith("NOT COMPARABLE: "), refused)

    def test_TC_10_different_files_is_not_comparable(self):
        other = copy.deepcopy(self.after)
        other["results"]["environment"]["extra"]["members"]["m1"]["files"] = 4
        code, printed, refused = self.run_main(self.before, other)
        self.assertEqual((2, ""), (code, printed))
        self.assertIn("NOT COMPARABLE", refused)

    def test_foreign_tool_is_not_comparable(self):
        with self.assertRaises(NotComparable):
            corpus_ctrf.diff(ctrf("full-x", tool="other"), self.after)

    def test_a_resolved_finding_alone_is_not_a_regression(self):
        lines, code = corpus_ctrf.diff(self.before, ctrf("full-x", [finding_test("m1", "id-keep", "corpus.crash", "b.lua")]))
        self.assertEqual(0, code)
        self.assertIn("m1\tRESOLVED\tinspection.LuaUnusedLocal\ta.lua:4:2\tinspection.LuaUnusedLocal", lines)


if __name__ == "__main__":
    unittest.main()
