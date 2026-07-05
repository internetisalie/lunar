---
id: "TOOLING-PRD"
title: "Product Requirements"
type: "spec"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# Unified Lua Toolchain Management — Product Requirements

## Feature Overview

Lunar's handling of external Lua binaries grew organically across four waves and now presents
users with five settings pages, two parallel inventories (interpreters vs tools), three
per-tool path fields that fight each other, and an environment-provisioning feature that
silently requires Python. A developer who wants "a working Lua 5.4 + luarocks + luacheck +
stylua setup for this project" must today understand and operate all of these subsystems.

This epic replaces the lot with one model: a **toolchain registry** of external binaries
(descriptor-driven kinds, interpreters included), a **binding** layer that answers "which
binary does this project use for kind X" in exactly one way, an **execution** layer that runs
any tool with consistent environment injection, and a **native provisioning engine** that can
download/build interpreters and install tools without any external bootstrap dependency —
subsuming what hererocks did, generalized to the entire tool set.

## Goals & Non-Goals

- **Goals**
  - A single answer to "which binary runs for kind X in project P", used by *every* consumer
    (inspections, formatter, test runner, run configurations, REPL, debugger, terminal,
    package browser, matrix runner).
  - Interpreters, package managers, and dev tools share one inventory, discovery, probing,
    binding, health, and UI stack.
  - Provision a complete per-project toolchain (interpreter + luarocks + tools) from a fresh
    machine with **no Python and no pre-installed Lua**.
  - Tool kinds are **data descriptors** (names, probe flags, version regex, provisioning
    strategy) — new kinds require no registry/UI changes.
  - One "Lua" settings tree; every tool-related option lives under it exactly once.
- **Non-Goals**
  - Remote/WSL/SSH target support for provisioning (local machine only in v1).
  - Version-update notifications / "new version available" checks (deferred, as in TOOL v1).
  - Transparent migration of existing persisted settings (clean break — see Resolved
    Decisions).
  - Changing the `Target`/language-level model or the stdlib library roots mechanism (they
    stay; they consume the new model).

## Key Use Cases

### Use Case 1: Zero-to-toolchain on a fresh machine
As a Lua developer on a machine with no Lua installed, I open my project, choose
*Provision Toolchain*, pick Lua 5.4 + latest LuaRocks (+ luacheck, stylua, busted), and the
IDE downloads/builds everything into a project-scoped environment, binds it, and every IDE
feature (run, lint, format, test, terminal, package browser) uses it — no Python, no manual
PATH work. *(TOOLING-04, -02, -03)*

### Use Case 2: Bring-your-own binaries
As a developer with system-installed tools, auto-discovery finds them (PATH + well-known
dirs), shows them in one inventory with health/version, and I bind per project or set global
defaults. Luacheck and luarocks honor those bindings the same way stylua does. *(TOOLING-01,
-02, -05)*

### Use Case 3: Multi-version testing
As a library author, I provision several environments (5.1, 5.4, LuaJIT) and run the test
matrix across them; each row uses that environment's own toolchain. *(TOOLING-02, -04;
matrix runner migrates in -05)*

### Use Case 4: Understanding and fixing a broken setup
As a user whose luacheck binary was deleted, I see one banner naming the tool and a settings
link to the single page where it can be fixed; health state distinguishes "file missing"
from "version probe failed". *(TOOLING-07, -06)*

## Functional Scope

| Feature | Capability | Priority |
|---------|------------|----------|
| TOOLING-00 | De-risking spikes: native build (POSIX/Windows), prebuilt-binary matrix, download infra | M |
| TOOLING-01 | Unified toolchain model & registry (descriptors, inventory, discovery, probing) | M |
| TOOLING-02 | Resolution, binding & environments (precedence, active environment, events) | M |
| TOOLING-03 | Execution & environment injection (process service; PATH/LUA_PATH/LUA_CPATH; terminal) | M |
| TOOLING-04 | Native provisioning engine (interpreters, luarocks, tool installs; caching; progress) | M |
| TOOLING-05 | Consumer migration & legacy removal (clean break) | M |
| TOOLING-06 | Settings UI consolidation (one Lua tree) | M |
| TOOLING-07 | Health monitoring & diagnostics | S |

## Benefits

- **Usability**: one place to see, add, provision, and bind tools; consistent behavior
  everywhere; setup time on a fresh machine drops from "install Python, pip install
  hererocks, configure three pages" to one dialog.
- **Correctness**: a single resolution/injection path eliminates the class of bugs where one
  consumer ignores bindings (luacheck, luarocks run configs today).
- **Maintainability**: one discovery engine, one probe engine, one process runner; roughly
  half the current tool-related surface is deleted.
- **Enablement**: REDIS-01 (redis-cli), future ldoc / lua-language-server integrations become
  descriptor additions.

## Implementation Roadmap

Dependency order: `00 → 01 → 02 → 03 → 04 → 05 → 06 → 07`, with 04 (provisioning) depending
on 01/02 for registration-of-results and on 00 for the build/download spikes; 05 (consumer
migration) is the cutover that deletes legacy code and can land per-consumer; 06/07 finish
the user-facing surface. Detail lives in each feature's `implementation-plan.md`.

## Success Metrics

- Every external-binary consumer resolves through the unified registry (0 direct
  `executablePath`-style fields left in code).
- `hererocks`/Python is no longer referenced anywhere in the plugin.
- Settings pages related to tools: exactly one app-level tree + one project page.
- A clean CI-image E2E: provision 5.4 + luarocks + luacheck, run lint + a script, all green.
- Full unit suite green; live VNC verification of the provisioning and binding flows.

## Resolved Decisions

1. **Full unification** — interpreters (and provisioned environments) join the same registry
   as tools; an interpreter is a tool kind with runtime metadata (feeds `Target`/language
   level). *(user decision, 2026-07-05)*
2. **Clean break on persisted state** — no transparent migration of `lunar.xml` fields; old
   fields are deleted and users re-discover/re-bind once. No external install base yet.
   *(user decision, 2026-07-05)*
3. **One Lua settings tree** — LuaCheck/LuaRocks/Lua Tools pages fold into a single tree.
   *(user decision, 2026-07-05)*
4. **Descriptor-driven kinds** — the closed `LuaToolType` enum is replaced by data
   descriptors; kind set is extensible without registry changes. *(user decision, 2026-07-05)*
5. **Hererocks is inlined** — provisioning is implemented natively in the plugin (no Python
   dependency) and generalized to manage more than lua/luarocks (tool installs via luarocks
   and per-kind release-binary providers). *(user decision, 2026-07-05)*
