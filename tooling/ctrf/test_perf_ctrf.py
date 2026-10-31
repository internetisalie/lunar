"""MAINT-45 TC 5-29, 35-50 and 52-73: perf_ctrf.py."""
import contextlib
import dataclasses
import io
import json
import os
import shutil
import tempfile
import unittest
from dataclasses import dataclass
from pathlib import Path
from unittest import mock

import loom_store
import perf_ctrf
from loom_stub import STUB
from test_junit_ctrf import commit_file, git

COMMITTED_GATE_MODE = Path(__file__).resolve().parent.parent / "perf" / "gate-mode"
CPUINFO_TEXT = ("processor\t: 0\nvendor_id\t: GenuineIntel\nmodel\t\t: 85\nmodel name\t: X\n\n"
                "processor\t: 1\nvendor_id\t: GenuineIntel\nmodel\t\t: 85\nmodel name\t: X\n")


@dataclass(frozen=True)
class Measured:
    """What one workload's record says; `warm_*` default to the recorded values."""

    workload: str
    marks: int = 100
    warm_marks: int | None = None
    duration: int = 46
    lookup_items: int | None = None
    warm_lookup_items: int | None = None
    fixture: str = "f" * 16
    counts: dict | None = None


def outputs_of(lookup_items: int | None) -> dict:
    return {} if lookup_items is None else {"lookupItems": lookup_items}


def record(spec: Measured) -> dict:
    counts = {"marks": spec.marks} if spec.counts is None else spec.counts
    warm_counts = counts if spec.warm_marks is None else {"marks": spec.warm_marks}
    warm_items = spec.lookup_items if spec.warm_lookup_items is None else spec.warm_lookup_items
    return {"schema": 1, "workload": spec.workload, "kind": "parse", "durationMs": spec.duration,
            "fixture": spec.fixture, "counts": counts, "outputs": outputs_of(spec.lookup_items),
            "warm": {"counts": warm_counts, "outputs": outputs_of(warm_items)}}


def quietly(argv: list[str]) -> tuple[int, str, str]:
    stdout, stderr = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
        code = perf_ctrf.main(argv)
    return code, stdout.getvalue(), stderr.getvalue()


class Workspace(unittest.TestCase):
    def setUp(self):
        scratch = tempfile.TemporaryDirectory()
        self.addCleanup(scratch.cleanup)
        self.tmp = Path(scratch.name)
        self.records = self.tmp / "records"
        self.records.mkdir()
        self.cpuinfo = self.tmp / "cpuinfo"
        self.cpuinfo.write_text(CPUINFO_TEXT, encoding="utf-8")
        self.out = self.tmp / "out" / "perf.ctrf.json"

    def write_record(self, name: str, content: dict):
        (self.records / name).write_text(json.dumps(content), encoding="utf-8")

    def assemble(self) -> tuple[int, str]:
        code, _, refused = quietly(["assemble", "--records", str(self.records), "--out", str(self.out),
                                    "--cpuinfo", str(self.cpuinfo)])
        return code, refused


class AssembleTest(Workspace):
    def test_TC_5_document_is_ordered_by_workload_and_carries_the_cpu(self):
        self.write_record("b.json", record(Measured("b", marks=7, duration=12)))
        self.write_record("a.json", record(Measured("a", marks=5, duration=34)))
        code, refused = self.assemble()
        self.assertEqual(0, code, refused)
        results = json.loads(self.out.read_text(encoding="utf-8"))["results"]
        self.assertEqual(["a", "b"], [test["name"] for test in results["tests"]])
        self.assertEqual([({"marks": 5}, 34), ({"marks": 7}, 12)],
                         [(test["extra"]["counts"], test["duration"]) for test in results["tests"]])
        self.assertEqual({"cpuModel": "X", "vcpus": 2}, results["environment"]["extra"])
        self.assertEqual(perf_ctrf.TOOL_NAME, results["tool"]["name"])

    def test_TC_6_no_records_are_refused(self):
        code, refused = self.assemble()
        self.assertEqual(2, code)
        self.assertIn("NOT COMPARABLE", refused)

    def test_TC_6_a_record_of_another_schema_is_refused(self):
        self.write_record("a.json", dict(record(Measured("a")), schema=2))
        code, refused = self.assemble()
        self.assertEqual(2, code)
        self.assertIn("NOT COMPARABLE", refused)


class StubbedLoomTest(Workspace):
    """Runs perf_ctrf against the MAINT-40 design §4.3 stub loom-cli, selected through LOOM_CLI."""

    def setUp(self):
        super().setUp()
        stub = self.tmp / "loom-cli"
        stub.write_text(STUB, encoding="utf-8")
        stub.chmod(0o755)
        self.scenario = self.tmp / "scenario.json"
        self.repo_dir = self.tmp
        patcher = mock.patch.object(loom_store, "LOOM_CLI", str(stub))
        patcher.start()
        self.addCleanup(patcher.stop)
        env = mock.patch.dict(os.environ, {"STUB_LOOM": str(self.scenario)})
        env.start()
        self.addCleanup(env.stop)

    def stage(self, scenario: dict):
        self.scenario.write_text(json.dumps(scenario), encoding="utf-8")

    def puts(self) -> list[list[str]]:
        path = Path(f"{self.scenario}.calls")
        calls = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()] if path.exists() else []
        return [argv for argv in calls if argv[1] == "put"]

    def set_mode(self, text: str):
        (self.repo_dir / "gate-mode").write_text(text, encoding="utf-8")

    def ci(self, event: str = "push", base_ref: str = "") -> tuple[int, str]:
        code, stdout, stderr = quietly(["ci", "--records", str(self.records), "--out", str(self.out),
                                        "--accepted", "accepted.json", "--gate-mode", "gate-mode",
                                        "--event", event, "--base-ref", base_ref, "--cpuinfo", str(self.cpuinfo),
                                        "--repo", str(self.repo_dir)])
        return code, stdout + stderr


def measured(sha256: str, key: str = "k") -> dict:
    return {"exit": 0, "json": {"commit": "x", "checks": [{"check": "a3", "status": "measured",
                                                          "record": {"key": key, "loom-sha256": sha256}}]}}


