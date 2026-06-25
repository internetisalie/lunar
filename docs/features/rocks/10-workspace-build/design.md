---
id: "ROCKS-10-DESIGN"
title: "Technical Design"
type: "design"
status: "in_progress"
priority: "medium"
parent_id: "ROCKS-10"
folders:
  - "[[features/rocks/10-workspace-build/requirements|requirements]]"
---

# Technical Design: ROCKS-10 — Workspace Build Orchestration (dependency order)

## 1. Architecture Overview

### Current State

- **Discovery** exists: `LuaRockspecDiscoveryService` (ROCKS-09) returns the discovered
  first-party rock set with built-in `thirdparty/`/`lua_modules/`/`build*/`/`output/`/`.luarocks/`
  exclusion ([09-workspace-discovery/design.md §2.1, §3.1-3.2](../09-workspace-discovery/design.md)).
  ROCKS-10 consumes it; it does **not** scan.
- **Per-rock execution** exists: `LuaRocksRunConfiguration.buildCommandLine`
  ([LuaRocksRunConfiguration.kt:181-199](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/run/LuaRocksRunConfiguration.kt))
  builds a `luarocks <command> [rockspec]` `GeneralCommandLine` with working dir = the rockspec's
  parent ([LuaRocksRunConfiguration.kt:171-178](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/run/LuaRocksRunConfiguration.kt)),
  parent env passed by default ([:91-98](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/run/LuaRocksRunConfiguration.kt)).
  `LUAROCKS_COMMANDS`/`ROCKSPEC_COMMANDS` ([:38-43](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/run/LuaRocksRunConfiguration.kt))
  and `LuaRocksSettings.executablePath`
  ([LuaRocksSettings.kt:33-44](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/run/LuaRocksSettings.kt))
  give the binary. ROCKS-10 reuses this verbatim.
- **Rockspec reading + dependency strings + cycle concept** exist: `RockspecBridge.read` →
  `RockspecData(packageName, version, dependencies: List<String>)`
  ([RockspecBridge.kt:13-46](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt));
  `DependencyNode.isCycle` ([DependencyNode.kt:11](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/deps/DependencyNode.kt))
  and `LuaRocksDependencyResolver` cycle detection
  ([LuaRocksDependencyResolver.kt:55-56](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/LuaRocksDependencyResolver.kt)).
- **Action pattern** exists: `PublishRockAction`
  ([PublishRockAction.kt:25-72](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/publish/PublishRockAction.kt))
  is a `DumbAwareAction` doing its work on a `Task.Backgroundable`, registered in `plugin.xml`
  ([plugin.xml:552-559](../../../../src/main/resources/META-INF/plugin.xml)). ROCKS-10's action
  clones this shape.

**What is missing — the unique value**: nothing orders sibling first-party builds by their
inter-rock dependencies. `luarocks` builds one rock at a time; `Kernel/v0`'s
`tools/install-first-party-rocks.sh` loops `for spec in rocks/*/*.rockspec` in directory order, not
dependency order. ROCKS-10 adds the missing topological-order orchestration.

### Prior Art in This Repo

- **`LuaRocksDependencyResolver`** ([LuaRocksDependencyResolver.kt](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/LuaRocksDependencyResolver.kt))
  — resolves the **transitive installed-rock** graph for a single root (children = a rock's deps
  expanded against the *installed* tree). ROCKS-10 needs a different graph: a **flat DAG over the
  discovered sibling set** (edges only between first-party rocks, no installed-tree expansion). The
  resolver is therefore **not reused for graph construction**; only its `RockspecBridge.read` input
  and its cycle concept are reused. This design does **not** duplicate the transitive resolver — it
  builds a distinct, smaller sibling DAG (§3.1).
- **`DependencyNode`** ([DependencyNode.kt](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/deps/DependencyNode.kt))
  — reused only as a vocabulary anchor (`isCycle`); the sibling DAG uses lightweight package-name
  nodes (§2.2), not full `DependencyNode`s, because per-rock version/conflict data is irrelevant to
  build ordering.
