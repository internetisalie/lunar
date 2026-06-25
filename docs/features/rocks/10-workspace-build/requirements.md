---
id: "ROCKS-10"
title: "10: Workspace Build Orchestration"
type: "feature"
status: "in_progress"
priority: "medium"
parent_id: "ROCKS"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-10: Workspace Build Orchestration (dependency order)

## Overview

For a multi-rock workspace (several first-party rocks discovered under the project tree), build
each rock with `luarocks make` in **topological dependency order**: a rock is built only after the
sibling rocks it depends on are built. Neither `luarocks` itself nor the common first-party install
glob (`Kernel/v0`'s `for spec in rocks/*/*.rockspec` loop in `tools/install-first-party-rocks.sh`)
orders builds by inter-rock dependencies — that ordering is this feature's unique value. ROCKS-10
**composes** existing features: discovery (ROCKS-09), the dependency graph + cycle detection
(ROCKS-03), and per-rock execution (ROCKS-04). Parent epic: [ROCKS](../requirements.md).

## Scope

### In Scope

- A single user action, **"Build Workspace (dependency order)"**, that:
  - consumes ROCKS-09's discovered rock set (`LuaRockspecDiscoveryService`),
  - reads each rock's `package` name and `dependencies` via ROCKS-03's `RockspecBridge.read`,
  - builds a directed acyclic graph (DAG) over the **discovered set only** (edges between
    first-party siblings; external/registry deps ignored),
  - topologically sorts the DAG (Kahn's algorithm, design §3.2),
  - on a cycle, **fails before building anything** and reports the cycle,
  - otherwise runs `luarocks make` once per rock, in order, each with working directory = that
    rock's rockspec parent, sequentially, stopping on the first non-zero exit,
  - streams all output to one console in the Run tool window.
- Reuse of the ROCKS-04 command-line builder (`LuaRocksRunConfiguration.buildCommandLine`,
  `LUAROCKS_COMMANDS`, `LuaRocksSettings.executablePath`) for each per-rock `luarocks make`.

### Out of Scope

- **Non-first-party / external / registry rocks** — only the rocks ROCKS-09 discovers (which
  already excludes `thirdparty/`, `lua_modules/`, `.luarocks/`, `build*/`, `output/`) are built.
  External dependency names that are not in the discovered set are ignored for ordering and never
  built (deferred: a future "install missing externals" step).
- **Disk-image / artifact assembly** (e.g. assembling a ramdisk or bootable image from built
  rocks) — that remains the Makefile's job (`Kernel/v0`'s `make` targets); ROCKS-10 stops at
  `luarocks make` per rock.
- **Parallel builds** — builds are strictly sequential (deferred: risks-and-gaps.md TBD).
- **A saved run-configuration type** for the orchestration — a transient action is used instead
  (design §2.5, §9); individual per-rock `luarocks make` configs remain ROCKS-04's domain.
- **C-toolchain provisioning** — ROCKS-10 inherits the parent environment via the ROCKS-04
  command line (parent env passed) but does not install a compiler (ROCKS-04-DR-01).
- Re-implementing discovery, the DAG/cycle primitives, or the `luarocks` command line — all
  three are consumed from ROCKS-09 / ROCKS-03 / ROCKS-04 respectively.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| ROCKS-10-01 | **Discover Build Set** | M | Use `LuaRockspecDiscoveryService` to obtain the first-party rock set (path + package name); read each rock's `dependencies` via `RockspecBridge.read`. |
| ROCKS-10-02 | **Inter-Rock DAG** | M | Build a DAG over the discovered set: edge A→B iff A's rockspec `dependencies` names a package whose normalized name equals B's package name and B is in the discovered set. External/unknown dep names are ignored. |
| ROCKS-10-03 | **Topological Order** | M | Produce a build order where every rock appears after all discovered rocks it depends on (Kahn's algorithm, deterministic tie-break by package name). |
| ROCKS-10-04 | **Cycle → Fail, No Build** | M | If the DAG contains a cycle, report the cycle (the packages involved) and build **nothing**. |
| ROCKS-10-05 | **Sequential `luarocks make`** | M | For each rock in order, run `luarocks make` (working dir = rockspec parent) sequentially, reusing the ROCKS-04 command line; stop on the first non-zero exit. |
| ROCKS-10-06 | **Console Streaming** | M | Stream every rock's stdout/stderr to a single console in the Run tool window, labeled per rock, with the overall pass/fail outcome. |
| ROCKS-10-07 | **Off-EDT Execution** | M | The whole orchestration (discovery read, bridge reads, sort, process runs) runs on a `Task.Backgroundable` off the EDT; cancellable. |
| ROCKS-10-08 | **Action Registration** | M | Register a `Lua.Rocks.BuildWorkspace` action, enabled only when ≥2 rocks are discovered, in the Tools menu and Project-view popup. |

## Detailed Specifications

### ROCKS-10-01: Discover Build Set

The build set is exactly `LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths()`
(ROCKS-09's LOCKED consumer contract): a `List<DiscoveredRockspec>` where each entry carries
`rockspec: java.nio.file.Path` and `packageName: String?`. ROCKS-10 does **not** scan the tree
itself. For each discovered rockspec, ROCKS-10 calls `RockspecBridge.read(project, rockspec)` to
obtain `RockspecData(packageName, version, dependencies: List<String>)`; the `dependencies` strings
are the raw constraint strings (e.g. `"adt >= 1.0"`, `"lua >= 5.1"`). A rockspec whose bridge read
fails (returns null) is dropped from the build set with a logged warning (it has no resolvable
package identity or dependency list).

### ROCKS-10-02: Inter-Rock DAG

Normalize a dependency constraint string to its bare package name by taking the substring before
the first run of whitespace or any of the constraint operators (`>= <= == ~> < > =`). Normalize
package names with `lowercase()` for matching (LuaRocks package names are case-insensitive in
practice; mirrors ROCKS-03's `packageName.lowercase()` keying). An edge A→B exists iff some
normalized dependency name of rock A equals the normalized package name of a **different** rock B
in the discovered set. Dependency names not matching any discovered rock (e.g. `lua`, `luafilesystem`
from the registry) contribute **no edge** and are silently ignored for ordering. A self-dependency
(A names its own package) contributes no edge.

### ROCKS-10-03 / ROCKS-10-04: Topological Order + Cycle Detection

Kahn's algorithm over the DAG (design §3.2). "A after its dependencies" means: in the edge A→B
("A depends on B"), B must be built **before** A, so the queue is seeded from nodes with **no
outgoing dependency edges** (in-degree computed on the *depends-on* relation — see design §3.2 for
the exact direction and the deterministic tie-break). If, after the sort, the number of emitted
nodes is fewer than the number of rocks, a cycle exists: collect the remaining (un-emitted) nodes
as the cycle set and **fail** — no `luarocks make` is run. This reuses ROCKS-03's notion of a
dependency cycle (`DependencyNode.isCycle`) at the spec level but operates on the sibling DAG, not
the transitive installed-rock graph.

### ROCKS-10-05: Sequential `luarocks make`

For each rock in topological order, ROCKS-10 constructs a transient `LuaRocksRunConfiguration`
(ROCKS-04) configured with `command = "make"`, `rockspecPath = <the rock's rockspec path>`, and an
empty `arguments`/`globalFlags`, then calls `buildCommandLine(LuaRocksSettings.getInstance().executablePath)`.
Because `"make" ∈ ROCKSPEC_COMMANDS` and `rockspecPath` is set, the resulting command line is
`luarocks make <rockspec>` with working directory = the rockspec's **parent** folder
(`LuaRocksRunConfiguration.resolveWorkingDirectory` already returns the rockspec parent when
`rockspecPath` is set) and parent environment passed (ROCKS-04-08 default). Each command runs to
completion before the next starts; a non-zero exit (or a thrown `ExecutionException` because
`luarocks` is missing) stops the orchestration immediately — subsequent rocks are not built.

### ROCKS-10-06: Console Streaming

A single `ConsoleView` (from `TextConsoleBuilderFactory.getInstance().createBuilder(project)`) is
shown in the Run tool window via `RunContentManager`. Before each rock, the console prints a header
line (`==> Building <package> (<n>/<total>) …`); each rock's process handler is attached to the
console so stdout/stderr stream live. On completion, a final summary line states overall success or
the failing rock and its exit code.

## Behavior Rules

- **Determinism / ordering**: among nodes simultaneously ready (in-degree 0), the one with the
  lexicographically smallest normalized package name is emitted first, so the build order is stable
  for a given rock set (independent-pair case: order is deterministic but either physical order is
  semantically valid — see TC #3).
- **Single-rock / empty set**: if fewer than 2 rocks are discovered, the action is disabled
  (ROCKS-10-08); if invoked anyway via 1 rock, it builds that one rock (no ordering needed).
- **Unparseable rockspec**: dropped from the build set with a warning (no node, no edges).
- **External deps**: dependency names not in the discovered set are ignored for ordering and are
  never built by ROCKS-10.
- **Cycle**: nothing is built; the console/report lists the cycle's packages; the task ends as a
  failure.
- **First failure stops the rest**: rocks after a failing rock are not built; the failing rock and
  exit code are reported.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | ROCKS-10-02/03 | 3 discovered rocks A, B, C where C's `dependencies` names `B`, B's names `A`, A names only `lua` | `WorkspaceBuildOrchestrator.computeBuildOrder(project)` | Returns `["A", "B", "C"]` (A built first, then B, then C) |
| 2 | ROCKS-10-04 | 2 discovered rocks A, B where A's `dependencies` names `B` and B's names `A` | `computeBuildOrder(project)` | Returns a `BuildPlan.Cycle` whose packages = `{A, B}`; **no** `luarocks make` is run |
| 3 | ROCKS-10-02/03 | 2 discovered rocks X, Y, neither depending on the other (each depends only on `lua`) | `computeBuildOrder(project)` | Returns both rocks in name-sorted order `["X", "Y"]`; either physical build order is valid (no edge between them) |
| 4 | ROCKS-10-02 | 2 discovered rocks A, B; A's `dependencies` names `dkjson` (a registry rock NOT in the discovered set) | `computeBuildOrder(project)` | No edge for `dkjson`; order is the name-sorted `["A", "B"]`; `dkjson` is never built |
| 5 | ROCKS-10-05 | `Kernel/v0`-shaped fixture, valid DAG, `luarocks` stubbed to exit 0 | invoke the action (run synchronously in test) | Each rock built once via `luarocks make <rockspec>` with working dir = its rockspec parent, in topological order |
| 6 | ROCKS-10-05 | 3 rocks A→B→C (TC #1 shape); `luarocks make` for **B** exits non-zero | invoke the action | A is built (exit 0), B is attempted and fails, C is **not** built; report names B and its exit code |
| 7 | ROCKS-10-01 | A discovered rockspec whose `RockspecBridge.read` returns null | `computeBuildOrder(project)` | That rockspec is dropped (warning logged); the order is built from the remaining rocks |
| 8 | ROCKS-10-08 | Project with exactly 1 discovered rock | open the Tools menu | The "Build Workspace (dependency order)" action is **disabled** (visible, not enabled) |
| 9 | ROCKS-10-08 | Project with ≥2 discovered rocks | open the Tools menu | The action is **enabled** |

## Acceptance Criteria

- [x] ROCKS-10-01: build set sourced from `LuaRockspecDiscoveryService`; deps via `RockspecBridge.read` (TC 1, 7).
- [x] ROCKS-10-02/03: DAG over discovered set, edges between first-party siblings only, Kahn topo-sort (TC 1, 3, 4).
- [x] ROCKS-10-04: cycle reported, nothing built (TC 2).
- [ ] ROCKS-10-05: sequential `luarocks make` via ROCKS-04 command line; stop on first failure (TC 5, 6).
- [ ] ROCKS-10-06: single Run-tool-window console with per-rock headers and a summary.
- [ ] ROCKS-10-07: orchestration runs off-EDT on a cancellable `Task.Backgroundable`.
- [ ] ROCKS-10-08: `Lua.Rocks.BuildWorkspace` registered; enabled only with ≥2 discovered rocks (TC 8, 9).

## Non-Functional Requirements

- **Threading**: all discovery/bridge/sort/process work runs off the EDT on a
  `Task.Backgroundable` (mirrors `PublishRockAction`); console creation and `RunContentManager`
  show happen on the EDT via `invokeLater`. `ProgressManager.checkCanceled()` at each rock and each
  graph-build loop iteration (engineering-contract §2). No `SlowOperationsException`.
- **Memory**: hold only `Project` (per-call); never retain `VirtualFile`/`PsiFile`. Use
  `java.nio.file.Path` for rockspec identity (as ROCKS-09 returns).
- **Determinism**: stable topo order via name tie-break (Behavior Rules).

## Dependencies

- **ROCKS-09** (workspace discovery): consumes `LuaRockspecDiscoveryService.discoverRockspecPaths()`
  (LOCKED contract). **Blocked by ROCKS-09** — it must be implemented first (it is `planned`, not
  yet `done`). See risks-and-gaps.md Risk 1.1.
- **ROCKS-03** (dependency resolution): reuses `RockspecBridge.read` and the `DependencyNode`
  cycle concept (`isCycle`). Does **not** reuse the transitive installed-rock resolver.
- **ROCKS-04** (task execution): reuses `LuaRocksRunConfiguration.buildCommandLine`,
  `LUAROCKS_COMMANDS`, `ROCKSPEC_COMMANDS`, and `LuaRocksSettings.executablePath`.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklists: [human-verification-checklists.md](human-verification-checklists.md)
