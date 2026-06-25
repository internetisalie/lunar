---
id: "ROCKS-10-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "done"
priority: "medium"
parent_id: "ROCKS-10"
folders:
  - "[[features/rocks/10-workspace-build/requirements|requirements]]"
---

# Verification Checklists: ROCKS-10 — Workspace Build Orchestration

Run these in a real IDE (sandbox `./gradlew runIde`, or the containerized GoLand per the
`verify-in-ide` skill). A `Kernel/v0`-shaped workspace is one with several first-party rocks at
`rocks/<name>/<name>-*.rockspec` and **no** root rockspec, where some rocks depend on sibling rocks.

## 1. Build Ordering

### Scenario 1.1: Build a Kernel/v0-shaped workspace and confirm dependency order
- **Setup**:
  - A host with `luarocks` and a Lua interpreter on `PATH`, and (for any C-module rocks) a working
    `cc`/`make` toolchain.
  - Open a `Kernel/v0`-shaped project: ≥3 first-party rocks under `rocks/<name>/`, where at least
    one rock's rockspec `dependencies` names a sibling rock's `package` (e.g. a chain A→B→C where C
    depends on B and B depends on A). Confirm ROCKS-09 discovers all of them (the dependency tool
    window shows multiple roots).
- **Steps**:
  1. Open the **Tools** menu (or right-click the project root in the Project view).
  2. Click **Build Workspace (dependency order)**.
  3. Watch the Run tool window console.
- **Expected**:
  - The console prints one `==> Building <package> (n/total)` header per rock.
  - The headers appear in **topological order**: every rock appears **after** all the sibling rocks
    it depends on (for the A→B→C chain: A first, then B, then C).
  - Each rock is built via `luarocks make <its rockspec>` (the command echoes the rockspec path and
    runs in that rock's directory).
  - The final line reports success and the count of rocks built.
- **Result**: Pass / Fail

### Scenario 1.2: Independent rocks build in a stable order
- **Setup**: a workspace with two first-party rocks that do **not** depend on each other (each
  depends only on registry rocks like `lua`/`luafilesystem`).
- **Steps**: invoke **Build Workspace (dependency order)** twice.
- **Expected**: both rocks build successfully; the build order is the same (deterministic,
  name-sorted) on both runs; either physical order is acceptable since there is no edge between them.
- **Result**: Pass / Fail

## 2. Failure & Cycle Handling

### Scenario 2.1: Cycle is reported and nothing is built
- **Setup**: temporarily edit two sibling rockspecs so A depends on B and B depends on A (a cycle).
- **Steps**: invoke **Build Workspace (dependency order)**.
- **Expected**: the console reports a dependency cycle naming the involved packages (A and B); **no**
  `luarocks make` runs (no `==> Building` header appears); the task ends as a failure.
- **Result**: Pass / Fail

### Scenario 2.2: First failure stops the remaining builds
- **Setup**: in an A→B→C chain, introduce a syntax/compile error into rock B's source so its
  `luarocks make` fails.
- **Steps**: invoke **Build Workspace (dependency order)**.
- **Expected**: A builds (exit 0), B is attempted and fails, C is **not** built; the summary names B
  as the failing rock with its exit code and notes that later rocks were skipped.
- **Result**: Pass / Fail

## 3. Action Gating

### Scenario 3.1: Action disabled for a single-rock project
- **Setup**: open a project with exactly **one** discovered rock (single `*.rockspec`).
- **Steps**: open the Tools menu.
- **Expected**: **Build Workspace (dependency order)** is visible but **disabled** (greyed out).
- **Result**: Pass / Fail

### Scenario 3.2: Action enabled for a multi-rock workspace
- **Setup**: open the `Kernel/v0`-shaped workspace from Scenario 1.1.
- **Steps**: open the Tools menu.
- **Expected**: **Build Workspace (dependency order)** is **enabled**.
- **Result**: Pass / Fail

## 4. Vendored Exclusion (first-party only)

### Scenario 4.1: Vendored rocks are not built
- **Setup**: the workspace contains a vendored rockspec under `thirdparty/` (and/or `lua_modules/`).
- **Steps**: invoke **Build Workspace (dependency order)** and inspect the console headers.
- **Expected**: no `==> Building` header appears for any vendored rock — only first-party rocks
  discovered by ROCKS-09 are built. (For non-standard vendoring dirs like `vendor/`, set ROCKS-09's
  `rockspecExcludeGlobs` and re-verify.)
- **Result**: Pass / Fail