- **`PublishRockAction`** — the action-on-`Task.Backgroundable` template, **extended** in spirit
  (new sibling class), not modified.
- Searched (`grep`) for an existing "build order"/"topolog"/"workspace build" component: none found
  in `src/` — this is greenfield orchestration over existing primitives.

### Target State

A new package `net.internetisalie.lunar.rocks.build` with:

```
BuildWorkspaceAction (AnAction)
        │  (Task.Backgroundable, off-EDT)
        ▼
WorkspaceBuildOrchestrator.computeBuildOrder(project) : BuildPlan
        ├─ LuaRockspecDiscoveryService.discoverRockspecPaths()      (ROCKS-09)
        ├─ RockspecBridge.read(project, path)  per rock         (ROCKS-03)
        ├─ WorkspaceBuildGraph.build(rocks)    → DAG (§3.1)
        └─ WorkspaceBuildGraph.topoSort()      → order | Cycle  (§3.2, Kahn)
        ▼
WorkspaceBuildRunner.run(project, order, console)               (ROCKS-04 cmd line)
        └─ per rock: LuaRocksRunConfiguration.buildCommandLine → OSProcessHandler → ConsoleView
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.rocks.build.WorkspaceBuildOrchestrator`

- **Responsibility**: turn the discovered rock set into a `BuildPlan` (ordered list or a cycle),
  reading each rock's dependencies via the bridge.
- **Threading**: background only (every `RockspecBridge.read` blocks); callers run it inside a
  `Task.Backgroundable`. Reads no PSI directly (discovery service wraps its own read action).
- **Collaborators**: `LuaRockspecDiscoveryService` (ROCKS-09), `RockspecBridge` (ROCKS-03),
  `WorkspaceBuildGraph`.
- **Key API**:
  ```kotlin
  object WorkspaceBuildOrchestrator {
      /** Build the sibling DAG and topo-sort it. Background only (bridge reads block). */
      fun computeBuildOrder(project: Project): BuildPlan

      private fun loadRocks(project: Project): List<WorkspaceRock>   // §3.0
  }

  /** One discovered, bridge-read rock relevant to build ordering. */
  data class WorkspaceRock(
      val packageName: String,        // from RockspecData.packageName (never blank)
      val rockspec: Path,             // from DiscoveredRockspec.rockspec
      val dependencyNames: List<String>, // normalized bare package names (§3.0 step 4)
  )

  /** Result of ordering. */
  sealed interface BuildPlan {
      data class Ordered(val rocks: List<WorkspaceRock>) : BuildPlan   // topo order
      data class Cycle(val packages: Set<String>) : BuildPlan          // un-emittable nodes
      object Empty : BuildPlan                                          // < 1 rock
  }
  ```

### 2.2 `net.internetisalie.lunar.rocks.build.WorkspaceBuildGraph`

- **Responsibility**: build the inter-rock DAG and run Kahn's topological sort with cycle
  detection. Pure; no platform access.
- **Threading**: pure function, any thread.
- **Collaborators**: none (operates on `List<WorkspaceRock>`).
- **Key API**:
  ```kotlin
  object WorkspaceBuildGraph {
      /**
       * Edges: A depends-on B (A→B) iff A.dependencyNames contains B.packageName (normalized),
       * A != B, and B is in [rocks]. External names are ignored. See §3.1.
       */
      fun topoSort(rocks: List<WorkspaceRock>): BuildPlan   // §3.2 — Ordered | Cycle | Empty
  }
  ```

### 2.3 `net.internetisalie.lunar.rocks.build.WorkspaceBuildRunner`

- **Responsibility**: run `luarocks make` sequentially for an ordered rock list, streaming to a
  console, stopping on first failure.
- **Threading**: called on the `Task.Backgroundable` thread; each process is awaited via
  `OSProcessHandler.waitFor()` (blocking on a background thread is correct here). Console attach is
  thread-safe (`ConsoleView.attachToProcess`).
