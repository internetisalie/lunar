#!/usr/bin/env python3
"""MAINT-45: performance records as CTRF 0.2.0 documents, stored per commit from ``main``.

``assemble`` turns the per-workload records the ``-PwithPerfRecords`` run writes
(``build/perf-records/*.json``) and the host's ``/proc/cpuinfo`` into one CTRF document with one
test per workload. ``ci`` is what the ``build`` job runs: it assembles, prints a ``WOBBLE`` line for
every value that differs between a workload's two passes, and then routes on the event. A ``push``
puts the document against ``HEAD`` as check ``a3`` suite ``perf``, once. Any other event stores
nothing. ``tooling/perf/gate-mode`` decides whether a wobble can fail the run.

Stdlib only. Exit codes: 0 success; 1 a ``loom-cli`` or ``git`` failure, or a wobble in ``gating``
mode; 2 not comparable (no records, a foreign record schema, a missing or unknown gate-mode file).
"""
import argparse
import json
import platform
import re
import subprocess
import sys
from pathlib import Path

import loom_store
from junit_ctrf import NotComparable
from loom_store import LoomError, Slot

CHECK = "a3"
SUITE_ARGV = ("--suite", "perf")
LABEL = "perf"
TOOL_NAME = "lunar-perf-records"
SPEC_VERSION = "0.2.0"
RECORD_SCHEMA = 1
CPUINFO = "/proc/cpuinfo"
REPORT_ONLY = "report-only"
GATING = "gating"
MODES = (REPORT_ONLY, GATING)
UNKNOWN_MODEL = "unknown"
MODEL_NAME = re.compile(r"^model name\s*:\s*(.*)$", re.MULTILINE)
PROCESSOR = re.compile(r"^processor\s*:", re.MULTILINE)
WOBBLE_SECTIONS = (("counts", 0), ("outputs", None))


def read_records(directory) -> list[dict]:
    """Every record in ``directory``, in file-name order. Refuses none, or a foreign schema."""
    records = [json.loads(path.read_text(encoding="utf-8")) for path in sorted(Path(directory).glob("*.json"))]
    if not records:
        raise NotComparable(f"no perf records in {directory}")
    for record in records:
        if record.get("schema") != RECORD_SCHEMA:
            raise NotComparable(f"perf record schema {record.get('schema')!r}, expected {RECORD_SCHEMA}")
    return records


def read_cpu(path) -> dict:
    text = Path(path).read_text(encoding="utf-8")
    model = MODEL_NAME.search(text)
    return {"cpuModel": model.group(1) if model else UNKNOWN_MODEL, "vcpus": len(PROCESSOR.findall(text))}


def _test(record: dict) -> dict:
    return {"name": record["workload"], "testId": record["workload"], "status": "passed",
            "duration": record["durationMs"], "suite": [record["kind"]],
            "extra": {"counts": record["counts"], "fixture": record["fixture"], "outputs": record["outputs"],
                      "warm": record["warm"]}}


def ctrf_document(records: list[dict], cpu: dict) -> bytes:
    """The canonical CTRF bytes for one workloads run (design §3.3, §4.2)."""
    tests = sorted((_test(record) for record in records), key=lambda test: test["name"])
    summary = {"tests": len(tests), "passed": len(tests), "failed": 0, "skipped": 0, "pending": 0, "other": 0,
               "start": 0, "stop": 0}
    environment = {"osPlatform": platform.system().lower(), "extra": cpu}
    doc = {"reportFormat": "CTRF", "specVersion": SPEC_VERSION, "results": {
        "tool": {"name": TOOL_NAME}, "summary": summary, "environment": environment, "tests": tests}}
    return json.dumps(doc, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode() + b"\n"


def write_document(out, data: bytes):
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    Path(out).write_bytes(data)


def read_mode(path) -> str:
    """The gate mode the committed file holds. Refuses a missing file or an unknown mode."""
    mode_file = Path(path)
    if not mode_file.is_file():
        raise NotComparable(f"no gate-mode file at {mode_file}")
    mode = mode_file.read_text(encoding="utf-8").strip()
    if mode not in MODES:
        raise NotComparable(f"{mode_file}: gate mode {mode!r}, expected one of {', '.join(MODES)}")
    return mode


def _section_wobbles(test: dict, section: tuple[str, object]) -> list[str]:
    key, absent = section
    recorded, warm = test["extra"][key], test["extra"]["warm"][key]
    return [f"WOBBLE\t{test['name']}\t{name}\t{warm.get(name, absent)}\t{recorded.get(name, absent)}"
            for name in sorted(recorded.keys() | warm.keys()) if recorded.get(name, absent) != warm.get(name, absent)]


def wobble_lines(doc: dict) -> list[str]:
    """One line for each value the recorded pass and the warm pass disagree on (design §3.6)."""
    tests = sorted(doc["results"]["tests"], key=lambda test: test["name"])
    return [line for test in tests for section in WOBBLE_SECTIONS for line in _section_wobbles(test, section)]


def gate(mode: str, moved: bool) -> int:
    if not moved:
        return 0
    if mode == GATING:
        print("::error::a count moved; accept a rise in tooling/perf/accepted-increases.json or fix it")
        return 1
    print("::warning::a count moved; tooling/perf/gate-mode is report-only, so this does not fail the job")
    return 0


def slot_for(repo: str) -> Slot:
    return Slot(CHECK, SUITE_ARGV, repo, LABEL)


def assemble(args) -> int:
    write_document(args.out, ctrf_document(read_records(args.records), read_cpu(args.cpuinfo)))
    return 0


def ci(args) -> int:
    """What the build job runs (design §3.6, steps 1-5): assemble, print wobbles, then store or stop."""
    slot = slot_for(args.repo)
    mode = read_mode(Path(args.repo, args.gate_mode))
    print(f"perf: gate-mode {mode}")
    data = ctrf_document(read_records(args.records), read_cpu(args.cpuinfo))
    write_document(args.out, data)
    wobbles = wobble_lines(json.loads(data))
    for line in wobbles:
        print(line)
    if args.event == "push":
        if gate(mode, bool(wobbles)) == 1:
            return 1
        return loom_store.store_once(slot, Path(args.out), data)
    print(f"::warning::perf: event {args.event} neither stores nor compares")
    return gate(mode, bool(wobbles))


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    assembling = commands.add_parser("assemble", help="write one CTRF document")
    assembling.add_argument("--records", required=True)
    assembling.add_argument("--out", required=True)
    assembling.add_argument("--cpuinfo", default=CPUINFO)
    assembling.set_defaults(handler=assemble)
    running = commands.add_parser("ci", help="assemble, print wobbles, then store on a push")
    running.add_argument("--records", required=True)
    running.add_argument("--out", required=True)
    running.add_argument("--accepted", required=True)
    running.add_argument("--gate-mode", required=True)
    running.add_argument("--event", required=True)
    running.add_argument("--base-ref", default="")
    running.add_argument("--cpuinfo", default=CPUINFO)
    running.add_argument("--repo", default=".")
    running.set_defaults(handler=ci)
    return parser


def main(argv) -> int:
    args = _parser().parse_args(argv)
    try:
        return args.handler(args)
    except NotComparable as refusal:
        print(f"NOT COMPARABLE: {refusal}", file=sys.stderr)
        return 2
    except LoomError as failure:
        print(f"loom-cli: {failure}", file=sys.stderr)
        return 1
    except subprocess.CalledProcessError as failure:
        print(f"git: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
