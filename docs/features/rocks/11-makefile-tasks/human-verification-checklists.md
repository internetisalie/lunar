---
id: ROCKS-11-CHECKLIST
title: "Verification Checklists"
type: qa
parent_id: ROCKS-11
folders:
  - "[[features/rocks/11-makefile-tasks/requirements|requirements]]"
---

# Verification Checklists: ROCKS-11 — Makefile Task Integration

Manual, human-run scenarios in a real IDE (GoLand sandbox via `./gradlew runIde`, or the
containerized IDE per the `verify-in-ide` skill). Each is reproducible from a clean project state.

## 1. Scaffolded Makefile content (ROCKS-11-01/-02/-03)

### Scenario 1.1: enriched Makefile is generated
- **Setup**: GoLand with the Lunar plugin loaded.
- **Steps**:
  1. New Project → LuaRocks generator; set name `my-lib`, enable the **Makefile** option.
  2. Open the generated `Makefile`.
- **Expected**: the file contains targets `build`, `test`, `lint`, `format`, `coverage`, `rocks`,
  `clean`; `lint:` recipe is `luacheck src spec`; `format:` recipe is `stylua src spec`;
  `coverage:` has `busted --coverage` then `luacov`; `rocks:` is
  `luarocks install --local my-lib-scm-1.rockspec`; the first line is exactly
  `.PHONY: build test lint format coverage rocks clean`; every recipe line is **tab**-indented.
- **Result**: Pass / Fail

### Scenario 1.2: targets run from the terminal (no Makefile plugin needed)
- **Setup**: as 1.1, with `luacheck`, `stylua`, `busted`, `luacov`, `luarocks` on `PATH`.
- **Steps**:
  1. Open the IDE terminal in the project root.
  2. Run `make lint`, then `make format`, then `make coverage`.
- **Expected**: each target invokes the corresponding CLI; `make coverage` produces
  `luacov.stats.out` and `luacov.report.out`; `make clean` removes them and `lua_modules`/
  `.luarocks`.
- **Result**: Pass / Fail

## 2. Optional Makefile-plugin dependency (ROCKS-11-04/-05)

> Prerequisite: DR ROCKS-11-00-01 completed (pluginId + behavior confirmed).

### Scenario 2.1: plugin ABSENT → Lunar loads, fallback works (TC #6)
- **Setup**: GoLand 2026.1.3 with the Lunar plugin built (Phase 2 applied) and the JetBrains
  "Makefile Language" plugin **not installed**.
- **Steps**:
  1. Launch the IDE; check `Settings → Plugins` shows Lunar enabled with no errors.
  2. Check the IDE log (`idea.log`) for plugin-load errors mentioning `lunar-makefile.xml` or
     `com.jetbrains.lang.makefile`.
  3. Open a scaffolded `Makefile`; confirm no Lunar-added gutter markers appear (markers are the
     Makefile plugin's job).
- **Expected**: Lunar loads cleanly; `lunar-makefile.xml` is silently skipped; no errors; targets
  still runnable via the terminal (Scenario 1.2).
- **Result**: Pass / Fail

### Scenario 2.2: plugin PRESENT → one-click target run
- **Setup**: as 2.1 but with the "Makefile Language" plugin **installed**; tools on `PATH`.
- **Steps**:
  1. Open a scaffolded `Makefile`.
  2. Confirm the Makefile plugin renders run gutter icons next to each target.
  3. Click the gutter icon for `lint`.
- **Expected**: a run console opens and executes `make lint` (→ `luacheck`); exit status shown.
  Lunar contributes nothing beyond having scaffolded the file (no custom Lunar marker/run-config).
- **Result**: Pass / Fail

## 3. Regression

### Scenario 3.1: Makefile option off → no Makefile
- **Setup**: as 1.1 but leave the **Makefile** option disabled.
- **Steps**: scaffold the project; inspect the project root.
- **Expected**: no `Makefile` is written (opt-in flag still respected — `LuaRocksScaffolder.kt:51`).
- **Result**: Pass / Fail
