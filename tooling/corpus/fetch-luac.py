#!/usr/bin/env python3
"""Build the pinned PUC `luac` binaries listed in luac.json into <repo>/test/luac/<version>/luac.

These are the MAINT-35 parse oracle: for a corpus file at language level L, `luac -p -` from the
build pinned to L decides whether the input is valid Lua, and disagreement with Lunar's parser is a
defect. The binaries therefore have to be byte-identical across machines, which is why they are
built from a checksummed upstream tarball rather than installed from a package.

A .luac-sha stamp holding the tarball digest makes re-runs a no-op.

Refuses to install anything on a checksum mismatch: an unverified oracle is worse than no oracle,
because the ratchet would record its verdicts as ground truth.
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
# Overridable so the refusal path can be tested without touching the real pins or the network
# (ParseOracleTest.testChecksumMismatchInstallsNothing points it at a local file:// entry).
MANIFEST = Path(os.environ.get("LUNAR_LUAC_MANIFEST", SCRIPT_DIR / "luac.json"))
LUAC_ROOT = Path(os.environ.get("LUNAR_LUAC_ROOT", REPO_ROOT / "test" / "luac"))

# PUC's makefile targets, newest naming first. 5.4+ has `make linux`; older releases want an
# explicit platform and fall back to `guess`.
MAKE_TARGETS = ("linux", "posix", "guess")


def log(msg: str) -> None:
    print(f"\033[1;34m[luac]\033[0m {msg}")


def die(msg: str) -> None:
    print(f"\033[1;31m[luac] {msg}\033[0m", file=sys.stderr)
    raise SystemExit(1)


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_one(entry: dict) -> None:
    version, url, expected = entry["version"], entry["url"], entry["sha256"]
    dest = LUAC_ROOT / version
    binary = dest / "luac"
    stamp = dest / ".luac-sha"

    if binary.is_file() and stamp.is_file() and stamp.read_text().strip() == expected:
        log(f"{version} already built — skipping")
        return

    log(f"building {version} from {url}")
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        tarball = work / f"lua-{version}.tar.gz"

        with urllib.request.urlopen(url) as response, tarball.open("wb") as out:
            shutil.copyfileobj(response, out)

        actual = sha256_of(tarball)
        if actual != expected:
            die(
                f"{version}: sha256 mismatch — refusing to build.\n"
                f"       expected {expected}\n"
                f"       actual   {actual}\n"
                f"       Either the pin in luac.json is wrong or the download was tampered with.\n"
                f"       Do NOT 'fix' this by updating the pin without establishing which."
            )

        with tarfile.open(tarball) as archive:
            # Explicit filter: Python 3.14 rejects an unfiltered extractall, and "data" is the
            # right policy for a source tarball — no absolute paths, no device nodes, no metadata.
            archive.extractall(work, filter="data")
        src = work / f"lua-{version}"

        for target in MAKE_TARGETS:
            built = subprocess.run(
                ["make", target], cwd=src, capture_output=True, text=True, errors="replace"
            )
            if built.returncode == 0 and (src / "src" / "luac").is_file():
                break
        else:
            die(f"{version}: no make target succeeded ({', '.join(MAKE_TARGETS)}). Is cc installed?")

        # Prove the artefact runs BEFORE installing it — a copied file is not a working compiler.
        # Order matters: verifying after the copy left a stamped, therefore trusted, binary behind
        # when die() fired, and the next run would report "already built — skipping" over it.
        staged = src / "src" / "luac"
        check = subprocess.run([str(staged), "-v"], capture_output=True, text=True, errors="replace")
        if check.returncode != 0 or version not in check.stdout + check.stderr:
            die(f"{version}: built binary does not report its own version: {check.stdout}{check.stderr}")

        # Stamp last, so an interruption between copy and stamp re-does the build rather than
        # blessing a half-installed one.
        dest.mkdir(parents=True, exist_ok=True)
        shutil.copy2(staged, binary)
        binary.chmod(0o755)
        stamp.write_text(expected + "\n")
    log(f"{version} -> {binary}")


def main() -> None:
    if not MANIFEST.is_file():
        die(f"Manifest not found: {MANIFEST}")
    manifest = json.loads(MANIFEST.read_text())
    LUAC_ROOT.mkdir(parents=True, exist_ok=True)
    for entry in manifest["builds"]:
        build_one(entry)
    log(f"Oracle binaries ready under {LUAC_ROOT}")


if __name__ == "__main__":
    main()