class StoreTest(StubbedLoomTest):
    def setUp(self):
        super().setUp()
        self.write_record("w.json", record(Measured("w")))
        self.set_mode(perf_ctrf.GATING)

    def test_TC_7_unmeasured_head_is_put_once_into_the_perf_suite(self):
        self.stage({})
        code, printed = self.ci()
        self.assertEqual(0, code, printed)
        puts = self.puts()
        self.assertEqual(1, len(puts))
        argv = puts[0]
        check = argv.index("--check")
        self.assertEqual(["--check", "a3", "--no-item"], argv[check:check + 3])
        self.assertEqual("perf", argv[argv.index("--suite") + 1])

    def test_TC_61_a_failed_put_fails_the_job_in_report_only(self):
        self.set_mode(perf_ctrf.REPORT_ONLY)
        self.stage({"put": {"exit": 1}})
        self.assertEqual(1, self.ci()[0])

    def test_TC_61_a_failed_resolve_fails_the_job_in_report_only(self):
        self.set_mode(perf_ctrf.REPORT_ONLY)
        self.stage({"resolve": {"HEAD": {"exit": 1}}})
        code, _ = self.ci()
        self.assertEqual((1, []), (code, self.puts()))

    def test_TC_8_a_different_record_at_head_is_kept_with_a_warning(self):
        self.stage({"resolve": {"HEAD": measured("f" * 64)}})
        code, printed = self.ci()
        self.assertEqual((0, []), (code, self.puts()))
        self.assertIn("WARNING store: HEAD already has a different a3 perf record; kept the first", printed)


class GateModeTest(StubbedLoomTest):
    def setUp(self):
        super().setUp()
        self.write_record("w.json", record(Measured("w", marks=101, warm_marks=100)))
        self.stage({})

    def test_TC_35_a_wobble_in_gating_fails_a_push_and_stores_nothing(self):
        self.set_mode(perf_ctrf.GATING)
        code, printed = self.ci()
        self.assertEqual((1, []), (code, self.puts()))
        self.assertIn("WOBBLE\tw\tmarks\t100\t101", printed)

    def test_TC_36_a_wobble_in_report_only_prints_and_still_stores(self):
        self.set_mode(perf_ctrf.REPORT_ONLY)
        code, printed = self.ci()
        self.assertEqual(0, code, printed)
        self.assertEqual(1, len(self.puts()))
        self.assertIn("WOBBLE\tw\tmarks\t100\t101", printed)

    def test_TC_62_an_output_that_differs_between_passes_is_a_wobble(self):
        self.write_record("w.json", record(Measured("w", lookup_items=201, warm_lookup_items=200)))
        self.set_mode(perf_ctrf.GATING)
        code, printed = self.ci()
        self.assertEqual((1, []), (code, self.puts()))
        self.assertIn("WOBBLE\tw\tlookupItems\t200\t201", printed)

    def test_TC_37_an_unknown_mode_is_refused(self):
        self.set_mode("report_only")
        code, printed = self.ci()
        self.assertEqual((2, []), (code, self.puts()))
        self.assertIn("NOT COMPARABLE", printed)

    def test_TC_37_a_missing_mode_file_is_refused(self):
        code, printed = self.ci()
        self.assertEqual((2, []), (code, self.puts()))
        self.assertIn("NOT COMPARABLE", printed)

    def test_TC_38_the_committed_mode_is_a_known_mode(self):
        self.assertIn(perf_ctrf.read_mode(COMMITTED_GATE_MODE), perf_ctrf.MODES)


class EventTest(StubbedLoomTest):
    def test_TC_39_an_event_that_neither_stores_nor_compares_names_itself(self):
        self.write_record("w.json", record(Measured("w")))
        (self.repo_dir / "accepted.json").write_text(json.dumps({"schema": 1, "accepted": []}), encoding="utf-8")
        self.set_mode(perf_ctrf.GATING)
        self.stage({})
        code, printed = self.ci(event="workflow_dispatch")
        self.assertEqual((0, []), (code, self.puts()))
        self.assertIn("::warning::perf: event workflow_dispatch neither stores nor compares", printed)

    def test_TC_68_a_wobble_on_another_event_fails_in_gating_and_stores_nothing(self):
        self.write_record("w.json", record(Measured("w", marks=101, warm_marks=100)))
        self.set_mode(perf_ctrf.GATING)
        self.stage({})
        code, printed = self.ci(event="workflow_dispatch")
        self.assertEqual((1, []), (code, self.puts()))
        self.assertIn("WOBBLE\tw\tmarks\t100\t101", printed)


def entry(counter: str, start: int, end: int) -> dict:
    return {"workload": "w", "counter": counter, "from": start, "to": end, "reason": "the grammar grew"}


def doc_of(specs: tuple[Measured, ...], model: str = "X") -> dict:
    cpu = {"cpuModel": model, "vcpus": 4}
    return json.loads(perf_ctrf.ctrf_document([record(spec) for spec in specs], cpu))


def single(marks: int = 100, **fields) -> dict:
    return doc_of((Measured("w", marks=marks, **fields),))