- **Collaborators**: `LuaRocksRunConfiguration` (ROCKS-04, transient instance for
  `buildCommandLine`), `LuaRocksSettings` (ROCKS-04), `ConsoleView`,
  `ProcessHandlerFactory`/`OSProcessHandler`.
- **Key API**:
  ```kotlin
  object WorkspaceBuildRunner {
      data class BuildOutcome(val builtCount: Int, val failedRock: WorkspaceRock?, val exitCode: Int?)

      /** Runs each rock's `luarocks make` in order; returns at the first non-zero exit. §3.3. */
      fun run(project: Project, order: List<WorkspaceRock>, console: ConsoleView,
              indicator: ProgressIndicator): BuildOutcome
  }
  ```

### 2.4 `net.internetisalie.lunar.rocks.build.BuildWorkspaceAction`

- **Responsibility**: the user entry point — gate, create the console, drive the orchestration on a
  `Task.Backgroundable`, report the outcome.
- **Threading**: `update` and `actionPerformed` boundaries on EDT; all heavy work inside the
  `Task.Backgroundable.run`; console show via `RunContentManager` on EDT (`invokeLater`).
- **Collaborators**: `WorkspaceBuildOrchestrator`, `WorkspaceBuildRunner`,
  `LuaRockspecDiscoveryService` (for the `update` gate count), `TextConsoleBuilderFactory`,
  `RunContentManager`, `DefaultRunExecutor`.
- **Key API**:
  ```kotlin
  class BuildWorkspaceAction : DumbAwareAction(
      "Build Workspace (dependency order)",
      "Build all first-party rocks with luarocks make in topological dependency order",
      LuaIcons.ROCKET,
  ) {
      override fun update(event: AnActionEvent)          // §3.4 gate (>= 2 discovered rocks)
      override fun actionPerformed(event: AnActionEvent) // §3.5 drive
      override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }
  ```
  `getActionUpdateThread() = BGT` is required because `update` reads the discovery service
  (off-EDT-safe data) — matches the platform contract for actions that touch project services in
  `update` (see the `actions` skill).

## 3. Algorithms

### 3.0 Loading rocks (`WorkspaceBuildOrchestrator.loadRocks`)

- **Input → Output**: `Project` → `List<WorkspaceRock>`.
- **Steps**:
  1. `val discovered = LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths()`
     (ROCKS-09 LOCKED contract; returns `List<DiscoveredRockspec(rockspec: Path, packageName: String?)>`).
  2. For each `d` in `discovered` (call `ProgressManager.checkCanceled()` each iteration):
     - `val data = RockspecBridge.read(project, d.rockspec) ?: continue` (drop + log warn on null).
     - `val name = data.packageName` (bridge guarantees non-blank, [RockspecBridge.kt:61-65]).
     - `val deps = data.dependencies.mapNotNull { normalizeDepName(it) }` (step 4).
     - emit `WorkspaceRock(name, d.rockspec, deps)`.
  3. Return the list (order irrelevant; the sort re-orders).
- **Step 4 — `normalizeDepName(raw: String): String?`** (the dependency-string parse format):
  LuaRocks dependency strings are `"<name>[ <op> <version>][, <op> <version>]"`, e.g.
  `"luafilesystem >= 1.6.3"`, `"lua ~> 5.1"`, `"adt"`. Parse:
  - `val trimmed = raw.trim()`; if blank → `null`.
  - Take the package token = the longest prefix of `trimmed` consisting of characters **not** in
    the set `{ whitespace, '>', '<', '=', '~', ',' }` — i.e. split on the first occurrence of any of
    those and keep the head. Regex: `Regex("^[^\\s<>=~,]+").find(trimmed)?.value`.
  - Return that token `lowercase()`; `null` if empty.
  - This mirrors `DependencySpec.parse`'s package extraction used by ROCKS-03 (verified the resolver
    keys deps by `spec.packageName.lowercase()`, [LuaRocksDependencyResolver.kt:54]).

### 3.1 Sibling DAG construction (inside `topoSort`)

