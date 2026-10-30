"""MAINT-48: the loom conformance store calls shared by the corpus and unit-test CTRF tools.

A ``Slot`` names one record per commit: a check, an optional suite and a repository. ``lookup``
resolves a commit's record, ``store_once`` puts a document against ``HEAD`` unless the slot already
holds one, and ``fetch_first`` walks a list of commits for the first record a caller accepts.

Stdlib only. ``loom-cli`` is taken from ``$LOOM_CLI`` (default ``loom-cli``); a resolve exit of 3
means unmeasured, and any other non-zero exit is a ``LoomError``.
"""
import hashlib
import json
import os
import subprocess
import sys
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

LOOM_CLI = os.environ.get("LOOM_CLI", "loom-cli")
EXIT_UNMEASURED = 3


class LoomError(Exception):
    """``loom-cli`` failed for a reason other than "unmeasured"."""


@dataclass(frozen=True)
class Slot:
    """Where one commit's record lives: ``check``, the suite arguments (empty for the primary record)."""

    check: str
    suite_argv: tuple[str, ...]
    repo: str
    label: str


@dataclass(frozen=True)
class Wanted:
    """What ``fetch_first`` writes (``out``) and which fetched documents it keeps (``accept``)."""

    out: Path
    accept: Callable[[dict], bool]


def loom(argv) -> subprocess.CompletedProcess:
    return subprocess.run([LOOM_CLI, *argv], capture_output=True, text=True, check=False)


def output(result: subprocess.CompletedProcess) -> str:
    return (result.stdout + result.stderr).strip()


def lookup(slot: Slot, sha: str) -> dict | None:
    """The slot's stored record for ``sha``: the resolve ``record``, or None when unmeasured."""
    result = loom(["artifact", "resolve", "--commit", sha, "--check", slot.check, "--repo", slot.repo, "--json",
                   *slot.suite_argv])
    if result.returncode == EXIT_UNMEASURED:
        return None
    if result.returncode != 0:
        raise LoomError(f"resolve {sha} exited {result.returncode}: {output(result)}")
    return json.loads(result.stdout)["checks"][0]["record"]


def store_once(slot: Slot, out: Path, data: bytes) -> int:
    """Put ``out`` (whose bytes are ``data``) against HEAD in ``slot``, unless HEAD already has a record."""
    record = lookup(slot, "HEAD")
    if record is not None:
        if record["loom-sha256"] == hashlib.sha256(data).hexdigest():
            print("store: already stored, identical")
        else:
            print(f"WARNING store: HEAD already has a different {slot.check} {slot.label} record; kept the first")
        return 0
    result = loom(["artifact", "put", "conformance", str(out), "--commit", "HEAD", "--check", slot.check,
                   "--no-item", "--repo", slot.repo, *slot.suite_argv])
    if result.returncode != 0:
        print(f"store: put exited {result.returncode}: {output(result)}", file=sys.stderr)
        return 1
    print(f"store: {result.stdout.strip()}")
    return 0


def first_parents(repo: str, commit: str, depth: int) -> list[str]:
    argv = ["git", "-C", repo, "rev-list", "--first-parent", f"--max-count={depth}", commit]
    return subprocess.run(argv, capture_output=True, text=True, check=True).stdout.split()


def get(record: dict, part: Path) -> dict:
    """Download ``record`` to ``part`` and parse it. Raises LoomError, leaving no ``part`` behind."""
    part.unlink(missing_ok=True)
    result = loom(["artifact", "get", record["key"], "--output", str(part)])
    if result.returncode != 0:
        part.unlink(missing_ok=True)
        raise LoomError(f"get {record['key']} exited {result.returncode}: {output(result)}")
    return json.loads(part.read_text(encoding="utf-8"))


def fetch_first(slot: Slot, shas: list[str], wanted: Wanted) -> tuple[str, int, dict] | None:
    """(sha, distance, document) of the first of ``shas`` whose record ``wanted`` accepts, written to its ``out``."""
    out = wanted.out
    out.parent.mkdir(parents=True, exist_ok=True)
    part = Path(f"{out}.part")
    for distance, sha in enumerate(shas):
        record = lookup(slot, sha)
        if record is None:
            continue
        doc = get(record, part)
        if not wanted.accept(doc):
            part.unlink()
            continue
        part.replace(out)
        return sha, distance, doc
    return None
