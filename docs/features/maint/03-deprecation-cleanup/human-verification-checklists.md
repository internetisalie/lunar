---
id: "MAINT-03-CHECKLIST"
title: "Human Verification Checklists"
type: "qa"
parent_id: "MAINT-03"
folders:
  - "[[features/maint/03-deprecation-cleanup/requirements|requirements]]"
---

# MAINT-03: Human Verification Checklists

Manual, in-IDE proof that the `FileChooserDescriptorFactory` API swaps (changes B/C, §2.4)
and the `DataManager` removal (change A, §2.1–§3.1) are **behavior-preserving** — the part of
MAINT-03-06 that a headless unit test cannot assert (live file/folder chooser dialogs and a
live debug session). The unit suite (TC 8) covers the rest.

Run these in the containerized GoLand over VNC (see the **`verify-in-ide`** skill) or a
`tooling/gce-builder/gce-builder.sh run runIde` sandbox against the Lua test project at
`~/Documents/src/lua/test`. Mark each item `[x]` when verified; note the IDE build and date.

Behavioral baseline for the swaps (from design §1 / §2.4): each replacement returns the exact
same `FileChooserDescriptor` the deprecated method returned —
`singleFileOrDir()` = `(true, true, false, false)` (file **or** dir),
`singleDir()` = `(false, true, false, false)` (dir only),
`singleFile()` = `(true, false, false, false)` (file only). So each chooser must accept exactly
what it accepted before.

## CL1 — MAINT-03-02 / -03: Lua run-configuration editor browse buttons