class DiffTest(Workspace):
    def dump(self, name: str, content: dict) -> str:
        path = self.tmp / name
        path.write_text(json.dumps(content), encoding="utf-8")
        return str(path)

    def diff(self, documents: tuple[dict, dict], accepted: list[dict] | None = None) -> tuple[int, str]:
        accepted_path = self.dump("accepted.json", {"schema": 1, "accepted": accepted or []})
        before, after = self.dump("before.json", documents[0]), self.dump("after.json", documents[1])
        code, printed, refused = quietly(["diff", before, after, "--accepted", accepted_path])
        return code, printed + refused

    def test_TC_10_any_rise_is_a_rise(self):
        code, printed = self.diff((single(100), single(101)))
        self.assertEqual(1, code)
        self.assertIn("RISE\tw\tmarks\t100\t101", printed)

    def test_TC_11_a_fall_is_reported_and_passes(self):
        code, printed = self.diff((single(100), single(99)))
        self.assertEqual(0, code)
        self.assertIn("FALL\tw\tmarks\t100\t99", printed)

    def test_TC_12_a_counter_absent_before_reads_as_zero(self):
        grown = doc_of((Measured("w", counts={"marks": 100, "stub:x": 3}),))
        code, printed = self.diff((single(100), grown))
        self.assertEqual(1, code)
        self.assertIn("RISE\tw\tstub:x\t0\t3", printed)

    def test_TC_13_an_entry_accepts_a_rise_within_its_bounds(self):
        code, printed = self.diff((single(100), single(120)), [entry("marks", 100, 120)])
        self.assertEqual(0, code)
        self.assertIn("ACCEPTED\tw\tmarks\t100\t120\tthe grammar grew", printed)

    def test_TC_14_a_rise_above_the_entry_maximum_is_still_a_rise(self):
        code, printed = self.diff((single(100), single(121)), [entry("marks", 100, 120)])
        self.assertEqual(1, code)
        self.assertIn("RISE\tw\tmarks\t100\t121", printed)

    def test_TC_15_an_entry_for_another_baseline_value_is_unused(self):
        code, printed = self.diff((single(101), single(120)), [entry("marks", 100, 120)])
        self.assertEqual(1, code)
        self.assertIn("RISE\tw\tmarks\t101\t120", printed)
        self.assertIn("ACCEPTANCE_UNUSED\tw\tmarks\t100", printed)

    def test_TC_16_an_entry_for_another_counter_accepts_nothing(self):
        before = doc_of((Measured("w", counts={"advances": 100}),))
        after = doc_of((Measured("w", counts={"advances": 120}),))
        code, printed = self.diff((before, after), [entry("marks", 100, 120)])
        self.assertEqual(1, code)
        self.assertIn("RISE\tw\tadvances\t100\t120", printed)

    def test_TC_17_an_entry_without_a_reason_is_refused(self):
        code, printed = self.diff((single(), single()), [dict(entry("marks", 100, 120), reason="")])
        self.assertEqual(2, code)
        self.assertIn("NOT COMPARABLE", printed)

    def test_TC_17_a_missing_accepted_file_is_refused(self):
        before, after = self.dump("before.json", single()), self.dump("after.json", single())
        code, _, refused = quietly(["diff", before, after, "--accepted", str(self.tmp / "absent.json")])
        self.assertEqual(2, code)
        self.assertIn("NOT COMPARABLE", refused)

    def test_TC_18_a_large_timing_rise_is_flagged_and_never_fails(self):
        code, printed = self.diff((single(duration=10), single(duration=100000)))
        self.assertEqual(0, code)
        self.assertIn("TIMING\tw\t10\t100000\tFLAG", printed)

    def test_TC_19_a_flag_needs_both_the_factor_and_the_floor(self):
        durations = {"a": (100, 400), "b": (100, 399), "c": (10, 30), "d": (1000, 1500)}
        before = doc_of(tuple(Measured(name, duration=pair[0]) for name, pair in durations.items()))
        after = doc_of(tuple(Measured(name, duration=pair[1]) for name, pair in durations.items()))
        _, printed = self.diff((before, after))
        flagged = [line for line in printed.splitlines() if line.startswith("TIMING") and line.endswith("FLAG")]
        self.assertEqual(["TIMING\ta\t100\t400\tFLAG"], flagged)
        self.assertEqual(4, sum(line.startswith("TIMING\t") for line in printed.splitlines()))

    def test_TC_20_timings_on_different_cpu_models_are_not_compared(self):
        code, printed = self.diff((single(), doc_of((Measured("w"),), model="Y")))
        self.assertEqual(0, code)
        self.assertIn("TIMING_NOT_COMPARABLE\tX\tY", printed)
        self.assertNotIn("TIMING\t", printed)

    def test_TC_25_a_changed_fixture_is_not_compared(self):
        code, printed = self.diff((single(100, fixture="1" * 16), single(200, fixture="2" * 16)))
        self.assertEqual(0, code)
        self.assertIn("FIXTURE_CHANGED\tw", printed)
        self.assertNotIn("RISE", printed)

    def test_TC_26_a_workload_on_one_side_only_is_reported(self):
        both = doc_of((Measured("w"), Measured("n")))
        added, removed = self.diff((single(), both)), self.diff((both, single()))
        self.assertEqual(0, added[0])
        self.assertIn("ADDED\tn", added[1])
        self.assertEqual(0, removed[0])
        self.assertIn("REMOVED\tn", removed[1])

    def test_TC_27_a_document_from_another_tool_is_refused(self):
        foreign = single()
        foreign["results"]["tool"]["name"] = "lunar-unit-tests"
        code, printed = self.diff((foreign, single()))
        self.assertEqual(2, code)
        self.assertIn("NOT COMPARABLE", printed)

    def test_TC_44_a_changed_output_is_reported_and_never_fails(self):
        code, printed = self.diff((single(lookup_items=200), single(lookup_items=201)))
        self.assertEqual(0, code)
        self.assertIn("OUTPUT_CHANGED\tw\tlookupItems\t200\t201", printed)


class OwnEntriesTest(unittest.TestCase):
    def test_TC_43_an_edited_retained_entry_is_a_new_entry(self):
        retained = entry("marks", 100, 120)
        edited = dict(retained, reason="a different reason")
        self.assertEqual([edited], perf_ctrf.own_entries([retained, edited], [retained]))

    def test_TC_43_every_entry_a_pull_request_adds_applies(self):
        added = [entry("marks", 100, 120), entry("advances", 5, 9)]
        self.assertEqual(added, perf_ctrf.own_entries(added, []))