- **Input → Output**: `List<WorkspaceRock>` → adjacency over package names.
- **Steps**:
  1. `val byName: Map<String, WorkspaceRock> = rocks.associateBy { it.packageName.lowercase() }`.
     (If two rocks share a package name, `associateBy` keeps the last; duplicates are a degenerate
     workspace — both still appear in the node set keyed by name, see Edge Cases.)
  2. Node set = `byName.keys` (normalized names).
  3. For each rock A (key `a`), for each `depName` in `A.dependencyNames`:
     - if `depName != a` **and** `depName in byName` → add edge `a → depName` ("A depends on B").
     - else ignore (external dep or self-dep).
  4. De-duplicate edges (a `Set<Pair<String,String>>` or `Set` per adjacency list).
- **Edge semantics**: `a → b` means "a depends on b", so **b must build before a**.

### 3.2 Topological sort — Kahn's algorithm (cycle-detecting)

- **Input → Output**: the DAG from §3.1 → `BuildPlan.Ordered` (build order) or `BuildPlan.Cycle`.
- **Definitions**: for the depends-on relation `a → b`, define **outDeg(a)** = number of distinct
  `b` such that `a → b` (the count of a's not-yet-built dependencies). A rock is *ready to build*
  when `outDeg == 0` (all its discovered dependencies already emitted).
- **Steps**:
  1. Compute `outDeg[n]` for every node `n` (number of distinct outgoing depends-on edges).
  2. Build `dependents[b] = { a : a → b }` (reverse adjacency: who depends on b).
  3. Seed a list `ready = nodes with outDeg == 0`, sorted ascending by name.
  4. `result = mutableListOf<String>()`.
  5. While `ready` is non-empty:
     - `ProgressManager.checkCanceled()`.
     - Remove the **lexicographically smallest** name `n` from `ready` (deterministic tie-break).
     - Append `n` to `result`.
     - For each `a` in `dependents[n]`: `outDeg[a] -= 1`; if `outDeg[a] == 0` insert `a` into
       `ready` keeping it name-sorted.
  6. If `result.size < nodes.size`: the remaining nodes (those still with `outDeg > 0`) are in or
     behind a cycle → return `BuildPlan.Cycle(packages = nodes - result.toSet())`.
  7. Else map `result` (names) back to `WorkspaceRock` via `byName` and return
     `BuildPlan.Ordered(result.map { byName.getValue(it) })`.
- **Rules / edge handling**: empty node set → `BuildPlan.Empty`. A node with no edges is ready
  immediately (independent rocks emit in name order — TC #3). Because we seed from `outDeg == 0`
  (dependency-free rocks) and decrement when a dependency is satisfied, the emitted order has every
  rock **after** all its discovered dependencies (TC #1: A has no deps → first; B depends on A →
  after A; C depends on B → last).
- **Complexity**: O(V + E) plus O(V log V) for the sorted-insert tie-break; V,E ≤ rock count, tiny.

### 3.3 Sequential run (`WorkspaceBuildRunner.run`)

- **Input → Output**: ordered rocks + console → `BuildOutcome`.
- **Steps**:
  1. `val exe = LuaRocksSettings.getInstance().executablePath`.
  2. For each `(i, rock)` in `order.withIndex()`:
     - `ProgressManager.checkCanceled()`; `indicator.text = "Building ${rock.packageName}"`.
     - Console header: `console.print("\n==> Building ${rock.packageName} (${i+1}/${order.size})\n", SYSTEM_OUTPUT)`.
     - Build a transient ROCKS-04 config and command line:
       ```kotlin
       val config = LuaRocksRunConfiguration(project, /*factory=*/null, "Workspace build: ${rock.packageName}")
       config.command = "make"
       config.rockspecPath = rock.rockspec.toString()   // work dir = rockspec parent (ROCKS-04)
       val cmd = config.buildCommandLine(exe)            // luarocks make <rockspec>
       ```
     - `val handler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(cmd)`
       (wrap in `try { … } catch (e: ExecutionException) { … }`: luarocks missing → treat as a
       failure with a synthetic exit, report and stop).
     - `console.attachToProcess(handler)`; `handler.startNotify()`; `handler.waitFor()`.
     - `val exit = handler.exitCode ?: -1`; if `exit != 0` → return
       `BuildOutcome(builtCount = i, failedRock = rock, exitCode = exit)` (stop; later rocks unbuilt).
  3. Return `BuildOutcome(builtCount = order.size, failedRock = null, exitCode = null)`.
- **Rules / edge handling**: `createColoredProcessHandler` throws `ExecutionException` when the
  `luarocks` binary is absent — caught, reported as the failing rock with a `null`/synthetic exit,
  and the loop stops (no further rocks). `command = "make"` is in `ROCKSPEC_COMMANDS` so the
  rockspec path is appended and the work dir is the rockspec parent — both handled inside
  `buildCommandLine`/`resolveWorkingDirectory`, no duplication here.

### 3.4 Action gate (`update`)

- **Input → Output**: `AnActionEvent` → enabled/visible state.
- **Steps**:
  1. `val project = event.project`; if null → `isEnabledAndVisible = false`; return.
  2. `val count = LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths().size`
     (cached by ROCKS-09; cheap). In dumb mode discovery returns empty (ROCKS-09 §6) → count 0.
  3. `event.presentation.isVisible = true`; `event.presentation.isEnabled = count >= 2`
     (TC #8/#9: 1 rock → disabled; ≥2 → enabled).

### 3.5 Action drive (`actionPerformed`)

- **Steps**:
  1. `val project = event.project ?: return`.
  2. Create the console on EDT: `val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console`.
  3. Show it in the Run tool window via `RunContentManager.getInstance(project).showRunContent(
     DefaultRunExecutor.getRunExecutorInstance(), descriptor)` where `descriptor` is a
     `RunContentDescriptor(console, /*processHandler=*/null, console.component, "Build Workspace")`.
  4. `ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Building workspace", true) {
        override fun run(indicator: ProgressIndicator) { … } })`:
     - `val plan = WorkspaceBuildOrchestrator.computeBuildOrder(project)`.
     - `when (plan) { Empty -> console "no rocks";
        is Cycle -> console.print("Build aborted: dependency cycle among ${plan.packages}\n", ERROR_OUTPUT) (no build);
        is Ordered -> val outcome = WorkspaceBuildRunner.run(project, plan.rocks, console, indicator); print summary }`.
     - Summary line: success → `"Workspace build complete: ${outcome.builtCount} rocks"`; failure →
       `"Workspace build FAILED at ${outcome.failedRock?.packageName} (exit ${outcome.exitCode})"`.

## 4. External Data & Parsing

The only external/unstructured input ROCKS-10 parses itself is the **dependency constraint string**
inside `RockspecData.dependencies` (already extracted by `RockspecBridge`, not re-read from disk).
Its format and parse strategy are pinned in §3.0 step 4 (`normalizeDepName`, a single regex). The
rockspec file itself is parsed by the existing `RockspecBridge` (JSON from the bundled
`rockspec.lua` bridge) — ROCKS-10 adds no rockspec parsing. `luarocks make` stdout/stderr is
streamed raw to the console (no parsing), exactly as ROCKS-04 does.

## 5. Data Flow

### Example 1: A→B→C chain (TC #1, #5)

1. User invokes "Build Workspace (dependency order)" (Tools menu); enabled because 3 rocks
   discovered (§3.4).
2. `actionPerformed` shows a console and starts a `Task.Backgroundable` (§3.5).
3. `computeBuildOrder` → `loadRocks` reads A/B/C via `RockspecBridge.read`; A.deps=`[lua]`→`[]`
   (external dropped), B.deps=`[adt→a]`, C.deps=`[b]`.
4. `WorkspaceBuildGraph.topoSort`: edges `b→a`, `c→b`; outDeg a=0,b=1,c=1 → ready=[a] → emit a,
   decrement b→0 → emit b, decrement c→0 → emit c → `Ordered([A, B, C])`.
5. `WorkspaceBuildRunner.run`: `luarocks make <A.rockspec>` (wd = A's dir), then B, then C; each
   awaited; console streams; summary "3 rocks".

### Example 2: cycle A↔B (TC #2)

`loadRocks` → A.deps=[b], B.deps=[a]; edges `a→b`,`b→a`; outDeg a=1,b=1 → ready empty → result
empty → `Cycle({a, b})`. Action prints the cycle and runs **no** `luarocks make`.

### Example 3: external dep (TC #4)

A.deps=`[dkjson]`; `dkjson ∉ byName` → no edge; B independent; both ready → `Ordered([A, B])`
(name-sorted). `dkjson` never built.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| < 2 discovered rocks | Action disabled (§3.4). If somehow 1 rock: `Ordered([that rock])`, built once. |
| Unparseable rockspec | `RockspecBridge.read` null → dropped from `loadRocks` with a warn; not a node (TC #7). |
| External / registry dep name | Not in `byName` → no edge, never built (§3.1 step 3; TC #4). |
| Self-dependency | `depName == a` → no edge (§3.1 step 3). |
| Duplicate package names across rocks | `associateBy` keeps one node per name; both rockspecs are discovered but only one builds per name (degenerate workspace; logged). Documented limitation. |
| Cycle | No build; cycle packages reported (§3.2 step 6; TC #2). |
| `luarocks` missing on PATH | `createColoredProcessHandler` throws `ExecutionException` → caught, reported as the failing rock, loop stops (§3.3). |
| Mid-build failure | First non-zero exit returns `BuildOutcome` with `failedRock`; later rocks not built (TC #6). |
| Dumb mode | ROCKS-09 discovery returns empty in dumb mode → count 0 → action disabled; no crash. |
| User cancels the task | `ProgressManager.checkCanceled()` between rocks aborts before the next process starts. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml, inside the existing <actions> block (after Lua.Rocks.Publish) -->
<action id="Lua.Rocks.BuildWorkspace"
        class="net.internetisalie.lunar.rocks.build.BuildWorkspaceAction"
        text="Build Workspace (dependency order)"
        description="Build all first-party rocks with luarocks make in topological dependency order"
        icon="net.internetisalie.lunar.lang.LuaIcons.ROCKET">
  <add-to-group group-id="ToolsMenu" anchor="last"/>
  <add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>
</action>
```

- Group IDs `ToolsMenu` and `ProjectViewPopupMenu` are the same groups used by existing Lunar
  actions (`Lua.Console` → `ToolsMenu`, [plugin.xml:548]; `Lua.Rocks.Publish` →
  `ProjectViewPopupMenu`, [plugin.xml:557]). Action id namespaced `Lua.Rocks.*` to match
  `Lua.Rocks.Publish` ([plugin.xml:552]).
- Icon `LuaIcons.ROCKET` ([LuaIcons.kt:9](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaIcons.kt)),
  shared with the other rocks actions.
- **No** `<configurationType>`/`<applicationService>`/`<projectService>` is added — the
  orchestrator/graph/runner are plain objects, and the action is the only registration (§9 explains
  why a transient action beats a new run-config type).
- No new notification group; failures surface in the build console (the existing
  `notification.group.lunar.luarocks` group remains ROCKS-08's).

## Cross-Feature Contract (consumed, not redefined)

ROCKS-10 **composes** three already-planned/built features and does not redefine any of them:

- **ROCKS-09 — Workspace Discovery** (`docs/features/rocks/09-workspace-discovery/design.md`):
  consumes `LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths(): List<DiscoveredRockspec>`
  (LOCKED contract; `DiscoveredRockspec(rockspec: java.nio.file.Path, packageName: String?)`).
  ROCKS-10 does **not** scan the tree, define an exclusion filter, or duplicate the cache — the
  built-in `thirdparty/`/`lua_modules/`/`build*/`/`output/`/`.luarocks/` exclusions come for free,
  which is exactly what keeps ROCKS-10's build set "first-party only".
- **ROCKS-03 — Dependency Resolution** (`docs/features/rocks/03-dependency-resolution/`): reuses
  `RockspecBridge.read(project, path): RockspecData?`
  ([RockspecBridge.kt:31](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt))
  for each rock's `package`/`dependencies`, and the cycle concept from `DependencyNode.isCycle`
  ([DependencyNode.kt:11](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/deps/DependencyNode.kt)).
  ROCKS-10 does **not** reuse the transitive installed-rock resolver (`LuaRocksDependencyResolver`)
  — it builds a distinct sibling DAG (§1 Prior Art).
- **ROCKS-04 — Task Execution** (`docs/features/rocks/04-task-execution/design.md`): reuses
  `LuaRocksRunConfiguration.buildCommandLine(executablePath): GeneralCommandLine`
  ([LuaRocksRunConfiguration.kt:181](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/run/LuaRocksRunConfiguration.kt)),
  the `command="make"` + `rockspecPath` path that yields `luarocks make <rockspec>` with work dir =
  rockspec parent and parent env passed, and `LuaRocksSettings.getInstance().executablePath`
  ([LuaRocksSettings.kt:40](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/run/LuaRocksSettings.kt)).
  ROCKS-10 does **not** redefine the command line or the binary lookup.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ROCKS-10-01 Discover Build Set | M | §2.1, §3.0 |
| ROCKS-10-02 Inter-Rock DAG | M | §2.2, §3.1 |
| ROCKS-10-03 Topological Order | M | §2.2, §3.2 |
| ROCKS-10-04 Cycle → Fail, No Build | M | §3.2 step 6, §3.5 |
| ROCKS-10-05 Sequential `luarocks make` | M | §2.3, §3.3 |
| ROCKS-10-06 Console Streaming | M | §2.3, §2.4, §3.3, §3.5 |
| ROCKS-10-07 Off-EDT Execution | M | §2.1, §2.4, §3.5 (`Task.Backgroundable`) |
| ROCKS-10-08 Action Registration | M | §2.4, §3.4, §7 |

## 9. Alternatives Considered

- **A dedicated run-configuration type** (vs the transient action): rejected. The orchestration is a
  one-shot, project-wide build with computed order; a saved per-config makes no sense (the rock set
  and order are *discovered*, not user-edited), and a run-config type would force a settings editor
  and persisted options for nothing. An `AnAction` driving sequential ROCKS-04 command lines reuses
  the ROCKS-04 `buildCommandLine` without dragging in `ConfigurationType`/`Factory`/editor surface.
  Recommended and chosen.
- **Chaining real `ExecutionEnvironment`/`ProgramRunner` runs** (one ROCKS-04 run config per rock,
  launched via the platform): rejected as over-complex — chaining async run launches and detecting
  each exit to gate the next is far heavier than blocking on `OSProcessHandler.waitFor()` on the
  already-background `Task.Backgroundable` thread. We still reuse ROCKS-04's `buildCommandLine` (the
  load-bearing part), just not the async launch pipeline.
- **Reusing `LuaRocksDependencyResolver` for the DAG**: rejected — it resolves the *transitive
  installed-rock* graph for one root, not a flat sibling DAG over discovered rockspecs; using it
  would pull in installed-tree version resolution irrelevant to build order. We reuse only its
  inputs (`RockspecBridge.read`) and cycle vocabulary.
- **DFS post-order topo-sort** (vs Kahn): both are O(V+E); Kahn was chosen because its
  "emit when in-degree hits 0" loop gives the deterministic name tie-break and the cycle set
  (un-emitted remainder) directly, with no recursion/stack-coloring bookkeeping.
- **Parallel builds** (independent rocks concurrently): deferred (risks-and-gaps TBD) — sequential
  is simpler, deterministic, and matches the `install-first-party-rocks.sh` baseline behavior.

## 10. Open Questions

_None — feature has cleared the planning bar._
