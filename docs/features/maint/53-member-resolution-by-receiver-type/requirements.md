---
id: "MAINT-53"
title: "53: Member Resolution by Receiver Type"
type: "feature"
parent_id: "MAINT"
status: "planned"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-53: Member Resolution by Receiver Type

## Overview

`LuaNameReference` resolves a member by the text of its receiver. `io.stdout:close` looks for that
text while the method is `File:close`, and `M.clear()` matches every file's `function M.clear`. This
feature resolves a member through the receiver's inferred type, stops a member or a function-name
segment resolving as a variable, and keeps text resolution where no type exists. It closes
[[BUG-490]], [[BUG-508]], [[BUG-512]] and [[BUG-516]], plus BUG-24, an inspection defect (DR-05).

The measured effect and cost are in `risks-and-gaps.md`: DR-01 (share of member calls resolving),
DR-02 (cost), DR-03 (recursion).

## Scope

### In Scope
- Typed resolution of dot members and of colon calls with a suffixed receiver.
- Removing lexical, bare-name and file-binding resolution from member names and function-name segments.
- Dropping cross-file text matches for a member of a local initialised from a table constructor.
- `LuaUnusedLocalInspection` following a read through a reassignment (BUG-24).

### Out of Scope
- [[BUG-511]], which has a different cause (DR-07).
- Colon calls on a bare receiver: these keep NAV-13's `LuaColonCallResolution` unchanged.
- Chained calls `a:m1():m2()` and `f().x`: no value node exists for them, so they stay on text.
- Adding `function M.x` members to `getFileReturnType` (Gap 2.1).
- Giving a `string` value the `string` library's methods (Gap 2.2).

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-53-01 | **Read through a reassignment uses the local** | M | Full | A local read only through a reassignment in a nested block is not reported unused. BUG-24. |
| MAINT-53-02 | **A function-name segment is not a usage** | M | Not Implemented | `m` in `function t:m()` or `function t.m()` never resolves to a global `m`. BUG-490. |
| MAINT-53-03 | **A member is never a variable** | M | Not Implemented | A member name or function-name segment does not resolve to a local, a bare global, or a file-scope binding of a required or platform file. BUG-512 (B), (C). |
| MAINT-53-04 | **A bare name is not a method** | M | Not Implemented | A bare `close()` does not resolve to a required module's `M:close`. BUG-512 (A). |
| MAINT-53-05 | **The receiver's type wins** | M | Not Implemented | A dot member whose receiver type declares it resolves to that declaration only. BUG-508. |
| MAINT-53-06 | **Suffixed colon calls resolve** | M | Not Implemented | `a.b:m()` resolves through the type of `a.b`, and never through text. BUG-516. |
| MAINT-53-07 | **No cross-file text for a constructed local** | M | Not Implemented | With no typed target, a member of `local M = {}` keeps same-file text matches only. BUG-508. |
| MAINT-53-08 | **Text stays the fallback** | M | Not Implemented | Any other member with no typed target resolves through the text path as Phase 2 leaves it (design §3.2). |
| MAINT-53-09 | **No typed lookup inside the file's own build** | M | Not Implemented | While a file's snapshot is under construction, a dot member in it resolves through the text path and a suffixed colon call resolves to nothing. Neither answer is cached. |
| MAINT-53-10 | **Corpus moves only as measured** | S | Not Implemented | Baselines move by the DR-05 per-phase deltas and no others. |

## Test Cases

`Run` names the executed fixture in DR-04 (TC-19: DR-05). Every input below has been run. TC-08, TC-21 and TC-22 need the real-disk `require` harness (Dependencies).