class AcceptedFileTest(unittest.TestCase):
    """TC 71: the refusals of design §3.5."""

    def refused(self, text: str):
        with self.assertRaises(perf_ctrf.NotComparable):
            perf_ctrf.parse_accepted(text)

    def accepted(self, **fields) -> str:
        return json.dumps({"schema": 1, "accepted": [dict(entry("marks", 100, 120), **fields)]})

    def test_TC_71_text_that_is_not_json_is_refused(self):
        self.refused("{not json")

    def test_TC_71_another_schema_is_refused(self):
        self.refused(json.dumps({"schema": 2, "accepted": []}))

    def test_TC_71_an_entry_must_rise(self):
        self.refused(self.accepted(to=100))
        self.refused(self.accepted(to=99))

    def test_TC_71_an_entry_needs_text_and_integer_fields(self):
        for fields in ({"workload": ""}, {"counter": None}, {"from": "100"}, {"to": 120.5}):
            with self.subTest(fields=fields):
                self.refused(self.accepted(**fields))

    def test_TC_71_a_document_that_is_not_an_object_with_a_list_is_refused(self):
        self.refused("[]")
        self.refused(json.dumps({"schema": 1, "accepted": {}}))
        self.refused(json.dumps({"schema": 1, "accepted": ["marks"]}))

    def test_TC_71_a_boolean_is_not_an_integer(self):
        self.refused(self.accepted(**{"from": True}))

    def test_TC_71_a_well_formed_file_is_read(self):
        self.assertEqual([entry("marks", 100, 120)], perf_ctrf.parse_accepted(self.accepted()))


class CountPathsTest(unittest.TestCase):
    def test_TC_72_count_paths_are_the_designs(self):
        self.assertEqual(("src/main", "src/test/kotlin/net/internetisalie/lunar/perfrecords",
                          "src/test/kotlin/net/internetisalie/lunar/BaseDocumentTest.kt",
                          "src/test/kotlin/net/internetisalie/lunar/IndexedDocumentTest.kt", "build.gradle.kts",
                          "gradle.properties", "settings.gradle.kts", "gradle", "tooling/ci-image",
                          ".gitea/workflows/build-plugin.yml"), perf_ctrf.COUNT_PATHS)


class Origin:
    """A scratch git repository standing in for the forge."""

    def __init__(self, path: Path):
        path.mkdir()
        self.path = path
        git(path, "init", "-q", "-b", "main")

    def commit(self, files: dict[str, str]) -> str:
        sha = ""
        for name, content in files.items():
            sha = commit_file(self.path, name, content)
        return sha

    def switch(self, branch: str, create: bool = False):
        git(self.path, "checkout", "-q", *(["-b"] if create else []), branch)

    def clone_shallow(self, target: Path, branch: str) -> Path:
        git(self.path, "clone", "-q", "--depth", "1", "--branch", branch, f"file://{self.path}", str(target))
        return target


class FetchTest(StubbedLoomTest):
    def test_TC_9_the_walk_stops_at_code_that_differs(self):
        origin = Origin(self.tmp / "origin")
        first = origin.commit({"src/a.kt": "one"})
        docs_b = origin.commit({"docs/b.md": "b"})
        origin.commit({"src/a.kt": "two"})
        docs_d = origin.commit({"docs/d.md": "d"})
        baseline = self.tmp / "baseline.json"
        baseline.write_text(json.dumps(single()), encoding="utf-8")
        self.stage({"resolve": {first: measured("0" * 64, "k-first")}, "get": {"k-first": str(baseline)}})
        out = self.tmp / "fetched.json"

        def fetch(commit: str) -> tuple[int, str]:
            code, printed, _ = quietly(["fetch", "--commit", commit, "--out", str(out), "--repo", str(origin.path)])
            return code, printed

        self.assertEqual((0, f"fetched {first} distance=1\n"), fetch(docs_b))
        self.assertEqual(3, fetch(docs_d)[0])

    def test_TC_73_a_record_of_another_tool_is_not_a_baseline(self):
        foreign = single()
        foreign["results"]["tool"]["name"] = "lunar-unit-tests"
        path = self.tmp / "foreign.json"
        path.write_text(json.dumps(foreign), encoding="utf-8")
        self.stage({"resolve": {"abc": measured("0" * 64, "k-abc")}, "get": {"k-abc": str(path)}})
        hit = perf_ctrf.baseline(perf_ctrf.slot_for(str(self.tmp)), ["abc"], self.tmp / "before.json")
        self.assertIsNone(hit)


class PullRequestFixture(StubbedLoomTest):
    """A depth-1 clone of branch P of a local `file://` origin: the executor's shallow checkout, not its network."""

    def setUp(self):
        super().setUp()
        self.origin = Origin(self.tmp / "origin")
        self.m1 = self.origin.commit({"src/a.kt": "one"})

    def propose(self, files: dict[str, str], name: str = "P"):
        self.origin.switch(name, create=True)
        self.origin.commit(files)
        self.origin.switch("main")

    def check_out(self, mode: str = perf_ctrf.GATING, name: str = "P"):
        self.repo_dir = self.origin.clone_shallow(self.tmp / f"clone-{name}", name)
        self.set_mode(mode)
        accepted = self.repo_dir / "accepted.json"
        if not accepted.exists():
            accepted.write_text(json.dumps({"schema": 1, "accepted": []}), encoding="utf-8")

    def publish(self, baselines: dict[str, dict]):
        keys = {sha: f"k-{sha}" for sha in baselines}
        files = {}
        for sha, document in baselines.items():
            path = self.tmp / f"baseline-{sha}.json"
            path.write_text(json.dumps(document), encoding="utf-8")
            files[keys[sha]] = str(path)
        self.stage({"resolve": {sha: measured("0" * 64, key) for sha, key in keys.items()}, "get": files})

    def run_record(self, **fields) -> tuple[int, str]:
        self.write_record("w.json", record(Measured("w", **fields)))
        return self.ci(event="pull_request", base_ref="main")

    def diverge(self, mode: str = perf_ctrf.GATING):
        self.propose({"src/a.kt": "pr"})
        self.m2 = self.origin.commit({"src/a.kt": "two"})
        self.check_out(mode)


