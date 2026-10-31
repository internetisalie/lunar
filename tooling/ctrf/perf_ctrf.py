#!/usr/bin/env python3
"""MAINT-45: performance records as CTRF 0.2.0 documents, stored per commit from ``main``.

``assemble`` turns the per-workload records the ``-PwithPerfRecords`` run writes
(``build/perf-records/*.json``) and the host's ``/proc/cpuinfo`` into one CTRF document with one
test per workload. ``ci`` is what the ``build`` job runs: it assembles, prints a ``WOBBLE`` line for
every value that differs between a workload's two passes, and then routes on the event. A ``push``
puts the document against ``HEAD`` as check ``a3`` suite ``perf``, once. A ``pull_request`` fetches the
record of the nearest code-identical ancestor of its merge-base and diffs the counts against it. Any
other event stores and compares nothing. ``tooling/perf/gate-mode`` decides whether a wobble or a rise
can fail the run. ``stability`` walks ``main``'s records and says when gating may be turned on.
``series`` prints each workload's counts and timings across ``main`` or the release tags, in commit order.

Stdlib only. Exit codes: 0 success; 1 a ``loom-cli`` or ``git`` failure, or a wobble or an unaccepted
rise in ``gating`` mode; 2 not comparable (no records, a foreign record schema, a missing or unknown
gate-mode or accepted-increases file, a shallow checkout for ``stability``); 3 ``fetch`` found no record.
"""
import argparse
import json
import os
import platform
import re
import subprocess
import sys
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

import loom_store
from junit_ctrf import NotComparable, code_identical_first_parents
from loom_store import LoomError, Slot, Wanted

CHECK = "a3"
SUITE_ARGV = ("--suite", "perf")
LABEL = "perf"
TOOL_NAME = "lunar-perf-records"
SPEC_VERSION = "0.2.0"
RECORD_SCHEMA = 1
ACCEPTED_SCHEMA = 1
DEFAULT_MAX_DEPTH = 50
TIMING_FACTOR = 2
TIMING_FLOOR_MS = 300
STABILITY_PAIRS = 5
STABILITY_CROSS_MODEL_PAIRS = 1
STABILITY_MAX_DEPTH = 400
WORK_DIR = "build/perf-series"
CPUINFO = "/proc/cpuinfo"
REPORT_ONLY = "report-only"
GATING = "gating"
MODES = (REPORT_ONLY, GATING)
UNKNOWN_MODEL = "unknown"
MODEL_NAME = re.compile(r"^model name\s*:\s*(.*)$", re.MULTILINE)
PROCESSOR = re.compile(r"^processor\s*:", re.MULTILINE)
WOBBLE_SECTIONS = (("counts", 0), ("outputs", None))
COUNT_PATHS = (
    "src/main",
    "src/test/kotlin/net/internetisalie/lunar/perfrecords",
    "src/test/kotlin/net/internetisalie/lunar/BaseDocumentTest.kt",
    "src/test/kotlin/net/internetisalie/lunar/IndexedDocumentTest.kt",
    "build.gradle.kts",
    "gradle.properties",
    "settings.gradle.kts",
    "gradle",
    "tooling/ci-image",
    ".gitea/workflows/build-plugin.yml",
)


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


def _is_perf(doc: dict) -> bool:
    return doc["results"].get("tool", {}).get("name") == TOOL_NAME


def _model(doc: dict) -> str:
    return doc["results"].get("environment", {}).get("extra", {}).get("cpuModel", UNKNOWN_MODEL)


def _by_name(doc: dict) -> dict[str, dict]:
    return {test["name"]: test for test in doc["results"]["tests"]}


def git_env() -> dict[str, str]:
    """The environment for a git call that reaches the forge: ``GITEA_TOKEN`` becomes an
    ``http.extraHeader`` through ``GIT_CONFIG_*``, so the token never appears on an argv."""
    token = os.environ.get("GITEA_TOKEN")
    if not token:
        return dict(os.environ)
    return {**os.environ, "GIT_CONFIG_COUNT": "1", "GIT_CONFIG_KEY_0": "http.extraHeader",
            "GIT_CONFIG_VALUE_0": f"Authorization: token {token}"}


