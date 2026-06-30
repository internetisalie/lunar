---
id: ROCKS-01-PLAN
title: "Implementation Plan"
type: plan
parent_id: ROCKS-01
priority: "high"
folders:
  - "[[features/rocks/01-project-initialization/requirements|requirements]]"
---

# Implementation Plan: Project Initialization & Setup (ROCKS-01)

Implements `design.md`. Targets GoLand (small IDE) via `DirectoryProjectGenerator`. Phases map
to requirement IDs.

## Phase 1: Templates & Scaffolder [Must] — ROCKS-01-02/03/04
- [ ] **Pre-req (shared)**: add `val ROCKET = getIcon("/icons/rocket_16.png", LuaIcons::class.java)` to
      `net.internetisalie.lunar.lang.LuaIcons` (currently only `FILE` exists, which already maps to that
      asset) — referenced by the tool-window/icon registrations.
- [ ] Create package `net.internetisalie.lunar.rocks.init`.
- [ ] `LuaRocksProjectSettings` data class (§2.1).
- [ ] `LuaRocksTemplates` with the exact bodies in §4 (rockspec library/application, setup.lua,
      mainModule, makefile, bustedSpec, gitignore, workspaceLua).
- [ ] `LuaRocksScaffolder.scaffold` single-rock (§3.1 steps 1–7) + workspace (§3.2), via
      `WriteAction` + `VfsUtil.saveText`/`createChildData`/`createChildDirectory`.
- [ ] Integration tests (temp VFS dir): TC-ROCKS-01-01/02/03/04/05 file presence + contents.

## Phase 2: Run-Config Patching + CLI Enrichment [Must/Should] — ROCKS-01-05/06
- [ ] `LUA_INIT` template patching (§3.3) via `RunManager.getConfigurationTemplate`.
- [ ] Optional `luarocks init --lua-versions` enrichment (§3.1 step 9) + `git init` (§3.1
      step 7), both best-effort via `LuaProcessUtil.capture`, run off the `WriteAction`.
- [ ] Unit/integration test: TC-ROCKS-01-06 (template env contains `LUA_INIT`).

## Phase 3: Wizard UI [Must] — ROCKS-01-01
- [ ] `LuaRocksProjectGenerator : DirectoryProjectGeneratorBase<…>` (§2.2) +
      `<directoryProjectGenerator>` registration.
- [ ] `LuaRocksGeneratorPeer` (§2.3): name/Kind/Type/options/workspace widgets with
      show/hide + enable/disable logic; `validate()` rejects blank/invalid names.
- [ ] Manual verification per `human-verification-checklists.md`.

## Verification Tasks
- Integration: scaffolder file/content assertions (Phase 1); LUA_INIT patch (Phase 2).
- Manual: create Application (all options), Library (Makefile only), and a Workspace; verify
  structure in the GoLand sandbox.