class PullRequestTest(PullRequestFixture):
    """TC 21-24, 39, 40, 42, 50, 60, 63, 69."""

    def test_TC_39_a_pull_request_without_a_base_ref_is_refused(self):
        self.diverge()
        self.stage({})
        self.write_record("w.json", record(Measured("w")))
        code, printed = self.ci(event="pull_request", base_ref="")
        self.assertEqual(2, code, printed)
        self.assertIn("NOT COMPARABLE: no base ref", printed)

    def test_TC_21_the_baseline_is_the_merge_base_and_a_rise_fails(self):
        self.diverge()
        self.publish({self.m1: single(100), self.m2: single(200)})
        code, printed = self.run_record(marks=200)
        self.assertEqual(1, code, printed)
        self.assertIn("RISE\tw\tmarks\t100\t200", printed)
        self.assertEqual([], self.puts())

    def test_TC_22_the_shallow_clone_is_deepened(self):
        self.diverge()
        self.publish({self.m1: single(100)})
        self.assertEqual("true", git(self.repo_dir, "rev-parse", "--is-shallow-repository"))
        self.run_record(marks=100)
        self.assertEqual("false", git(self.repo_dir, "rev-parse", "--is-shallow-repository"))

    def test_TC_23_no_baseline_warns_and_passes(self):
        self.diverge()
        self.stage({})
        code, printed = self.run_record(marks=200)
        self.assertEqual(0, code, printed)
        self.assertIn(f"::warning::no perf record for a code-identical ancestor of {self.m1}; counts were not gated",
                      printed)

    def test_TC_24_a_failed_lookup_fails_the_job(self):
        self.diverge()
        self.stage({"resolve": {self.m1: {"exit": 1}}})
        code, _ = self.run_record(marks=200)
        self.assertEqual(1, code)

    def test_TC_60_a_failed_lookup_fails_the_job_in_report_only_too(self):
        self.diverge(perf_ctrf.REPORT_ONLY)
        self.stage({"resolve": {self.m1: {"exit": 1}}})
        code, printed = self.run_record(marks=200)
        self.assertEqual(1, code)
        self.assertIn("loom-cli:", printed)
        self.assertNotIn("::warning::", printed)

    def test_TC_40_a_rise_in_report_only_warns_and_passes(self):
        self.diverge(perf_ctrf.REPORT_ONLY)
        self.publish({self.m1: single(100)})
        code, printed = self.run_record(marks=200)
        self.assertEqual(0, code, printed)
        self.assertIn("RISE\tw\tmarks\t100\t200", printed)
        self.assertIn("::warning::", printed)
        self.assertNotIn("::error::", printed)

    def test_TC_42_an_entry_is_accepted_when_the_merge_base_has_no_file(self):
        self.propose({"src/a.kt": "pr", "accepted.json": json.dumps({"schema": 1, "accepted": [
            entry("marks", 100, 200)]})})
        self.origin.commit({"src/a.kt": "two"})
        self.check_out()
        self.publish({self.m1: single(100)})
        code, printed = self.run_record(marks=200)
        self.assertEqual(0, code, printed)
        self.assertIn("ACCEPTED\tw\tmarks\t100\t200", printed)

    def test_TC_50_a_wobble_fails_a_pull_request_whose_counts_match(self):
        self.diverge()
        self.publish({self.m1: single(100)})
        code, printed = self.run_record(marks=100, warm_marks=99)
        self.assertEqual(1, code, printed)
        self.assertIn("WOBBLE\tw\tmarks\t99\t100", printed)
        self.assertNotIn("RISE", printed)

    def test_TC_63_a_wobble_on_a_pull_request_fails_in_gating_and_stores_nothing(self):
        self.diverge()
        self.publish({self.m1: single(101)})
        code, printed = self.run_record(marks=101, warm_marks=100)
        self.assertEqual((1, []), (code, self.puts()))
        self.assertIn("WOBBLE\tw\tmarks\t100\t101", printed)

    def test_TC_69_the_baseline_may_be_a_code_identical_ancestor_of_the_merge_base(self):
        self.origin.commit({"docs/x.md": "d"})
        self.diverge()
        self.publish({self.m1: single(100)})
        code, printed = self.run_record(marks=100)
        self.assertEqual(0, code, printed)
        self.assertIn(f"baseline {self.m1} distance=1", printed)


GIT_SPY = """#!/bin/sh
printf '%s\\t%s\\t%s\\t%s\\n' "$GIT_CONFIG_COUNT" "$GIT_CONFIG_KEY_0" "$GIT_CONFIG_VALUE_0" "$*" >> "$SPY_LOG"
if [ -n "$SPY_ECHO" ] && [ -n "$GIT_CONFIG_VALUE_0" ]; then echo "fatal: forge replied to $GIT_CONFIG_VALUE_0" >&2; exit 128; fi
exec {git} "$@"
"""
TOKEN = "t0ken-for-the-forge"


