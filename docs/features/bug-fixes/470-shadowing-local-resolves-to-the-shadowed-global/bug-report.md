---
id: "BUG-470"
title: "A `local` that shadows an earlier same-file global resolves to the global's declaration"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-470: a shadowing `local` hands over the shadowed global's declaration

Found 2026-08-25 by [[REFACT-07]]'s `REFACT-07-00-05` (DR-05) data-context probe. Not caused by
REFACT-07; a shipped defect the probe surfaced while measuring something else.

## Reproduction

A file that assigns a global and then declares a `local` of the same name:

```lua
counter = 0
local counter = 0
print(counter)
```

Place the caret on the **`local`'s** `counter` — the declaration on line 2.

## Expected

The caret's declaration is the `local` on line 2. Rename, Go to Declaration and Find Usages should
all target it, and must not reach the global on line 1, which is a different variable that the
`local` shadows for the rest of the scope.

## Actual

The platform is handed the **global's** declaration leaf. DR-05 probe **a2**, read from a real
editor data context with nothing injected:

| field | value |
| :--- | :--- |
| `supplied` | `LeafPsiElement` (IDENTIFIER) |
| `textRange` | **(0,6)** — line 1's global, not the `local` on line 2 |
| `supplied is LuaNameRef` | false |
| registry returns | `PsiElementRenameHandler` (the default) |

Contrast probe **a**, a `local` with no shadowed global, which correctly supplies the declaring
`LuaNameRefImpl` at its own range. The difference is the shadowed global's presence.

Evidence: `docs/features/refactoring/07-inplace-rename/dr-05-evidence/measured-rows.txt`.

## Why this is `high`

If the resolution is wrong at the data context, every feature keyed on it is wrong for this file
shape — rename included. A rename driven from that element would target the **global**, so it would
rewrite the global's uses across the project while leaving the `local` and its uses alone, or
rename both names into one. That is [[BUG-457]]'s class of outcome — code silently broken by a
refactoring that reports success.

## Scope note

Measured at the **data context only**. The end-to-end rename was **not** driven, so the damage above
is the reasoned consequence of the measured resolution, **not an observed outcome**. Establish the
real end-to-end behaviour first — it is possible a later substitution step corrects the target, in
which case this is a resolution defect with no user-visible rename symptom, and the priority drops.

## Where the fix likely belongs

`LuaNameReference.resolve()` / `LuaScopeProcessor` scope walking: a `local` declaration's own name
must resolve to itself rather than to an earlier same-name global. See [[REFACT-01]]'s
`LuaDeclarationSite` and `AGENTS.md`'s note that Phase-1 (local) resolution returns the IDENTIFIER
leaf while Phase-2 (stub-index) returns the declaration element.
