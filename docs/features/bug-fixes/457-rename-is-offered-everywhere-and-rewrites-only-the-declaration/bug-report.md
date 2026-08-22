---
id: "BUG-457"
title: "Rename is offered on ordinary identifiers and silently rewrites only the declaration, leaving every usage bound to the old name"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "critical"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-457: a refactoring that reports success and corrupts the file

Found 2026-08-22 by the [[REFACT-01]] retroactive-requirements agent as a prediction from platform
control flow. **CONFIRMED LIVE the same day** — sandbox GoLand, real project file. The prediction
was exact; §5 records the transcript.

## 1. Reproduction — verified

1. Open a Lua file with `local counter = 0` and several later uses of `counter`.
2. Put the caret on the **declaration** and press Shift+F6. Rename to `total`.

## 2. Expected vs actual

- **Expected**: either every occurrence is renamed, or the refactoring declines with a message.
- **Actual, measured**: the declaration becomes `local total = 0`, **every usage still reads
  `counter`**, and the refactoring reports success. The file now refers to an undefined global.

## 3. Root cause

Rename is not implemented for anything except labels, but the platform does not know that.

- No `renamePsiElementProcessor` is registered — **zero** across all three plugin XMLs.
- No `elementManipulator` is registered either, so the inherited `PsiReferenceBase.handleElementRename`
  would throw `PluginException("No ElementManipulator instance registered")`.
- On a declaration, `TargetElementUtilBase` falls through to `ELEMENT_NAME_ACCEPTED` and returns the
  enclosing `LuaNameRef`, which *is* a `PsiNamedElement` with a working `setName` — so `canRename`
  passes and the rename proceeds.
- But `findReferences` searches for references to that composite, while
  `LuaNameReferenceSearcher.isNameDeclarationLeaf` returns early unless the element type is
  `IDENTIFIER`. **Zero usages are collected.**

On a *usage* the failure is the opposite and harmless: resolution yields a raw `IDENTIFIER` leaf,
not a `PsiNamedElement`, so `canRename` fails with "cannot rename this symbol".

The epic table compounds it by claiming rename is *"Implemented (`LuaNameReference.handleElementRename`)"*.
**That method does not exist** — the only `handleElementRename` in the codebase is on
`LuaLabelReference`. Same provenance as [[BUG-450]] §4: bulk placeholder, bulk tick, no commit
between.

## 4. Related: no conflict detection

Even where rename works ([[REFACT-04]], labels), `findExistingNameConflicts` is unoverridden. In
Lua, renaming to a name already visible does not collide — it silently **rebinds**, and the file
still compiles. Measured on labels: one rename changed a program's output on 5.3.6 and made it fail
to load on 5.4.7. The same hazard applies to every local this bug would enable.

## 5. Live verification, 2026-08-22 — silent partial rewrite confirmed

Sandbox GoLand on the builder VM, Lunar 0.18.0 loaded, a real project file:

```lua
local counter = 0                       -- caret here, Shift+F6 -> "total"

local function bump()
    counter = counter + 1
    ...
    return counter, sql
end

print(bump())
print(counter)
```

**The rename dialog opened**, confirming `canRename` passes on a declaration. Its prompt read:

> Rename **Lua Name Ref Impl** 'counter' and its usages to:

— both the promise of "its usages", and the raw PSI class name leaking into user-facing text.

**Result after Refactor:**

```lua
local total = 0            -- renamed
    counter = counter + 1  -- NOT renamed
    return counter, sql    -- NOT renamed
print(counter)             -- NOT renamed
```

One occurrence changed, four left behind. **No error, no warning, no conflict dialog.** `counter`
is now an undefined global and `print(counter)` prints `nil` where it printed `1` — the program's
behaviour changed silently.

The IDE's own inspections underline the damage immediately afterwards: `total` as an unused local,
all four `counter` occurrences as undefined globals. Lunar's analysis knows the file is broken; the
refactoring that broke it reported success.

## 6. Fix strategy sketch

If confirmed: register a `RenamePsiElementProcessor` for Lua that collects usages through
`LuaNameReference`/`LuaScopeProcessor` rather than through the default composite search, register an
`elementManipulator`, and implement `findExistingNameConflicts` against Lua's shadowing rules. Until
that exists, **`isAvailable` should refuse rename on non-label elements** — declining loudly is
strictly better than a silent partial rewrite, and is a far smaller change.