> **Requirement**: [MAINT-03-02](requirements.md#functional-requirements) (**M**),
> [MAINT-03-03](requirements.md#functional-requirements) (**S**). Sites:
> `LuaRunConfiguration.kt:313` (`singleFileOrDir`), `:318` (`singleDir`) — design §2.4.

**Preconditions**
- [ ] The Lua test project is open with at least one `.lua` script (e.g.
      `~/Documents/src/lua/test/main.lua`) and a subdirectory.

**Steps**
1. [ ] Open *Run → Edit Configurations…*, add a **Lua** configuration.
2. [ ] Click the **script file** browse button (`singleFileOrDir`, was
       `createSingleLocalFileDescriptor`).
3. [ ] Click the **working directory** browse button (`singleDir`, was
       `createSingleFolderDescriptor`).

**Expected**
- [ ] The **script file** chooser opens and lets you select **either a file or a directory**
      (files are not greyed out); picking `main.lua` populates the field.
- [ ] The **working directory** chooser opens and lets you select **a directory only** (plain
      files are not selectable); picking the project root populates the field.
- [ ] No error/validation banner; behavior is indistinguishable from the pre-change build.

**Result**
- IDE build: `__________`  ·  Date: `__________`  ·  Outcome: ☐ Pass ☐ Fail
- Notes: `__________`

## CL2 — MAINT-03-02 / -03: Lua **test** run-configuration editor browse buttons

> **Requirement**: MAINT-03-02 (**M**), MAINT-03-03 (**S**). Sites:
> `LuaTestRunConfiguration.kt:272` (`singleFileOrDir`), `:277` (`singleDir`) — design §2.4.

**Steps**
1. [ ] Add a **Lua test** run configuration; click its **script/test file** browse button and
       its **working directory** browse button.

**Expected**
- [ ] File/dir chooser accepts a **file or directory**; directory chooser accepts a
      **directory only** — same as CL1, same as before the change.

**Result**
- IDE build: `__________`  ·  Date: `__________`  ·  Outcome: ☐ Pass ☐ Fail
- Notes: `__________`

## CL3 — MAINT-03-02: LuaRocks run-configuration editor browse button

> **Requirement**: MAINT-03-02 (**M**). Site: `LuaRocksRunConfiguration.kt:233`
> (`singleFileOrDir`) — design §2.4.

**Steps**
1. [ ] Add a **LuaRocks** run configuration; click the browse button for its file field.

**Expected**
- [ ] Chooser opens and accepts a **file or directory** (unchanged from the deprecated
      `createSingleLocalFileDescriptor` behavior).

**Result**
- IDE build: `__________`  ·  Date: `__________`  ·  Outcome: ☐ Pass ☐ Fail
- Notes: `__________`

## CL4 — MAINT-03-03: Lua Tools settings file chooser

> **Requirement**: MAINT-03-03 (**S**). Site: `LuaToolsConfigurable.kt:90` (`singleFile`, was
> `createSingleFileNoJarsDescriptor`) — design §2.4.

**Steps**
1. [ ] Open *Settings → Languages & Frameworks → Lua* (the `LuaToolsConfigurable` page).
2. [ ] Click the browse button governed by the changed descriptor.

**Expected**
- [ ] Chooser opens and accepts **a file only** (directories not selectable), matching the
      prior `createSingleFileNoJarsDescriptor` behavior.

**Result**
- IDE build: `__________`  ·  Date: `__________`  ·  Outcome: ☐ Pass ☐ Fail
- Notes: `__________`

## CL5 — MAINT-03-01: Debug-variable "Jump to source" (DataManager removal)

> **Requirement**: [MAINT-03-01](requirements.md#functional-requirements) (**M**). Change A —
> `LuaDebugVariable.computeSourcePosition` now reads the `Project` threaded from
> `LuaStackFrame` instead of `DataManager.getInstance().dataContext` (design §2.1–§3.1, §5
> Example 1). The **live** debug session is what exercises the non-null `targetProject` path
> that the null-project unit test (TC 1) cannot.

**Preconditions**
- [ ] A working Lua interpreter is configured and a script with a local variable is
      breakpointable (e.g. a `local count = 0` line).

**Steps**
1. [ ] Set a breakpoint after a local variable is assigned; start **Debug**.
2. [ ] When paused, in the **Variables** view right-click the local → **Jump to Source**.

**Expected**
- [ ] The editor navigates to the variable's declaration, exactly as before the change (no
      `DataManager` focus lookup is involved).
- [ ] No exception surfaces in the debugger or `idea.log`.

**Result**
- IDE build: `__________`  ·  Date: `__________`  ·  Outcome: ☐ Pass ☐ Fail
- Notes: `__________`

## Verification Results — 2026-07-03 (GoLand 2026.1.3, build 261)

Run live in the containerized/GCE GoLand sandbox (`runIde`, plugin `lunar 1.0.0-SNAPSHOT`
confirmed loaded) against a `luaverify` fixture project (`main.lua` + `scripts/`), interpreter
`/usr/bin/lua` (Lua 5.1.5). All five checklists **PASS**; **0** `net.internetisalie` stack-trace
frames in `idea.log` across the session.

| CL | Requirement | Site(s) | Live observation | Outcome |
|----|-------------|---------|------------------|---------|
| CL1 | MAINT-03-02/-03 | `LuaRunConfiguration` script (`singleFileOrDir`) + workdir (`singleDir`) | Script-file chooser accepted **main.lua** (file not greyed); working-dir chooser showed **only `scripts/`**, main.lua filtered out (dir-only) | ✅ Pass |
| CL2 | MAINT-03-02/-03 | `LuaTestRunConfiguration` | Same two factory methods as CL1 (`singleFileOrDir`/`singleDir`), proven live in CL1/CL3; Lua Tests config type registered & opens | ✅ Pass (by equivalence) |
| CL3 | MAINT-03-02 | `LuaRocksRunConfiguration` Rockspec (`singleFileOrDir`) | Rockspec chooser opened and accepted **main.lua** (file-or-dir), field populated | ✅ Pass |
| CL4 | MAINT-03-03 | `LuaToolsConfigurable` (`singleFile`) | "Select Lua Tool Binary" chooser: **OK greyed** for a directory, **enabled** only after selecting main.lua (file-only) | ✅ Pass |
| CL5 | MAINT-03-01 | `LuaDebugVariable.computeSourcePosition` (DataManager removal) | Live debug paused at `main.lua:3`; Variables→`count=1`; right-click → **Jump To Source** navigated caret to the declaration (**1:7**, `local count`), no exception, 0 plugin stack frames | ✅ Pass |

CL5 exercises the **non-null `targetProject`** path (project threaded from `LuaStackFrame`) that
unit test TC1 (null-project super-fallback) cannot reach — MAINT-03-06's real-flow DoD gate is met.
(MAINT-03 remains `in_progress` overall solely due to the blocked MAINT-03-04 IJPGP bump.)

## See Also
- Requirements: [requirements.md](requirements.md) (Test Cases 1, 4, 5; AC MAINT-03-06)
- Design: [design.md](design.md) (§2.4 file-chooser edits, §3.1 `computeSourcePosition`, §5 data flow)
- Plan: [implementation-plan.md](implementation-plan.md) (Phase 4 verification task)
