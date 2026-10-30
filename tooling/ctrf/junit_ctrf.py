#!/usr/bin/env python3
"""MAINT-48: unit-test results as CTRF 0.2.0 documents, stored per commit, fetched and compared.

``assemble`` turns Gradle's JUnit XML (``build/test-results/test/TEST-*.xml``) and the test-set
sidecar the ``test`` task writes beside it (``build/test-results/test-filter.json``) into one CTRF
document with one test per ``testcase``. Its ``testId`` is ``<class>.<method>`` (``#<n>`` only for
repeats) and ``results.environment.testEnvironment`` is derived from the sidecar's filter patterns.
The same inputs always give the same bytes.

``store`` assembles, then puts the document against ``HEAD`` in loom's conformance store (check
``a3``, no suite) unless ``HEAD`` already has a record. ``fetch`` reads the record of the nearest
first-parent ancestor whose code is identical to the given commit's; ``diff`` compares two
documents test by test.

Stdlib only. Exit codes: 0 success; 1 a ``loom-cli`` failure, or ``diff`` found a new failure or a
passed test now skipped; 2 not comparable (a missing or unknown sidecar, no XML, a duplicate
``testId``, a foreign document), or ``store`` refusing a partial run; 3 ``fetch`` found no record;
4 ``diff`` found fewer executed tests.
"""
import argparse
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ElementTree
from collections import Counter
from pathlib import Path

import loom_store
from loom_store import EXIT_UNMEASURED, LoomError

CHECK = "a3"
SUITE_ARGV: tuple[str, ...] = ()
LABEL = "unit-test"
TOOL_NAME = "lunar-unit-tests"
SPEC_VERSION = "0.2.0"
FILTER_SCHEMA = 1
FILTER_LISTS = ("excludePatterns", "includePatterns", "commandLineIncludePatterns")
MESSAGE_CAP = 1000
DEFAULT_MAX_DEPTH = 50
EXIT_DROP = 4
# The `changes` job's docs-only rule (build-plugin.yml): a path matching it is not code.
DOCS_ONLY_PATH = re.compile(r"^docs/|\.md$")
# A line break and the whitespace around it, so a message stays on one diff line.
LINE_BREAK = re.compile(r"\s*[\r\n]+\s*")
OUTCOME_TAGS = (("failure", "failed"), ("error", "failed"), ("skipped", "skipped"))
EXECUTED = ("passed", "failed")


class NotComparable(Exception):
    """The inputs, or two documents, cannot be assembled or compared. Never a regression."""


def read_filter(path) -> dict:
    """The sidecar with each pattern list sorted. Refuses a missing file or an unknown schema."""
    sidecar = Path(path)
    if not sidecar.is_file():
        raise NotComparable(f"no test-filter.json at {sidecar}")
    doc = json.loads(sidecar.read_text(encoding="utf-8"))
    if doc.get("schema") != FILTER_SCHEMA:
        raise NotComparable(f"{sidecar}: filter schema {doc.get('schema')!r}, expected {FILTER_SCHEMA}")
    return dict(doc, **{name: sorted(doc.get(name, [])) for name in FILTER_LISTS})


def _message(child: ElementTree.Element) -> str:
    message = child.get("message") or ""
    if not message:
        lines = (child.text or "").strip().splitlines()
        message = lines[0].strip() if lines else ""
    return LINE_BREAK.sub(" ", message.strip())[:MESSAGE_CAP]


def outcome(testcase: ElementTree.Element) -> tuple[str, str | None, str]:
    """(status, the XML child tag that gave it or None, message) for one ``testcase``."""
    for tag, status in OUTCOME_TAGS:
        child = testcase.find(tag)
        if child is not None:
            return status, tag, _message(child)
    return "passed", None, ""


def _case(testcase: ElementTree.Element, file_name: str) -> dict:
    status, raw, message = outcome(testcase)
    seconds = float(testcase.get("time") or "0")
    return {"classname": testcase.get("classname", ""), "method": testcase.get("name", ""), "file": file_name,
            "status": status, "raw": raw, "message": message, "duration": round(seconds * 1000)}


def read_cases(directory) -> list[dict]:
    """Every ``testcase`` of every ``TEST-*.xml`` in ``directory``. Refuses a directory with none."""
    paths = sorted(Path(directory).glob("TEST-*.xml"))
    if not paths:
        raise NotComparable(f"no JUnit XML in {directory}")
    return [_case(testcase, path.name) for path in paths for testcase in ElementTree.parse(path).iter("testcase")]


def _name(case: dict) -> str:
    return f"{case['classname']}.{case['method']}"


def _identity_order(case: dict) -> tuple:
    return case["classname"], case["method"], case["file"], case["message"], case["status"], case["duration"]


