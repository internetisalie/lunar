---
id: "ROCKS-13-HVC"
title: "Human Verification Checklists"
type: "qa"
status: "planned"
parent_id: "ROCKS-13"
folders:
  - "[[features/rocks/13-rockspec-editor-support/requirements|requirements]]"
---

# ROCKS-13: Human Verification Checklists

Run in the containerized GoLand via the **verify-in-ide** skill. Use a fixture file named
`demo-1.0-1.rockspec` (extension matters — the guard keys on `.rockspec`).

## Setup
- [ ] Hot-swap the built plugin jar into the running GoLand (verify-in-ide skill), restart GoLand cleanly.
- [ ] Open a project containing `demo-1.0-1.rockspec` and an unrelated `plain.lua`.

## ROCKS-13-01 — Guard (no leakage onto plain Lua)
- [ ] In `plain.lua`, type `foo = 1` and `local x = 2`. **No** "unknown rockspec field" warning appears.
- [ ] In `plain.lua`, trigger completion at a statement start. Rockspec keys (`package`, `source`) are
      **not** offered.

## ROCKS-13-03/04/05 — Validation
- [ ] In the rockspec, type `pkg_name = "x"`. A WARNING squiggle "Unknown rockspec field 'pkg_name'"
      appears on `pkg_name`.
- [ ] Add `description = { sumary = "x" }`. A WARNING appears on `sumary` (unknown nested field).
- [ ] Add `build = { my_backend_opt = 1 }`. **No** warning inside `build` (open object).
- [ ] With only `package`/`version` present, a WARNING reports missing `source` and `build`.
- [ ] Set `package = {}`. A WARNING "Field 'package' should be a string" appears.
- [ ] Set `version = somevar` (a bare identifier). **No** value-kind warning (conservative).

## ROCKS-13-08 — Quick fix
- [ ] On the missing-required warning, Alt+Enter → "Add missing required rockspec fields". `source = {}`
      and `build = {}` are inserted at the top; the warning clears.

## ROCKS-13-06 — Completion
- [ ] At a top-level statement start, invoke completion. The popup lists `package`, `version`, `source`,
      `build`, `dependencies`, `description`, … and omits keys already present.
- [ ] Inside `description = { <caret> }`, invoke completion. The popup lists `summary`, `detailed`,
      `homepage`, `license`, `maintainer`, `labels`, `issues_url`.

## ROCKS-13-07 — Hover documentation
- [ ] Quick-Doc (Ctrl+Q / hover) on `version` shows its description (e.g. "The version of the package
      plus the rockspec revision …").

## ROCKS-13-09 — Version selection
- [ ] Add `rockspec_format = "3.1"` and `test = {}`. **No** unknown-field warning on `test`.
- [ ] Remove `rockspec_format` (or set `"3.0"`). `test = {}` now warns as an unknown field.

## Regression
- [ ] Syntax highlighting of the rockspec is unchanged (strings, keywords, numbers colourised as before).
- [ ] `plain.lua` editing (completion, inspections) is unaffected.
