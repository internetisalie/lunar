---
id: "RUN-01-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "RUN-01"
folders:
  - "[[features/debug/run-01-lua-interpreter-sdk/requirements|requirements]]"
---

# RUN-01: Implementation Plan

> Status: **done** — the feature is implemented and shipping. Tasks are recorded as completed
> with the files that realize each design section.

## Phases

### Phase 1: Model & Family Registry [Must]
- **Goal**: define the serializable interpreter model and the static family registry.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.platform.LuaInterpreter` data class with derived
    `valid`/`family`/`familyOrUnknown`/`executable` — realizes design §2.1
    (`platform/LuaInterpreter.kt:11`).
  - [x] Create `LuaInterpreterFamily` with `BinaryType`, `platformExecutableName`, `leveler`,
    and the static `FAMILIES` map (Lua/LuaJIT/Tarantool) + `UNKNOWN_INTERPRETER` — realizes
    design §2.2 (`platform/LuaInterpreter.kt:65`).
  - [x] Add `LuaPlatform` enum usage and `LuaLanguageLevel` mapping — realizes design §2.2
    (`platform/LuaPlatform.kt:3`, `lang/LuaLanguageLevel.kt:19`).
- **Exit criteria**: `valid`/`family` resolution and version→level mapping pass (TC 1–3, 6–7).

### Phase 2: Discovery & Identification Service [Must]
- **Goal**: scan the search path and identify binaries via `-v`.
- **Tasks**:
  - [x] Create `@Service(Service.Level.APP) LuaInterpreterService` with `findInterpreters`,
    `find`, `validate`, `identify` — realizes design §2.3, §3.1, §3.2
    (`platform/LuaInterpreterService.kt:15`).
  - [x] Create `Banner` parser (`VERSION_PATTERN`, stderr-first selection) — realizes design
    §2.4, §3.3, §4.1 (`platform/LuaInterpreterService.kt:178`).
  - [x] Add env-var substitution (`substituteEnvVars`, `envVarPattern`) and glob helpers
    (`isGlob`/`patternFromGlob`/`matchesGlob`) — realizes design §3.4, §3.5
    (`platform/LuaInterpreterService.kt:112`, `:209`).
  - [x] Define `PATHS_UNIX` / `PATHS_WINDOWS` search arrays — realizes design §3.1
    (`platform/LuaInterpreterService.kt:132`, `:149`).
- **Exit criteria**: banner parse + invalid-on-failure behavior pass (TC 4–5, 9–10).

### Phase 3: Command-Line Construction [Must]
- **Goal**: turn a selected interpreter into an executable command line.
- **Tasks**:
  - [x] Create `newLuaInterpreterCommandLine` (CONSOLE parent env, working dir, `.jar` →
    `java -cp <jar> lua`) — realizes design §2.7, §3.2 (`command/LuaCommandLine.kt:32`).
  - [x] Add `newProjectLuaInterpreterCommandLine` consuming `LuaProjectSettings` — realizes
    design §5 Example 2 (`command/LuaCommandLine.kt:18`).
- **Exit criteria**: JavaJar command line is correct (TC 8).

### Phase 4: Persistence [Must]
- **Goal**: persist the global inventory and per-project selection.
- **Tasks**:
  - [x] Add `State.interpreters: List<LuaInterpreter>` + `validInterpreters()` /
    `findInterpreter()` to `LuaApplicationSettings` and register the `applicationService` in
    `plugin.xml` — realizes design §7 (`settings/LuaApplicationSettings.kt:39`, plugin.xml:370).
  - [x] Add `State.interpreter: LuaInterpreter?` to `LuaProjectSettings` — realizes design §7
    (`settings/LuaProjectSettings.kt:50`).
- **Exit criteria**: inventory + selection round-trip across restart (TC 11–12).

### Phase 5: Settings & Selection UI [Must / Should]
- **Goal**: surface the inventory in settings and provide a reusable selector.
- **Tasks**:
  - [x] Create `LuaInterpretersTable` (`ListTableWithButtons`) with editable path column, file
    chooser, read-only product/version/platform/language columns, and the "Re-scan" toolbar
    action (merge by path) — realizes design §2.5, §3.6 (`settings/LuaInterpretersTable.kt:35`).
  - [x] Host the table in `LuaApplicationSettingsPanel` under the "Lua" applicationConfigurable
    — realizes design §7 (`settings/LuaApplicationSettingsPanel.kt:54`, plugin.xml:393).
  - [x] [Should] Create `customizeLuaInterpreterComboBox` + `LuaInterpreterListCellRenderer`
    with background inspection of free-typed paths — realizes design §2.6, §5 Example 3
    (`platform/LuaInterpreterComponent.kt:18`).
- **Exit criteria**: add/edit/re-scan/delete work; combo box lists valid interpreters and
  renders invalid/unknown distinctly.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| RUN-01-01 | M | Phase 1 |
| RUN-01-02 | M | Phase 1 |
| RUN-01-03 | M | Phase 2 |
| RUN-01-04 | M | Phase 2 |
| RUN-01-05 | M | Phase 4 |
| RUN-01-06 | M | Phase 4 |
| RUN-01-07 | M | Phase 5 |
| RUN-01-08 | S | Phase 5 |
| RUN-01-09 | M | Phase 3 |
| RUN-01-10 | S | Phase 1 / Phase 2 |
| RUN-01-11 | S | Phase 2 |
| RUN-01-12 | C | Phase 1 / Phase 2 |

## Verification Tasks

> **Test-coverage audit (2026-06-21):** an audit of `src/test/` found that only the
> `Banner.create` *success* path is actually covered by an automated test
> (`settings/TestBanner.kt`). The remaining unit-test tasks were previously marked `[x]` but
> **no corresponding test exists** in the codebase. They are corrected to `[ ]` below and the
> missing coverage is tracked as de-risking tasks in [risks-and-gaps.md](risks-and-gaps.md).

- [ ] Unit-test `LuaInterpreter.valid`/`family` and family registry resolution — covers TC 1–3. **No such test exists.**
- [x] Unit-test `Banner.create` for success banners (Lua 5.0–5.4, LuaJIT, Tarantool) — covers TC 4 (`settings/TestBanner.kt`).
- [ ] Unit-test `Banner.create` for a non-matching/garbage banner returning `null` — covers TC 5. **No such test exists.**
- [ ] Unit-test `family.languageLevel` mapping across 5.1–5.4 and fallback — covers TC 6–7. **No such test exists.**
- [ ] Unit-test `newLuaInterpreterCommandLine` JavaJar branch — covers TC 8. **No such test exists.**
- [ ] Unit-test env-var expansion and `platformExecutableName` — covers TC 9–10. **No such test exists.**
- [ ] Unit-test auto-discovery and re-scan merge (replace-by-path / append-new / preserve-user) — covers TC 13–16. **No such test exists.**
- [ ] Persistence round-trip for application inventory + per-project interpreter selection — covers TC 11–12. **No such test exists** (`settings/LuaProjectSettingsTest.kt` covers `Target`/language-level migration, not interpreter persistence; `LuaApplicationSettings` tests cover `toolInventory`, not `interpreters`).
- [ ] Manual: Settings ▸ Languages ▸ Lua — add/edit/re-scan/delete an interpreter. **Not recorded/verified.**

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Model & Family Registry | done | Must |
| Phase 2: Discovery & Identification Service | done | Must |
| Phase 3: Command-Line Construction | done | Must |
| Phase 4: Persistence | done | Must |
| Phase 5: Settings & Selection UI | done | Must |
