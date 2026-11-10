"""MAINT-56: type_corpus.py and fetch-corpus.py's --manifest, against the cases of requirements.md."""
import contextlib
import importlib.util
import io
import json
import re
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import type_corpus
from type_audit_summary import COVERAGE_KEYS

HERE = Path(__file__).resolve().parent
DATA = HERE / "testdata" / "type_corpus"


def load_fetcher():
    spec = importlib.util.spec_from_file_location("fetch_corpus", HERE / "fetch-corpus.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class TypeCorpusManifestTest(unittest.TestCase):
    def setUp(self):
        self.scratch = Path(tempfile.mkdtemp(prefix="type-corpus-"))
        self.addCleanup(shutil.rmtree, self.scratch)

    def test_TC_1_check_reports_each_error_in_order(self):
        errors = type_corpus.check(
            type_corpus.load(DATA / "check_manifest.json"),
            {"penlight"},
            type_corpus.load_domains(DATA / "check_domains.json"),
        )
        self.assertEqual(
            ["zz: missing 'cohort'", "penlight: also in corpus.json", "love: 2 members, need 3"],
            errors,
        )

    def test_TC_2_real_manifest_checks_clean(self):
        entries = type_corpus.load(type_corpus.MANIFEST)
        sampled = [d for d in type_corpus.load_domains(type_corpus.DOMAINS)["domains"] if d["sampled"]]
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(0, type_corpus.main(["check"]))
        self.assertEqual(f"type-corpus ok ({len(entries)} members, {len(sampled)} domains)\n", out.getvalue())

    def test_TC_3_density_counts_tag_lines_and_non_blank_lines(self):
        self.assertEqual(
            {"files": 1, "lines": 4, "catsTags": 2, "maxFileLines": 4, "maxFile": "a.lua"},
            type_corpus.density(DATA / "dens", ["."]),
        )

    def test_TC_21_check_enforces_the_line_cap(self):
        checkout = self.scratch / "big"
        checkout.mkdir()
        (checkout / "a.lua").write_text("x = 1\n" * 3501, encoding="utf-8")
        entries = [
            {"name": name, "url": "u", "commit": "c", "roots": ["."], "luaLevel": "LUA51", "domain": "love",
             "cohort": "young", "licence": "MIT"}
            for name in ("big", "small", "tiny")
        ]
        self.assertEqual(
            ["big: a.lua has 3501 lines, cap 3500"],
            type_corpus.check(entries, set(), type_corpus.load_domains(DATA / "check_domains.json"), self.scratch),
        )


class FetchManifestTest(unittest.TestCase):
    def test_TC_4_manifest_argument_is_read(self):
        fetcher = load_fetcher()
        scratch = Path(tempfile.mkdtemp(prefix="fetch-corpus-"))
        self.addCleanup(shutil.rmtree, scratch)
        err = io.StringIO()
        with mock.patch.object(fetcher, "CORPUS_ROOT", scratch), \
                mock.patch.object(fetcher, "fetch_one", lambda entry: None), \
                contextlib.redirect_stderr(err), contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(SystemExit) as raised:
                fetcher.main(["--manifest", str(DATA / "bad_manifest.json")])
        self.assertEqual(1, raised.exception.code)
        self.assertIn("Malformed manifest entry bad: missing 'url'", err.getvalue())

    def test_TC_5_sparse_patterns_cover_roots_then_module_root(self):
        self.assertEqual(["/a/b/", "/c/"], load_fetcher().sparse_patterns({"roots": ["a/b"], "moduleRoot": "c"}))


class TypeDomainsTest(unittest.TestCase):
    def domains(self) -> dict:
        return type_corpus.load_domains(DATA / "weight_domains.json")

    def test_TC_19_weights_are_shares_of_sampled_weighted_domains(self):
        weights = type_corpus.weights_of(self.domains())
        self.assertEqual({"a", "b"}, set(weights))
        self.assertAlmostEqual(0.40, weights["a"])
        self.assertAlmostEqual(0.60, weights["b"])

    def test_TC_24_coverage_leaves_excluded_out(self):
        domains = self.domains()
        domains["excluded"] = [{"name": "e", "counts": {"s7": 5000}}]
        self.assertAlmostEqual(0.4878, type_corpus.coverage(domains), places=4)

    def test_TC_25_share_crossings_skip_named_hosts_and_interpreters(self):
        self.assertEqual(
            [
                "h: 1.00 % of measured usage crosses the 2 % sampling line; sampled stays true",
                "u: 3.00 % of measured usage crosses the 2 % sampling line; sampled stays false",
            ],
            type_corpus.share_crossings(type_corpus.load_domains(DATA / "crossing_domains.json")),
        )

    def test_TC_20_measure_usage_queries_each_signature(self):
        def search(query: str) -> int:
            return 7 if query == '"vim.api" extension:lua' else 0

        measured = type_corpus.measure_usage(type_corpus.load_domains(DATA / "usage_domains.json"), search, "2026-11-08")
        self.assertEqual({"vim.api": 7}, measured["domains"][0]["hosts"][0]["counts"])
        self.assertEqual("2026-11-08", measured["measuredAt"])

    def test_TC_26_measure_usage_escapes_quotes_inside_a_signature(self):
        def search(query: str) -> int:
            return 7 if query == '"require(\\"awful\\")" extension:lua' else 0

        domains = {"query": '"<signature>" extension:lua',
                   "domains": [{"id": "awesome", "hosts": [{"name": "awesome", "counts": {'require("awful")': 1}}]}]}
        measured = type_corpus.measure_usage(domains, search, "2026-11-10")
        self.assertEqual({'require("awful")': 7}, measured["domains"][0]["hosts"][0]["counts"])


def member(name: str, domain: str, lines: int, cases: list[tuple], baseline: dict | None = None) -> dict:
    """A `load_run` record from (shape, path, form, population, outcomes) cases."""
    cells, records = [], []
    for shape, path, form, population, outcomes in cases:
        cell = {"shape": shape, "path": path, "form": form}
        cells.append({"cell": cell, "population": population})
        records += [{"cell": cell, "outcome": outcome, "newErrors": []} for outcome in outcomes]
    audit = {"schema": 1, "member": name, "commit": "c", "cap": 4, "coverage": dict.fromkeys(COVERAGE_KEYS, 0), "cells": cells, "records": records}
    return {"name": name, "domain": domain, "kloc": lines / 1000, "catsTags": 0, "audit": audit,
            "extras": {"baseline": dict(baseline or {})}}


def domain_entry(domain_id: str, weighted: bool, count: int) -> dict:
    return {"id": domain_id, "kind": "host" if weighted else "interpreter", "sampled": True, "weighted": weighted,
            "lunar": "none", "hosts": [{"name": domain_id, "counts": {f"{domain_id}.api": count}}]}


def silent(measured_silent: int, measured: int) -> list[str]:
    return ["SILENT"] * measured_silent + ["REPORTED"] * (measured - measured_silent)


class TypeCorpusSummaryTest(unittest.TestCase):
    W = {"x": 0.75, "y": 0.25}

    def setUp(self):
        self.scratch = Path(tempfile.mkdtemp(prefix="type-corpus-run-"))
        self.addCleanup(shutil.rmtree, self.scratch)

    def summarize(self, run_dir: Path, out: Path) -> int:
        return type_corpus.main([
            "summarize", str(run_dir), "--checkouts", str(DATA / "checkouts"), "--lunar-commit", "abc",
            "--out", str(out), "--manifest", str(DATA / "run_manifest.json"),
            "--domains", str(DATA / "run_domains.json"),
        ])

    def test_TC_9_summarize_refuses_a_missing_payload(self):
        run_dir = self.scratch / "run"
        shutil.copytree(DATA / "run", run_dir)
        (run_dir / "extras" / "lde.json").unlink()
        err = io.StringIO()
        with contextlib.redirect_stderr(err), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(2, self.summarize(run_dir, self.scratch / "out.json"))
        self.assertIn("missing payload: extras/lde.json", err.getvalue())

    def test_TC_18_rule_weights_are_canonical_and_reproducible(self):
        first, second = self.scratch / "first.json", self.scratch / "second.json"
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, self.summarize(DATA / "run", first))
            self.assertEqual(0, self.summarize(DATA / "run", second))
        text = first.read_text(encoding="utf-8")
        self.assertEqual(first.read_bytes(), second.read_bytes())
        self.assertEqual(json.dumps(json.loads(text), indent=2, sort_keys=True) + "\n", text)
        document = json.loads(text)
        self.assertEqual(
            {"schema", "corpus", "lunarCommit", "thresholds", "usage", "domains", "verdicts", "shapes", "families",
             "bugs", "timedOut"},
            set(document),
        )
        self.assertEqual(1, document["schema"])

    def test_TC_27_a_timed_out_member_is_listed_and_enters_no_figure(self):
        run_dir = self.scratch / "run"
        shutil.copytree(DATA / "run", run_dir)
        extras = run_dir / "extras" / "snabb.json"
        extras.write_text(json.dumps(dict(json.loads(extras.read_text(encoding="utf-8")), timedOut=True)), encoding="utf-8")
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(0, self.summarize(run_dir, self.scratch / "out.json"))
        document = json.loads((self.scratch / "out.json").read_text(encoding="utf-8"))
        self.assertEqual(["snabb"], document["timedOut"])
        self.assertEqual({"x"}, set(document["domains"]))
        self.assertEqual({"lde", "snabb"}, set(document["corpus"]["members"]))
        self.assertIn("- Timed out, in no figure above: snabb", out.getvalue())

    def test_TC_10_exposure_is_the_mean_of_member_rates(self):
        members = [
            member("a", "x", 2000, [("arg.table", "LIBRARY", "GLOBAL", 1, []), ("arg.table", "SAME_FILE", "LOCAL", 1, [])]),
            member("b", "x", 1000, [("arg.table", "LIBRARY", "GLOBAL", 2, [])]),
        ]
        self.assertAlmostEqual(1.50, type_corpus.exposure(members, self.W)["arg.table"]["x"])

    def test_TC_11_weighted_verdict_beside_domain_mean_and_pooled(self):
        members = [
            member("a", "x", 1000, [("arg.primitive", "PROJECT_FILE", "MEMBER", 1000, silent(2, 4))]),
            member("b", "y", 1000, [("arg.primitive", "SAME_FILE", "MEMBER", 10, silent(1, 10))]),
        ]
        result = type_corpus.domain_verdicts(members, self.W, drop_library=False)
        self.assertAlmostEqual(0.40, result["weighted"]["F"])
        self.assertAlmostEqual(0.30, result["domainMean"]["F"])
        self.assertAlmostEqual(0.50, result["pooled"]["F"], places=2)

    def test_TC_12_non_library_stratum_drops_library_cells(self):
        members = [member("a", "x", 1000, [
            ("arg.primitive", "LIBRARY", "MEMBER", 100, silent(4, 4)),
            ("arg.primitive", "SAME_FILE", "MEMBER", 100, silent(0, 4)),
        ])]
        stratum = type_corpus.domain_verdicts(members, {"x": 1.0}, drop_library=True)
        self.assertAlmostEqual(0.00, stratum["domains"]["x"]["F"])
        self.assertAlmostEqual(0.00, stratum["weighted"]["F"])

    def test_TC_16_bug_map_reproduces_maint_54_cell_table(self):
        rules = json.loads(type_corpus.BUG_MAP.read_text(encoding="utf-8"))
        row = re.compile(r"^\| (\S+) / (\S+) / (\S+) \| \d+ \| \d+ \| \d+ \| \[\[(BUG-\d+)\]\] \|")
        rows = [match.groups() for line in (DATA / "cell_to_bug.txt").read_text().splitlines()
                if (match := row.match(line))]
        self.assertEqual(45, len(rows))
        for shape, path, form, bug in rows:
            expected = "unmapped" if bug == "BUG-38" else bug
            self.assertEqual(expected, type_corpus.bug_for_cell((shape, path, form), rules), f"{shape}/{path}/{form}")

    def representative(self) -> list[dict]:
        return [
            member("a", "x", 2000, [
                ("arg.table", "LIBRARY", "GLOBAL", 30, silent(1, 5)),
                ("arg.table", "PROJECT_FILE", "MEMBER", 10, silent(3, 5)),
                ("return.mismatch", "SAME_FILE", "MEMBER", 10, silent(0, 2)),
            ]),
            member("b", "y", 1000, [("arg.table", "LIBRARY", "GLOBAL", 10, silent(2, 2))]),
            member("c", "z", 10000, [
                ("arg.table", "LIBRARY", "MEMBER", 1000, silent(0, 4)),
                ("arg.class", "SAME_FILE", "MEMBER", 10, silent(1, 1)),
            ]),
        ]

    def test_TC_22_per_shape_figures_and_reading_order(self):
        domains = {"measuredAt": "2026-11-08", "excluded": [],
                   "domains": [domain_entry("x", True, 300), domain_entry("y", True, 100), domain_entry("z", False, 50)]}
        meta = {"manifest": "m", "domains": "d", "lunarCommit": "abc"}
        rules = json.loads(type_corpus.BUG_MAP.read_text(encoding="utf-8"))
        document = type_corpus.weights(self.representative(), domains, rules, meta)
        shapes = document["shapes"]

        def figures(exposure, rate, missed):
            return {"exposurePerKloc": exposure, "silentRate": rate, "missedPerKloc": missed}

        self.assertEqual(
            {"x": figures(20.0, 0.3, 6.0), "y": figures(10.0, 1.0, 10.0), "z": figures(100.0, 0.0, 0.0)},
            shapes["arg.table"]["byDomain"],
        )
        self.assertEqual(figures(17.5, 0.475, 7.0), shapes["arg.table"]["pooled"])
        self.assertEqual(figures(0.0, None, None), shapes["arg.class"]["pooled"])
        self.assertEqual(figures(3.75, 0.0, 0.0), shapes["return.mismatch"]["pooled"])
        reading = type_corpus.render(document).split("## Reading 2")[0]
        positions = [reading.index(f"**{shape}**") for shape in ("arg.table", "return.mismatch", "arg.class")]
        self.assertEqual(sorted(positions), positions)

    def test_TC_23_false_positives_average_each_members_own_rate(self):
        members = [
            member("a", "x", 2000, [], {"arity.tooMany": 4}),
            member("b", "x", 1000, [], {"arity.tooMany": 0}),
            member("c", "y", 1000, [], {"arity.tooMany": 2}),
            member("d", "z", 1000, [], {"arity.tooMany": 10}),
        ]
        result = type_corpus.false_positives(members, self.W)["arity.tooMany"]
        self.assertEqual({"x", "y", "z"}, set(result["byDomain"]))
        for domain, expected in {"x": 1.00, "y": 2.00, "z": 10.00}.items():
            self.assertAlmostEqual(expected, result["byDomain"][domain])
        self.assertAlmostEqual(1.25, result["pooled"])


if __name__ == "__main__":
    unittest.main()
