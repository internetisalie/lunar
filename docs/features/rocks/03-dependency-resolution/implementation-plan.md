---
id: ROCKS-03-PLAN
title: "Implementation Plan"
type: plan
parent_id: ROCKS-03
priority: "high"
folders:
  - "[[features/rocks/03-dependency-resolution/requirements|requirements]]"
---

# Implementation Plan: Dependency Resolution (ROCKS-03)

Implements `design.md`. Phases map to requirement IDs; each is verified by `requirements.md`
test cases.

## Phase 1: Version Model & Algorithms [Must] — ROCKS-03-03 core
- [ ] **Pre-req (shared)**: add `val ROCKET = getIcon("/icons/rocket_16.png", LuaIcons::class.java)` to
      `net.internetisalie.lunar.lang.LuaIcons` (currently only `FILE` exists, which already maps to that
      asset) — referenced by the tool-window/icon registrations.
- [ ] Create package `net.internetisalie.lunar.rocks.deps`.
- [ ] `LuaRocksVersion` with `parse` (§3.1, exact `DELTAS` table) and `compareTo` (§3.2).
- [ ] `ConstraintOp`, `VersionConstraint.isSatisfiedBy` (§3.4, incl. `~>` partial match).
- [ ] `DependencySpec.parse` (§3.3).
- [ ] Unit tests: TC-ROCKS-03-03, TC-ROCKS-03-04.

## Phase 2: Data Extraction [Must] — ROCKS-03-01/02 inputs
- [ ] **Gated build step**: relocate the bridge scripts from `src/main/lua/` to
      `src/main/resources/lua/` so they ship on the classpath (`rockspec.lua`, `lunar/json.lua`,
      `lunar/export.lua`). This relocation is genuine un-done build work — do not skip it; the
      classpath-resource extraction below depends on it. (Note: the historical `export.lua`
      `ipairs(name)`→`ipairs(names)` bug is **already fixed** in the tree — line 7 uses `ipairs(names)`;
      no code change needed there.)
- [ ] Verify the bridge's actual JSON output shape against a real rockspec (run the relocated
      `export.lua` over a known rockspec and confirm the `package`/`version`/`dependencies` keys and
      nesting match what `RockspecBridge.read` expects).
- [ ] `LuaRocksBridgeFiles` (§2.6a): extract the 3 classpath resources to a cached temp dir;
      `rockspecScript()`, `luaPathTemplate()`.
- [ ] `RockspecBridge.read` (§4.1): build `GeneralCommandLine` (interpreter `path ?: "lua"`)
      with `LUNAR_LUA_PATH_TEMPLATE`, run via `LuaProcessUtil.capture`, parse JSON
      (`com.google.gson.JsonParser`), consume only `package`/`version`/`dependencies`.
- [ ] Smoke-test the bridge end-to-end against a real rockspec.
- [ ] `LuaRocksTreeLocator` (§4.2): `treeRoot`, `installedRocks` (directory enumeration),
      `projectRockspec`.
- [ ] Unit tests with a synthetic `lua_modules` tree: locator returns the expected triples.

## Phase 3: Graph & Conflict Engine [Must] — ROCKS-03-02/03/05
- [ ] `DependencyNode`, `ConflictInfo`.
- [ ] `LuaRocksDependencyResolver.resolve` (§3.5): recursive build, cycle detection, reverse
      edges.
- [ ] `VersionConflictEngine.annotate` (§3.6): MISSING + VERSION_MISMATCH + unsatisfiable-set.
- [ ] Unit tests: TC-ROCKS-03-01, TC-ROCKS-03-02, TC-ROCKS-03-05 (synthetic rockspecs).

## Phase 4: Tool Window UI [Must/Should] — ROCKS-03-01/04/05/06
- [ ] `LuaRocksToolWindowFactory` + `<toolWindow id="LuaRocks" …>` registration (§7).
- [ ] `DependencyTreePanel` (JTree + `DefaultTreeModel`) with conflict/transitive icons;
      resolution on a pooled thread, model update via `invokeLater`.
- [ ] `DependencyInspectorPanel`: selected-node metadata + "Required by" reverse list (§2.10).
- [ ] Toolbar: expand/collapse/refresh + name/version filter (ROCKS-03-06).
- [ ] Manual verification per `human-verification-checklists.md`.

## Verification Tasks
- Unit: version comparator & constraints (Phase 1); locator (Phase 2); resolver + conflicts
  (Phase 3) — all listed above against the TC IDs.
- Integration/manual: open a project with `busted`/`luacheck` installed under `lua_modules`;
  verify transitive tree, a synthetic conflict, and a missing dep.
