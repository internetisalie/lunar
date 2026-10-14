#!/usr/bin/env python3
"""Classify the delta between a bare and a pinned member-alone corpus tally (MAINT-37, design 3.5).

A definition library outside the project can only declare names and contract types. It may lower
undeclared-variable counts for the names it declares and resolve requires; it cannot move parsing,
lexing, oracle or recognised-require counts. Every other movement is surfaced for a human, and the
exit code is 1 when any row is UNEXPLAINED, 2 when the two tallies are not comparable.
"""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path

import definition_pins

STRUCTURAL_KEYS = frozenset({
    "parseErrors", "requires", "unresolvedRequires", "oracleDisagreements",
    "oracleFalseAccepts", "oracleTimeouts", "lexerRoundTripFailures", "unmergedTokens",
})
REFRESH_KEYS = ("settingsBroadcasts", "rootsTicks", "physicalPsiChanges")
REPEATED_KEYS = frozenset({"parseErrorFile", "oracleSite"})
INSPECTION_PREFIX = "inspection."
LIBRARY_PREFIX = "library."
UNDECLARED = "inspection.LuaUndeclaredVariable"
HIGHLIGHT_FAILURES = "inspection.highlightFailures"
TOP_COUNT = 5

EXPLAINED = "EXPLAINED"
EXPECTED = "EXPECTED"
UNEXPLAINED = "UNEXPLAINED"
TRIAGE_DROP = "TRIAGE-DROP"
TRIAGE_GLOBAL = "TRIAGE-GLOBAL"
TRIAGE_CONTRACT = "TRIAGE-CONTRACT"


class NotComparable(Exception):
    pass


@dataclass(frozen=True)
class Row:
    key: str
    bare: int
    pinned: int
    delta: int
    cls: str
    evidence: str


def tracked(key: str) -> bool:
    return key in STRUCTURAL_KEYS or key.startswith((INSPECTION_PREFIX, "crash."))


