"""MAINT-45 TC 5-8, 35-38 and 61-63: perf_ctrf.py."""
import contextlib
import io
import json
import os
import tempfile
import unittest
from dataclasses import dataclass
from pathlib import Path
from unittest import mock

import loom_store
import perf_ctrf
from loom_stub import STUB

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


def outputs_of(lookup_items: int | None) -> dict:
    return {} if lookup_items is None else {"lookupItems": lookup_items}


def record(spec: Measured) -> dict:
    warm_marks = spec.marks if spec.warm_marks is None else spec.warm_marks
    warm_items = spec.lookup_items if spec.warm_lookup_items is None else spec.warm_lookup_items
    return {"schema": 1, "workload": spec.workload, "kind": "parse", "durationMs": spec.duration,
            "fixture": "f" * 16, "counts": {"marks": spec.marks}, "outputs": outputs_of(spec.lookup_items),
            "warm": {"counts": {"marks": warm_marks}, "outputs": outputs_of(warm_items)}}


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
        (self.tmp / "gate-mode").write_text(text, encoding="utf-8")

    def ci(self, event: str = "push", base_ref: str = "") -> tuple[int, str]:
        code, stdout, stderr = quietly(["ci", "--records", str(self.records), "--out", str(self.out),
                                        "--accepted", "accepted.json", "--gate-mode", "gate-mode",
                                        "--event", event, "--base-ref", base_ref, "--cpuinfo", str(self.cpuinfo),
                                        "--repo", str(self.tmp)])
        return code, stdout + stderr


def measured(sha256: str) -> dict:
    return {"exit": 0, "json": {"commit": "x", "checks": [{"check": "a3", "status": "measured",
                                                          "record": {"key": "k", "loom-sha256": sha256}}]}}


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
    """Phase 2 routing: an event other than push stores nothing (design §3.6 step 5)."""

    def test_a_pull_request_names_its_event_and_stores_nothing(self):
        self.write_record("w.json", record(Measured("w")))
        self.set_mode(perf_ctrf.REPORT_ONLY)
        self.stage({})
        code, printed = self.ci(event="pull_request", base_ref="main")
        self.assertEqual((0, []), (code, self.puts()))
        self.assertIn("::warning::perf: event pull_request neither stores nor compares", printed)

    def test_TC_63_a_wobble_on_a_pull_request_fails_in_gating_and_stores_nothing(self):
        self.write_record("w.json", record(Measured("w", marks=101, warm_marks=100)))
        self.set_mode(perf_ctrf.GATING)
        self.stage({})
        code, printed = self.ci(event="pull_request", base_ref="main")
        self.assertEqual((1, []), (code, self.puts()))
        self.assertIn("WOBBLE\tw\tmarks\t100\t101", printed)


if __name__ == "__main__":
    unittest.main()