| TC | Req | Input | Expected | Mutation that turns it red | Run |
|----|-----|-------|----------|----------------------------|-----|
| TC-01 | 01 | `local x` / `do` / `x = 1` / `print(x)` / `end` | no *Unused local* | delete the §3.1 follow loop | R1W |
| TC-02 | 01 | `local x` / `do` / `x = 1` / `do` / `x = 2` / `print(x)` / `end` / `end` | no *Unused local* | stop the loop after one hop: the read reaches the local in three (R1C) | R1W, R1C |
| TC-03 | 01 | `local x` / `x = 1` | *Unused local variable 'x'* | make `classify` add write targets to `usages` | F18-2 |
| TC-04 | 02 | `function m() end` / `local t = {}` / `function t:m() end` / `t:m()` | usages of `m`@9 `[]`; usages of `m`@41 `[51]` | remove `LuaFuncNameMethod` from the §3.2 test | F4, R1 |
| TC-20 | 02 | `function g() end` / `local t = {}` / `function t.g() end` / `t.g()` | usages of `g`@9 `[]`; usages of `g`@41 `[51]` | remove `LuaFuncNameProperty` from `namesATableKey` | R1 |
| TC-05 | 03 | `legacy_lib.lua`: `function legacy() end`; `local handlers = {{}}` / `local a = handlers[1].legacy` | `legacy` resolves to nothing | remove `LuaIndexExpr` from the §3.2 test | F3B |
| TC-06 | 03 | `local config = {}` / `local function timeout(ms) config.timeout = ms end` / `local function get() return config.timeout end` | the read resolves to the `timeout` of `config.timeout = ms`, not to `local function timeout` | remove the `LuaIndexExpr` clause of `lexicalTargetOf`. Reachable in Phase 2 only: from Phase 3 the typed path answers first. TC-07 still reaches it | F3C |
| TC-07 | 03 | `local t = {}` / `local version` / `print(t.version)` | *Unused local variable 'version'* | as TC-06 | F18-3 |
| TC-21 | 03 | `lib.lua`: `local insert = table.insert` / `gins = 1` / `local M = {}` / `function M.go() end` / `return M`; `main.lua`: `local lib = require("lib")` / `local t = {}` / `t.insert(1)` / `print(t.insert)` / `function t.insert() end` / `function t:insert() end` / `local u = make()` / `u.insert(1)` / `print(u.gins)` / `function u.gins() end` | lines 3, 4: `function t.insert`; lines 5, 6, 8: nothing; line 9: `function u.gins`; line 10: nothing. No target in `lib.lua` | run the `queryFiles` loop for a table key (§3.2 item 1): lines 5, 6, 8 gain `lib.lua`'s `insert`, lines 9, 10 its `gins` | B1 |
| TC-08 | 04 | `LuaDeprecatedBareNameThroughRequireTest` fixture | bare `close` has no target in `mod.lua`; no deprecated range | restore the method leaf in `funcDeclNameLeaves`: `close` resolves into `mod.lua`. The no-range half is BUG-504's existing assertion and stays green under this mutation | TC08 |
| TC-09 | 05 | `vim/lsp/codelens.lua`: `local M = {}` / `---@deprecated` / `function M.clear(client_id, bufnr) end` / `return M`; `lazy/manage.lua`: `local M = setmetatable({}, {})` / `function M.clear(plugins) end` / `M.clear()` / `return M` | exactly `function M.clear(plugins)` | make `doMultiResolve` skip `typedTargets`: both `M.clear` | R9L |
| TC-11 | 05 | `local a = {}` / `a.b = {}` / `a.c = 1` / `print(a.b.c)` | `c` resolves to nothing | make `receiverType` ignore `steps`: `a` declares `c` | F10 |
| TC-22 | 05 | `codelens.lua`: `local M = {}` / `---@deprecated` / `function M.clear(client_id, bufnr) end` / `return M`; `manage.lua`: `local M = {}` / `function M.clear(plugins) end` / `return M`; `main.lua`: `local M = require("manage")` / `M.clear()` | exactly `function M.clear(plugins)` | delete `requiredModuleType`'s call in `headType`; or skip `typedTargets`: both `M.clear` | M1 |
| TC-23 | 05 | `other.lua`: `local p = {}` / `p.y = 2` / `local x = {}` / `x.y = 3` / `return p, x`; `g1.lua`: `local function f(p)` / `p.y = 1` / `return p.y` / `end` / `return f` | the read `p.y` resolves to `g1.lua` line 2 only | in `headType`, return null for a head with no write of its own: `other.lua` line 2 joins | RG |
| TC-12 | 05 | `---@class Foo` / `local Foo = {}` / `function Foo.new() end` / `---@type Foo` / `local x` / `x.new()` | `function Foo.new` | as TC-09: text finds nothing | F11 |
| TC-13 | 06 | Standard 5.1 target: `io.stdout:close()`, `io.stdout:setvbuf("no")`, `io.stdout:nosuch()` | `function File:close`, `function File:setvbuf`, nothing | delete the suffixed branch of `colonCallResults` | F1 |
| TC-14 | 07 | `vim/lib.lua`: `local M = {}` / `function M.gone() end` / `return M`; `app/a.lua`: `local M = {}` / `M.gone()` / `return M` | `gone` resolves to nothing | delete the same-file filter in `doMultiResolve` | F15 |
| TC-15 | 07 | as TC-14 with `app/c.lua`: `local M = {}` / `M = other` / `M.gone()` | `gone` resolves to nothing | as TC-14 | F17 |
| TC-16 | 08 | as TC-14 with `app/e.lua`: `local M = make()` / `M.gone()` / `return M` | `function M.gone` in `vim/lib.lua` | make `isConstructedLocalHead` return true for any local; or make `doMultiResolve` return `typed` when empty | R16 |
| TC-18 | 09 | `local M = {}` / `---@param cb fun(x:string)` / `function M.each(cb) end` / `M.each(function(s) return s end)` / `return M`, resolved after building the snapshot | `function M.each`; `s` is `string`; `TYPED_DURING_BUILD` unchanged | delete the guard in `multiResolve` (the counter rises) | F13, F14 |
| TC-24 | 09 | `local M = {}` / `M.obj = {}` / `---@param cb fun(x:string)` / `function M.obj:each(cb) end` / `M.obj:each(function(s) return s end)` / `return M`, resolving `each` before any snapshot exists | `TYPED_DURING_BUILD` unchanged; `each` resolves to nothing, before and after the build | delete the under-construction return in `colonCallResults`: the counter rises by 1 | CG |
| TC-19 | 10 | the corpus record of the gate (`implementation-plan.md`) after each phase | `git diff` of the baselines matches that phase's DR-05 row by the gate's rule | not a code test. The diff is the check: `CorpusGuards.assertRatchet` fails only on increases, so it misses a fall | DR-05 |

## Dependencies & Assumptions

- `LuaColonCallUsageWithdrawalTest.aSameNamedGlobalFunctionLosesTheColonCallSite` and
  `LuaDeprecatedBareNameThroughRequireTest` pin defects this feature fixes, so their expectations
  change (`implementation-plan.md`).
- Requires a `require` target on real disk for cross-file module tests: a light fixture's `temp://`
  module is never found.

## Non-Functional Requirements

- **Performance:** no budget test in the suite may be relaxed (Phase 3 exit criteria). DR-02's figures
  are the expected cost, not a gate: nothing re-measures them during implementation.
- **Threading:** runs inside the read action `PsiReference` resolution already requires.
