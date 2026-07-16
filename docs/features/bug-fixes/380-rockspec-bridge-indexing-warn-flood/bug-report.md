---
id: "BUG-380"
title: "RockspecBridge floods the log with per-rockspec WARNings during indexing when no runtime resolves"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-380: RockspecBridge floods the log with per-rockspec WARNings during indexing when no runtime resolves

## 1. Reproduction

1. Open a project that contains many `.rockspec` files (the `test` fixture tree has ~100+ under
   `luacheck/rockspecs/`) **without a Lunar runtime resolvable for the project**.
2. Let indexing run.

Observed (VNC-verified live, GoLand 2026.1.3, 2026-07-16): the IDE log fills with one
`WARN` per rockspec:
```
WARN - #net.internetisalie.lunar.rocks.RockspecBridge - Rockspec bridge skipped for
  /…/luacheck/rockspecs/luacheck-0.x.y-1.rockspec: no Lua runtime is configured
```
A single indexing pass produced **110** such lines; the full session (indexing + dependency-tree
resolution) produced **165**.

## 2. Expected vs Actual Behavior

- **Expected**: when no runtime is configured, the plugin notes it **once** (or stays quiet and
  surfaces a single actionable hint), and does not attempt a bridge invocation per rockspec during
  indexing.
- **Actual**: `RockspecBridge.read` is called once per discovered rockspec during indexing and each
  call logs a full WARN line — a flood proportional to the rockspec count.

## 3. Context / Environment

- **Confidence**: high — root-caused (this is the "numerous exceptions during indexing" half of the
  [[bug-report|BUG-364]] umbrella, now reclassified: these are **WARN log noise, not exceptions** —
  `RockspecBridge` only ever logs at `WARN` and never throws to the error reporter).
- **Root cause**:
  [`RockspecBridge.read`](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt)
  resolves the runtime per call (`LuaToolResolver…resolveRuntime(project)`, line 37) and, when it is
  null, logs `WARN "…no Lua runtime is configured"` (line 40) and returns. Callers iterate every
  discovered rockspec (`LuaRockspecDiscoveryService` / `LuaRocksDependencyResolver` /
  `RockspecSourcePathProvider`), so the WARN fires N times. A second WARN path exists at line 51
  ("Rockspec bridge failed … exit=…") for the runtime-present-but-parse-fails case — same flooding
  shape if a runtime is configured but many rockspecs fail to parse (e.g. the intentionally-malformed
  `test/luacheck/spec/samples/bad.rockspec`).
- **Amplifier observed live**: the verification project's runtime never resolved because its
  `.idea/lunar.xml` is in the **pre-TOOLING format** (`<option name="interpreter">` + `hererocksEnvs`),
  which the current toolchain does not load — `[TOOLCHAIN-DIAG] … kinds=10 tools=0 envs=0`. So even
  though a valid `.lua/bin/lua` (Lua 5.1.5) exists on disk, `resolveRuntime` returned null and every
  rockspec hit the line-40 WARN. (This stale-settings condition is itself consistent with the
  documented clean-break of the settings state; no migration is provided.)
- **Fix direction**: (a) resolve the runtime **once** per discovery/resolution batch and short-circuit
  the whole rockspec sweep when it is null (log a single WARN, or none, and rely on the existing
  "Configure toolchain" surfaces); and/or (b) demote the per-rockspec message to `debug`; and/or
  (c) avoid invoking the bridge (a subprocess per rockspec) during indexing entirely — batch or defer
  it. Note the perf dimension independent of logging: with a runtime configured, the current code path
  would spawn one `lua` subprocess **per rockspec** during discovery.
- **Relevant files**:
  - `src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt` (lines 37, 40, 51).
  - `src/main/kotlin/net/internetisalie/lunar/rocks/LuaRockspecDiscoveryService.kt`,
    `LuaRocksDependencyResolver.kt`, `RockspecSourcePathProvider.kt` (the per-rockspec callers).

## 4. Other Notes

- Distinct from [[bug-report|BUG-379]] (the LuaRocks Packages Alarm exception), which is the *panel-open*
  half of BUG-364. Together the two fully account for what BUG-364 was filed to capture.
- Purely log-noise / minor-perf at present (no exception, no incorrect result — the bridge correctly
  skips when there is no runtime). Low priority, but worth fixing because the flood obscures real log
  signal on any rockspec-heavy project.