class GitCredentialsTest(PullRequestFixture):
    """TC 64-67: the forge token reaches the git calls that touch the remote, through the environment only."""

    def setUp(self):
        super().setUp()
        bin_dir = self.tmp / "bin"
        bin_dir.mkdir()
        spy = bin_dir / "git"
        spy.write_text(GIT_SPY.format(git=shutil.which("git")), encoding="utf-8")
        spy.chmod(0o755)
        self.spy_log = self.tmp / "git-calls.log"
        env = mock.patch.dict(os.environ, {"PATH": f"{bin_dir}{os.pathsep}{os.environ['PATH']}",
                                           "SPY_LOG": str(self.spy_log)})
        env.start()
        self.addCleanup(env.stop)
        for name in ("GITEA_TOKEN", "GIT_CONFIG_COUNT"):
            os.environ.pop(name, None)

    def all_calls(self) -> list[list[str]]:
        """(count, key, value, argv) of each recorded git call."""
        return [line.split("\t", 3) for line in self.spy_log.read_text(encoding="utf-8").splitlines()]

    def calls(self, subcommand: str) -> list[list[str]]:
        return [row for row in self.all_calls() if subcommand in row[3].split()]

    def run_with_accepted_file_at_merge_base(self) -> tuple[int, str]:
        self.origin.commit({"accepted.json": json.dumps({"schema": 1, "accepted": []})})
        self.diverge()
        self.publish({})
        return self.run_record(marks=100)

    def test_TC_64_the_token_reaches_the_fetch_as_a_header_in_the_environment(self):
        os.environ["GITEA_TOKEN"] = TOKEN
        code, printed = self.run_with_accepted_file_at_merge_base()
        self.assertEqual(0, code, printed)
        fetches = self.calls("fetch")
        self.assertEqual(1, len(fetches))
        self.assertEqual(["1", "http.extraHeader", f"Authorization: token {TOKEN}"], fetches[0][:3])
        self.assertIn("--unshallow --filter=blob:none origin", fetches[0][3])

    def test_TC_65_the_token_reaches_the_lazy_read_of_the_accepted_file(self):
        os.environ["GITEA_TOKEN"] = TOKEN
        self.run_with_accepted_file_at_merge_base()
        shows = self.calls("show")
        self.assertEqual(1, len(shows))
        self.assertEqual(["1", "http.extraHeader", f"Authorization: token {TOKEN}"], shows[0][:3])

    def test_TC_66_without_a_token_nothing_is_added(self):
        self.run_with_accepted_file_at_merge_base()
        remote_calls = self.calls("fetch") + self.calls("show")
        self.assertEqual(2, len(remote_calls))
        self.assertEqual([["", "", ""]] * 2, [row[:3] for row in remote_calls])

    def test_TC_66_an_empty_token_adds_nothing(self):
        os.environ["GITEA_TOKEN"] = ""
        self.run_with_accepted_file_at_merge_base()
        remote_calls = self.calls("fetch") + self.calls("show")
        self.assertEqual([["", "", ""]] * 2, [row[:3] for row in remote_calls])

    def test_TC_66_local_git_calls_never_carry_the_header(self):
        os.environ["GITEA_TOKEN"] = TOKEN
        self.run_with_accepted_file_at_merge_base()
        local_calls = self.calls("merge-base") + self.calls("ls-tree") + self.calls("rev-parse")
        self.assertGreaterEqual(len(local_calls), 3)
        self.assertEqual([["", "", ""]] * len(local_calls), [row[:3] for row in local_calls])

    def test_TC_67_a_token_that_git_prints_to_stderr_is_hidden_by_main(self):
        os.environ["GITEA_TOKEN"] = TOKEN
        os.environ["SPY_ECHO"] = "1"
        self.diverge()
        self.publish({})
        code, printed = self.run_record(marks=100)
        self.assertEqual(1, code)
        self.assertIn("fatal: forge replied to Authorization: token ***", printed)
        self.assertNotIn(TOKEN, printed)

    def test_TC_67_the_token_is_on_no_argv_and_in_no_output(self):
        os.environ["GITEA_TOKEN"] = TOKEN
        _, printed = self.run_with_accepted_file_at_merge_base()
        self.assertGreater(len(self.all_calls()), 5)
        self.assertNotIn(TOKEN, "\n".join(row[3] for row in self.all_calls()))
        self.assertNotIn(TOKEN, printed)

    def test_TC_67_a_failed_fetch_prints_gits_reason_without_the_token(self):
        os.environ["GITEA_TOKEN"] = TOKEN
        self.diverge()
        git(self.repo_dir, "remote", "set-url", "origin", f"file://{self.tmp}/gone")
        self.publish({})
        code, printed = self.run_record(marks=100)
        self.assertEqual(1, code)
        self.assertIn("git: ", printed)
        self.assertIn("gone", printed)
        self.assertNotIn(TOKEN, printed)


class TokenScrubbingTest(unittest.TestCase):
    def test_TC_67_a_token_echoed_by_git_is_hidden(self):
        with mock.patch.dict(os.environ, {"GITEA_TOKEN": TOKEN}):
            self.assertEqual("remote said *** twice ***", perf_ctrf._scrubbed(f"remote said {TOKEN} twice {TOKEN}"))


class AcceptedLifetimeTest(PullRequestFixture):
    """TC 41: an entry applies on the PR that adds it, and on no later PR."""

    ACCEPTED = json.dumps({"schema": 1, "accepted": [entry("marks", 100, 120)]})

    def pull_request(self, name: str, marks: int) -> tuple[int, str]:
        self.check_out(name=name)
        return self.run_record(marks=marks)

    def test_TC_41_a_merged_entry_never_applies_again(self):
        self.origin.commit({"accepted.json": json.dumps({"schema": 1, "accepted": []})})
        m1 = git(self.origin.path, "rev-parse", "HEAD")
        self.propose({"accepted.json": self.ACCEPTED}, "PR1")
        self.check_out(name="PR1")
        m2 = self.origin.commit({"src/a.kt": "two", "accepted.json": self.ACCEPTED})
        self.propose({"src/b.kt": "b"}, "PR2")
        m3 = self.origin.commit({"src/a.kt": "three"})
        self.propose({"src/c.kt": "c"}, "PR3")
        self.publish({m1: single(100), m2: single(120), m3: single(100)})
        first = self.run_record(marks=120)
        second = self.pull_request("PR2", 100)
        self.origin.commit({"src/a.kt": "four"})
        third = self.pull_request("PR3", 118)
        self.assertEqual(0, first[0], first[1])
        self.assertIn("ACCEPTED\tw\tmarks\t100\t120", first[1])
        self.assertEqual(0, second[0], second[1])
        self.assertIn("FALL\tw\tmarks\t120\t100", second[1])
        self.assertEqual(1, third[0], third[1])
        self.assertIn("RISE\tw\tmarks\t100\t118", third[1])
        self.assertNotIn("ACCEPTED", third[1])
        self.assertNotIn("ACCEPTANCE_UNUSED", third[1])


@dataclass(frozen=True)
class Stamp:
    """One commit of a stability history: its record (none when unmeasured), CPU model and extra paths."""

    specs: tuple[Measured, ...] = (Measured("w"),)
    model: str = "X"
    paths: tuple[str, ...] = ()
    measured: bool = True
    code: bool = True


def base_stamps(models: tuple[str, ...] = ("X", "Y"), count: int = perf_ctrf.STABILITY_PAIRS + 1) -> list[Stamp]:
    """Newest first; the models cycle from the head."""
    return [Stamp(model=models[index % len(models)]) for index in range(count)]


def with_specs(stamp: Stamp, *specs: Measured) -> Stamp:
    return dataclasses.replace(stamp, specs=specs)


