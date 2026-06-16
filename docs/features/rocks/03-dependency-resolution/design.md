---
id: ROCKS-03-DESIGN
title: "Technical Design"
type: design
parent_id: ROCKS-03
status: "done"
priority: "high"
folders:
  - "[[features/rocks/03-dependency-resolution/requirements|requirements]]"
---

# Technical Design: Dependency Resolution (ROCKS-03)

> **⚠ Grounding correction (2026-06-16):** the "`export.lua` latent bug `ipairs(name)`→`ipairs(names)`"
> task is **stale — already fixed** in the tree (line 7 uses `ipairs(names)`); drop it and instead verify
> the bridge's JSON output shape against a real rockspec. The `src/main/lua/` → `src/main/resources/lua/`
> relocation (to package the bridge scripts) is genuine un-done build work — treat as an explicit gate.
> Also requires the shared `LuaIcons.ROCKET` field. See [planning-gaps.md](../../../planning-gaps.md#wave-10-grounding-audit-2026-06-16).

## 1. Architecture Overview

### Current State
No LuaRocks code exists. The repo ships the rockspec→JSON bridge `src/main/lua/rockspec.lua`
(+ pure-Lua encoder `src/main/lua/lunar/json.lua`, exporter `src/main/lua/lunar/export.lua`),
the rockspec schemas (`src/main/resources/jsonschema/rockspec-schema-v3{0,1}.json`), and
LuaCATS type stubs. External processes are run via `net.internetisalie.lunar.util.LuaProcessUtil`
(`capture(GeneralCommandLine, timeout): ProcessOutput`). There is **no** existing
`LuaToolManager` (TOOL epic is planned, not built), so this feature depends only on
`LuaProcessUtil`, `LuaProjectSettings`, and the bundled bridge — not on TOOL.

### Target State
A tool window renders the project's dependency graph as a tree, resolving transitive
dependencies from installed rockspecs and flagging version conflicts. Pipeline:

```
.rockspec ──RockspecBridge──▶ DependencySpec[]            (direct deps)
                                  │
LuaRocksTreeLocator ─finds──▶ installed rockspecs ─bridge─▶ each rock's deps
                                  │
                       LuaRocksDependencyResolver ─builds──▶ DependencyNode tree
                                  │                               │
                       VersionConflictEngine ─annotates──▶ ConflictInfo[]
                                  │
                       LuaRocksToolWindowFactory ─renders──▶ tree + inspector
```

All parsing/resolution runs on a pooled background thread (`LuaProcessUtil.capture` blocks);
the tree model is published to the EDT via `invokeLater`. No hard refs to `Project`/`PsiFile`
are retained (the resolver takes `Project` per call).

## 2. Core Components

### 2.1 `net.internetisalie.lunar.rocks.deps.LuaRocksVersion`
- **Responsibility**: Parse and compare a LuaRocks version string per LuaRocks semantics.
- **Threading**: Pure, immutable; any thread.
- **Key API**:
  ```kotlin
  data class LuaRocksVersion(
      val components: List<Double>,   // numeric + delta-mapped tokens, in order
      val revision: Int?,             // trailing -<int>, or null
      val raw: String,
  ) : Comparable<LuaRocksVersion> {
      override fun compareTo(other: LuaRocksVersion): Int   // §3.2
      companion object { fun parse(raw: String): LuaRocksVersion }  // §3.1
  }
  ```

### 2.2 `net.internetisalie.lunar.rocks.deps.VersionConstraint`
- **Responsibility**: One `<op> <version>` predicate; test a version against it.
- **Key API**:
  ```kotlin
  enum class ConstraintOp(val token: String) {
      EQ("=="), NE("~="), LT("<"), LE("<="), GT(">"), GE(">="), COMPATIBLE("~>")
  }
  data class VersionConstraint(val op: ConstraintOp, val version: LuaRocksVersion) {
      fun isSatisfiedBy(v: LuaRocksVersion): Boolean   // §3.4
  }
  ```

### 2.3 `net.internetisalie.lunar.rocks.deps.DependencySpec`
- **Responsibility**: A parsed dependency entry: package name + its constraint list.
- **Key API**:
  ```kotlin
  data class DependencySpec(
      val packageName: String,
      val constraints: List<VersionConstraint>,
      val raw: String,
  ) {
      fun isSatisfiedBy(v: LuaRocksVersion): Boolean = constraints.all { it.isSatisfiedBy(v) }
      companion object { fun parse(raw: String): DependencySpec? }  // §3.3
  }
  ```

### 2.4 `net.internetisalie.lunar.rocks.deps.DependencyNode`
- **Responsibility**: A node in the resolved graph (mutable during build, then read-only).
- **Key API**:
  ```kotlin
  class DependencyNode(
      val packageName: String,
      val requiredBy: MutableList<DependencyNode>,     // reverse edges (ROCKS-03-05)
      val requiredConstraints: MutableList<VersionConstraint>,
      var resolvedVersion: LuaRocksVersion?,           // null ⇒ MISSING
      val isTransitive: Boolean,
      val children: MutableList<DependencyNode>,
      val conflicts: MutableList<ConflictInfo>,
  )
  ```

### 2.5 `net.internetisalie.lunar.rocks.deps.ConflictInfo`
  ```kotlin
  enum class ConflictType { VERSION_MISMATCH, MISSING_DEPENDENCY }
  data class ConflictInfo(val type: ConflictType, val description: String,
                          val offendingConstraints: List<VersionConstraint>)
  ```

### 2.6 `net.internetisalie.lunar.rocks.RockspecBridge`
- **Responsibility**: Run the bundled bridge over a `.rockspec` path and return the parsed
  fields needed here (`package`, `version`, `dependencies`).
- **Threading**: Background only (`capture` blocks). Never call on EDT.
- **Collaborators**: `LuaProcessUtil.capture`; the interpreter executable path
  `LuaProjectSettings.getInstance(project).state.interpreter?.path ?: "lua"` (note
  `State.interpreter` is `LuaInterpreter?`, a data class whose executable is the nullable
  `path: String`; fall back to `"lua"` on `PATH`); `LuaRocksBridgeFiles` (§2.6a); the bundled
  `rockspec.lua`.
- **Key API**:
  ```kotlin
  data class RockspecData(val packageName: String, val version: String?,
                          val dependencies: List<String>)
  object RockspecBridge {
      fun read(project: Project, rockspecPath: Path): RockspecData?  // §4.1
  }
  ```

### 2.6a `net.internetisalie.lunar.rocks.LuaRocksBridgeFiles`
- **Responsibility**: Make the bundled Lua bridge scripts available as real filesystem paths
  at runtime (Lua `dofile`/`require` need files, not classpath URLs).
- **Build change**: the bridge scripts move to `src/main/resources/lua/` so they are packaged
  on the plugin classpath: `lua/rockspec.lua`, `lua/lunar/json.lua`, `lua/lunar/export.lua`.
  (They live under `src/main/lua/` today, which is **not** packaged — this relocation is a
  required Phase-2 task, replacing the prior `<luaDir>` uncertainty.)
- **Key API**:
  ```kotlin
  object LuaRocksBridgeFiles {
      /** Extract the bridge scripts (once) to a cached temp dir; returns that dir. */
      fun ensureExtracted(): Path   // copies the 3 classpath resources under
                                    // PathManager.getTempPath()/lunar-rocks/lua/ if absent
      fun rockspecScript(): Path    // ensureExtracted().resolve("rockspec.lua")
      fun luaPathTemplate(): String // "<dir>/?/init.lua;<dir>/?.lua" for <dir>=ensureExtracted()
  }
  ```
- **Note**: the bundled exporter has a latent bug — `lua/lunar/export.lua` `extract()`
  iterates `ipairs(name)` but the parameter is `names`; fix to `ipairs(names)` as part of
  this feature (Phase 2). With that fix, the bridge `print`s the JSON object described in §4.1.

### 2.7 `net.internetisalie.lunar.rocks.LuaRocksTreeLocator`
- **Responsibility**: Locate the rock tree and enumerate installed `(package, version,
  rockspecPath)` triples.
- **Key API**:
  ```kotlin
  data class InstalledRock(val packageName: String, val version: LuaRocksVersion, val rockspec: Path)
  object LuaRocksTreeLocator {
      fun treeRoot(project: Project): Path?                 // §4.2
      fun installedRocks(project: Project): List<InstalledRock>  // §4.2
      fun projectRockspec(project: Project): Path?          // §4.2
  }
  ```

### 2.8 `net.internetisalie.lunar.rocks.LuaRocksDependencyResolver`
- **Responsibility**: Build the `DependencyNode` tree from the project rockspec + installed
  rocks, with cycle detection.
- **Threading**: Background.
- **Key API**:
  ```kotlin
  object LuaRocksDependencyResolver {
      fun resolve(project: Project): DependencyNode?   // root = project; §3.5
  }
  ```

### 2.9 `net.internetisalie.lunar.rocks.VersionConflictEngine`
- **Key API**:
  ```kotlin
  object VersionConflictEngine {
      fun annotate(root: DependencyNode)   // fills node.conflicts; §3.6
  }
  ```

### 2.10 `net.internetisalie.lunar.rocks.ui.LuaRocksToolWindowFactory`
- **Responsibility**: Build the tool-window UI (tree + inspector split pane, toolbar with
  expand/collapse/refresh/filter).
- **Threading**: EDT for Swing; triggers resolution on a pooled thread and updates the tree
  model via `ApplicationManager.getApplication().invokeLater`.
- **Key API**:
  ```kotlin
  class LuaRocksToolWindowFactory : ToolWindowFactory, DumbAware {
      override fun createToolWindowContent(project: Project, toolWindow: ToolWindow)
  }
  // Swing: DependencyTreePanel(JTree over DefaultTreeModel<DependencyNode>) +
  //        DependencyInspectorPanel (JBPanel) showing selected node detail + reverse deps.
  ```

## 3. Algorithms

### 3.1 `LuaRocksVersion.parse` (mirrors LuaRocks `core/vers.lua` `parse_version`)
- **Input → Output**: `String` → `LuaRocksVersion`.
- **Steps**:
  1. Match the trailing revision: `Regex("^(.*)-(\\d+)$")` on `raw`. If it matches, `main` =
     group 1, `revision` = group 2 as Int; else `main` = `raw`, `revision` = null.
  2. Tokenise `main` left-to-right, consuming each token then any run of `. - _` delimiters:
     - a run of digits `\d+` → `token.toDouble()`;
     - a run of letters `[A-Za-z]+` → `DELTAS[token.lowercase()]` if present, else
       `token[0].code.toDouble() / 1000.0`.
     Append each value to `components` in order. Stop when the remaining string is empty or
     starts with no digit/letter.
  3. Return `LuaRocksVersion(components, revision, raw)`.
- **DELTAS** (exact, from LuaRocks):
  ```
  dev = 120000000   scm = 110000000   cvs = 100000000
  rc  = -1000       pre = -10000      beta = -100000    alpha = -1000000
  ```
- **Edge handling**: empty/garbage input ⇒ `components = []`. Examples: `"3.1-0"` →
  `components=[3.0,1.0]`, `revision=0`; `"scm-1"` → `[1.1e8]`, `revision=1`; `"dev-1"` →
  `[1.2e8]`, `revision=1`; `"1.0beta2"` → `[1.0, 0.0, -100000.0, 2.0]` (letters and digits are
  separate tokens — LuaRocks does **not** split `beta2`; the `0.0` comes from the `1.0`→`1`,`0`
  numeric split). Note: comparison only needs relative order, so absolute values are not
  asserted in tests — see §3.2 test cases.

### 3.2 `LuaRocksVersion.compareTo` (mirrors `__lt`/`__eq`)
- **Steps**:
  1. For `i` in `0 until max(a.components.size, b.components.size)`: let `ai = a.components
     .getOrElse(i){0.0}`, `bi = b.components.getOrElse(i){0.0}`. If `ai != bi` return
     `ai.compareTo(bi)`.
  2. If both `revision`s are non-null and differ, return `a.revision!!.compareTo(b.revision!!)`.
  3. Return `0` (equal).
- **Rules**: shorter component list is zero-padded; a missing revision is ignored in the tie
  (matches LuaRocks: `revision` only compared when both present).

### 3.3 `DependencySpec.parse`
- **Input**: a dependency entry string, e.g. `"luasocket >= 2.0, < 4.0"` or `"penlight"` or
  `"copas ~> 2"`.
- **Steps**:
  1. Trim. Split off the **name**: leading match `Regex("^\\s*([A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)?)\\s*(.*)$")`
     (name per the v3.1 rockspec regex; optional `scope/name`). Group 1 = `packageName`, group
     2 = the constraints remainder. If no name → return null.
  2. Split the remainder on `,`; for each non-empty piece, parse one constraint (§3.4 parse).
     An empty remainder ⇒ `constraints = []` (any version satisfies).
  3. Return `DependencySpec(packageName, constraints, raw)`.

### 3.4 Constraint parse + `isSatisfiedBy` (mirrors `match_constraints`)
- **Parse one constraint**: trim; longest-token match of an operator prefix from
  `{"==","~=","<=",">=","~>","<",">"}` (check 2-char ops before 1-char). If no operator
  prefix, the implicit operator is `==` with the whole token as the version (LuaRocks treats a
  bare version as exact). The remainder (trimmed) is `LuaRocksVersion.parse`d.
- **`isSatisfiedBy(v)`** by `op`:
  - `EQ`: `v.compareTo(c) == 0`
  - `NE`: `v.compareTo(c) != 0`
  - `LT`: `v < c`; `LE`: `v <= c`; `GT`: `v > c`; `GE`: `v >= c`
  - `COMPATIBLE` (`~>`): **partial match** — for `i in c.components.indices`,
    `v.components.getOrElse(i){0.0} == c.components[i]` must hold for **all** i; remaining
    `v` components are unconstrained. (So `~>1.2` matches `1.2.0`,`1.2.99`, not `1.3.0`.)
- **A `DependencySpec` is satisfied** iff **all** its constraints are (`AND`).

### 3.5 Graph build (`LuaRocksDependencyResolver.resolve`)
- **Input → Output**: `Project` → root `DependencyNode` (the project), or null if no project
  rockspec.
- **Steps**:
  1. `installed = LuaRocksTreeLocator.installedRocks(project)` indexed as
     `Map<String, List<InstalledRock>>` by lowercase package name.
  2. Read the project rockspec via `RockspecBridge.read`; build root node
     (`packageName = data.packageName`, `isTransitive = false`).
  3. Recursively expand, carrying a `visiting: MutableSet<String>` (lowercase package names on
     the current path) for **cycle detection** and a `seen: MutableMap<String, DependencyNode>`
     for sharing:
     - For each `DependencySpec` of the current node:
       - If `spec.packageName.lowercase()` ∈ `visiting` → create a node marked with a
         back-edge (do **not** recurse) — cycle broken.
       - Else pick the installed rock: the **highest** installed version (by §3.2) whose
         version satisfies `spec` (if several) — else any installed version (resolved but
         possibly conflicting) — else `resolvedVersion = null` (MISSING).
       - Create child `DependencyNode(isTransitive = current != root)`, add `spec`'s
         constraints to `child.requiredConstraints`, add reverse edge `child.requiredBy += current`.
       - If resolved and not a cycle, recurse into the rock's own rockspec deps with
         `visiting + packageName`.
  4. Return root.
- **Bounds**: cycle set guarantees termination; `seen` map keeps each package expanded once
  (a re-encountered package reuses its node but still records the new `requiredBy`/constraint).

### 3.6 Conflict detection (`VersionConflictEngine.annotate`)
- **Steps** (one pass over all nodes, grouped by lowercase package name):
  1. For each package group, gather `allConstraints = ⋃ node.requiredConstraints`.
  2. **MISSING_DEPENDENCY**: if `resolvedVersion == null`, add `ConflictInfo(MISSING_DEPENDENCY,
     "‘<pkg>’ is required but not installed", allConstraints)` to the node.
  3. **VERSION_MISMATCH**: if `resolvedVersion != null` and some `c ∈ allConstraints` has
     `!c.isSatisfiedBy(resolvedVersion)`, add `ConflictInfo(VERSION_MISMATCH,
     "installed <ver> violates <c.token c.version.raw> required by <parents>", failing)`.
  4. **Unsatisfiable set** (no install present to pick from): if the constraint set is
     internally unsatisfiable — detected as a non-empty pair `(GE/GT a, LE/LT b)` with `a > b`
     — flag VERSION_MISMATCH even when nothing is installed (TC-ROCKS-03-02: `>=2.0` vs `<1.5`).
- **Rules**: each node gets its own `conflicts`; the UI shows a warning icon when
  `node.conflicts.isNotEmpty()`.

## 4. External Data & Parsing

### 4.1 Rockspec → JSON bridge (`RockspecBridge.read`)
- **Command**: let `interpreterExe = LuaProjectSettings.getInstance(project).state.interpreter
  ?.path ?: "lua"`, `scripts = LuaRocksBridgeFiles` (§2.6a). Build
  `GeneralCommandLine(interpreterExe, scripts.rockspecScript().toString(),
  rockspecPath.toString())` with environment `LUNAR_LUA_PATH_TEMPLATE =
  scripts.luaPathTemplate()`. Run via `LuaProcessUtil.capture(cmd, 10_000)`.
- **Output format**: with the §2.6a exporter fix applied, the bridge `print`s a single JSON
  **object** on stdout (encoder `lua/lunar/json.lua`). The object contains all rockspec fields
  the script exports (`rockspec_format`, `package`, `version`, `description`, `dependencies`,
  `build_dependencies`, …); **this feature reads only three** and ignores the rest:
  `package` (string), `version` (string), `dependencies` (array of constraint strings).
  Sample (truncated to the consumed keys):
  ```json
  {"package":"busted","version":"2.2.0-1","dependencies":["lua >= 5.1","say >= 1.4-3","luassert >= 1.9.0"]}
  ```
- **Parse strategy**: parse stdout with `com.google.gson.JsonParser` (bundled on the IntelliJ
  platform classpath; if absent, add `com.google.code.gson:gson` to `build.gradle.kts` — see
  `ROCKS-03-DR-02`). Read `package`/`version` as strings; map each element of the
  `dependencies` array through `DependencySpec.parse`; ignore all other keys. If
  `dependencies` is absent → empty list.
- **Failure handling**: non-zero `exitCode`, timeout (`exitCode == -1`), empty stdout, or JSON
  parse error → `read` returns null and logs `log.warn` with the rockspec path + stderr; the
  caller treats the node as MISSING.

### 4.2 Rock tree layout (`LuaRocksTreeLocator`)
- **`treeRoot`**: first existing of `<projectBaseDir>/lua_modules`, then `<projectBaseDir>/
  .luarocks`, then null. (System trees are out of scope for v1 — `ROCKS-03-DR-01`.)
- **`installedRocks`**: for each `rocks-<X.Y>` dir under `<treeRoot>/lib/luarocks/`, enumerate
  `<pkg>/<version>/` subdirectories; the rockspec is `<pkg>/<version>/<pkg>-<version>.rockspec`.
  `packageName = <pkg>`, `version = LuaRocksVersion.parse(<version>)`. Directory enumeration —
  **no manifest parsing required** (the `manifest` Lua file is a fallback only, `ROCKS-03-DR-03`).
- **`projectRockspec`**: the single `*.rockspec` in `<projectBaseDir>` (if several, the
  newest-modified); null if none.

## 5. Data Flow

### Example 1: `A → B → C` (TC-ROCKS-03-01/02)
`resolve` reads project rockspec → dep `A`. Tree locator finds installed `A`, `B`, `C`.
Expand `A` (rockspec dep `B`) → child `B` (transitive); expand `B` (dep `C`) → child `C`.
Tree: project → A → B → C. No conflicts (all installed, constraints satisfied).

### Example 2: conflict (TC-ROCKS-03-02)
`A` requires `lib >= 2.0`, `B` requires `lib < 1.5`. Both constraints land on the `lib` group.
`VersionConflictEngine` step 4 detects `(>=2.0, <1.5)` is unsatisfiable → both the `lib` node
(or, if `lib` absent, the two requiring edges) flagged VERSION_MISMATCH; the inspector shows
"Required by A (>=2.0) and B (<1.5)".

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Dependency cycle `A→B→A` | `visiting` set breaks recursion; back-edge node rendered without children. |
| Same package required twice with compatible constraints | Single node, both constraints recorded, no conflict if resolved version satisfies all. |
| `lua >= 5.1` pseudo-dependency | Treated as a normal dep; if no `lua` rock installed, MISSING unless filtered (the UI may hide the `lua` node — `ROCKS-03-DR-04`). |
| Rockspec with platform-mapped `dependencies` table (object, not array) | Bridge emits an object; `read` flattens values to a string list; if shape unrecognised → empty list + `log.warn`. |
| No interpreter configured | exe falls back to `"lua"` on `PATH`; if that also fails, `capture` returns non-zero and `read` returns null → tool window shows "Configure a Lua interpreter" empty state. |
| Bridge script bug (`export.lua` uses `name` vs `names`) | Fixed in this feature (§2.6a, Phase 2) — `ipairs(names)`; smoke-tested before Phase 2 sign-off. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<toolWindow
    id="LuaRocks"
    anchor="right"
    secondary="true"
    icon="net.internetisalie.lunar.lang.LuaIcons.ROCKET"
    factoryClass="net.internetisalie.lunar.rocks.ui.LuaRocksToolWindowFactory"/>
```
- Icon: reuse the existing `src/main/resources/icons/rocket_16.png` (add a `ROCKET` field to
  `LuaIcons` if not present).
- Services: the resolver/engine/locator/bridge are stateless `object`s — no `<projectService>`
  registration needed. If caching is later added, register a `@Service(PROJECT)` (out of scope).
- Bundled resources: `lua/rockspec.lua` + `lua/lunar/*.lua` are already in `src/main/lua`; the
  build must copy them to a runtime-resolvable path (confirm in `ROCKS-03-DR-06`).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ROCKS-03-01 Dependency Tree View | M | §2.8, §2.10, §3.5, §7 |
| ROCKS-03-02 Transitive Resolution | M | §3.5 (recursive expand) |
| ROCKS-03-03 Conflict Detection | M | §2.9, §3.6 |
| ROCKS-03-04 Package Metadata | S | §2.6 (bridge fields), §2.10 inspector |
| ROCKS-03-05 Reverse Dependency View | C | §2.4 `requiredBy`, §3.5 |
| ROCKS-03-06 Search in Tree | S | §2.10 toolbar filter |

## 9. Alternatives Considered

- **Manifest parsing vs directory enumeration**: chose directory enumeration of `rocks-<X.Y>`
  (no Lua-table parser needed). Manifest parsing kept as a DR fallback.
- **`luarocks` CLI output parsing vs rockspec bridge**: chose the rockspec→JSON bridge — its
  output is structured JSON (vs. luarocks' unstructured text, which ROCKS-02 must parse). This
  keeps ROCKS-03 independent of ROCKS-02's text parser.
- **TOOL epic dependency**: deliberately avoided — uses `LuaProcessUtil` directly so ROCKS-03
  is buildable before TOOL lands.

## 10. Open Questions

_None — feature has cleared the planning bar._
