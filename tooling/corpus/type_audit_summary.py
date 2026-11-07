#!/usr/bin/env python3
"""MAINT-54 design §3.6: pool the type-checking false-negative audit and state its verdict.

Reads every ``build/type-audit/<member>.json`` written by ``LuaTypeFalseNegativeAuditTest``
(schema in design §4, pinned by ``testdata/type_audit_sample.json``) and prints Markdown: a shape
table, a cell table, coverage per member and pooled, then ``F``, ``R`` and the verdict.

The thresholds are fixed before the run (design §3.6); do not tune them to a result.

Usage: ``type_audit_summary.py build/type-audit``. Exits 2 on a missing directory, no payloads,
or a payload whose ``schema`` is not 1.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

SCHEMA = 1
OUTCOMES = (
    "REPORTED", "REPORTED_OTHER_RULE", "SILENT", "LENIENT_BY_DESIGN", "NOISY", "SKIPPED_PREEXISTING", "FAILED",
)
MEASURED = ("REPORTED", "REPORTED_OTHER_RULE", "SILENT")
COVERAGE_KEYS = (
    "calls", "stringOrTableArgs", "unresolvedCallee", "unresolvedMethod", "formMismatch", "noTag",
    "arityGuard", "eligible", "fieldUntagged", "ldocParam",
)
F_THRESHOLD = 0.25
R_THRESHOLD = 0.5
NODE_CARRYING_SHAPES = ("arg.table", "arg.class")
DIVERGENT_MIN_MEASURED = 3
DIVERGENT_HIGH = 0.8
DIVERGENT_LOW = 0.2


class BadInput(Exception):
    """The input directory cannot be summarised."""


def load(directory) -> list[dict]:
    path = Path(directory)
    if not path.is_dir():
        raise BadInput(f"no audit directory at {path}")
    payloads = [json.loads(member.read_text(encoding="utf-8")) for member in sorted(path.glob("*.json"))]
    if not payloads:
        raise BadInput(f"no audit payloads in {path}")
    for payload in payloads:
        if payload.get("schema") != SCHEMA:
            raise BadInput(f"{payload.get('member')}: schema {payload.get('schema')} is not {SCHEMA}")
    return payloads


def cell_key(cell: dict) -> tuple[str, str, str]:
    return cell["shape"], cell["path"], cell["form"]


def empty_cell() -> dict:
    counts = {outcome: 0 for outcome in OUTCOMES}
    counts.update(population=0, noAtSite=0, body=0, declaringFile=0)
    return counts


def add_record(row: dict, record: dict) -> None:
    row[record["outcome"]] += 1
    anchors = [entry["anchor"] for entry in record["newErrors"]]
    if record["outcome"] == "REPORTED" and "AT_SITE" not in anchors:
        row["noAtSite"] += 1
    row["body"] += anchors.count("BODY")
    row["declaringFile"] += anchors.count("DECLARING_FILE")


def aggregate(payloads: list[dict]) -> dict:
    """Pools every member per (shape, path, form): populations and outcome counts are summed."""
    cells: dict[tuple[str, str, str], dict] = {}
    coverage: dict[str, dict] = {}
    for payload in payloads:
        for population in payload["cells"]:
            cells.setdefault(cell_key(population["cell"]), empty_cell())["population"] += population["population"]
        for record in payload["records"]:
            add_record(cells.setdefault(cell_key(record["cell"]), empty_cell()), record)
        coverage[payload["member"]] = {key: payload["coverage"][key] for key in COVERAGE_KEYS}
    coverage["pooled"] = {key: sum(member[key] for member in coverage.values()) for key in COVERAGE_KEYS}
    commits = {payload["member"]: payload["commit"] for payload in payloads}
    return {"cells": cells, "coverage": coverage, "commits": commits, "cap": payloads[0]["cap"]}


def measured(row: dict) -> int:
    return sum(row[outcome] for outcome in MEASURED)


def rated(cells: dict) -> dict:
    """Non-lenient cells with at least one measured site, keyed to their silent rate."""
    return {
        key: row["SILENT"] / measured(row)
        for key, row in cells.items()
        if not key[0].startswith("lenient.") and measured(row) >= 1
    }


def route_divergent(cells: dict, rates: dict) -> set:
    def solid(key) -> bool:
        return measured(cells[key]) >= DIVERGENT_MIN_MEASURED

    return {
        key for key, rate in rates.items()
        if solid(key) and rate >= DIVERGENT_HIGH and any(
            other[0] == key[0] and other[1:] != key[1:] and solid(other) and other_rate <= DIVERGENT_LOW
            for other, other_rate in rates.items()
        )
    }


def verdict(cells: dict) -> dict:
    rates = rated(cells)
    silent = sum(cells[key]["population"] * rate for key, rate in rates.items())
    population = sum(cells[key]["population"] for key in rates)
    node_carrying = {key for key in rates if key[0] in NODE_CARRYING_SHAPES and key[1] != "SAME_FILE"}
    divergent = route_divergent(cells, rates)
    mechanism = sum(cells[key]["population"] * rates[key] for key in node_carrying | divergent)
    f_value = silent / population if population else 0.0
    r_value = mechanism / silent if silent else 0.0
    return {
        "F": f_value,
        "R": r_value,
        "S": silent,
        "P": population,
        "nodeCarrying": sorted(node_carrying),
        "routeDivergent": sorted(divergent),
        "verdict": "REOPEN" if f_value >= F_THRESHOLD and r_value >= R_THRESHOLD else "HOLD",
    }


COLUMNS = (
    ("population", "population"), ("measured", None), ("reported", "REPORTED"),
    ("other-rule", "REPORTED_OTHER_RULE"), ("silent", "SILENT"), ("lenient", "LENIENT_BY_DESIGN"),
    ("noisy", "NOISY"), ("skipped", "SKIPPED_PREEXISTING"), ("failed", "FAILED"),
    ("reported, no AT_SITE", "noAtSite"), ("BODY entries", "body"), ("DECLARING_FILE entries", "declaringFile"),
)


def row_values(row: dict) -> list[str]:
    values = [str(measured(row)) if key is None else str(row[key]) for _, key in COLUMNS]
    rate = f"{row['SILENT'] / measured(row):.2f}" if measured(row) else "-"
    return values + [rate]


def table(title: str, labels: list[str], rows: dict) -> list[str]:
    header = labels + [name for name, _ in COLUMNS] + ["silent rate"]
    lines = [f"### {title}", "", "| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    for key in sorted(rows):
        lines.append("| " + " | ".join(list(key) + row_values(rows[key])) + " |")
    return lines + [""]


def by_shape(cells: dict) -> dict:
    shapes: dict[tuple[str], dict] = {}
    for key, row in cells.items():
        pooled = shapes.setdefault((key[0],), empty_cell())
        for column in pooled:
            pooled[column] += row[column]
    return shapes


def coverage_table(coverage: dict) -> list[str]:
    header = ["member"] + list(COVERAGE_KEYS)
    lines = ["### Coverage", "", "| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    for member, counts in coverage.items():
        lines.append("| " + " | ".join([member] + [str(counts[key]) for key in COVERAGE_KEYS]) + " |")
    return lines + [""]


def render(aggregated: dict, result: dict) -> str:
    pooled = aggregated["coverage"]["pooled"]
    commits = ", ".join(f"{member} `{commit}`" for member, commit in sorted(aggregated["commits"].items()))
    lines = [f"CAP = {aggregated['cap']}; members: {commits}", ""]
    lines += table("Shapes (pooled)", ["shape"], by_shape(aggregated["cells"]))
    lines += table("Cells (pooled)", ["shape", "path", "form"], aggregated["cells"])
    lines += coverage_table(aggregated["coverage"])
    lines += [
        "### Verdict",
        "",
        f"- Unmeasured: unresolvedCallee {pooled['unresolvedCallee']}, unresolvedMethod {pooled['unresolvedMethod']}",
        f"- ldocParam {pooled['ldocParam']}: not read by the checker (not LuaCATS)",
        f"- Node-carrying cells: {', '.join('/'.join(key) for key in result['nodeCarrying']) or 'none'}",
        f"- Route-divergent cells: {', '.join('/'.join(key) for key in result['routeDivergent']) or 'none'}",
        f"- S = {result['S']:.2f}, P = {result['P']}",
        f"- F = {result['F']:.2f} (threshold {F_THRESHOLD}), R = {result['R']:.2f} (threshold {R_THRESHOLD})",
        f"- **Verdict: {result['verdict']}**",
    ]
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    if len(argv) != 1:
        print("usage: type_audit_summary.py <type-audit directory>", file=sys.stderr)
        return 2
    try:
        payloads = load(argv[0])
    except BadInput as error:
        print(f"type_audit_summary: {error}", file=sys.stderr)
        return 2
    aggregated = aggregate(payloads)
    print(render(aggregated, verdict(aggregated["cells"])), end="")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
