---
id: ROCKS-14
title: "14: Hererocks Environment Lifecycle"
type: feature
status: "cancelled"
vf_icon: 🚫
priority: medium
parent_id: ROCKS
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-14: Hererocks Environment Lifecycle

> **⚠️ Superseded / cancelled (2026-07-09) — never implemented.** The Python/hererocks
> environment lifecycle is replaced by the TOOLING epic's Python-free native provisioning
> engine ([TOOLING-04](../../tooling/04-native-provisioning/requirements.md)) and environment
> model ([TOOLING-02](../../tooling/02-resolution-and-environments/requirements.md)); see
> TOOLING [Supersedes](../../tooling/requirements.md). Retained for design/history reference
> only. Marked `cancelled` (not `done`) so `status.md` counts it out of remaining work rather
> than as a shipped feature.

## Overview

[hererocks](https://github.com/luarocks/hererocks) (the LuaRocks-org fork) provisions a
self-contained Lua (PUC or LuaJIT) **plus** LuaRocks into a single project-local directory —
the "virtualenv for Lua". This feature lets Lunar **detect, create, upgrade, recreate, and
remove** such an environment from within the IDE, then binds the produced `bin/lua` interpreter
and `bin/luarocks` tool through the **existing** resolution machinery (`LuaInterpreterService`,
`LuaToolManager` / TOOL-02 project bindings) so every downstream ROCKS feature transparently
targets the isolated env. hererocks is used purely as an out-of-process *provisioner*; nothing in
the plugin's runtime depends on it after provisioning. Parent epic:
[[features/rocks/requirements|ROCKS]]. Multi-version (matrix) development builds on this and is
deferred to [ROCKS-15](../15-multi-version-development/requirements.md).

## Scope

### In Scope

- **Detection** of an existing hererocks-shaped directory in the project and a one-click offer to
  bind it as the project interpreter + LuaRocks tool.
- **Bootstrap (create)** action: prompt for target directory, Lua flavor/version, and LuaRocks
  version; run `hererocks` on a background task; bind the result on success.
- **Upgrade / change-version**: re-provision the same directory with a new Lua/LuaRocks version.
- **Recreate**: delete and re-provision the directory (repair a corrupt env).
- **Remove**: unbind and (optionally) delete the environment directory.
- A single **environment descriptor** persisted per project in `.idea/lunar.xml` (VCS-shared),
  recording how the env was provisioned so upgrade/recreate are reproducible.
- **hererocks locator**: resolve the `hererocks` entry point (a binary on `PATH`, else
  `python -m hererocks`), with an actionable error when unavailable.

### Out of Scope

- **Multiple coexisting environments / version switcher / cross-version test matrix** — deferred
  to [ROCKS-15](../15-multi-version-development/requirements.md). ROCKS-14 stores exactly **one**
  env per project.
- **Cross-version static analysis** (flagging code that breaks on 5.1 but not 5.4) — out of scope
  for the whole hererocks track; the matrix runner (ROCKS-15) covers this at run/test time only.
- **Bundling / installing hererocks itself** — the user must have `hererocks` on `PATH` or
  `pip install hererocks`; we only document and detect it.
- Changes to `LuaRocksEnvironment.resolveServer` / registry/credential handling (owned by
  [ROCKS-06](../06-project-environment/requirements.md)).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| ROCKS-14-01 | **Environment descriptor** | M | A per-project record of the env (dir, Lua flavor, Lua version, LuaRocks version, label) persisted in `.idea/lunar.xml`. |
| ROCKS-14-02 | **hererocks locator** | M | Resolve the hererocks entry point: prefer a `hererocks` executable on `PATH`, else `python3 -m hererocks` / `python -m hererocks`; report unavailability with a remediation hint. |
| ROCKS-14-03 | **Create environment** | M | An action that provisions a new env via hererocks on a background task and binds it on success. |
| ROCKS-14-04 | **Bind produced binaries** | M | After provisioning, register `bin/lua` as the project interpreter and `bin/luarocks` as the TOOL-02 LuaRocks binding, then fire the settings-changed topic. |
| ROCKS-14-05 | **Detect existing environment** | M | On project open, detect a hererocks-shaped directory and offer to bind it (non-modal notification). |
| ROCKS-14-06 | **Upgrade / change version** | S | Re-provision the same directory with a different Lua/LuaRocks version, updating the descriptor and re-binding. |
| ROCKS-14-07 | **Recreate environment** | S | Delete and re-provision the env directory (repair), preserving descriptor spec. |
| ROCKS-14-08 | **Remove environment** | S | Clear the bindings + descriptor and optionally delete the directory. |
| ROCKS-14-09 | **Concurrency guard** | M | Provision/upgrade/recreate against a given directory are serialized; a second request while one is running is refused with a message. |

## Detailed Specifications

### ROCKS-14-01: Environment descriptor

Fields (all defaulted for XML serialization): stable `id` (UUID), absolute `directory`,
`flavor` ∈ {`PUC`, `LUAJIT`}, `luaVersion` (e.g. `"5.4"`), `luarocksVersion` (e.g. `"latest"` or
`"3.11.1"`), `label` (defaults to `"<flavor> <luaVersion>"`, e.g. `"PUC 5.4"`). Persisted as a
nested object on `LuaProjectSettings.State` exactly as `interpreter: LuaInterpreter?` is today.

### ROCKS-14-02: hererocks locator

Resolution order, first hit wins, returns a **command prefix** (`List<String>`):
1. `hererocks` (or `hererocks.exe`/`hererocks.bat` on Windows) found on `PATH` → `["<abs path>"]`.
2. `python3 -m hererocks` if `python3` on `PATH` **and** `python3 -c "import hererocks"` exits 0.
3. `python -m hererocks` under the same probe with `python`.
4. None → `null`; callers show *"hererocks not found. Install it with `pip install hererocks` or
   put it on your PATH."*

### ROCKS-14-03 / ROCKS-14-06 / ROCKS-14-07: Provision command

Given a descriptor, the argument list is:
`<locatorPrefix...>` + `<directory>` + flavor/version flag + luarocks flag, where:
- flavor/version flag = `--lua <luaVersion>` for PUC, `--luajit <luaVersion>` for LuaJIT.
- luarocks flag = `--luarocks <luarocksVersion>`.

**Create** (ROCKS-14-03): run against a directory that does not yet exist.
**Upgrade** (ROCKS-14-06): run against the existing directory with a changed version (hererocks
rebuilds in place).
**Recreate** (ROCKS-14-07): delete the directory first, then run as create with the stored spec.

Exit code 0 = success; non-zero = failure, surface stderr tail in an error balloon; the descriptor
and bindings are only written/updated on success.

### ROCKS-14-04: Binding

On success, with `bin` = `<directory>/bin` (`<directory>` on Windows for `.bat`/`.exe`):
1. `LuaToolManager.getInstance().registerTool("<bin>/luarocks", LuaToolType.LUAROCKS)` → `LuaTool`.
2. `LuaProjectSettings.getInstance(project).setProjectToolBindingAndNotify(LuaToolType.LUAROCKS.name, tool.id)`.
3. Build `LuaInterpreter(Path.of("<bin>/lua"))` (or `luajit`), `LuaInterpreterService.getInstance().identify(it)`,
   then set it as the project interpreter and notify (see design §2.6).

### ROCKS-14-05: Detection layout

A directory `D` is hererocks-shaped iff it contains **both** an executable Lua
(`bin/lua`, `bin/luajit`, `lua.exe`, or `luajit.exe`) **and** a `luarocks` launcher
(`bin/luarocks`, `luarocks.bat`). Detection scans: the project base dir's immediate children plus
the conventional names `.lua`, `lua_env`, `hererocks`, `.hererocks`, `_lua` (case-sensitive on
POSIX). First match wins; if it is not already the bound env, show a non-modal notification
offering **Bind**.

## Acceptance / Test Cases

| TC | Requirement | Input | Expected |
|----|-------------|-------|----------|
| TC-1 | 14-02 | `hererocks` on `PATH` | locator returns `["<abs>/hererocks"]`. |
| TC-2 | 14-02 | no `hererocks`, `python3 -c import hererocks` exits 0 | locator returns `["python3","-m","hererocks"]`. |
| TC-3 | 14-02 | neither present | locator returns `null`; action shows the pip remediation message. |
| TC-4 | 14-03 | descriptor {dir=`/p/.lua`, PUC, `5.4`, `latest`}, prefix `["hererocks"]` | args = `["hererocks","/p/.lua","--lua","5.4","--luarocks","latest"]`. |
| TC-5 | 14-03 | descriptor {LuaJIT, `2.1`} | flavor flag = `["--luajit","2.1"]`. |
| TC-6 | 14-04 | successful provision at `/p/.lua` | `LUAROCKS` project binding id == the registered `/p/.lua/bin/luarocks` tool id; `state.interpreter.path == /p/.lua/bin/lua`; TOPIC fired once. |
| TC-7 | 14-05 | project base contains `.lua/bin/lua` + `.lua/bin/luarocks`, not yet bound | detector returns `/p/.lua`; notification offers Bind. |
| TC-8 | 14-05 | no hererocks layout anywhere | detector returns `null`; no notification. |
| TC-9 | 14-07 | recreate with existing dir | dir deleted then re-provisioned with the stored spec; descriptor unchanged. |
| TC-10 | 14-09 | second provision request while one runs for the same dir | refused with a "provisioning already in progress" message; no second process spawned. |