def _git(repo: str, argv: list[str], env: dict[str, str] | None = None) -> str:
    return subprocess.run(["git", "-C", repo, *argv], capture_output=True, text=True, check=True, env=env).stdout


def _remote_git(repo: str, argv: list[str]) -> str:
    """A git call that may touch the remote: the fetch, and the lazy blob read after it (DR-06, DR-15)."""
    return _git(repo, argv, git_env())


def _valid_entry(entry) -> bool:
    if not isinstance(entry, dict):
        return False
    texts = all(isinstance(entry.get(key), str) and entry[key] for key in ("workload", "counter", "reason"))
    numbers = all(type(entry.get(key)) is int for key in ("from", "to"))
    return texts and numbers and entry["to"] > entry["from"]


def parse_accepted(text: str) -> list[dict]:
    """The entries of an accepted-increases file's text. Refuses anything malformed (design §3.5)."""
    try:
        doc = json.loads(text)
    except json.JSONDecodeError as failure:
        raise NotComparable(f"accepted increases are not JSON: {failure}") from failure
    if not isinstance(doc, dict) or doc.get("schema") != ACCEPTED_SCHEMA:
        raise NotComparable(f"accepted increases: schema is not {ACCEPTED_SCHEMA}")
    accepted = doc.get("accepted")
    if not isinstance(accepted, list) or not all(_valid_entry(entry) for entry in accepted):
        raise NotComparable("accepted increases: each entry needs a workload, counter, reason and from < to")
    return accepted


def read_accepted(path) -> list[dict]:
    accepted_file = Path(path)
    if not accepted_file.is_file():
        raise NotComparable(f"no accepted-increases file at {accepted_file}")
    return parse_accepted(accepted_file.read_text(encoding="utf-8"))


def accepted_at(repo: str, commit: str, path: str) -> list[dict]:
    """The accepted entries at ``commit``; none when the file did not exist there."""
    if not _git(repo, ["ls-tree", "--name-only", commit, "--", path]).strip():
        return []
    return parse_accepted(_remote_git(repo, ["show", f"{commit}:{path}"]))


def own_entries(current: list[dict], previous: list[dict]) -> list[dict]:
    """The entries of ``current`` this PR adds or edits: whole-entry comparison (design §3.5)."""
    return [entry for entry in current if entry not in previous]


@dataclass(frozen=True)
class Change:
    """One counter of one workload, before and after."""

    workload: str
    counter: str
    before: int
    after: int

    def fields(self) -> str:
        return f"{self.workload}\t{self.counter}\t{self.before}\t{self.after}"


def _covering_entry(change: Change, accepted: list[dict]) -> dict | None:
    return next((entry for entry in accepted
                 if entry["workload"] == change.workload and entry["counter"] == change.counter
                 and entry["from"] == change.before and change.after <= entry["to"]), None)


def _counter_line(change: Change, accepted: list[dict]) -> tuple[str, dict | None]:
    if change.after < change.before:
        return f"FALL\t{change.fields()}", None
    entry = _covering_entry(change, accepted)
    if entry is None:
        return f"RISE\t{change.fields()}", None
    return f"ACCEPTED\t{change.fields()}\t{entry['reason']}", entry


def _changes(name: str, tests: tuple[dict, dict]) -> list[Change]:
    before, after = tests[0]["extra"]["counts"], tests[1]["extra"]["counts"]
    changes = [Change(name, counter, before.get(counter, 0), after.get(counter, 0))
               for counter in sorted(before.keys() | after.keys())]
    return [change for change in changes if change.before != change.after]


def _output_lines(name: str, tests: tuple[dict, dict]) -> list[str]:
    before, after = tests[0]["extra"]["outputs"], tests[1]["extra"]["outputs"]
    return [f"OUTPUT_CHANGED\t{name}\t{key}\t{before.get(key)}\t{after.get(key)}"
            for key in sorted(before.keys() | after.keys()) if before.get(key) != after.get(key)]


