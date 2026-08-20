---
id: "BUG-443"
title: "Quick Doc was reported empty for `ngx.say` live, but does not reproduce headlessly — the definitions-loading path is the untested variable"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-443: one half of [[BUG-440]] that never reproduced

Carved out of [[BUG-440]] on 2026-08-20 when its `---@field` half was fixed and VNC-verified. This
half is **not** about that fix and was never reproduced outside the original live session, so it is
tracked separately rather than left holding a closed report open.

## What BUG-440 recorded

> | `ngx.say` | `function ngx.say` in `ngx.lua` | ❌ **"No documentation found."** |
>
> Every openresty member tried failed.

## What every subsequent measurement says

| measurement | `function X.y()` Quick Doc |
| :-- | :-- |
| headless, real `ngx.lua` via `registerLibraryRoot` (BUG-440 planning probe) | **1 target, with HTML** |
| headless, real `love.lua`, same route | **1 target, with HTML** |
| unit fixture, project file (`LuaFieldQuickDocTest`) | **1 target** |
| **live IDE, project file, VNC 2026-08-20** | **renders fully** — signature, prose, `Returns: boolean ok` |

So the function form works through every route that has been tried, including the running IDE.

## The one variable never exercised

Every measurement above declares the function in a **project file** or registers the root directly.
The original observation had openresty enabled through the **shipped catalog** — fetch, unpack, and
registration by `LuaDefinitionLibraryProvider` — which no probe has reproduced. If the defect is
real, that path is where it lives, and it would be a *library-registration* defect rather than a
documentation one.

## A cheaper explanation, and it must be ruled out first

**The caret.** During BUG-440's VNC verification, `Ctrl+Q` with the caret at **end of line** — one
character past the identifier — produced **"No documentation found."** verbatim, on a member whose
documentation renders correctly with the caret one character to the left. The reported symptom is
therefore also the symptom of a caret that is not inside the name.

That is not a claim the original observation was wrong; it is the cheapest hypothesis and it has to
be eliminated before anything is built. **Re-check with the caret provably inside the identifier**
before treating this as a defect at all.

## How to settle it

1. Enable `openresty` from the shipped catalog in a sandbox IDE (Settings ▸ Lua ▸ definition
   libraries), so the library arrives through `LuaDefinitionLibraryProvider` rather than a test root.
2. `Ctrl+Q` on `ngx.say`, caret **inside** the identifier, verified from a cropped screenshot.
3. If it renders, close this: the original was a caret artifact.
4. If it does not, compare against the same file opened as an ordinary project file — that isolates
   the registration path from the documentation path.

Related: [[BUG-440]] (the `---@field` half, fixed and verified), and BUG-440's own note that a member
reached through a **typed local** documents nothing whichever form declares it — a third, separate
shape also worth its own check.
