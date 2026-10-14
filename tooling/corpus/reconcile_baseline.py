#!/usr/bin/env python3
"""Account for a re-recorded corpus baseline term by term (MAINT-37, design 3.11).

The classifier (attribute_delta.py) attributes the member-alone delta between two refreshed arms.
A record commits the full-shape delta between two configurations. This script subtracts the
attributed delta from the committed one so that what is left is the shape offset alone, and
measures the refresh effect (first pin) or previous-pin effect (re-pin) as its own labelled table.
Exit 1 when any row blocks, 2 when the arguments are inconsistent.
"""
from __future__ import annotations

import argparse
import sys
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path

from attribute_delta import STRUCTURAL_KEYS, parse_tally, tracked

SHAPE_OFFSET_BOUND = 2
SHAPE_RESIDUAL_BOUND = 2 * SHAPE_OFFSET_BOUND
HIGHLIGHT_FAILURES = "inspection.highlightFailures"
LIBRARY_PREFIX = "library."
UNRESOLVED_REQUIRES = "unresolvedRequires"
EMPTY_ROW = "| — | 0 | 0 | 0 | no |"
VERSION_WIDTH = 8


@dataclass(frozen=True)
class Residual:
    key: str
    committed: int
    attributed: int
    residual: int
    blocking: bool


@dataclass(frozen=True)
class StartRow:
    key: str
    before: int
    bare: int
    effect: int
    blocking: bool


def always_blocking(key: str) -> bool:
    return key in STRUCTURAL_KEYS or key.startswith("crash.") or key == HIGHLIGHT_FAILURES


def key_delta(before: dict[str, str], after: dict[str, str]) -> dict[str, int]:
    deltas = {
        key: int(after.get(key, "0")) - int(before.get(key, "0"))
        for key in sorted(before.keys() | after.keys())
        if tracked(key)
    }
    return {key: value for key, value in deltas.items() if value != 0}


def reconcile(committed: dict[str, int], attributed: dict[str, int], bound: int = SHAPE_RESIDUAL_BOUND) -> list[Residual]:
    rows = []
    for key in sorted(committed.keys() | attributed.keys()):
        taken, explained = committed.get(key, 0), attributed.get(key, 0)
        residual = taken - explained
        if residual != 0:
            rows.append(Residual(key, taken, explained, residual, always_blocking(key) or abs(residual) > bound))
    return rows


def previous_pins(before: dict[str, str]) -> list[tuple[str, str]]:
    return [(key.removeprefix(LIBRARY_PREFIX), value) for key, value in sorted(before.items()) if key.startswith(LIBRARY_PREFIX)]


def start_effect(before: dict[str, str], bare: dict[str, str]) -> tuple[str, list[StartRow]]:
    pins = previous_pins(before)
    rows = []
    for key in sorted(k for k in before.keys() | bare.keys() if tracked(k)):
        start, end = int(before.get(key, "0")), int(bare.get(key, "0"))
        effect = start - end if pins else end - start
        if effect == 0:
            continue
        blocking = always_blocking(key)
        if pins:
            blocking = effect > 0 if key == UNRESOLVED_REQUIRES else blocking
        rows.append(StartRow(key, start, end, effect, blocking))
    if not pins:
        return "refresh effect", rows
    listed = ", ".join(f"{library_id}={version[:VERSION_WIDTH]}" for library_id, version in pins)
    return f"previous pin effect ({listed})", rows


def residual_exit(rows: Sequence[Residual | StartRow]) -> int:
    return 1 if any(row.blocking for row in rows) else 0


def signed(value: int) -> str:
    return f"{value:+d}" if value != 0 else "0"


def yes_no(flag: bool) -> str:
    return "yes" if flag else "no"


def render(corpus: str, rows: list[Residual], title: str = "shape residual") -> str:
    lines = [f"## {corpus} — {title}", "| key | committed | attributed | residual | blocking |", "| :-- | --: | --: | --: | :-- |"]
    for row in rows:
        lines.append(
            f"| {row.key} | {signed(row.committed)} | {signed(row.attributed)} | {signed(row.residual)} | {yes_no(row.blocking)} |"
        )
    return "\n".join(lines if rows else [*lines, EMPTY_ROW])


def render_start(corpus: str, title: str, rows: list[StartRow]) -> str:
    lines = [f"## {corpus} — {title}", "| key | before | bare | effect | blocking |", "| :-- | --: | --: | --: | :-- |"]
    for row in rows:
        lines.append(f"| {row.key} | {row.before} | {row.bare} | {signed(row.effect)} | {yes_no(row.blocking)} |")
    return "\n".join(lines if rows else [*lines, EMPTY_ROW])


def parse_arguments(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Reconcile a re-recorded corpus baseline.")
    parser.add_argument("--corpus", required=True)
    parser.add_argument("--old", required=True, type=Path)
    parser.add_argument("--new", required=True, type=Path)
    parser.add_argument("--before", type=Path)
    parser.add_argument("--bare", type=Path)
    parser.add_argument("--pinned", type=Path)
    parser.add_argument("--offset", action="store_true")
    return parser.parse_args(argv)


def argument_error(arguments: argparse.Namespace) -> str | None:
    directories = [arguments.before, arguments.bare, arguments.pinned]
    given = sum(directory is not None for directory in directories)
    if arguments.offset and given:
        return "--offset compares two records and takes no --before, --bare or --pinned"
    if given not in (0, len(directories)):
        return "--before, --bare and --pinned go together"
    return None


def read_tally(directory: Path, corpus: str) -> dict[str, str]:
    return parse_tally((directory / f"{corpus}.tally").read_text())


def reconcile_with_tallies(arguments: argparse.Namespace, committed: dict[str, int]) -> int:
    before = read_tally(arguments.before, arguments.corpus)
    bare = read_tally(arguments.bare, arguments.corpus)
    pinned = read_tally(arguments.pinned, arguments.corpus)
    rows = reconcile(committed, key_delta(before, pinned))
    title, start = start_effect(before, bare)
    print(render(arguments.corpus, rows))
    print()
    print(render_start(arguments.corpus, title, start))
    return residual_exit([*rows, *start])


def main(argv: list[str]) -> int:
    arguments = parse_arguments(argv)
    error = argument_error(arguments)
    if error:
        print(f"reconcile_baseline: {error}", file=sys.stderr)
        return 2
    committed = key_delta(parse_tally(arguments.old.read_text()), parse_tally(arguments.new.read_text()))
    if arguments.offset:
        rows = reconcile(committed, {}, SHAPE_OFFSET_BOUND)
        print(render(arguments.corpus, rows, "shape offset"))
        return residual_exit(rows)
    if arguments.before is None:
        rows = reconcile(committed, {})
        print(render(arguments.corpus, rows))
        return residual_exit(rows)
    return reconcile_with_tallies(arguments, committed)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
