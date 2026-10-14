#!/usr/bin/env python3
"""Resolve, fetch and verify the definition-library archives the corpus manifest pins (MAINT-37).

`corpus.json` entries may carry `definitionLibraries`, a list of ids from the plugin's
`lunar-definitions-catalog.json`. The catalog is the version authority: its `version`, `urls`,
`sha256` and `size` say which archive a pin means. This module resolves the pinned ids (plus their
`requires` closure), downloads each archive into `test/corpus/.definitions/`, and re-checks them.

Run as a script, `--verify DIR` is the CI fixture check (`fetch-corpus.py`'s own exit status is
invisible to the CI step, whose verdict is its last command).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import tarfile
import urllib.request
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
MANIFEST = SCRIPT_DIR / "corpus.json"
CATALOG = REPO_ROOT / "src/main/resources/definitions/lunar-definitions-catalog.json"

DOWNLOAD_TIMEOUT_SECONDS = 60
GITHUB_WARNING = (
    "GitHub regenerates /archive/ tarballs. Do NOT update the catalog pin without establishing "
    "the content is unchanged (diff -r the two extracted trees)."
)
FUNCTION_DECLARATION = re.compile(r"^function\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(")
GLOBAL_ASSIGNMENT = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)\s*=(?!=)")


class PinError(Exception):
    pass


def log(message: str) -> None:
    print(f"\033[1;34m[definitions]\033[0m {message}")


def load_catalog(path: Path) -> dict[str, dict]:
    return {entry["id"]: entry for entry in json.loads(path.read_text())["libraries"]}


def pinned_ids(entry: dict) -> list[str]:
    ids = entry.get("definitionLibraries", [])
    if not isinstance(ids, list) or not all(isinstance(item, str) for item in ids):
        raise PinError(f"{entry.get('name', '?')}: definitionLibraries must be a list of catalog ids")
    return [item.strip() for item in ids if item.strip()]


def manifest_pins(manifest: dict, catalog: dict[str, dict]) -> list[str]:
    pending: list[str] = []
    for entry in manifest["corpora"]:
        for library_id in pinned_ids(entry):
            if library_id not in catalog:
                raise PinError(f"{entry.get('name', '?')}: definition library '{library_id}' is not in {CATALOG}")
            pending.append(library_id)
    return pending


def resolve_libraries(manifest: dict, catalog: dict[str, dict]) -> list[dict]:
    pending = manifest_pins(manifest, catalog)
    resolved: dict[str, dict] = {}
    while pending:
        library_id = pending.pop(0)
        if library_id in resolved:
            continue
        if library_id not in catalog:
            raise PinError(f"catalog entry requires unknown definition library '{library_id}'")
        resolved[library_id] = catalog[library_id]
        pending.extend(catalog[library_id].get("requires", []))
    return [resolved[key] for key in sorted(resolved)]


def archive_name(lib: dict) -> str:
    return f"{lib['id']}-{lib['version']}.tar.gz"


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def matches_pin(path: Path, lib: dict) -> bool:
    return path.is_file() and path.stat().st_size == lib["size"] and sha256_of(path) == lib["sha256"]


def download(url: str, part: Path) -> None:
    with urllib.request.urlopen(url, timeout=DOWNLOAD_TIMEOUT_SECONDS) as response, part.open("wb") as sink:
        shutil.copyfileobj(response, sink)


def try_url(url: str, dest: Path, lib: dict) -> str | None:
    """Download `url` into `dest` when it matches the pin; otherwise return the failure text."""
    part = dest.with_name(dest.name + ".part")
    try:
        download(url, part)
        if matches_pin(part, lib):
            os.replace(part, dest)
            return None
        return f"{url}: sha256 {sha256_of(part)} size {part.stat().st_size}"
    except OSError as error:
        return f"{url}: {error}"
    finally:
        part.unlink(missing_ok=True)


def fetch_library(lib: dict, dest_dir: Path) -> Path:
    dest = dest_dir / archive_name(lib)
    if matches_pin(dest, lib):
        log(f"{lib['id']} already at {lib['version'][:8]} — skipping")
        return dest
    failures = []
    for url in lib["urls"]:
        failure = try_url(url, dest, lib)
        if failure is None:
            log(f"{lib['id']} fetched at {lib['version'][:8]}")
            return dest
        failures.append(failure)
    raise PinError(
        f"{lib['id']}: no url produced the pinned archive (expected sha256 {lib['sha256']}, size {lib['size']}):\n  "
        + "\n  ".join(failures)
        + f"\n{GITHUB_WARNING}"
    )


def declared_globals(text: str) -> set[str]:
    names: set[str] = set()
    for line in text.splitlines():
        match = FUNCTION_DECLARATION.match(line) or GLOBAL_ASSIGNMENT.match(line)
        if match:
            names.add(match.group(1))
    return names


def library_globals(archive: Path, root_prefix: str) -> set[str]:
    names: set[str] = set()
    with tarfile.open(archive, "r:gz") as bundle:
        for member in bundle:
            if member.isfile() and member.name.startswith(root_prefix + "/") and member.name.endswith(".lua"):
                text = bundle.extractfile(member).read().decode("utf-8", errors="replace")
                names |= declared_globals(text)
    return names


def verify_archives(libraries: list[dict], dest_dir: Path) -> list[str]:
    return [archive_name(lib) for lib in libraries if not matches_pin(dest_dir / archive_name(lib), lib)]


def parse_arguments(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify the pinned definition-library archives.")
    parser.add_argument("--verify", required=True, metavar="DIR")
    parser.add_argument("--manifest", default=str(MANIFEST))
    parser.add_argument("--catalog", default=str(CATALOG))
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    arguments = parse_arguments(argv)
    try:
        manifest = json.loads(Path(arguments.manifest).read_text())
        libraries = resolve_libraries(manifest, load_catalog(Path(arguments.catalog)))
    except PinError as error:
        print(f"definition_pins: {error}", file=sys.stderr)
        return 1
    missing = verify_archives(libraries, Path(arguments.verify))
    if missing:
        print(f"definition_pins: not provisioned: {', '.join(missing)}; run tooling/corpus/fetch-corpus.py", file=sys.stderr)
        return 1
    print(f"definitions ok ({len(libraries)} archives verified in {arguments.verify})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
