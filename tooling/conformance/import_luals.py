#!/usr/bin/env python3
"""MAINT-55 design §3.1: import LuaLS's type-checking diagnostic tests as LuaCATS conformance cases.

A one-shot importer from a pinned LuaLS checkout. It refuses a pin whose diagnostic groups no
longer match the codes it knows (the scope check), converts every in-scope ``TEST [[ … ]]`` block
into a case under ``<out>/cases/<code>.lua``, records every exclusion and the pin in
``<out>/import.json``, and copies LuaLS's ``LICENSE`` to ``<out>/LICENSE-LuaLS``.

Usage: ``import_luals.py --luals <checkout> --out <dir>``. Exits 0, or 2 with a message on stderr.
The output is byte-identical on a re-run.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPOSITORY = "https://github.com/LuaLS/lua-language-server"
CODE_RULES = {
    "param-type-mismatch": "arg",
    "assign-type-mismatch": None,
    "return-type-mismatch": "return.mismatch",
    "missing-return-value": "return.count",
    "redundant-return-value": "return.count",
    "missing-return": "return.missing",
    "redundant-parameter": "arity.tooMany",
    "missing-parameter": "arity.tooFew",
    "missing-fields": "field.missing",
    "undefined-field": "field.undefined",
    "inject-field": "field.inject",
    "need-check-nil": "nil.check",
    "cast-local-type": "cast.local",
    "cast-type-mismatch": "cast.mismatch",
}
EXCLUDED_UNBALANCED = ("redundant-value", "unbalanced-assignments")
BASELINE = {
    "castNumberToInteger": "false",
    "weakUnionCheck": "false",
    "weakNilCheck": "false",
    "checkTableShape": "false",
}

REGISTER = re.compile(r"m\.register\s*\{(.*?)\}\s*\{(.*?)\}", re.DOTALL)
REGISTER_CODE = re.compile(r"^\s*'([a-z0-9-]+)'", re.MULTILINE)
REGISTER_GROUP = re.compile(r"group\s*=\s*'([^']+)'")
SCAN = re.compile(
    r"^config\.set\(\s*nil\s*,\s*'([^']+)'\s*,\s*([^)]*?)\s*\)|^TEST\s*\[(=*)\[(.*?)\]\3\]",
    re.MULTILINE | re.DOTALL,
)
FOREIGN_MARKER = re.compile(r"<[?#]")
ASSIGNMENT = re.compile(r"(?<![=~<>])=(?!=)")
FIELD_TARGET = re.compile(r"^\s*[A-Za-z_]\w*(\.[A-Za-z_]\w*|\[[^\]]*\])+\s*=(?!=)")
MUST = " -- E"


class ImportFailure(Exception):
    """The checkout cannot be imported."""


def git_commit(checkout: Path) -> str:
    completed = subprocess.run(
        ["git", "-C", str(checkout), "rev-parse", "HEAD"], capture_output=True, text=True, check=False,
    )
    if completed.returncode != 0:
        raise ImportFailure(f"not a git checkout: {checkout}")
    return completed.stdout.strip()


def groups(diagnostic_lua: str) -> dict[str, set[str]]:
    """Group name -> the codes ``m.register { … } { group = … }`` puts in it."""
    found: dict[str, set[str]] = {}
    for match in REGISTER.finditer(diagnostic_lua):
        group = REGISTER_GROUP.search(match.group(2))
        if group:
            found.setdefault(group.group(1), set()).update(REGISTER_CODE.findall(match.group(1)))
    return found


def scope_check(diagnostic_lua: str) -> None:
    by_group = groups(diagnostic_lua)
    type_check = by_group.get("type-check", set())
    unbalanced = by_group.get("unbalanced", set())
    for code in sorted(CODE_RULES):
        if code not in type_check and code not in unbalanced:
            raise ImportFailure(f"scope: {code} is in neither type-check nor unbalanced")
    for code in sorted(type_check - CODE_RULES.keys()):
        raise ImportFailure(f"scope: {code} is in type-check but has no rule")
    for code in sorted(unbalanced - CODE_RULES.keys() - set(EXCLUDED_UNBALANCED)):
        raise ImportFailure(f"scope: {code} is in unbalanced but is neither imported nor excluded")


def blocks(text: str) -> list[tuple[int, str, bool]]:
    """Every ``TEST`` block as (ordinal, body, config-dependent), in file order."""
    state = dict(BASELINE)
    found = []
    for match in SCAN.finditer(text):
        if match.group(1) is not None:
            key = match.group(1).removeprefix("Lua.type.")
            if match.group(2) == "nil":
                state.pop(key, None)
            else:
                state[key] = match.group(2)
            continue
        body = match.group(4)
        body = body[2:] if body.startswith("\r\n") else body[1:] if body.startswith("\n") else body
        found.append((len(found) + 1, body, state != BASELINE))
    return found


def exclusion(body: str, configured: bool) -> str | None:
    if configured:
        return "config"
    if FOREIGN_MARKER.search(body):
        return "foreign-marker"
    if any("<!" in line and line.lstrip().startswith("---") for line in body.split("\n")):
        return "marker-on-doc-line"
    return None


def convert(body: str) -> list[str]:
    lines = body.split("\n")
    if lines and lines[-1] == "":
        lines = lines[:-1]
    return [
        line.replace("<!", "").replace("!>", "") + MUST if "<!" in line else line.replace("!>", "")
        for line in lines
    ]


def assign_rule(lines: list[str]) -> str:
    marked = [line[: -len(MUST)] for line in lines if line.endswith(MUST)]
    if marked:
        anchor = marked[0]
    else:
        assignments = [
            line for line in lines if not line.lstrip().startswith("--") and ASSIGNMENT.search(line)
        ]
        anchor = assignments[-1] if assignments else ""
    return "field.assign" if FIELD_TARGET.match(anchor) else "assign.local"


def rule_for(code: str, lines: list[str]) -> str:
    rule = CODE_RULES[code]
    return assign_rule(lines) if rule is None else rule


def header_line(code: str, commit: str) -> str:
    return (
        f"-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/{code}.lua"
        f" @ {commit[:8]} (MIT); see LICENSE-LuaLS"
    )


def import_code(checkout: Path, code: str, commit: str) -> tuple[str, dict, list[dict]]:
    """One code's case file text, its ``codes`` entry and its exclusions."""
    text = (checkout / "test" / "diagnostics" / f"{code}.lua").read_text(encoding="utf-8")
    out = [header_line(code, commit)]
    excluded = []
    found = blocks(text)
    for ordinal, body, configured in found:
        reason = exclusion(body, configured)
        if reason:
            excluded.append({"source": f"{code}.lua#{ordinal}", "reason": reason})
            continue
        lines = convert(body)
        out.append("")
        out.append(f"-- ==== case {code}-{ordinal:03d} rule={rule_for(code, lines)} source={code}.lua#{ordinal}")
        out.extend(lines)
    entry = {"blocks": len(found), "imported": len(found) - len(excluded)}
    return "\n".join(out) + "\n", entry, excluded


def run(checkout: Path, out: Path) -> None:
    commit = git_commit(checkout)
    scope_check((checkout / "script" / "proto" / "diagnostic.lua").read_text(encoding="utf-8"))
    codes, excluded = {}, []
    (out / "cases").mkdir(parents=True, exist_ok=True)
    for code in sorted(CODE_RULES):
        case_text, codes[code], code_excluded = import_code(checkout, code, commit)
        excluded.extend(code_excluded)
        (out / "cases" / f"{code}.lua").write_text(case_text, encoding="utf-8", newline="\n")
    record = {
        "schema": 1,
        "luals": {"repository": REPOSITORY, "commit": commit, "license": "MIT"},
        "codes": codes,
        "excluded": excluded,
    }
    (out / "import.json").write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    shutil.copyfile(checkout / "LICENSE", out / "LICENSE-LuaLS")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--luals", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        run(args.luals, args.out)
    except (ImportFailure, OSError) as failure:
        print(failure, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