def _compare_test(name: str, tests: tuple[dict, dict], accepted: list[dict]) -> tuple[list[str], list[dict]]:
    if tests[0]["extra"]["fixture"] != tests[1]["extra"]["fixture"]:
        return [f"FIXTURE_CHANGED\t{name}"], []
    judged = [_counter_line(change, accepted) for change in _changes(name, tests)]
    lines = [line for line, _ in judged] + _output_lines(name, tests)
    return lines, [entry for _, entry in judged if entry is not None]


def _timing_line(name: str, durations: tuple[int, int]) -> str:
    before, after = durations
    flagged = after >= TIMING_FACTOR * before and after - before >= TIMING_FLOOR_MS
    return f"TIMING\t{name}\t{before}\t{after}" + ("\tFLAG" if flagged else "")


def timing_lines(before: dict, after: dict) -> list[str]:
    """Timings are comparable only on one CPU model; they are reported and never gate (design §3.4)."""
    if _model(before) != _model(after):
        return [f"TIMING_NOT_COMPARABLE\t{_model(before)}\t{_model(after)}"]
    older, newer = _by_name(before), _by_name(after)
    return [_timing_line(name, (older[name]["duration"], newer[name]["duration"]))
            for name in sorted(older.keys() & newer.keys())]


def diff(before: dict, after: dict, accepted: list[dict]) -> tuple[list[str], int]:
    """Count-by-count lines and 1 when any count rose without an accepted entry (design §3.4)."""
    if not (_is_perf(before) and _is_perf(after)):
        raise NotComparable(f"not both {TOOL_NAME} documents")
    older, newer = _by_name(before), _by_name(after)
    compared = [_compare_test(name, (older[name], newer[name]), accepted)
                for name in sorted(older.keys() & newer.keys())]
    used = [entry for _, entries in compared for entry in entries]
    lines = [line for lines_of, _ in compared for line in lines_of]
    lines += [f"ADDED\t{name}" for name in sorted(newer.keys() - older.keys())]
    lines += [f"REMOVED\t{name}" for name in sorted(older.keys() - newer.keys())]
    lines += [f"ACCEPTANCE_UNUSED\t{entry['workload']}\t{entry['counter']}\t{entry['from']}"
              for entry in accepted if entry not in used]
    lines += timing_lines(before, after)
    return lines, 1 if any(line.startswith("RISE\t") for line in lines) else 0


def deepen(repo: str, base_ref: str):
    """Make the base branch and the merge-base reachable from a depth-1 checkout (DR-06)."""
    if not base_ref:
        raise NotComparable("no base ref to compare against")
    shallow = _git(repo, ["rev-parse", "--is-shallow-repository"]).strip() == "true"
    head = _git(repo, ["rev-parse", "HEAD"]).strip()
    refspec = f"+refs/heads/{base_ref}:refs/remotes/origin/{base_ref}"
    unshallow = ["--unshallow"] if shallow else []
    _remote_git(repo, ["fetch", "-q", *unshallow, "--filter=blob:none", "origin", head, refspec])


def baseline(slot: Slot, shas: list[str], out: Path) -> tuple[str, int, dict] | None:
    """The first perf record among ``shas``, written to ``out``."""
    return loom_store.fetch_first(slot, shas, Wanted(out, _is_perf))


def fetch(args) -> int:
    """The perf record of the nearest code-identical first-parent ancestor of ``--commit``."""
    shas = code_identical_first_parents(args.repo, args.commit, args.max_depth)
    hit = baseline(slot_for(args.repo), shas, Path(args.out))
    if hit is None:
        print(f"no perf record for a code-identical ancestor of {args.commit}")
        return loom_store.EXIT_UNMEASURED
    print(f"fetched {hit[0]} distance={hit[1]}")
    return 0


def _diff_command(args) -> int:
    before = json.loads(Path(args.before).read_text(encoding="utf-8"))
    after = json.loads(Path(args.after).read_text(encoding="utf-8"))
    lines, code = diff(before, after, read_accepted(args.accepted))
    for line in lines:
        print(line)
    return code