class StabilityTest(StubbedLoomTest):
    def history(self, stamps: list[Stamp]) -> list[str]:
        """Commit ``stamps`` oldest first; returns the shas newest first, as ``stamps`` is."""
        origin = Origin(self.tmp / "history")
        self.origin = origin
        shas, resolve, files = [], {}, {}
        for index, stamp in enumerate(reversed(stamps)):
            changed = {path: str(index) for path in stamp.paths}
            sha = origin.commit(({"tooling/x": str(index)} if stamp.code else {}) | changed)
            shas.append(sha)
            if stamp.measured:
                path = self.tmp / f"doc-{sha}.json"
                path.write_text(json.dumps(doc_of(stamp.specs, stamp.model)), encoding="utf-8")
                resolve[sha], files[f"k-{sha}"] = measured("0" * 64, f"k-{sha}"), str(path)
        self.stage({"resolve": resolve, "get": files})
        return shas[::-1]

    def stability(self, repo: Path | None = None) -> tuple[int, str]:
        code, printed, refused = quietly(["stability", "--first-parent", "HEAD", "--repo", str(repo or self.origin.path),
                                          "--work", str(self.tmp / "work")])
        return code, printed + refused

    def test_TC_45_enough_pairs_across_two_models_are_stable(self):
        self.history(base_stamps())
        code, printed = self.stability()
        self.assertEqual(0, code, printed)
        self.assertIn(f"eligible\t{perf_ctrf.STABILITY_PAIRS}\n", printed)
        self.assertIn("cpuModels\tX,Y\n", printed)
        self.assertIn("STABLE\n", printed)

    def test_TC_46_no_cross_model_pair_is_named_and_not_yet(self):
        self.history(base_stamps(("X",)))
        code, printed = self.stability()
        self.assertEqual(1, code)
        self.assertIn(f"WAITING\tw\t{perf_ctrf.STABILITY_PAIRS}\t0\n", printed)
        self.assertIn("NOT_YET", printed)

    def test_TC_47_a_pair_that_differs_is_a_break(self):
        stamps = base_stamps(("X",))
        stamps[2] = with_specs(stamps[2], Measured("w", marks=101))
        shas = self.history(stamps)
        code, printed = self.stability()
        self.assertEqual(1, code)
        self.assertIn(f"BREAK\t{shas[1]}\t{shas[2]}\tw\tmarks\t100\t101\n", printed)
        self.assertIn("NOT_YET", printed)

    def test_TC_48_a_record_whose_passes_differ_is_a_break(self):
        stamps = base_stamps()
        stamps[1] = with_specs(stamps[1], Measured("w", marks=100, warm_marks=99))
        self.history(stamps)
        code, printed = self.stability()
        self.assertEqual(1, code)
        self.assertRegex(printed, r"BREAK\t[0-9a-f]+\tWOBBLE\tw\tmarks\t99\t100")
        self.assertIn("NOT_YET", printed)

    def test_TC_49_an_unmeasured_docs_only_commit_is_skipped(self):
        stamps = base_stamps()
        stamps.insert(1, Stamp(measured=False, code=False, paths=("docs/y.md",)))
        self.history(stamps)
        code, printed = self.stability()
        self.assertEqual(0, code, printed)
        self.assertIn(f"eligible\t{perf_ctrf.STABILITY_PAIRS}\n", printed)
        self.assertIn("STABLE\n", printed)

    def test_TC_70_the_walk_stops_once_every_workload_is_credited(self):
        stamps = base_stamps(count=perf_ctrf.STABILITY_PAIRS + 2)
        stamps[-1] = with_specs(stamps[-1], Measured("w", marks=101))
        self.history(stamps)
        code, printed = self.stability()
        self.assertEqual(0, code, printed)
        self.assertNotIn("BREAK", printed)
        self.assertIn("STABLE\n", printed)

    def test_TC_52_credit_is_earned_only_on_the_heads_fixture(self):
        stamps = [with_specs(Stamp(model="X"), Measured("w", marks=100, fixture="2" * 16))]
        stamps += [with_specs(Stamp(model="YX"[index % 2]), Measured("w", marks=101, fixture="1" * 16))
                   for index in range(9)]
        self.history(stamps)
        code, printed = self.stability()
        self.assertEqual(1, code)
        self.assertIn("WAITING\tw\t0\t0\n", printed)
        self.assertNotIn("BREAK", printed)
        self.assertNotIn("STABLE\n", printed)

    def test_TC_53_the_pair_across_a_fixture_change_earns_no_credit(self):
        newest = [with_specs(Stamp(model="X"), Measured("w", marks=100, fixture="2" * 16)) for _ in range(5)]
        oldest = [with_specs(Stamp(model="Y"), Measured("w", marks=101, fixture="1" * 16)) for _ in range(5)]
        self.history(newest + oldest)
        code, printed = self.stability()
        self.assertEqual(1, code)
        self.assertIn("WAITING\tw\t4\t0\n", printed)
        self.assertNotIn("BREAK", printed)
        self.assertNotIn("STABLE\n", printed)

    def test_TC_54_credit_is_counted_per_workload(self):
        stamps = base_stamps()
        for index in range(3):
            stamps[index] = with_specs(stamps[index], Measured("w"), Measured("n"))
        self.history(stamps)
        code, printed = self.stability()
        self.assertEqual(1, code)
        self.assertIn("WAITING\tn\t2\t2\n", printed)
        self.assertNotIn("WAITING\tw", printed)

    def test_TC_55_pairs_that_change_production_code_are_ignored(self):
        stamps = [with_specs(dataclasses.replace(stamp, paths=("src/main/x",)), Measured("w", marks=100 + index))
                  for index, stamp in enumerate(base_stamps())]
        self.history(stamps)
        code, printed = self.stability()
        self.assertEqual(1, code)
        self.assertIn(f"eligible\t0\nignored\t{perf_ctrf.STABILITY_PAIRS}\n", printed)
        self.assertNotIn("BREAK", printed)

    def test_TC_56_a_differing_pair_breaks_whatever_the_models(self):
        stamps = base_stamps()
        stamps[1] = with_specs(stamps[1], Measured("w", marks=101))
        shas = self.history(stamps)
        code, printed = self.stability()
        self.assertEqual(1, code)
        self.assertIn(f"BREAK\t{shas[0]}\t{shas[1]}\tw\tmarks\t100\t101\n", printed)

    def test_TC_57_a_head_that_changes_the_build_is_not_evidence(self):
        stamps = base_stamps()
        stamps[0] = with_specs(dataclasses.replace(stamps[0], paths=("gradle.properties",)), Measured("w", marks=101))
        self.history(stamps)
        code, printed = self.stability()
        self.assertEqual(1, code)
        self.assertIn("ignored\t1\n", printed)
        self.assertNotIn("BREAK", printed)

    def test_TC_58_an_unknown_model_is_not_a_model(self):
        self.history(base_stamps(("X", perf_ctrf.UNKNOWN_MODEL)))
        code, printed = self.stability()
        self.assertEqual(1, code)
        self.assertIn(f"WAITING\tw\t{perf_ctrf.STABILITY_PAIRS}\t0\n", printed)
        self.assertIn("cpuModels\tX\n", printed)
        self.assertIn("NOT_YET", printed)

    def test_TC_59_a_shallow_checkout_is_refused(self):
        self.history(base_stamps())
        clone = self.origin.clone_shallow(self.tmp / "shallow", "main")
        code, printed = self.stability(clone)
        self.assertEqual(2, code)
        self.assertIn("NOT COMPARABLE", printed)


