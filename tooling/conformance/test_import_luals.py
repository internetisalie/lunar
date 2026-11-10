"""MAINT-55 TC-9, TC-10, TC-11: import_luals.py against the fixture checkout in testdata/luals."""
import contextlib
import io
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from import_luals import main

FIXTURE = Path(__file__).parent / "testdata" / "luals"
HEADER = "-- ==== case "


def git(checkout: Path, *args: str) -> None:
    subprocess.run(
        ["git", "-C", str(checkout), "-c", "user.name=fixture", "-c", "user.email=fixture@example.invalid",
         "-c", "commit.gpgsign=false", *args],
        check=True, capture_output=True,
    )


def checkout_of_fixture(root: Path) -> Path:
    """A git checkout holding the fixture, so ``rev-parse HEAD`` names the fixture, not this repository."""
    checkout = root / "luals"
    shutil.copytree(FIXTURE, checkout)
    git(checkout, "init", "-q")
    git(checkout, "add", ".")
    git(checkout, "commit", "-q", "-m", "fixture")
    return checkout


def run_main(checkout: Path, out: Path) -> tuple[int, str]:
    stderr = io.StringIO()
    with contextlib.redirect_stderr(stderr):
        code = main(["--luals", str(checkout), "--out", str(out)])
    return code, stderr.getvalue()


def cases(case_file: Path) -> list[tuple[str, list[str]]]:
    """(header, body lines) per case, blank separator lines dropped."""
    found: list[tuple[str, list[str]]] = []
    for line in case_file.read_text(encoding="utf-8").split("\n")[1:]:
        if line.startswith(HEADER):
            found.append((line, []))
        elif found:
            found[-1][1].append(line)
    return [(header, [line for line in body if line]) for header, body in found]


def snapshot(directory: Path) -> dict[str, bytes]:
    return {str(path.relative_to(directory)): path.read_bytes() for path in sorted(directory.rglob("*")) if path.is_file()}


class ImportTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.checkout = checkout_of_fixture(self.root)
        self.out = self.root / "out"

    def tearDown(self):
        shutil.rmtree(self.root)

    def test_converts_one_case_and_records_each_exclusion_TC_9(self):
        self.assertEqual(run_main(self.checkout, self.out), (0, ""))
        imported = cases(self.out / "cases" / "param-type-mismatch.lua")
        self.assertEqual(len(imported), 1)
        header, body = imported[0]
        self.assertEqual(header, "-- ==== case param-type-mismatch-001 rule=arg source=param-type-mismatch.lua#1")
        self.assertEqual(body[2], "f(true) -- E")
        text = (self.out / "cases" / "param-type-mismatch.lua").read_text(encoding="utf-8").split("\n")
        fourth = text[text.index(header) + 4]
        self.assertTrue(fourth.endswith(" -- E") and "<!" not in fourth, fourth)
        record = json.loads((self.out / "import.json").read_text(encoding="utf-8"))
        excluded = [entry for entry in record["excluded"] if entry["source"].startswith("param-type-mismatch.lua")]
        self.assertEqual(excluded, [
            {"source": "param-type-mismatch.lua#2", "reason": "config"},
            {"source": "param-type-mismatch.lua#3", "reason": "foreign-marker"},
            {"source": "param-type-mismatch.lua#4", "reason": "marker-on-doc-line"},
        ])
        self.assertEqual(record["codes"]["param-type-mismatch"], {"blocks": 4, "imported": 1})
        first = snapshot(self.out)
        self.assertEqual(run_main(self.checkout, self.out), (0, ""))
        self.assertEqual(snapshot(self.out), first)

    def test_assignment_rules_TC_10(self):
        self.assertEqual(run_main(self.checkout, self.out)[0], 0)
        headers = [header for header, _ in cases(self.out / "cases" / "assign-type-mismatch.lua")]
        rules = [header.split(" rule=")[1].split(" ")[0] for header in headers]
        self.assertEqual(rules, ["field.assign", "assign.local", "field.assign"])

    def test_scope_check_refuses_a_new_type_check_code_TC_11(self):
        diagnostic = self.checkout / "script" / "proto" / "diagnostic.lua"
        text = diagnostic.read_text(encoding="utf-8")
        diagnostic.write_text(text.replace("    'inject-field',\n", "    'inject-field',\n    'new-check',\n"), encoding="utf-8")
        code, stderr = run_main(self.checkout, self.out)
        self.assertEqual(code, 2)
        self.assertTrue(stderr.startswith("scope: new-check "), stderr)


if __name__ == "__main__":
    unittest.main()