def identify(cases: list[dict]) -> list[tuple[str, dict]]:
    """(testId, case) pairs. Ordinals follow a total sort, so file and element order never matter."""
    seen: dict[str, int] = {}
    pairs = []
    for case in sorted(cases, key=_identity_order):
        name = _name(case)
        ordinal = seen.get(name, 0)
        seen[name] = ordinal + 1
        pairs.append((name if ordinal == 0 else f"{name}#{ordinal}", case))
    repeated = sorted(test_id for test_id, count in Counter(test_id for test_id, _ in pairs).items() if count > 1)
    if repeated:
        raise NotComparable(f"duplicate testId {repeated[0]}")
    return pairs


def _filter_lists(filter_doc: dict) -> dict:
    return {name: filter_doc[name] for name in FILTER_LISTS}


def test_set_identity(filter_doc: dict) -> str:
    """``set-<16 hex>``, derived from the sidecar's three pattern lists."""
    canonical = json.dumps(_filter_lists(filter_doc), sort_keys=True, separators=(",", ":"))
    return "set-" + hashlib.sha256(canonical.encode()).hexdigest()[:16]


def _test(test_id: str, case: dict) -> dict:
    test = {"name": _name(case), "testId": test_id, "status": case["status"], "duration": case["duration"],
            "suite": [case["classname"]]}
    if case["raw"] is not None:
        test["rawStatus"] = case["raw"]
    if case["message"]:
        test["message"] = case["message"]
    return test


def _summary(tests: list[dict]) -> dict:
    counts = Counter(test["status"] for test in tests)
    return {"tests": len(tests), "passed": counts["passed"], "failed": counts["failed"], "skipped": counts["skipped"],
            "pending": 0, "other": 0, "start": 0, "stop": 0}


