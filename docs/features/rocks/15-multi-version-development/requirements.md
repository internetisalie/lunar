---
id: ROCKS-15
title: "15: Multi-Version Rocks Development"
type: feature
status: "todo"
priority: low
parent_id: ROCKS
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-15: Multi-Version Rocks Development

> **Status: Future Work / not yet designed.** This is a scoping stub that captures intent and the
> dependency on [ROCKS-14](../14-hererocks-environment/requirements.md). It must go through the
> full `plan-feature` workflow (PRD → design → plan) before any `planned` status.

## Overview

Rock authors routinely need to develop and test a library across **multiple** Lua versions
(5.1 / 5.2 / 5.3 / 5.4 / LuaJIT) — exactly hererocks' original CI-matrix use case. This feature
generalizes [ROCKS-14](../14-hererocks-environment/requirements.md)'s single environment into a
**set** of environments with one *active* binding for editing, plus a **fan-out matrix runner**
for building/testing against every environment at once. Parent epic:
[[features/rocks/requirements|ROCKS]].

## Dependency

**Depends on ROCKS-14.** ROCKS-15's list-of-environments is a strict superset of ROCKS-14's single
`HererocksEnvState`, and its version switcher reuses ROCKS-14's provisioner/binder verbatim. Do not
start ROCKS-15 until ROCKS-14 is `done`.

## Scope (provisional)

### In Scope

- **Environment set**: promote the single `hererocksEnv` descriptor to a `List<HererocksEnvState>`
  plus an `activeEnvId` on `LuaProjectSettings.State`.
- **Version switcher**: a UI affordance (status-bar widget / interpreter picker) that repoints the
  interpreter + `LUAROCKS` binding at the chosen env and fires `LuaSettingsChangedListener.TOPIC`
  so indexing/resolution rebind. The single-valued `LuaRocksEnvironment` resolver contract is
  **unchanged** — it always resolves "the active env".
- **Matrix runner**: `resolveAllEnvs(project)` + a run action that executes the rockspec test/build
  command against each env on its own `Task.Backgroundable`, aggregating per-version pass/fail into
  one results view (the local analog of a CI matrix).
- **Batch provisioning**: "provision the full matrix" — run ROCKS-14 provisioning once per row.

### Out of Scope

- **Cross-version static analysis** (simultaneously flagging code that breaks on 5.1 but not 5.4).
  The IDE resolves one Lua stdlib/tree per project; multi-version editing is *switch-and-reindex*.
  Cross-version correctness is covered at **run/test** time by the matrix runner, not by analysis.
- Registry/credential handling — remains project-level per [ROCKS-06](../06-project-environment/requirements.md);
  not pushed per-env.

## Open scoping questions (to resolve in planning)

- Widget placement (status bar vs. interpreter combo vs. both).
- Matrix results presentation (tool window vs. run-console tabs).
- Whether `activeEnvId` switching should trigger an automatic reindex or prompt.

## Requirements (placeholder — to be expanded)

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| ROCKS-15-01 | **Environment set + active** | M | Store multiple envs and one active id; switching rebinds and notifies. |
| ROCKS-15-02 | **Version switcher UI** | M | User can pick the active env from the UI. |
| ROCKS-15-03 | **Matrix run/test** | S | Run the rockspec build/test command across all envs with aggregated results. |
| ROCKS-15-04 | **Batch provisioning** | C | Provision a whole version matrix in one action. |
