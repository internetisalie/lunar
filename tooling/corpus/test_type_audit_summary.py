"""MAINT-54 TC-20: type_audit_summary.py against the shared golden sample and fixed cell cases."""
import contextlib
import io
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from type_audit_summary import aggregate, load, main, verdict

SAMPLE = Path(__file__).parent / "testdata" / "type_audit_sample.json"
COVERAGE = {
    "calls": 0, "stringOrTableArgs": 0, "unresolvedCallee": 0, "unresolvedMethod": 0, "formMismatch": 0,
    "noTag": 0, "arityGuard": 0, "eligible": 0, "fieldUntagged": 0, "ldocParam": 0,
}


def payload(member: str, cases: list[tuple]) -> dict:
    """One member's payload from (shape, path, form, population, outcomes) cases."""
    cells, records = [], []
    for shape, path, form, population, outcomes in cases:
        cell = {"shape": shape, "path": path, "form": form}
        cells.append({"cell": cell, "population": population})
        records += [
            {"cell": cell, "file": "f.lua", "line": 1, "declaredType": "t", "wrong": "true", "outcome": outcome,
             "newErrors": []}
            for outcome in outcomes
        ]
    return {"schema": 1, "member": member, "commit": "c", "cap": 4, "coverage": dict(COVERAGE), "cells": cells,
            "records": records}


def judged(*payloads: dict) -> dict:
    return verdict(aggregate(list(payloads))["cells"])


A = [("arg.class", "PROJECT_FILE", "GLOBAL", 30, ["SILENT"] * 4),
     ("arg.class", "SAME_FILE", "LOCAL", 70, ["REPORTED"] * 4)]
B = [("arg.primitive", "PROJECT_FILE", "MEMBER", 30, ["SILENT"] * 2),
     ("arg.primitive", "PROJECT_FILE", "GLOBAL", 70, ["REPORTED"] * 4)]
C = [("arg.class", "PROJECT_FILE", "GLOBAL", 20, ["SILENT"] * 4),
     ("arg.class", "SAME_FILE", "LOCAL", 80, ["REPORTED"] * 4)]
D = C + [("lenient.primitiveForTable", "PROJECT_FILE", "GLOBAL", 100, ["LENIENT_BY_DESIGN"] * 4)]


class TypeAuditSummaryTest(unittest.TestCase):
    def setUp(self):
        self.scratch = Path(tempfile.mkdtemp(prefix="type-audit-"))
        self.addCleanup(shutil.rmtree, self.scratch)

    def sample_dir(self, schema: int = 1) -> Path:
        data = json.loads(SAMPLE.read_text(encoding="utf-8"))
        data["schema"] = schema
        (self.scratch / "sample.json").write_text(json.dumps(data), encoding="utf-8")
        return self.scratch

    def test_TC_20_sample_loads_with_full_entry_messages(self):
        loaded = load(self.sample_dir())
        entries = [entry for record in loaded[0]["records"] for entry in record["newErrors"]]
        self.assertTrue(all(isinstance(entry, dict) for entry in entries))
        self.assertIn("boolean is not assignable to union number | string", [entry["message"] for entry in entries])
        cells = aggregate(loaded)["cells"]
        self.assertEqual(1, sum(row["noAtSite"] for row in cells.values()))

    def test_TC_20_bad_input_exits_2(self):
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(2, main([str(self.scratch / "missing")]))
            self.assertEqual(2, main([str(self.sample_dir(schema=2))]))

    def test_TC_20_main_prints_the_verdict(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(0, main([str(self.sample_dir())]))
        self.assertIn("**Verdict: HOLD**", out.getvalue())

    def test_TC_20_case_a_node_carrying_reopens(self):
        result = judged(payload("m", A))
        self.assertAlmostEqual(0.30, result["F"])
        self.assertAlmostEqual(1.00, result["R"])
        self.assertEqual("REOPEN", result["verdict"])

    def test_TC_20_case_b_thin_divergence_holds(self):
        result = judged(payload("m", B))
        self.assertAlmostEqual(0.30, result["F"])
        self.assertAlmostEqual(0.00, result["R"])
        self.assertEqual("HOLD", result["verdict"])

    def test_TC_20_case_c_below_threshold_holds(self):
        result = judged(payload("m", C))
        self.assertAlmostEqual(0.20, result["F"])
        self.assertEqual("HOLD", result["verdict"])

    def test_TC_20_case_d_lenient_is_not_silent(self):
        self.assertAlmostEqual(0.20, judged(payload("m", D))["F"])

    def test_TC_20_case_e_members_pool_before_rating(self):
        first = payload("m1", [("arg.primitive", "PROJECT_FILE", "GLOBAL", 10, ["SILENT"] * 4)])
        second = payload("m2", [("arg.primitive", "PROJECT_FILE", "GLOBAL", 30, ["REPORTED"] * 4)])
        self.assertAlmostEqual(0.50, judged(first, second)["F"])


if __name__ == "__main__":
    unittest.main()
