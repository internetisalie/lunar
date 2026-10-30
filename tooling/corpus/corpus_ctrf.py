#!/usr/bin/env python3
"""MAINT-40: corpus-sweep findings as CTRF 0.2.0 documents, fetched per commit and compared.

Each member's sweep writes ``build/corpus-findings/<member>.json`` (design §4.1). ``assemble`` reads
that directory and writes a CTRF document holding one ``failed`` test per finding, whose ``testId``
is the finding's stable fingerprint (§3.3) and whose ``results.environment.testEnvironment`` is the
run's derived identity (§3.2). The same findings always give the same bytes.

``store`` assembles, then puts the document against ``HEAD`` in loom's conformance store (check
``a3``, suite ``corpus``) unless ``HEAD`` already has a record. ``fetch`` reads the stored document of
the nearest first-parent ancestor through ``loom-cli``; ``diff`` compares two documents finding by
finding.

Stdlib only. Exit codes: 0 success; 1 a ``loom-cli`` failure, or ``diff`` found new findings;
2 not comparable (mixed JVMs, a partial run set, an unknown findings schema, differing identities),
or ``store`` refusing a run that is not full-shape; 3 ``fetch`` found no stored document.
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "ctrf"))
import loom_store  # noqa: E402
from loom_store import EXIT_UNMEASURED, LoomError  # noqa: E402

CHECK = "a3"
TOOL_NAME = "lunar-corpus-sweep"
SPEC_VERSION = "0.2.0"
COMPARABILITY_KEYS = ("commit", "roots", "luaLevel", "moduleRoot", "target", "definitionLibraries")
SCHEMA = 1
DEFAULT_MAX_DEPTH = 50
# The arguments that select suite `corpus` on resolve and put (loom-cli v0.90.0, DR-10): the record
# is stored as `a3.corpus.ctrf.json`. `get` takes the key resolve returns, so it carries none.
SUITE_ARGV: tuple[str, ...] = ("--suite", "corpus")


class NotComparable(Exception):
    """The runs, or two documents, cannot be compared or combined. Never a regression."""


def load_runs(directory) -> list[dict]:
    """Every findings file in ``directory``, sorted by member. Refuses an unknown schema."""
    runs = []
    for path in sorted(Path(directory).glob("*.json")):
        run = json.loads(path.read_text(encoding="utf-8"))
        if run.get("schema") != SCHEMA:
            raise NotComparable(f"{path.name}: findings schema {run.get('schema')!r}, expected {SCHEMA}")
        runs.append(run)
    return sorted(runs, key=lambda r: r["member"])


def _manifest_entries(manifest: dict) -> dict[str, dict]:
    return {entry["name"]: entry for entry in manifest["corpora"]}


def _shape(runs: list[dict], manifest: dict) -> str:
    tokens = {run["jvmToken"] for run in runs}
    if len(tokens) != 1:
        raise NotComparable("runs come from more than one JVM")
    members = {run["member"] for run in runs}
    if members == set(_manifest_entries(manifest)):
        return "full"
    if len(runs) == 1:
        return "member"
    raise NotComparable(f"partial run set: {sorted(members)} of {sorted(_manifest_entries(manifest))}")


def _comparability(manifest: dict, member: str) -> dict:
    entry = _manifest_entries(manifest).get(member)
    if entry is None:
        raise NotComparable(f"member {member!r} is not in the manifest")
    return {key: entry.get(key) for key in COMPARABILITY_KEYS}


def identity_of(runs: list[dict], manifest: dict) -> str:
    """``full-<16 hex>`` or ``member-<name>-<16 hex>``, from the shape, pins and observed libraries."""
    shape = _shape(runs, manifest)
    lines = []
    for run in sorted(runs, key=lambda r: r["member"]):
        member = run["member"]
        line = {"member": member, "entry": _comparability(manifest, member), "libraries": run["libraries"]}
        lines.append(json.dumps(line, sort_keys=True, separators=(",", ":")))
    digest = hashlib.sha256("\n".join(lines).encode()).hexdigest()[:16]
    return f"full-{digest}" if shape == "full" else f"member-{runs[0]['member']}-{digest}"


def _key(member: str, finding: dict) -> str:
    fields = [member, finding["rule"], finding["path"], str(finding["startOffset"]),
              str(finding["endOffset"]), finding.get("symbol") or ""]
    return "\0".join(fields)


def _sort_key(finding: dict) -> tuple:
    return (finding["rule"], finding["path"], finding["startOffset"], finding["endOffset"],
            finding.get("symbol") or "", finding["message"], finding["line"], finding["column"],
            finding["count"])


def fingerprints(member: str, findings: list[dict]) -> list[tuple[str, dict]]:
    """(fingerprint, finding) pairs. Ordinals are assigned after a total sort, so list order never matters."""
    seen: dict[str, int] = {}
    pairs = []
    for finding in sorted(findings, key=_sort_key):
        key = _key(member, finding)
        ordinal = seen.get(key, 0)
        seen[key] = ordinal + 1
        pairs.append((hashlib.sha256(f"{key}\0{ordinal}".encode()).hexdigest(), finding))
    return pairs


def _extra(finding: dict, message: str) -> dict:
    extra = {"ruleId": finding["rule"], "uri": finding["path"], "message": message, "count": finding["count"]}
    if finding["startOffset"] >= 0:
        extra["startOffset"] = finding["startOffset"]
        extra["endOffset"] = finding["endOffset"]
    if finding["line"] != 0:
        extra["line"] = finding["line"]
        extra["column"] = finding["column"]
    if finding.get("symbol") is not None:
        extra["symbol"] = finding["symbol"]
    return extra


def _test(member: str, fingerprint: str, finding: dict) -> dict:
    message = finding["message"] or finding["rule"]
    return {
        "name": f"{member} {finding['rule']} {finding['path']} {fingerprint}",
        "testId": fingerprint,
        "status": "failed",
        "duration": 0,
        "suite": [member],
        "message": message,
        "extra": _extra(finding, message),
    }


def _test_order(test: dict) -> tuple:
    extra = test["extra"]
    return (test["suite"][0], extra["uri"], extra.get("startOffset", -1), extra.get("endOffset", -1),
            extra["ruleId"], extra.get("symbol") or "", test["testId"])


def _environment(runs: list[dict], manifest: dict) -> dict:
    members = {
        run["member"]: {"files": run["files"], "requires": run["requires"], "libraries": run["libraries"],
                        "comparability": _comparability(manifest, run["member"])}
        for run in runs
    }
    return {"testEnvironment": identity_of(runs, manifest),
            "extra": {"shape": _shape(runs, manifest), "members": members}}


def ctrf_document(runs: list[dict], manifest: dict) -> bytes:
    """The canonical CTRF bytes for one invocation's runs (design §3.4, §4.2)."""
    environment = _environment(runs, manifest)
    tests = [_test(run["member"], fp, f) for run in runs for fp, f in fingerprints(run["member"], run["findings"])]
    tests.sort(key=_test_order)
    n = len(tests)
    summary = {"tests": n, "failed": n, "passed": 0, "skipped": 0, "pending": 0, "other": 0, "start": 0, "stop": 0}
    doc = {"reportFormat": "CTRF", "specVersion": SPEC_VERSION, "results": {
        "tool": {"name": TOOL_NAME}, "summary": summary, "tests": tests, "environment": environment}}
    return json.dumps(doc, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode() + b"\n"


def _assemble_to(findings_dir, manifest_path, out) -> bytes:
    manifest = json.loads(Path(manifest_path).read_text(encoding="utf-8"))
    data = ctrf_document(load_runs(findings_dir), manifest)
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    Path(out).write_bytes(data)
    return data


def assemble(args) -> int:
    _assemble_to(args.findings, args.manifest, args.out)
    return 0


def _slot(repo: str) -> loom_store.Slot:
    return loom_store.Slot(CHECK, SUITE_ARGV, repo, "corpus")


def store(args) -> int:
    """Put this run's document against HEAD as check ``a3`` suite ``corpus``, once (§3.5)."""
    data = _assemble_to(args.findings, args.manifest, args.out)
    shape = _identity(json.loads(data)).split("-", 1)[0]
    if shape != "full":
        print(f"refusing to store a {shape} run: suite corpus holds full runs only")
        return 2
    return loom_store.store_once(_slot(args.repo), Path(args.out), data)


def _identity(doc: dict) -> str | None:
    return doc["results"].get("environment", {}).get("testEnvironment")


def _wanted(like) -> str | None:
    if like is None:
        return None
    return _identity(json.loads(Path(like).read_text(encoding="utf-8")))


def fetch(args) -> int:
    """The nearest first-parent ancestor's stored corpus document, within ``--max-depth`` (§3.6)."""
    want = _wanted(args.like)

    def accept(doc: dict) -> bool:
        return doc["results"].get("tool", {}).get("name") == TOOL_NAME and (want is None or _identity(doc) == want)

    shas = loom_store.first_parents(args.repo, args.commit, args.max_depth)
    hit = loom_store.fetch_first(_slot(args.repo), shas, loom_store.Wanted(Path(args.out), accept))
    if hit is not None:
        sha, distance, doc = hit
        print(f"fetched {sha} identity={_identity(doc)} distance={distance}")
        return 0
    print(f"no stored result within {args.max_depth} first-parent ancestors of {args.commit}")
    return EXIT_UNMEASURED


def _members(doc: dict) -> dict:
    return doc["results"]["environment"]["extra"]["members"]


def _assert_comparable(before: dict, after: dict) -> None:
    for name, doc in (("before", before), ("after", after)):
        if doc["results"]["tool"]["name"] != TOOL_NAME:
            raise NotComparable(f"{name} is not a {TOOL_NAME} document")
    if _identity(before) != _identity(after):
        raise NotComparable(f"identity {_identity(before)} vs {_identity(after)}")
    if set(_members(before)) != set(_members(after)):
        raise NotComparable(f"members {sorted(_members(before))} vs {sorted(_members(after))}")
    for member, seen in _members(before).items():
        now = _members(after)[member]
        for field in ("files", "requires"):
            if seen.get(field) != now.get(field):
                raise NotComparable(f"{member} {field} {seen.get(field)} vs {now.get(field)}")


def _by_member(doc: dict) -> dict[str, dict[str, dict]]:
    grouped: dict[str, dict[str, dict]] = {member: {} for member in _members(doc)}
    for test in doc["results"]["tests"]:
        grouped.setdefault(test["suite"][0], {})[test["testId"]] = test
    return grouped


def _finding_line(member: str, verb: str, test: dict) -> str:
    extra = test["extra"]
    where = f"{extra['uri']}:{extra.get('line', 0)}:{extra.get('column', 0)}"
    return f"{member}\t{verb}\t{extra['ruleId']}\t{where}\t{extra['message']}"


def _rule_counts(tests: dict[str, dict]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for test in tests.values():
        rule = test["extra"]["ruleId"]
        counts[rule] = counts.get(rule, 0) + test["extra"]["count"]
    return counts


def _member_delta(member: str, before: dict[str, dict], after: dict[str, dict]) -> list[str]:
    lines = [_finding_line(member, "NEW", after[i]) for i in after.keys() - before.keys()]
    lines += [_finding_line(member, "RESOLVED", before[i]) for i in before.keys() - after.keys()]
    was, now = _rule_counts(before), _rule_counts(after)
    for rule in was.keys() | now.keys():
        if was.get(rule, 0) != now.get(rule, 0):
            lines.append(f"{member}\tCOUNT\t{rule}\t{was.get(rule, 0)}\t{now.get(rule, 0)}")
    return lines


def diff(before: dict, after: dict) -> tuple[list[str], int]:
    """Finding-level delta (§3.7). Exit 1 when any finding is new. Raises NotComparable."""
    _assert_comparable(before, after)
    old, new = _by_member(before), _by_member(after)
    lines = sorted(line for member in old for line in _member_delta(member, old[member], new.get(member, {})))
    return lines, 1 if any("\tNEW\t" in line for line in lines) else 0


def _diff_command(args) -> int:
    documents = [json.loads(Path(path).read_text(encoding="utf-8")) for path in (args.before, args.after)]
    lines, code = diff(*documents)
    for line in lines:
        print(line)
    return code


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    assembling = commands.add_parser("assemble", help="write one CTRF document from a findings directory")
    assembling.add_argument("--findings", required=True)
    assembling.add_argument("--manifest", required=True)
    assembling.add_argument("--out", required=True)
    assembling.set_defaults(handler=assemble)
    storing = commands.add_parser("store", help="assemble, then put the document against HEAD unless stored")
    storing.add_argument("--findings", required=True)
    storing.add_argument("--manifest", required=True)
    storing.add_argument("--out", required=True)
    storing.add_argument("--repo", default=".")
    storing.set_defaults(handler=store)
    fetching = commands.add_parser("fetch", help="fetch the nearest first-parent ancestor's stored document")
    fetching.add_argument("--commit", required=True)
    fetching.add_argument("--out", required=True)
    fetching.add_argument("--like", help="only a document whose identity equals this document's")
    fetching.add_argument("--max-depth", type=int, default=DEFAULT_MAX_DEPTH)
    fetching.add_argument("--repo", default=".")
    fetching.set_defaults(handler=fetch)
    diffing = commands.add_parser("diff", help="compare two documents finding by finding")
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