def ctrf_document(cases: list[dict], filter_doc: dict) -> bytes:
    """The canonical CTRF bytes for one ``test`` invocation (design §3.4, §4.3)."""
    tests = sorted((_test(test_id, case) for test_id, case in identify(cases)), key=lambda test: test["testId"])
    environment = {"testEnvironment": test_set_identity(filter_doc), "extra": {"filter": _filter_lists(filter_doc)}}
    doc = {"reportFormat": "CTRF", "specVersion": SPEC_VERSION, "results": {
        "tool": {"name": TOOL_NAME}, "summary": _summary(tests), "tests": tests, "environment": environment}}
    return json.dumps(doc, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode() + b"\n"


def _assemble_to(results, filter_path, out) -> tuple[bytes, dict]:
    filter_doc = read_filter(filter_path)
    data = ctrf_document(read_cases(results), filter_doc)
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    Path(out).write_bytes(data)
    return data, filter_doc


def assemble(args) -> int:
    _assemble_to(args.results, args.filter, args.out)
    return 0


def _slot(repo: str) -> loom_store.Slot:
    return loom_store.Slot(CHECK, SUITE_ARGV, repo, LABEL)


def store(args) -> int:
    """Put this run's document against HEAD as check ``a3``, no suite, once (design §3.5)."""
    data, filter_doc = _assemble_to(args.results, args.filter, args.out)
    partial = filter_doc["commandLineIncludePatterns"]
    if partial:
        print(f"refusing to store a partial run: --tests {', '.join(partial)}")
        return 2
    return loom_store.store_once(_slot(args.repo), Path(args.out), data)


def _code_identical(repo: str, ancestor: str, commit: str) -> bool:
    argv = ["git", "-C", repo, "diff", "--no-renames", "--name-only", ancestor, commit]
    changed = subprocess.run(argv, capture_output=True, text=True, check=True).stdout.splitlines()
    return all(DOCS_ONLY_PATH.search(path) for path in changed)


def code_identical_first_parents(repo: str, commit: str, depth: int) -> list[str]:
    """The first parents of ``commit`` up to the first one whose code differs from ``commit``'s."""
    prefix = []
    for sha in loom_store.first_parents(repo, commit, depth):
        if not _code_identical(repo, sha, commit):
            break
        prefix.append(sha)
    return prefix


def _tool_name(doc: dict) -> str | None:
    return doc["results"].get("tool", {}).get("name")


def _test_environment(doc: dict) -> str | None:
    return doc["results"].get("environment", {}).get("testEnvironment")


def fetch(args) -> int:
    """The unit-test record of the nearest code-identical first-parent ancestor (design §3.6)."""
    shas = code_identical_first_parents(args.repo, args.commit, args.max_depth)
    wanted = loom_store.Wanted(Path(args.out), lambda doc: _tool_name(doc) == TOOL_NAME)
    hit = loom_store.fetch_first(_slot(args.repo), shas, wanted)
    if hit is None:
        print(f"no unit-test record for a code-identical first-parent ancestor of {args.commit} "
              f"(searched {len(shas)})")
        return EXIT_UNMEASURED
    sha, distance, doc = hit
    print(f"fetched {sha} identity={_test_environment(doc)} distance={distance}")
    return 0


def _pattern(pattern: str) -> re.Pattern:
    return re.compile("".join(".*" if char == "*" else re.escape(char) for char in pattern))


def _hits(patterns: list[str], test: dict) -> bool:
    targets = (test["suite"][0], test["name"])
    return any(_pattern(pattern).fullmatch(target) for pattern in patterns for target in targets)


def selected(filter_doc: dict, test: dict) -> bool:
    """Whether a ``test`` run under ``filter_doc``'s patterns would include ``test`` (design §3.7)."""
    includes, command_line = filter_doc["includePatterns"], filter_doc["commandLineIncludePatterns"]
    return ((not includes or _hits(includes, test)) and (not command_line or _hits(command_line, test))
            and not _hits(filter_doc["excludePatterns"], test))


def _filter_of(doc: dict) -> dict:
    return doc["results"]["environment"]["extra"]["filter"]


def _by_id(doc: dict) -> dict[str, dict]:
    return {test["testId"]: test for test in doc["results"]["tests"]}


def _change(test_id: str, before: dict, after: dict) -> str | None:
    was, now = before["status"], after["status"]
    if was == now:
        return None
    if was in ("passed", "skipped") and now == "failed":
        return f"NEWLY_FAILING\t{test_id}\t{after.get('message', '')}"
    if was == "passed" and now == "skipped":
        return f"NEWLY_SKIPPED\t{test_id}"
    if was == "failed" and now == "passed":
        return f"NEWLY_PASSING\t{test_id}"
    return f"STATUS\t{was}->{now}\t{test_id}"


def _one_sided(tests: dict[str, dict], other_filter: dict, names: tuple[str, str]) -> list[str]:
    """Lines for tests only one side has: ``names`` = (the in-set verb, the out-of-set side)."""
    in_set, side = names
    return [f"{in_set}\t{test['status']}\t{test_id}" if selected(other_filter, test)
            else f"OUT_OF_SET\t{side}\t{test['status']}\t{test_id}" for test_id, test in tests.items()]


def _executed(doc: dict, other_filter: dict) -> int:
    return sum(1 for test in doc["results"]["tests"] if test["status"] in EXECUTED and selected(other_filter, test))


def _verdict(lines: list[str], dropped: bool) -> int:
    regression = ("NEWLY_FAILING\t", "NEWLY_SKIPPED\t", "ADDED\tfailed\t", "OUT_OF_SET\tafter\tfailed\t")
    if any(line.startswith(regression) for line in lines):
        return 1
    return EXIT_DROP if dropped else 0


def diff(before: dict, after: dict) -> tuple[list[str], int]:
    """Test-level delta (design §3.8). Raises NotComparable on a foreign document."""
    for side, doc in (("before", before), ("after", after)):
        if _tool_name(doc) != TOOL_NAME:
            raise NotComparable(f"{side} is not a {TOOL_NAME} document")
    old, new = _by_id(before), _by_id(after)
    lines = [line for test_id in old.keys() & new.keys() if (line := _change(test_id, old[test_id], new[test_id]))]
    lines += _one_sided({i: new[i] for i in new.keys() - old.keys()}, _filter_of(before), ("ADDED", "after"))
    lines += _one_sided({i: old[i] for i in old.keys() - new.keys()}, _filter_of(after), ("REMOVED", "before"))
    lines.sort()
    executed_before, executed_after = _executed(before, _filter_of(after)), _executed(after, _filter_of(before))
    code = _verdict(lines, executed_after < executed_before)
    lines.append(f"EXECUTED\t{executed_before}\t{executed_after}")
    if executed_after < executed_before:
        lines.append(f"EXECUTED_DROP\t{executed_before - executed_after}")
    return lines, code


def _diff_command(args) -> int:
    documents = [json.loads(Path(path).read_text(encoding="utf-8")) for path in (args.before, args.after)]
    lines, code = diff(*documents)
    for line in lines:
        print(line)
    return code


def _results_arguments(command: argparse.ArgumentParser) -> argparse.ArgumentParser:
    command.add_argument("--results", required=True, help="the JUnit XML directory")
    command.add_argument("--filter", required=True, help="the test task's test-filter.json")
    command.add_argument("--out", required=True)
    return command


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    _results_arguments(commands.add_parser("assemble", help="write one CTRF document")).set_defaults(handler=assemble)
    storing = _results_arguments(commands.add_parser("store", help="assemble, then put against HEAD unless stored"))
    storing.add_argument("--repo", default=".")
    storing.set_defaults(handler=store)
    fetching = commands.add_parser("fetch", help="fetch the nearest code-identical ancestor's record")
    fetching.add_argument("--commit", required=True)
    fetching.add_argument("--out", required=True)
    fetching.add_argument("--max-depth", type=int, default=DEFAULT_MAX_DEPTH)
    fetching.add_argument("--repo", default=".")
    fetching.set_defaults(handler=fetch)
    diffing = commands.add_parser("diff", help="compare two documents test by test")
    diffing.add_argument("before")
    diffing.add_argument("after")
    diffing.set_defaults(handler=_diff_command)
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


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
