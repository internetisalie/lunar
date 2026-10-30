#!/usr/bin/env python3
"""Install the pinned `luac` binaries listed in luac.json into <repo>/test/luac/<version>/luac, downloaded from the TOOLING-09 release archives.

These are the MAINT-35 parse oracle: for a corpus file at language level L, `luac -p -` from the
build pinned to L decides whether the input is valid Lua, and disagreement with Lunar's parser is a
defect. The binaries therefore have to be byte-identical across machines, which is why they come
from a checksummed release archive rather than being installed from a package.

A .luac-sha stamp holding the archive digest makes re-runs a no-op.

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
# (FetchLuacScriptTest points it at local file:// archives).
MANIFEST = Path(os.environ.get("LUNAR_LUAC_MANIFEST", SCRIPT_DIR / "luac.json"))
LUAC_ROOT = Path(os.environ.get("LUNAR_LUAC_ROOT", REPO_ROOT / "test" / "luac"))

ARCHIVE_SUFFIX = ".tar.gz"


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


def member_of(url: str) -> str:
    name = url.rsplit("/", 1)[-1]
    if not name.endswith(ARCHIVE_SUFFIX):
        die(f"{url}: not a {ARCHIVE_SUFFIX} archive")
    return f"{name[: -len(ARCHIVE_SUFFIX)]}/bin/luac"


def download(url: str, target: Path) -> None:
    with urllib.request.urlopen(url) as response, target.open("wb") as out:
        shutil.copyfileobj(response, out)


def verify_digest(version: str, archive: Path, expected: str) -> None:
    actual = sha256_of(archive)
    if actual != expected:
        die(
            f"{version}: sha256 mismatch — refusing to install.\n"
            f"       expected {expected}\n"
            f"       actual   {actual}\n"
            f"       Either the pin in luac.json is wrong or the download was tampered with.\n"
            f"       Do NOT 'fix' this by updating the pin without establishing which."
        )


def extract_member(version: str, archive: Path, member: str, work: Path) -> Path:
    # Explicit filter: Python 3.14 rejects an unfiltered extract, and "data" is the right policy
    # for a downloaded archive — no absolute paths, no device nodes, no metadata.
    with tarfile.open(archive) as bundle:
        if member not in bundle.getnames():
            die(f"{version}: archive lacks {member}")
        bundle.extract(member, work, filter="data")
    return work / member


def install_one(entry: dict) -> None:
    version, url, expected = entry["version"], entry["url"], entry["sha256"]
    dest = LUAC_ROOT / version
    binary = dest / "luac"
    stamp = dest / ".luac-sha"

    if binary.is_file() and stamp.is_file() and stamp.read_text().strip() == expected:
        log(f"{version} already installed — skipping")
        return

    member = member_of(url)
    log(f"installing {version} from {url}")
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        archive = work / "archive.tar.gz"
        download(url, archive)
        # The archive is never opened before its digest is verified.
        verify_digest(version, archive, expected)
        staged = extract_member(version, archive, member, work)

        # Prove the artefact runs BEFORE installing it — a copied file is not a working compiler.
        # Order matters: verifying after the copy left a stamped, therefore trusted, binary behind
        # when die() fired, and the next run would report "already installed — skipping" over it.
        check = subprocess.run([str(staged), "-v"], capture_output=True, text=True, errors="replace")
        if check.returncode != 0 or version not in check.stdout + check.stderr:
            die(f"{version}: downloaded luac does not report its own version: {check.stdout}{check.stderr}")

        # Stamp last, so an interruption between copy and stamp re-downloads rather than
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
        install_one(entry)
    log(f"Oracle binaries ready under {LUAC_ROOT}")


if __name__ == "__main__":
    main()