def compare(args, slot: Slot, after: dict) -> int:
    """Diff ``after`` against the baseline of the PR's merge-base (design §3.6)."""
    current = read_accepted(Path(args.repo, args.accepted))
    deepen(args.repo, args.base_ref)
    merge_base = _git(args.repo, ["merge-base", "HEAD", f"origin/{args.base_ref}"]).strip()
    own = own_entries(current, accepted_at(args.repo, merge_base, args.accepted))
    shas = code_identical_first_parents(args.repo, merge_base, DEFAULT_MAX_DEPTH)
    hit = baseline(slot, shas, Path(args.out).with_name("before.ctrf.json"))
    if hit is None:
        print(f"::warning::no perf record for a code-identical ancestor of {merge_base}; counts were not gated")
        return 0
    print(f"baseline {hit[0]} distance={hit[1]}")
    lines, code = diff(hit[2], after, own)
    for line in lines:
        print(line)
    return code


def assemble(args) -> int:
    write_document(args.out, ctrf_document(read_records(args.records), read_cpu(args.cpuinfo)))
    return 0


def ci(args) -> int:
    """What the build job runs (design §3.6): assemble, print wobbles, then store, compare or stop."""
    slot = slot_for(args.repo)
    mode = read_mode(Path(args.repo, args.gate_mode))
    print(f"perf: gate-mode {mode}")
    data = ctrf_document(read_records(args.records), read_cpu(args.cpuinfo))
    write_document(args.out, data)
    after = json.loads(data)
    wobbles = wobble_lines(after)
    for line in wobbles:
        print(line)
    if args.event == "push":
        if gate(mode, bool(wobbles)) == 1:
            return 1
        return loom_store.store_once(slot, Path(args.out), data)
    if args.event != "pull_request":
        print(f"::warning::perf: event {args.event} neither stores nor compares")
        return gate(mode, bool(wobbles))
    code = compare(args, slot, after)
    return gate(mode, bool(wobbles) or code == 1)


@dataclass
class Credit:
    """Eligible pairs with identical counts, per workload: all of them, and those across two CPU models."""

    pairs: Counter = field(default_factory=Counter)
    cross: Counter = field(default_factory=Counter)


@dataclass
class Walk:
    """What `stability` has seen so far, newest record first (design §3.8)."""

    head: dict | None = None
    newer: tuple[str, dict] | None = None
    credit: Credit = field(default_factory=Credit)
    eligible: int = 0
    ignored: int = 0
    broken: bool = False
    models: set[str] = field(default_factory=set)


def same_count_code(repo: str, older: str, newer: str) -> bool:
    """True when no path in COUNT_PATHS differs between the two commits."""
    changed = _git(repo, ["diff", "--no-renames", "--name-only", older, newer, "--", *COUNT_PATHS])
    return not changed.strip()


def _counter_differences(name: str, tests: tuple[dict, dict]) -> list[str]:
    newer, older = tests[0]["extra"]["counts"], tests[1]["extra"]["counts"]
    return [f"{name}\t{counter}\t{newer.get(counter, 0)}\t{older.get(counter, 0)}"
            for counter in sorted(newer.keys() | older.keys()) if newer.get(counter, 0) != older.get(counter, 0)]


def count_differences(newer: dict, older: dict) -> list[str]:
    """Count lines that differ between two records, for workloads with an equal fixture."""
    later, earlier = _by_name(newer), _by_name(older)
    return [line for name in sorted(later.keys() & earlier.keys())
            if later[name]["extra"]["fixture"] == earlier[name]["extra"]["fixture"]
            for line in _counter_differences(name, (later[name], earlier[name]))]


def _across_models(newer: dict, older: dict) -> bool:
    models = {_model(newer), _model(older)}
    return len(models) == 2 and UNKNOWN_MODEL not in models


def credit_pair(credit: Credit, pair: tuple[dict, dict], head: dict):
    """Credit every workload of ``head`` that both records of an eligible, identical pair hold on head's fixture."""
    newer, older = pair
    cross = _across_models(newer, older)
    wanted, later, earlier = _by_name(head), _by_name(newer), _by_name(older)
    for name in sorted(wanted.keys() & later.keys() & earlier.keys()):
        fixtures = {wanted[name]["extra"]["fixture"], later[name]["extra"]["fixture"],
                    earlier[name]["extra"]["fixture"]}
        if len(fixtures) == 1:
            credit.pairs[name] += 1
            if cross:
                credit.cross[name] += 1