class SeriesTest(PullRequestFixture):
    """TC 28-29. `self.m1` is commit A."""

    def setUp(self):
        super().setUp()
        self.work = self.tmp / "work"

    def series(self, *selector: str) -> tuple[int, str]:
        code, printed, refused = quietly(["series", *selector, "--repo", str(self.origin.path),
                                          "--work", str(self.work)])
        return code, printed + refused

    def tag(self, name: str, date: str = "2026-01-01T00:00:00Z"):
        with mock.patch.dict(os.environ, {"GIT_COMMITTER_DATE": date}):
            git(self.origin.path, "tag", "-a", "-m", name, name)

    def test_TC_28_values_print_in_commit_order_per_workload_and_counter(self):
        a = self.m1
        self.origin.commit({"docs/b.md": "b"})
        c = self.origin.commit({"src/a.kt": "two"})
        self.publish({a: doc_of((Measured("w", duration=40, counts={"marks": 100, "advances": 9}),
                                 Measured("n", marks=7, duration=11)), "X"),
                      c: doc_of((Measured("w", duration=41, counts={"marks": 90, "advances": 8}),
                                 Measured("n", marks=6, duration=12)), "Y")})
        code, printed = self.series("--first-parent", c)
        self.assertEqual(0, code, printed)
        self.assertEqual([f"n\tduration\t{a}\t11\tX", f"n\tduration\t{c}\t12\tY",
                          f"n\tmarks\t{a}\t7", f"n\tmarks\t{c}\t6",
                          f"w\tadvances\t{a}\t9", f"w\tadvances\t{c}\t8",
                          f"w\tduration\t{a}\t40\tX", f"w\tduration\t{c}\t41\tY",
                          f"w\tmarks\t{a}\t100", f"w\tmarks\t{c}\t90",
                          "unmeasured\t1"], printed.splitlines())
        self.assertEqual([], self.puts())
        self.assertEqual([], list(self.work.iterdir()))

    def test_TC_28_the_depth_bounds_the_walk(self):
        a = self.m1
        self.origin.commit({"docs/b.md": "b"})
        c = self.origin.commit({"src/a.kt": "two"})
        self.publish({a: single(100), c: single(90)})
        code, printed = self.series("--first-parent", c, "--max-depth", "2")
        self.assertEqual(0, code, printed)
        self.assertNotIn(a, printed)
        self.assertIn(f"w\tmarks\t{c}\t90\n", printed)
        self.assertIn("unmeasured\t1\n", printed)

    def test_TC_28_a_record_of_another_tool_is_unmeasured(self):
        foreign = single()
        foreign["results"]["tool"]["name"] = "lunar-unit-tests"
        self.publish({self.m1: foreign})
        code, printed = self.series("--first-parent", self.m1)
        self.assertEqual((0, "unmeasured\t1\n"), (code, printed))

    def test_TC_28_a_failed_lookup_exits_one(self):
        self.stage({"resolve": {self.m1: {"exit": 1}}})
        code, printed = self.series("--first-parent", self.m1)
        self.assertEqual(1, code)
        self.assertIn("loom-cli:", printed)

    def test_TC_28_exactly_one_selector_is_required(self):
        for selector in ((), ("--tags", "--first-parent", "HEAD")):
            with self.subTest(selector=selector), self.assertRaises(SystemExit) as refused:
                self.series(*selector)
            self.assertEqual(2, refused.exception.code)

    def test_TC_29_a_tag_on_a_docs_only_commit_reports_the_record_of_its_code(self):
        self.origin.commit({"docs/b.md": "b"})
        self.tag("v1")
        self.publish({self.m1: single(100)})
        code, printed = self.series("--tags")
        self.assertEqual(0, code, printed)
        self.assertEqual(["w\tduration\tv1\t46\tX", "w\tmarks\tv1\t100", "unmeasured\t0"], printed.splitlines())

    def test_TC_29_a_tag_whose_code_differs_from_the_record_is_unmeasured(self):
        self.origin.commit({"src/a.kt": "two"})
        self.tag("v1")
        self.publish({self.m1: single(100)})
        code, printed = self.series("--tags")
        self.assertEqual((0, "unmeasured\t1\n"), (code, printed))

    def test_TC_29_the_depth_bounds_a_tags_walk(self):
        self.origin.commit({"docs/b.md": "b"})
        self.tag("v1")
        self.publish({self.m1: single(100)})
        code, printed = self.series("--tags", "--max-depth", "1")
        self.assertEqual((0, "unmeasured\t1\n"), (code, printed))

    def test_TC_29_tags_print_in_creation_order_and_only_release_tags_count(self):
        self.tag("v2", "2026-01-01T00:00:00Z")
        self.tag("other", "2026-01-02T00:00:00Z")
        newer = self.origin.commit({"src/a.kt": "two"})
        self.tag("v1", "2026-01-03T00:00:00Z")
        self.publish({self.m1: single(100), newer: single(90)})
        code, printed = self.series("--tags")
        self.assertEqual(0, code, printed)
        self.assertEqual(["w\tduration\tv2\t46\tX", "w\tduration\tv1\t46\tX", "w\tmarks\tv2\t100",
                          "w\tmarks\tv1\t90", "unmeasured\t0"], printed.splitlines())


if __name__ == "__main__":
    unittest.main()
