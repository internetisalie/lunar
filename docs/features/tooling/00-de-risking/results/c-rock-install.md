---
id: "TOOLING-00-RESULT-04"
title: "C-Rock Install & Failure UX — Spike Results"
type: "results"
parent_id: "TOOLING-00"
spike: "TOOLING-00-04"
verdict: "PASS"
---

# TOOLING-00-04: C-Rock Install & Failure UX — Spike Results

**Date**: 2026-07-06  
**Executor**: gce-builder VM (Debian 12, gcc 12.2.0)  
**Script**: `tooling/spikes/tooling-00/install-crock.sh`  
**Design ref**: `design.md §2.4, §3.2`

## Verdict: PASS

Both TC 4 (Run A) and TC 5 (Run B) pass. The heuristic derived from Run B output is specified below.

## Recorded SHA-256 Pins

| Artifact | SHA-256 |
|----------|---------|
| `luarocks-3.13.0.tar.gz` | `245bf6ec560c042cb8948e3d661189292587c5949104677f1eecddc54dbe7e37` |

## Run A: cc present (TC 4) — PASS

**Command**: `<prefix>/bin/luarocks install busted 2.2.0-1 --force`  
**Prefix**: `/tmp/lunar-spike-54` (Lua 5.4.8 from TOOLING-00-01)

**Exit code**: 0

**LuaRocks installed**: version 3.13.0 (configured via `./configure --prefix=<prefix> --with-lua=<prefix> && make build && make install`)

**busted --version output**:
```
2.2.0
```

**TC 4 verification**: `busted --version` exits 0 printing `2.2.0` — PASS.

**Dependencies installed** (in order):
- `lua_cliargs 3.0-2` (pure Lua — no C compile)
- `luasystem 0.7.1-1` (C rock — compiled with `gcc`)
- `dkjson 2.10-1` (pure Lua)
- `say 1.4.1-3` (pure Lua)
- `luassert 1.9.0-1` (pure Lua)
- `lua-term 0.8-1` (C rock — compiled with `gcc`)
- `luafilesystem 1.9.0-1` (C rock — compiled with `gcc`)
- `penlight 1.15.0-1` (pure Lua, depends on luafilesystem)
- `mediator_lua 1.1.2-0` (pure Lua)
- `busted 2.2.0-1` (pure Lua)

**Observation**: busted itself is pure Lua, but its dependency `luasystem` is a C rock. Without a C compiler, `luasystem` fails to build, causing the entire `luarocks install busted` to fail.

## Run B: cc absent (TC 5) — PASS

**Command**: `<prefix>/bin/luarocks install busted 2.2.0-1 CC=/nonexistent/cc LD=/nonexistent/cc`  
**Prefix**: `/tmp/lunar-spike-54-run-b` (fresh Lua 5.4.8 build + fresh LuaRocks 3.13.0)

**Exit code**: `1`

**Combined stdout+stderr (verbatim)**:
```
Installing https://luarocks.org/busted-2.2.0-1.src.rock

Missing dependencies for busted 2.2.0-1:
   lua_cliargs 3.0 (not installed)
   luasystem >= 0.2.0 (not installed)
   dkjson >= 2.1.0 (not installed)
   say >= 1.4-1 (not installed)
   luassert >= 1.9.0-1 (not installed)
   lua-term >= 0.1 (not installed)
   penlight >= 1.3.2 (not installed)
   mediator_lua >= 1.1.1 (not installed)

busted 2.2.0-1 depends on lua >= 5.1 (5.4-1 provided by VM: success)
busted 2.2.0-1 depends on lua_cliargs 3.0 (not installed)
Installing https://luarocks.org/lua_cliargs-3.0-2.src.rock


lua_cliargs 3.0-2 depends on lua >= 5.1 (5.4-1 provided by VM: success)
No existing manifest. Attempting to rebuild...
lua_cliargs 3.0-2 is now installed in /tmp/lunar-spike-54-run-b (license: MIT <http://opensource.org/licenses/MIT>)

busted 2.2.0-1 depends on luasystem >= 0.2.0 (not installed)
Installing https://luarocks.org/luasystem-0.7.1-1.src.rock
sh: 1: /nonexistent/cc: not found

Error: Failed installing dependency: https://luarocks.org/luasystem-0.7.1-1.src.rock - Build error: Failed compiling object src/core.o


luasystem 0.7.1-1 depends on lua >= 5.1 (5.4-1 provided by VM: success)
/nonexistent/cc -O2 -fPIC -I/tmp/lunar-spike-54-run-b/include -c src/core.c -o src/core.o -I/usr/include
```

## Finalized §3.2 Detection Heuristic

**Input**: `(exitCode: Int, combinedOutput: String)` → `ToolchainMissingResult`

**Classification rules** (evaluated in order):

1. `SUCCESS`: `exitCode == 0` regardless of output.
2. `TOOLCHAIN_MISSING`: `exitCode != 0` **and** `combinedOutput` matches the case-insensitive pattern:
   ```
   (sh: \d+: [^\n]*: not found|No such file or directory|Failed compiling object|Build error: Failed compiling)
   ```
   **and** the matching line(s) reference an invocation of the configured `CC` or `LD` path, **or** `Error: Failed installing dependency` appears with a "Build error" sub-message.
3. `OTHER_FAILURE`: `exitCode != 0` and none of the above patterns match (network timeout, `Could not fetch`, Lua syntax error, etc.).

**Simplified rule (production implementation guidance)**:

```
exitCode != 0
AND (
    "not found" in output (case-insensitive, on a line referencing the CC/LD path)
    OR "Failed compiling object" in output
    OR "Build error: Failed compiling" in output
)
```

**Heuristic correctness against spike data**:
- Run A (exitCode=0): does NOT match → `SUCCESS` ✓
- Run B (exitCode=1, output contains `sh: 1: /nonexistent/cc: not found` and `Error: Failed installing dependency … Build error: Failed compiling object`): matches → `TOOLCHAIN_MISSING` ✓
- Network failure (`Could not fetch …`): exitCode=1, no compiler-error pattern → `OTHER_FAILURE` ✓

## Guidance Notification Copy

Targeted at `notification.group.lunar.tools` (registered at `src/main/resources/META-INF/plugin.xml:543`).

**Title**: `Lua rock installation failed — C compiler required`

**Body**:
> Installing busted requires a C compiler — several of its dependencies (e.g. luasystem) are C rocks. Install gcc or clang (`apt install build-essential` on Debian/Ubuntu, `brew install gcc` on macOS) and retry. If you are on a system without a compiler, consider using a pre-built Lua environment or pure-Lua rocks only.

**Action buttons** (TOOLING-07 to wire): `Retry` | `Open Documentation` | `Dismiss`

## Downstream Handoff

- **TOOLING-04 `LuaRocksInstallStrategy`**: use the heuristic above to classify `luarocks install` exit + output; emit `TOOLCHAIN_MISSING` when the C-compiler patterns match.
- **TOOLING-07 notifications**: use the guidance copy above verbatim (adjust `build-essential` → `xcode-select --install` for macOS in a platform-aware variant).
- **LuaRocks configure step**: `./configure --prefix=<prefix> --with-lua=<prefix> && make build && make install` — confirmed working with a prefix-baked Lua 5.4.8 (`--with-lua=<prefix>` picks up the headers and binary).