def waiting_lines(head: dict, credit: Credit) -> list[str]:
    return [f"WAITING\t{name}\t{credit.pairs[name]}\t{credit.cross[name]}" for name in sorted(_by_name(head))
            if credit.pairs[name] < STABILITY_PAIRS or credit.cross[name] < STABILITY_CROSS_MODEL_PAIRS]


def walk_pair(repo: str, walk: Walk, record: tuple[str, dict]) -> bool:
    """Fold ``record`` and the previous one into ``walk``; False when the pair differs (a BREAK)."""
    sha, doc = record
    newer_sha, newer_doc = walk.newer
    if not same_count_code(repo, sha, newer_sha):
        walk.ignored += 1
    else:
        walk.eligible += 1
        lines = count_differences(newer_doc, doc)
        if lines:
            for line in lines:
                print(f"BREAK\t{newer_sha}\t{sha}\t{line}")
            walk.broken = True
            return False
        credit_pair(walk.credit, (newer_doc, doc), walk.head)
    walk.newer = record
    return True


def _record_among(slot: Slot, shas: list[str], work: str) -> dict | None:
    """The first perf record stored for any of ``shas``, or None (docs-only or unbuilt commits)."""
    scratch = Path(work, "record.json")
    hit = loom_store.fetch_first(slot, shas, Wanted(scratch, _is_perf))
    scratch.unlink(missing_ok=True)
    return None if hit is None else hit[2]


def _report_wobbles(record: tuple[str, dict]) -> bool:
    sha, doc = record
    lines = wobble_lines(doc)
    for line in lines:
        print(f"BREAK\t{sha}\t{line}")
    return bool(lines)


def _fold(repo: str, walk: Walk, record: tuple[str, dict]) -> bool:
    """Take one record into ``walk``, newest first. False when the walk is over."""
    walk.models.update({_model(record[1])} - {UNKNOWN_MODEL})
    if _report_wobbles(record):
        walk.broken = True
        return False
    if walk.head is None:
        walk.head, walk.newer = record[1], record
        return True
    return walk_pair(repo, walk, record) and bool(waiting_lines(walk.head, walk.credit))


def _verdict(walk: Walk) -> int:
    print(f"eligible\t{walk.eligible}")
    print(f"ignored\t{walk.ignored}")
    print(f"cpuModels\t{','.join(sorted(walk.models))}")
    waiting = [] if walk.head is None else waiting_lines(walk.head, walk.credit)
    for line in waiting:
        print(line)
    stable = walk.head is not None and not walk.broken and not waiting
    print("STABLE" if stable else "NOT_YET")
    return 0 if stable else 1


def stability(args) -> int:
    """Whether `main`'s records show counts equal across hosts, enough to turn gating on (design §3.8)."""
    slot = slot_for(args.repo)
    if _git(args.repo, ["rev-parse", "--is-shallow-repository"]).strip() == "true":
        raise NotComparable("shallow checkout: the walk would stop silently at its boundary")
    walk = Walk()
    for sha in loom_store.first_parents(args.repo, args.first_parent, args.max_depth):
        doc = _record_among(slot, [sha], args.work)
        if doc is not None and not _fold(args.repo, walk, (sha, doc)):
            break
    return _verdict(walk)


def _tag_points(repo: str, depth: int) -> list[tuple[str, list[str]]]:
    """Each release tag, oldest first, and the commits holding its code: a tag may sit on a docs-only commit."""
    tags = _git(repo, ["tag", "--list", "v*", "--sort=creatordate"]).split()
    return [(tag, code_identical_first_parents(repo, tag, depth)) for tag in tags]


def _series_points(args) -> list[tuple[str, list[str]]]:
    """(label, the commits to search) for each point of the series, oldest first."""
    if args.tags:
        return _tag_points(args.repo, args.max_depth)
    shas = loom_store.first_parents(args.repo, args.first_parent, args.max_depth)
    return [(sha, [sha]) for sha in reversed(shas)]