def parse_tally(text: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in text.splitlines():
        if "=" not in line or line.startswith("#"):
            continue
        key, value = line.split("=", 1)
        if key not in REPEATED_KEYS:
            values[key] = value
    return values


def require_same_run(bare: dict[str, str], pinned: dict[str, str]) -> None:
    if bare.get("commit") != pinned.get("commit"):
        raise NotComparable(f"not the same corpus commit (bare {bare.get('commit')}, pinned {pinned.get('commit')})")
    if bare.get("files") != pinned.get("files"):
        raise NotComparable(f"file counts differ (bare {bare.get('files')}, pinned {pinned.get('files')})")


def require_same_refresh(bare: dict[str, str], pinned: dict[str, str]) -> None:
    for key in REFRESH_KEYS:
        for arm, tally in (("bare", bare), ("pinned", pinned)):
            if key not in tally:
                raise NotComparable(f"the {arm} tally has no {key} line")
    for key in REFRESH_KEYS:
        if bare[key] != pinned[key]:
            raise NotComparable(
                f"refresh observable {key} differs (bare {bare[key]}, pinned {pinned[key]}): "
                "the arms did not receive the same refresh"
            )


def has_library(tally: dict[str, str]) -> bool:
    return any(key.startswith(LIBRARY_PREFIX) for key in tally)


def highlight_failures(tally: dict[str, str]) -> int:
    return int(tally.get(HIGHLIGHT_FAILURES, "0"))


def require_comparable(bare: dict[str, str], pinned: dict[str, str]) -> None:
    require_same_run(bare, pinned)
    require_same_refresh(bare, pinned)
    if has_library(bare):
        raise NotComparable("the bare run carried libraries")
    if not has_library(pinned):
        raise NotComparable("nothing to attribute: the pinned run registered no library")
    if highlight_failures(bare) > 0 or highlight_failures(pinned) > 0:
        raise NotComparable("highlight failures make inspection counts non-reproducible (BUG-390)")


def symbol_deltas(bare: dict[str, str], pinned: dict[str, str], tool: str) -> dict[str, int]:
    prefix = f"allsymbol.{tool}."
    symbols = {key.removeprefix(prefix) for key in bare.keys() | pinned.keys() if key.startswith(prefix)}
    deltas = {s: int(pinned.get(prefix + s, "0")) - int(bare.get(prefix + s, "0")) for s in sorted(symbols)}
    return {symbol: value for symbol, value in deltas.items() if value != 0}


def top(deltas: dict[str, int]) -> str:
    ranked = sorted(deltas.items(), key=lambda item: (-abs(item[1]), item[0]))[:TOP_COUNT]
    return ", ".join(f"{symbol}:{value:+d}" for symbol, value in ranked)


@dataclass(frozen=True)
class Movement:
    delta: int
    symbols: dict[str, int]
    library_names: frozenset[str]


def classify_undeclared(movement: Movement) -> tuple[str, str]:
    symbols = movement.symbols
    outside = {s: v for s, v in symbols.items() if s not in movement.library_names}
    risen = {s: v for s, v in symbols.items() if s in movement.library_names and v > 0}
    covered = sum(symbols.values()) == movement.delta
    if not outside and not risen and covered:
        return EXPLAINED, top(symbols)
    evidence = top({**outside, **risen})
    if not covered:
        evidence += f"; symbol tally sums to {sum(symbols.values())}, inspection delta {movement.delta}"
    return UNEXPLAINED, evidence


def classify_inspection(movement: Movement) -> tuple[str, str]:
    evidence = top(movement.symbols)
    if movement.delta < 0:
        return TRIAGE_DROP, evidence
    risen = [symbol for symbol, value in movement.symbols.items() if value > 0]
    if risen and all(symbol in movement.library_names for symbol in risen):
        return TRIAGE_GLOBAL, evidence
    return TRIAGE_CONTRACT, evidence


def classify(key: str, movement: Movement) -> tuple[str, str]:
    if key == UNDECLARED:
        return classify_undeclared(movement)
    if key == "unresolvedRequires":
        if movement.delta < 0:
            return EXPECTED, "library modules now resolve"
        return UNEXPLAINED, "a library cannot unresolve a require"
    if key.startswith(INSPECTION_PREFIX):
        return classify_inspection(movement)
    return UNEXPLAINED, "a definition library cannot move this metric"


def attribute(bare: dict[str, str], pinned: dict[str, str], library_globals: set[str]) -> list[Row]:
    require_comparable(bare, pinned)
    rows = []
    for key in sorted(k for k in bare.keys() | pinned.keys() if tracked(k)):
        before, after = int(bare.get(key, "0")), int(pinned.get(key, "0"))
        if before == after:
            continue
        tool = key.removeprefix(INSPECTION_PREFIX)
        symbols = symbol_deltas(bare, pinned, tool) if key.startswith(INSPECTION_PREFIX) else {}
        cls, evidence = classify(key, Movement(after - before, symbols, frozenset(library_globals)))
        rows.append(Row(key, before, after, after - before, cls, evidence))
    return rows


def exit_code(rows: list[Row]) -> int:
    return 1 if any(row.cls == UNEXPLAINED for row in rows) else 0


def library_heading(pinned: dict[str, str]) -> str:
    pins = [f"{k.removeprefix(LIBRARY_PREFIX)}={v[:8]}" for k, v in sorted(pinned.items()) if k.startswith(LIBRARY_PREFIX)]
    return ", ".join(pins)


def render(corpus: str, pinned: dict[str, str], rows: list[Row]) -> str:
    lines = [
        f"## {corpus} — libraries: {library_heading(pinned)}",
        "| key | bare | pinned | delta | class | evidence |",
        "| :-- | --: | --: | --: | :-- | :-- |",
    ]
    if not rows:
        lines.append("| — | | | 0 | NO-CHANGE | |")
    for row in rows:
        lines.append(f"| {row.key} | {row.bare} | {row.pinned} | {row.delta:+d} | {row.cls} | {row.evidence} |")
    return "\n".join(lines)


def read_tally(directory: Path, corpus: str) -> dict[str, str]:
    return parse_tally((directory / f"{corpus}.tally").read_text())


def pinned_library_globals(pinned: dict[str, str], definitions_dir: Path, catalog_path: Path) -> set[str]:
    catalog = definition_pins.load_catalog(catalog_path)
    names: set[str] = set()
    for key, version in sorted(pinned.items()):
        if not key.startswith(LIBRARY_PREFIX):
            continue
        library_id = key.removeprefix(LIBRARY_PREFIX)
        if library_id not in catalog:
            raise NotComparable(f"library {library_id}: not in the catalog {catalog_path}")
        if catalog[library_id]["version"] != version:
            raise NotComparable(
                f"library {library_id}: the pinned tally carries {version}, the catalog has {catalog[library_id]['version']}"
            )
        archive = definitions_dir / f"{library_id}-{version}.tar.gz"
        if not archive.is_file():
            raise NotComparable(f"library {library_id}: archive {archive} is missing; run tooling/corpus/fetch-corpus.py")
        names |= definition_pins.library_globals(archive, catalog[library_id]["rootPrefix"])
    return names


def parse_arguments(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Classify a bare-vs-pinned corpus tally delta.")
    parser.add_argument("--corpus", required=True)
    parser.add_argument("--bare", required=True, type=Path)
    parser.add_argument("--pinned", required=True, type=Path)
    parser.add_argument("--definitions", type=Path, default=definition_pins.REPO_ROOT / "test/corpus/.definitions")
    parser.add_argument("--catalog", type=Path, default=definition_pins.CATALOG)
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    arguments = parse_arguments(argv)
    bare = read_tally(arguments.bare, arguments.corpus)
    pinned = read_tally(arguments.pinned, arguments.corpus)
    try:
        names = pinned_library_globals(pinned, arguments.definitions, arguments.catalog)
        rows = attribute(bare, pinned, names)
    except NotComparable as failure:
        print(f"attribute_delta: not comparable: {failure}", file=sys.stderr)
        return 2
    print(render(arguments.corpus, pinned, rows))
    return exit_code(rows)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
