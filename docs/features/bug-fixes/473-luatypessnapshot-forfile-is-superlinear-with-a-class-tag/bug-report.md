---
id: "BUG-473"
title: "`LuaTypesSnapshot.forFile` is superlinear in call-site count once a `@class` tag is present"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-473: one `---@class` turns snapshot cost superlinear

Found 2026-08-27 by [[REFACT-01]]'s `REFACT-01-00-DR-03` while sizing receiver-type method
resolution. **Not caused by that spike** — re-measured on the restored clean tree, with the
prototype reverted, and the numbers are unchanged.

## Reproduction

A single Lua file containing a `---@class` annotation and *n* colon-call sites against that class.

```lua
---@class Builder
local Builder = {}
function Builder:setName(n) end

local b = Builder
b:setName("a")   -- × n
```

Measure `LuaTypesSnapshot.forFile` on it.

## Measured

| call sites *n* | with `---@class` | same file, tag removed |
| ---: | ---: | ---: |
| 40 | 1 383 ms | ~190 ms |
| 80 | **12 807 ms** | ~190 ms |
| 200 | **344 736 ms** | ~190 ms |

Roughly an order of magnitude per doubling — 200 call sites take **5.7 minutes**. Without the
`---@class` the same file is flat at ~190 ms, so the tag is the trigger, not the file size.

## Expected

Snapshot cost grows no worse than linearly in call-site count, and an annotated file is not
dramatically more expensive than an unannotated one of the same size.

## Why this is `high`

`LuaTypesSnapshot.forFile` is reachable from the resolve path. A user editing a well-annotated
module — precisely the code LuaCATS support exists to reward — can freeze the IDE for minutes.
The failure gets *worse* the more the user follows the conventions Lunar encourages.

It also blocks work: `DR-03` rejected the type route for `REFACT-01-08`'s colon form partly on
this, because putting `forFile` on `resolve()` would inherit the cost.

## Why the test suite never caught it

Measured during the same spike: across the **734** corpus `.lua` files, **zero** carry a
`---@class` (four carry any `---@` tag at all) — against 809 colon-method declarations and 16 336
colon-call tokens. The corpus exercises colon calls heavily and the annotated path not at all, so
the trigger condition is unsatisfiable on 100% of the corpus.

That is a coverage gap in its own right, and it is the reason a green corpus sweep says nothing
about annotated-file performance.

## Where to look

`LuaTypesSnapshot.forFile`, and what it recomputes per call site once a class is in scope. The
shape of the growth suggests work repeated per call site that should be computed once per file or
cached — see the engineering contract's `CachedValuesManager` rule for bindings.

## Evidence

`docs/features/refactoring/01-rename-refactoring/risks-and-gaps.md`, `REFACT-01-00-DR-03`, and
`.agents/handoffs/REFACT-01-00-DR-03-phase-1.md`.
