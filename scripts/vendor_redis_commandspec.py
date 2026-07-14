#!/usr/bin/env python3
# ---------------------------------------------------------------------------
# vendor_redis_commandspec.py
#
# PROVENANCE / LICENSING
# ----------------------
# Generates the bundled per-version Redis command specs consumed by
# RedisCommandSpecService (REDIS-04 design §4.1, §3.2).
#
#   Source repository : valkey-io/valkey  (https://github.com/valkey-io/valkey)
#   Source path       : src/commands/*.json
#   Pinned ref        : VALKEY_REF below (a release tag, for reproducibility)
#   Source license    : BSD 3-Clause License (Valkey `COPYING`)
#
# Only the BSD-3-Clause Valkey command JSON is used. The Redis-repo
# `commands.json` (RSALv2 / SSPLv1 for Redis >= 7.4, AGPLv3 in Redis 8) is
# NOT copied or derived from — that license is incompatible with bundling.
# All bundled data (including every `summary` free-text field) originates from
# the BSD-3-Clause Valkey source above.
#
# NONDETERMINISM FLAG
# -------------------
# The modern Valkey/Redis 7+ command JSON no longer carries the historical
# `random` / nondeterministic command flag (effects replication made it
# obsolete). REDIS-04 AC-9 (Redis 5/6 determinism inspection) needs that
# classification, so this script adds a `nondeterministic` flag to the
# well-known, documented historical set of Redis `random`-flagged commands
# (NONDETERMINISTIC_COMMANDS below). That set is factual command-metadata
# knowledge, not a copy of any license-encumbered JSON file.
#
# OUTPUT
# ------
# Reduces each command to the REDIS-04 §4.1 schema:
#     "<UPPER_NAME>": { "arity": Int, "since": "x.y.z",
#                       "summary": "...", "flags": ["write", ...] }
# and writes one file per target version, filtered on since <= version:
#     src/main/resources/commandspec/redis-5.json  (since <= 5)
#     src/main/resources/commandspec/redis-6.json  (since <= 6)
#     src/main/resources/commandspec/redis-7.json  (since <= 7, i.e. all)
#
# USAGE
# -----
#     python3 scripts/vendor_redis_commandspec.py           # fetch from GitHub
#     python3 scripts/vendor_redis_commandspec.py --src DIR  # use a local
#                                                            # valkey/src/commands
#
# Re-run to regenerate the bundled JSON from scratch (reproducible).
# ---------------------------------------------------------------------------

import argparse
import json
import os
import sys
import urllib.request

VALKEY_REPO = "valkey-io/valkey"
VALKEY_REF = "8.0.0"  # pinned BSD-3-Clause release tag
RAW_BASE = f"https://raw.githubusercontent.com/{VALKEY_REPO}/{VALKEY_REF}/src/commands"
TREE_API = f"https://api.github.com/repos/{VALKEY_REPO}/git/trees/{VALKEY_REF}?recursive=1"

# Per-version max major (design §3.2 / §3.11): redis-5 -> [5], redis-6 -> [6],
# redis-7 -> [7]. A command is included when its `since` major <= this value.
VERSIONS = {"redis-5": 5, "redis-6": 6, "redis-7": 7}

# Historical Redis commands that carried the `random` (nondeterministic) flag
# before effects replication (Redis <= 6). Public, documented command metadata.
NONDETERMINISTIC_COMMANDS = frozenset({
    "TIME", "RANDOMKEY", "SRANDMEMBER", "SPOP", "SCAN", "HSCAN", "SSCAN",
    "ZSCAN", "LPOP", "RPOP", "INCRBYFLOAT", "HINCRBYFLOAT", "LASTSAVE",
    "SPUBLISH", "GETEX",
})

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "commandspec",
)


def _http_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "lunar-vendor"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def list_command_files_remote():
    tree = _http_json(TREE_API)
    return sorted(
        os.path.basename(t["path"])
        for t in tree["tree"]
        if t["path"].startswith("src/commands/") and t["path"].endswith(".json")
    )


def load_command_remote(filename):
    return _http_json(f"{RAW_BASE}/{filename}")


def load_command_local(src_dir, filename):
    with open(os.path.join(src_dir, filename), "r", encoding="utf-8") as fh:
        return json.load(fh)


def since_major(since):
    try:
        return int(str(since).split(".")[0])
    except (ValueError, IndexError):
        return 0


def reduce_command(name, body):
    """Reduce a single Valkey command object to the §4.1 schema value."""
    flags = [f.lower() for f in body.get("command_flags", []) or []]
    upper = name.upper()
    if upper in NONDETERMINISTIC_COMMANDS and "nondeterministic" not in flags:
        flags.append("nondeterministic")
    return {
        "arity": body.get("arity", 0),
        "since": str(body.get("since", "0")),
        "summary": body.get("summary", "") or "",
        "flags": flags,
    }


def collect(src_dir):
    """Return a dict: UPPER_NAME -> reduced §4.1 value, for all commands."""
    if src_dir:
        files = sorted(f for f in os.listdir(src_dir) if f.endswith(".json"))
        loader = lambda fn: load_command_local(src_dir, fn)
    else:
        files = list_command_files_remote()
        loader = load_command_remote

    spec = {}
    for i, filename in enumerate(files, 1):
        obj = loader(filename)
        for name, body in obj.items():
            # Skip container subcommands (e.g. `SLOWLOG GET`): their JSON key is
            # the bare subcommand (`GET`) with a `container` field, which would
            # otherwise clobber the top-level command of the same name. The
            # first `redis.call` string arg is the container token itself
            # (`SLOWLOG`), already covered by the container's own file.
            if body.get("container"):
                continue
            spec[name.upper()] = reduce_command(name, body)
        if not src_dir and i % 50 == 0:
            print(f"  fetched {i}/{len(files)}", file=sys.stderr)
    return spec


def write_versions(spec):
    os.makedirs(OUT_DIR, exist_ok=True)
    for seg, max_major in VERSIONS.items():
        filtered = {
            name: value
            for name, value in spec.items()
            if since_major(value["since"]) <= max_major
        }
        ordered = dict(sorted(filtered.items()))
        out_path = os.path.join(OUT_DIR, f"{seg}.json")
        with open(out_path, "w", encoding="utf-8") as fh:
            json.dump(ordered, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        print(f"wrote {out_path}: {len(ordered)} commands", file=sys.stderr)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--src",
        help="local valkey src/commands directory (default: fetch from GitHub)",
    )
    args = parser.parse_args()
    print(
        f"Vendoring from Valkey {VALKEY_REF} "
        f"({'local ' + args.src if args.src else 'GitHub raw'}) — BSD-3-Clause",
        file=sys.stderr,
    )
    spec = collect(args.src)
    print(f"collected {len(spec)} commands total", file=sys.stderr)
    write_versions(spec)


if __name__ == "__main__":
    main()
