---
id: ROCKS-11
title: "11: Makefile Task Integration"
type: feature
parent_id: ROCKS
status: "done"
vf_icon: ✅
priority: "low"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-11: Makefile Task Integration

## Overview
Make is the de-facto orchestrator for Lua projects (e.g. `~/Documents/Kernel/v0/Makefile`).
Lunar does **not** build a Make engine. This feature (a) enriches the scaffolded `Makefile`
with canonical targets that invoke the same CLI tools Lunar already integrates
(`luacheck`/`stylua`/`luacov`/`busted`/`luarocks`) for CLI/CI use, and (b) adds an **optional**
soft dependency on the freely-available JetBrains "Makefile Language" plugin so those targets
are one-click runnable when that plugin is installed — degrading gracefully (terminal / run
config) when it is absent. Parent epic: [ROCKS](../requirements.md).

## Scope

### In Scope
- Enrich `LuaRocksTemplates.makefile(name)` to emit canonical targets: `build`, `test`,
  `lint`, `format`, `coverage`, `rocks`, `clean`, wired to the tool binaries.
- Keep `.PHONY` correct (lists every target).
- Optional `<depends optional="true" config-file="lunar-makefile.xml">com.jetbrains.lang.makefile</depends>`
  so the scaffolded Makefile's targets are one-click runnable when the user has the plugin.
- Graceful behavior when the plugin is absent: no hard dependency; targets still run from the
  terminal or the existing ROCKS-04 run-config.

### Out of Scope
- **Custom Makefile PSI, parser, line-markers, or run-config provider** — that is the JetBrains
  Makefile Language plugin's job; Lunar must not re-implement it. Deferred indefinitely.
- **Duplicating the native in-editor format/lint/coverage UX** — `luacheck` (annotator,
  `analysis/luacheck/`), `stylua` (formatter via the TOOL registry), `luacov` coverage
  (`coverage/`), and `busted` test runs (`run/test/`) remain the in-editor experience. The
  Makefile targets only invoke the same CLIs for headless CLI/CI use.
- Bundling the Makefile Language plugin (it is marketplace-only, not bundled in GoLand
  2026.1.3).
- Parsing arbitrary user Makefile targets into the IDE (covered conceptually by the Makefile
  plugin once installed).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| ROCKS-11-01 | **Enriched Makefile template** | M | `LuaRocksTemplates.makefile(name)` emits `build`, `test`, `lint`, `format`, `coverage`, `rocks`, `clean` targets with the recipes specified in design §2.1/§3.1. |
| ROCKS-11-02 | **Lint / format / coverage recipes** | M | `lint:` invokes `luacheck`, `format:` invokes `stylua`, `coverage:` invokes `busted --coverage` then `luacov`, with the exact recipe text in design §3.1. |
| ROCKS-11-03 | **Correct `.PHONY`** | M | The `.PHONY` line lists every target emitted (no missing/extra entries). |
| ROCKS-11-04 | **Optional Makefile-plugin soft dependency** | S | `plugin.xml` declares `<depends optional="true" config-file="lunar-makefile.xml">com.jetbrains.lang.makefile</depends>`; the plugin loads with no hard dependency when the Makefile plugin is absent (gated on DR ROCKS-11-00-01). |
| ROCKS-11-05 | **Graceful fallback documentation** | C | When the Makefile plugin is absent, the design and user-facing note state that target running falls back to the terminal / ROCKS-04 run config; Lunar adds no custom Makefile markers. |

## Detailed Specifications

### ROCKS-11-01 / ROCKS-11-02: Enriched template
The exact template text and the per-target recipe lines are specified in `design.md` §2.1 and
§3.1. Tool binary references in recipes use the bare tool name (`luacheck`, `stylua`, `busted`,
`luacov`, `luarocks`) resolved from `PATH` at make-run time — see design §3.2 for the rationale
(the Makefile is a portable CI artifact, not an IDE-bound file, so it must not hard-code absolute
paths from the TOOL registry).

