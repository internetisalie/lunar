#!/usr/bin/env python3
"""Fetch the pinned fuzzer corpora listed in torture.json into <repo>/test/corpus-torture/<name>.

These are MAINT-35-06's torture inputs: minimized outputs from squeek502's Lua fuzzers, which are
pathological by construction (invalid UTF-8, unterminated long brackets, NUL bytes, 4 MB of one
character). They exercise the lexer invariants and the parse oracle; nothing here is a Lua project,
so there are no requires and no inspections worth counting.

Pinned by ARCHIVE sha256, not a commit SHA — they are release assets. A .corpus-sha stamp holding
that digest makes re-runs a no-op, and a mismatch refuses to unpack: an unverified torture corpus
would silently redefine what the baseline means.

Python rather than shell, matching fetch-corpus.py and fetch-luac.py — the design named a .sh, but
BUG-407's root cause was hand-rolled parsing in a shell script, and a JSON manifest read by `cut`
would be the same mistake with a new format.
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import sys
import tarfile
import tempfile
import urllib.request
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
MANIFEST = Path(os.environ.get("LUNAR_TORTURE_MANIFEST", SCRIPT_DIR / "torture.json"))
TORTURE_ROOT = Path(os.environ.get("LUNAR_TORTURE_ROOT", REPO_ROOT / "test" / "corpus-torture"))


def log(msg: str) -> None:
    print(f"\033[1;34m[torture]\033[0m {msg}")


def die(msg: str) -> None:
    print(f"\033[1;31m[torture] {msg}\033[0m", file=sys.stderr)
    raise SystemExit(1)


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch_one(entry: dict) -> None:
    name, url, expected = entry["name"], entry["url"], entry["sha256"]
    dest = TORTURE_ROOT / name
    stamp = dest / ".corpus-sha"

    if stamp.is_file() and stamp.read_text().strip() == expected:
        log(f"{name} already at {expected[:8]} — skipping")
        return

    log(f"fetching {name} from {url}")
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        archive = work / "asset.tar.gz"
        with urllib.request.urlopen(url) as response, archive.open("wb") as out:
            shutil.copyfileobj(response, out)

        actual = sha256_of(archive)
        if actual != expected:
            die(
                f"{name}: sha256 mismatch — refusing to unpack.\n"
                f"         expected {expected}\n"
                f"         actual   {actual}\n"
                f"         Either the pin in torture.json is wrong or the asset was re-uploaded.\n"
                f"         Do NOT 'fix' this by updating the pin without establishing which."
            )

        staged = work / "unpacked"
        with tarfile.open(archive) as tar:
            # "data" filter: these are hostile inputs by design, so refuse absolute paths, links
            # escaping the tree, and device nodes rather than trusting the archive's metadata.
            tar.extractall(staged, filter="data")

        # Replace wholesale, then stamp last: an interruption re-does the fetch rather than
        # blessing a half-unpacked tree as pinned.
        shutil.rmtree(dest, ignore_errors=True)
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(staged), str(dest))

    files = sum(1 for path in dest.rglob("*") if path.is_file())
    stamp.write_text(expected + "\n")
    log(f"{name} -> {dest} ({files} inputs)")


def main() -> None:
    if not MANIFEST.is_file():
        die(f"Manifest not found: {MANIFEST}")
    manifest = json.loads(MANIFEST.read_text())
    TORTURE_ROOT.mkdir(parents=True, exist_ok=True)
    for entry in manifest["members"]:
        fetch_one(entry)
    log(f"Torture corpora ready under {TORTURE_ROOT}")


if __name__ == "__main__":
    main()
