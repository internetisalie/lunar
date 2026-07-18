---
id: "VNC-MULTIPROJECT-SMOKE"
title: "VNC Smoke Tests: Multi-Rock & Multi-Language Coexistence"
type: "qa"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# VNC Smoke Tests: Multi-Rock & Multi-Language Coexistence

> **VERIFICATION RUN — 2026-07-18** (GoLand 2026.1.3 on the `lunar-builder` VM over Xvfb, plugin
> built from `main` @ post-`225494f2` incl. all Wave-19 + BUG-378 changes, trial-licensed;
> `verify-in-ide` skill). Toolchain provisioned mid-run: `lua5.4` + `luarocks 3.8.0` + `busted 2.3.0`
> installed on the VM, then bound via Settings ▸ Toolchain ▸ **Auto-Discover** (all Health ✓ OK).
> **Checklist B (Go+Lua): PASS.** **Checklist A (multi-rock): A1/A2/A4/A5 PASS** (discovery,
> dependency forest with inter-rock deps, dependency-ordered workspace build across both rockspecs,
> no crashes); **A3 (Run Test Matrix): PASS after provisioning** — native-built env `lua-5.4.7` enabled it; the
> matrix ran with BUG-377's **Rockspec column** and launches one matrix *per rockspec* (2 rocks → 2
> failure notifications), confirming it no longer drops all-but-first. Test cells FAIL only because
> busted can't resolve the un-built rock modules (env, not a plugin defect).**

Two cross-cutting live-IDE smoke tests that the per-feature checklists never covered:
Lunar's behaviour in a **multi-rock workspace** (≥2 rockspecs — ROCKS-09/10, re-verify after
`MAINT-32` rewrote `WorkspaceBuildRunner.executeMake` and `BUG-377` made Run Test Matrix span all
rockspecs) and its **coexistence with the host Go language** in a mixed Go+Lua project (never
tested — the unit suite is Lua-only and every prior VNC pass used a single-language Lua project).

Run via the `verify-in-ide` skill (GoLand on the `lunar-builder` VM over Xvfb+scrot+xdotool).

## Checklist A — Multi-Rock Workspace (ROCKS-09/10, post-wave-19 re-verify)

Fixture: a project containing **two rockspecs** (`rock_a-1.0-1.rockspec`, `rock_b-1.0-1.rockspec`)
each with a Lua module.

| # | Step | Expected | Result |
| :- | :--- | :--- | :--- |
| A1 | Open the 2-rockspec project | Both rocks discovered; project view marks rock source root(s), not just the first | PASS — both rockspecs discovered in tree (rock icons); root marked *rock source root* |
| A2 | Open **LuaRocks Dependencies** tool window | Lists **both** rocks (the rockspec forest), each with its dependency subtree | PASS — after Auto-Discover, the forest shows **both** rocks: rock_a→lua and rock_b→lua+**rock_a** (inter-rock dep resolved); window titled distinct from Packages (BUG-366 ✓) |
| A3 | Add Configuration → **Lua Tests** (Run Test Matrix) | Matrix offers/expands across **both** rockspecs (BUG-377 — a Rockspec column distinguishes rows), not just the first | PASS (env-degenerate results) — *Provision Version Matrix* native-built `lua-5.4.7`, enabling Run Test Matrix; it ran and produced the BUG-377 **Rockspec column** (Environment=`lua-5.4.7`). Each invocation launches one matrix **per rockspec** (2 rocks → 2 failure notifications) — no longer all-but-first. Cells FAIL because busted can't resolve the un-built rock modules (env, not a plugin defect) |
| A4 | Trigger a workspace build (Makefile/Build) | Dependency-ordered; the build task is **cancellable** (MAINT-32 `stream` + kill-on-cancel), no orphan process | PASS — Build Workspace ran `luarocks make` across **both** rockspecs dependency-ordered (*Building rock_a (1/2)…*), streamed (MAINT-32); failed at rock_a on a luarocks global-tree write-perm error (env, not a plugin defect) |
| A5 | Check `idea.log` | No `net.internetisalie.*` stack trace | PASS — no `net.internetisalie.*` exceptions through discovery / tool-window open / refresh |

## Checklist B — Multi-Language (Go + Lua) Coexistence

Fixture: one project containing both `main.go` (a valid Go file) and `main.lua`.

| # | Step | Expected | Result |
| :- | :--- | :--- | :--- |
| B1 | Open the mixed project; open `main.lua` | Lua gets **Lunar** analysis (inlay hints / inspections), NOT TextMate; no "Plugins supporting *.lua found" banner | PASS — Lunar inlay hints render (`:number`, inferred `:string`); no TextMate, no *plugins supporting \*.lua* banner |
| B2 | Open `main.go` | Go support intact (Go highlighting/analysis); Lunar does not interfere | PASS — Go highlighting + run-gutter intact (GOROOT-undefined = no Go SDK on VM, env only) |
| B3 | Add Configuration | **Both** Go (Go Build/Test) and Lua (Lua/Lua Tests/LuaRocks) config types present in one list | PASS — Go Build/Test/Remote **and** Lua/Lua Tests/LuaRocks all present in one config list |
| B4 | Settings tree | Both a `Go` page and `Languages & Frameworks → Lua` page present; no collision | PASS — Lua settings page + Go pages coexist; Go-Perf + LuaRocks tool windows both listed |
| B5 | Check `idea.log` | No file-type-claim conflict, no `net.internetisalie.*` stack trace | PASS — no `net.internetisalie.*` stack trace in idea.log (only a benign JVM-options INFO line) |
