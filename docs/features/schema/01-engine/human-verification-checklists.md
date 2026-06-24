---
id: "SCHEMA-01-HVC"
title: "Human Verification Checklists"
type: "qa"
status: "planned"
parent_id: "SCHEMA-01"
folders:
  - "[[features/schema/01-engine/requirements|requirements]]"
---

# SCHEMA-01: Human Verification Checklists

Run in the containerized GoLand via the **verify-in-ide** skill. Use the TEST-ONLY provider + harness
schema (or, once SCHEMA-02 lands, a real `.rockspec`). Fixtures: a shape-A data file, a shape-B
`return {…}` data file, and a plain `plain.lua`.

## Setup
- [ ] Build with `com.intellij.modules.json` in `platformBundledPlugins`; hot-swap the plugin jar;
      relaunch GoLand cleanly.
- [ ] Confirm the JSON plugin is enabled (Settings → Plugins → JSON).

## Engine engages on mapped files
- [ ] In a mapped shape-A file, an unknown key (`bogus = 1`) shows a schema WARNING.
- [ ] An enum violation (`opts = { level = "mid" }` where enum is low/high) is flagged — proving the
      platform engine (not a depth-1 hand-rolled check) is doing the work.
- [ ] An array-typed key with the wrong scalar (`tags = "a"`) is flagged.
- [ ] A shape-B `return { name = "x", bogus = 1 }` flags `bogus` (returned table resolved as root).

## Completion & docs (via the walker)
- [ ] Completion at a top-level key position offers the schema's keys (`name`, `opts`, `tags`).
- [ ] Quick-Doc on a key shows the schema `description`.

## Plain Lua is untouched (the critical guard)
- [ ] `plain.lua` with `name = 1; bogus = 2` shows **no** schema warnings.
- [ ] Completion / inspections in `plain.lua` are unchanged from before the plugin update.

## Regression
- [ ] No new errors in `idea.log` referencing the JSON schema engine or the Lua walker.
- [ ] Existing `.lua` features (highlighting, type inference, luacheck) still work.
