#!/usr/bin/env python3
"""Fetch the pinned corpus fixtures listed in corpus.json into <repo>/test/corpus/<name>.

The checkouts are stripped of .git so the gce-builder rsync (which pushes the whole test/ tree
dereferenced) stays small. A .corpus-sha stamp makes re-runs a no-op when already at the pin.

Replaces fetch-corpus.sh (2026-08-05, BUG-407). The shell version read positional TSV with
`IFS=$'\t' read -r ...`; tab is IFS *whitespace*, so bash collapsed runs of tabs and every column
after an empty field shifted left — `prune` bound the language level and fed `rm -rf`. Python's
stdlib json removes the hand-written parser rather than repairing it.
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
MANIFEST = SCRIPT_DIR / "corpus.json"
CORPUS_ROOT = Path(os.environ.get("LUNAR_CORPUS_ROOT", REPO_ROOT / "test" / "corpus"))


def log(msg: str) -> None:
    print(f"\033[1;34m[corpus]\033[0m {msg}")


def die(msg: str) -> None:
    print(f"\033[1;31m[corpus] {msg}\033[0m", file=sys.stderr)
    raise SystemExit(1)


def git(*args: str, cwd: Path) -> None:
    subprocess.run(["git", *args], cwd=cwd, check=True, stdout=subprocess.DEVNULL)


def fetch_one(entry: dict) -> None:
    name, url, commit = entry["name"], entry["url"], entry["commit"]
    dest = CORPUS_ROOT / name
    stamp = dest / ".corpus-sha"

    if stamp.is_file() and stamp.read_text().strip() == commit:
        log(f"{name} already at {commit[:8]} — skipping")
        return

    log(f"fetching {name} @ {commit[:8]} from {url}")
    shutil.rmtree(dest, ignore_errors=True)
    dest.mkdir(parents=True)
    git("init", "--quiet", cwd=dest)
    git("remote", "add", "origin", url, cwd=dest)
    # Depth-1 fetch of a specific SHA; fine for tag tips, which is all the manifest pins.
    git("fetch", "--quiet", "--depth", "1", "origin", commit, cwd=dest)
    git("checkout", "--quiet", "FETCH_HEAD", cwd=dest)
    shutil.rmtree(dest / ".git", ignore_errors=True)

    for victim in entry.get("prune", []):
        shutil.rmtree(dest / victim, ignore_errors=True)

    # Symlinks are never needed by a read-only corpus, and they are an rsync landmine: the builder
    # pushes test/ with `rsync -aL` (dereferencing), so a self-referential link loops until it hits
    # the 40-level limit and aborts the whole sync. ZeroBrane ships exactly that — its macOS app
    # bundle links Contents/ZeroBraneStudio back to its own ancestor.
    for path in dest.rglob("*"):
        if path.is_symlink():
            path.unlink()

    stamp.write_text(commit + "\n")
    lua_files = sum(1 for _ in dest.rglob("*.lua"))
    size = subprocess.run(["du", "-sh", str(dest)], capture_output=True, text=True).stdout.split()[0]
    log(f"{name} → {lua_files} .lua files, {size}")


def main() -> None:
    if not MANIFEST.is_file():
        die(f"Manifest not found: {MANIFEST}")
    manifest = json.loads(MANIFEST.read_text())

    CORPUS_ROOT.mkdir(parents=True, exist_ok=True)
    for entry in manifest["corpora"]:
        for required in ("name", "url", "commit", "roots", "luaLevel"):
            if not entry.get(required):
                die(f"Malformed manifest entry {entry.get('name', '?')}: missing '{required}'")
        fetch_one(entry)
        for root in entry["roots"]:
            if not (CORPUS_ROOT / entry["name"] / root).is_dir():
                die(f"{entry['name']}: declared root '{root}' missing from the checkout")

    log(f"Corpus ready at {CORPUS_ROOT}")


if __name__ == "__main__":
    main()
