---
id: ROCKS-15
title: "15: Multi-Version Rocks Development"
type: feature
status: "planned"
priority: low
parent_id: ROCKS
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-15: Multi-Version Rocks Development

> **Depends on [ROCKS-14](../14-hererocks-environment/requirements.md).** ROCKS-14's symbols
> (`HererocksEnvState`, `HererocksProvisioner`, `HererocksEnvBinder`, `HererocksLocator`,
> `HererocksEnvDetector`, the `rocks.env` package) are **planned, not yet on disk**. ROCKS-15
> generalizes ROCKS-14's single descriptor into a set — see `design.md` / `implementation-plan.md`.

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

## Resolved scoping decisions

The three open scoping questions from the original stub are resolved in `design.md`:

- **Widget placement** → a **status-bar widget** (`StatusBarWidgetFactory`), popup lists all envs
  with a check on the active one. No interpreter-combo change (that UI is owned by
  `LuaProjectSettingsPanel`); the status bar is the single switcher (design §2.5).
- **Matrix results presentation** → one **tool window** tab holding a per-env row table backed by a
  single `ConsoleView`-per-row model, not N run-console tabs (design §2.6, §3.3).
- **Active-env switching** → fires `LuaSettingsChangedListener.TOPIC` (the existing reindex trigger),
  which callers already coalesce; **no extra prompt** (design §3.2). Consistent with ROCKS-14's bind.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| ROCKS-15-01 | **Environment set + active** | M | Store a `List<HererocksEnvState>` plus an `activeEnvId` on `LuaProjectSettings.State`; migrate the ROCKS-14 single `hererocksEnv` into the list on load. |
| ROCKS-15-02 | **Active-env switch rebinds** | M | Switching the active env repoints the interpreter + `LUAROCKS` binding at that env and fires `LuaSettingsChangedListener.TOPIC`; the single-valued `LuaRocksEnvironment` contract is unchanged (always resolves the active env). |
| ROCKS-15-03 | **Version switcher UI** | M | A status-bar widget lets the user pick the active env from the set; the active one is marked; "Add environment…" delegates to ROCKS-14 create. |
| ROCKS-15-04 | **Matrix run/test** | S | Run the rockspec build/test command against every env, each on its own `Task.Backgroundable`, aggregating per-env pass/fail into one results view. |
| ROCKS-15-05 | **Batch provisioning** | C | Provision a whole version matrix (one ROCKS-14 provision per row) in a single action. |

## Detailed Specifications

### ROCKS-15-01: Environment set + active

`LuaProjectSettings.State` gains `var hererocksEnvs: MutableList<HererocksEnvState> = mutableListOf()`
and `var activeEnvId: String = ""`. On `loadState`, a **one-time migration** moves any legacy
single `hererocksEnv` (ROCKS-14 field, retained as `@Deprecated` for read) into `hererocksEnvs` and
sets `activeEnvId` to its `id`. Each env's `id` is unique (UUID from ROCKS-14 `provision`).
`activeEnvId == ""` or an id not present in the list ⇒ no active env (empty state).

### ROCKS-15-02: Active-env switch

`setActiveEnvAndNotify(envId)` looks up the env by id; if found, calls
`HererocksEnvBinder.bind(project, env)` (ROCKS-14, which registers/binds interpreter + `luarocks`
and fires `TOPIC`) and sets `activeEnvId = envId`. Unknown id ⇒ no-op. Switching never re-provisions.

### ROCKS-15-03: Version switcher UI

A `StatusBarWidgetFactory` produces a text-presentation widget showing the active env's
`displayLabel()` (ROCKS-14) or `"No Lua env"`. Clicking opens a popup listing every env
(active one bulleted/checked); selecting one calls `setActiveEnvAndNotify`; a trailing
"Add environment…" item runs the ROCKS-14 `CreateHererocksEnvAction`.

### ROCKS-15-04: Matrix run/test

`resolveAllEnvs(project): List<HererocksEnvState>` returns the full set. The matrix action, for a
chosen luarocks command (`make` / `test` / `build`) and rockspec, spawns **one**
`Task.Backgroundable` per env that runs `<env>/bin/luarocks <command> <rockspec>` via
`GeneralCommandLine`, capturing exit code + output, and reports each row into a shared results model
(pass = exit 0). Rows run concurrently; the aggregate is "all passed" iff every row exit == 0.

### ROCKS-15-05: Batch provisioning

A dialog collecting a list of {flavor, luaVersion} rows + a base directory produces one
`HererocksEnvState` per row (directory = `<base>/<flavor>-<luaVersion>`) and calls the ROCKS-14
`HererocksProvisioner.provision(spec, CREATE)` once per row (each on its own background task via the
ROCKS-14 concurrency guard). Provisioned envs are appended to `hererocksEnvs`.

## Acceptance / Test Cases

| TC | Requirement | Input | Expected |
|----|-------------|-------|----------|
| TC-1 | 15-01 | State with legacy `hererocksEnv={id=A,dir=/p/.lua}` loaded | after `loadState`, `hererocksEnvs` contains that env and `activeEnvId == "A"`; round-trips through `getState`. |
| TC-2 | 15-01 | State with two envs A,B and `activeEnvId=B` | `resolveAllEnvs` returns [A,B]; active resolves to B. |
| TC-3 | 15-02 | `setActiveEnvAndNotify("B")` on a project with envs A(active),B | `HererocksEnvBinder.bind` called with B; `activeEnvId=="B"`; `TOPIC` fired ≥1. |
| TC-4 | 15-02 | `setActiveEnvAndNotify("Z")` (unknown id) | no-op: `activeEnvId` unchanged, `bind` not called, no TOPIC. |
| TC-5 | 15-02 | active env = B | `LuaRocksEnvironment.resolveExecutable(project)` returns B's `bin/luarocks` (via the existing TOOL-02 binding set by bind). |
| TC-6 | 15-03 | widget with active env B `displayLabel()=="PUC 5.3"` | widget text == `"PUC 5.3"`; popup lists A and B, B checked; empty set ⇒ text `"No Lua env"`. |
| TC-7 | 15-04 | matrix `test` over envs A,B where A exits 0, B exits 1 | results model: row A = PASS, row B = FAIL(exit 1); aggregate = FAIL; two `luarocks test <rockspec>` command lines built, one per env bin. |
| TC-8 | 15-04 | matrix over empty env set | action disabled / reports "no environments"; no process spawned. |
| TC-9 | 15-05 | batch spec [{PUC,5.3},{PUC,5.4}] base `/p/envs` | two `provision(...,CREATE)` calls with dirs `/p/envs/PUC-5.3`, `/p/envs/PUC-5.4`; both appended to `hererocksEnvs` on success. |