def _series_rows(point: tuple[int, str], doc: dict) -> list[tuple]:
    """(workload, counter, position, label, value) rows; a duration row also carries the CPU model."""
    position, label = point
    tests = doc["results"]["tests"]
    counts = [(test["name"], counter, position, label, value)
              for test in tests for counter, value in test["extra"]["counts"].items()]
    return counts + [(test["name"], "duration", position, label, test["duration"], _model(doc)) for test in tests]


def series(args) -> int:
    """Print every workload's values in commit order, so drift no single PR shows is visible (design §3.7)."""
    slot = slot_for(args.repo)
    found = [(label, _record_among(slot, shas, args.work)) for label, shas in _series_points(args)]
    rows = [row for position, (label, doc) in enumerate(found) if doc is not None
            for row in _series_rows((position, label), doc)]
    for row in sorted(rows, key=lambda row: row[:3]):
        print("\t".join(str(value) for value in row[:2] + row[3:]))
    print(f"unmeasured\t{sum(doc is None for _, doc in found)}")
    return 0


def _add_assemble(commands):
    assembling = commands.add_parser("assemble", help="write one CTRF document")
    assembling.add_argument("--records", required=True)
    assembling.add_argument("--out", required=True)
    assembling.add_argument("--cpuinfo", default=CPUINFO)
    assembling.set_defaults(handler=assemble)


def _add_ci(commands):
    running = commands.add_parser("ci", help="assemble, then store on a push or compare on a pull request")
    running.add_argument("--records", required=True)
    running.add_argument("--out", required=True)
    running.add_argument("--accepted", required=True)
    running.add_argument("--gate-mode", required=True)
    running.add_argument("--event", required=True)
    running.add_argument("--base-ref", default="")
    running.add_argument("--cpuinfo", default=CPUINFO)
    running.add_argument("--repo", default=".")
    running.set_defaults(handler=ci)


def _add_fetch_and_diff(commands):
    fetching = commands.add_parser("fetch", help="the record of the nearest code-identical ancestor")
    fetching.add_argument("--commit", required=True)
    fetching.add_argument("--out", required=True)
    fetching.add_argument("--max-depth", type=int, default=DEFAULT_MAX_DEPTH)
    fetching.add_argument("--repo", default=".")
    fetching.set_defaults(handler=fetch)
    diffing = commands.add_parser("diff", help="compare two documents, count by count")
    diffing.add_argument("before")
    diffing.add_argument("after")
    diffing.add_argument("--accepted", required=True)
    diffing.set_defaults(handler=_diff_command)


def _add_stability(commands):
    judging = commands.add_parser("stability", help="whether main's records show gating can be turned on")
    judging.add_argument("--first-parent", required=True)
    judging.add_argument("--max-depth", type=int, default=STABILITY_MAX_DEPTH)
    judging.add_argument("--work", default=WORK_DIR)
    judging.add_argument("--repo", default=".")
    judging.set_defaults(handler=stability)


def _add_series(commands):
    reporting = commands.add_parser("series", help="each workload's values across main or the release tags")
    selector = reporting.add_mutually_exclusive_group(required=True)
    selector.add_argument("--first-parent")
    selector.add_argument("--tags", action="store_true")
    reporting.add_argument("--max-depth", type=int, default=DEFAULT_MAX_DEPTH)
    reporting.add_argument("--work", default=WORK_DIR)
    reporting.add_argument("--repo", default=".")
    reporting.set_defaults(handler=series)


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    for add in (_add_assemble, _add_ci, _add_fetch_and_diff, _add_stability, _add_series):
        add(commands)
    return parser


def _scrubbed(text: str) -> str:
    """``text`` without the forge token, in case git ever echoes the header."""
    token = os.environ.get("GITEA_TOKEN")
    return text.replace(token, "***") if token else text


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
        print(f"git: {_scrubbed(str(failure))}\n{_scrubbed(failure.stderr or '')}".rstrip(), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