### ROCKS-11-03: `.PHONY`
All targets are phony (none produce a file named after the target). The emitted line is exactly:
`.PHONY: build test lint format coverage rocks clean`.

### ROCKS-11-04: Optional plugin dependency
Mirrors the TOOL-00 terminal precedent (`plugin.xml:25-28`,
`META-INF/lunar-terminal.xml`). `lunar-makefile.xml` is loaded only when
`com.jetbrains.lang.makefile` is present. Its concrete contents are in design §7; per DR
ROCKS-11-00-01 it contributes **nothing** beyond the dependency declaration unless the spike
confirms a usable extension point.

## Behavior Rules
- The Makefile is written only when `LuaRocksProjectSettings.makefile == true`
  (`LuaRocksScaffolder.kt:51-53`); this feature does not change the opt-in flag or when the file
  is written, only its content.
- Recipe lines MUST be tab-indented (Make requires hard tabs, not spaces).
- No absolute paths or IDE-specific values are emitted into the Makefile (CI portability).

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | ROCKS-11-01 | `LuaRocksTemplates.makefile("my-lib")` | call the function | result contains `build:`, `test:`, `lint:`, `format:`, `coverage:`, `rocks:`, `clean:` (each followed by a newline + tab recipe) |
| 2 | ROCKS-11-02 | `LuaRocksTemplates.makefile("my-lib")` | call the function | result contains a `lint:` target whose recipe line contains `luacheck`, a `format:` target whose recipe contains `stylua`, and a `coverage:` target whose recipe contains `busted --coverage` and `luacov` |
| 3 | ROCKS-11-03 | `LuaRocksTemplates.makefile("my-lib")` | call the function | result contains the exact line `.PHONY: build test lint format coverage rocks clean` |
| 4 | ROCKS-11-01 | `LuaRocksProjectSettings(name="my-lib", makefile=true)` | `LuaRocksScaffolder.scaffold(...)` | the generated `Makefile` file exists and `readText()` contains `lint:`, `format:`, and `coverage:` recipes invoking `luacheck`/`stylua`/`luacov` |
| 5 | ROCKS-11-01 | `LuaRocksTemplates.makefile("my-lib")` (rocks target) | call the function | result contains a `rocks:` target whose recipe contains `luarocks install --local my-lib-scm-1.rockspec` (name interpolated) |
| 6 | ROCKS-11-04 | a built plugin distribution **without** the Makefile plugin installed | load Lunar in GoLand 2026.1.3 | the plugin loads with no error; `lunar-makefile.xml` is silently skipped (verified manually, gated on DR ROCKS-11-00-01) |

## Acceptance Criteria
- [x] ROCKS-11-01: scaffolding with the Makefile option produces a Makefile with all seven canonical targets.
- [x] ROCKS-11-02: `lint`/`format`/`coverage` recipes invoke `luacheck`/`stylua`/`luacov` respectively.
- [x] ROCKS-11-03: `.PHONY` lists exactly the emitted targets.
- [x] ROCKS-11-04: optional `<depends>` added; plugin loads with the Makefile plugin absent (DR-gated).
- [x] ROCKS-11-05: fallback behavior documented; no custom Makefile markers added.

## Non-Functional Requirements
- **Threading**: none beyond the existing scaffolding write. `LuaRocksScaffolder.scaffold` already
  runs inside a `WriteAction` (`LuaRocksScaffolder.kt:20`, doc-commented "Must be called inside a
  WriteAction"); template generation is pure string building with no I/O.
- **Portability**: emitted Makefile must contain no absolute paths and use hard tabs.

## Dependencies
- ROCKS-01 (Project Initialization) — owns `LuaRocksScaffolder` / `LuaRocksTemplates` and the
  `makefile` opt-in flag.
- TOOL track (`tool/LuaToolManager.kt`, `tool/LuaToolDescriptor.kt`) — the same CLIs the recipes
  invoke; no compile-time dependency (recipes use `PATH` names, see design §3.2).
- DR ROCKS-11-00-01 — Makefile Language plugin spike; gates ROCKS-11-04.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklists: [human-verification-checklists.md](human-verification-checklists.md)
