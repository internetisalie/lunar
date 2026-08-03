---
id: "BUG-391"
title: "Globals assigned in one project file are reported undeclared in another"
type: "bug"
parent_id: "BUG"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-391: Globals assigned in one project file are reported undeclared in another

`LuaUndeclaredVariable` flags uses of a global that is plainly assigned at top level in another
file of the *same* project — a file Lunar has indexed. The result is a wall of false errors in any
codebase that shares state through globals, which is a normal Lua idiom.

Found by the MAINT-33 corpus sweep's per-symbol breakdown (MAINT-33-10).

## 1. Reproduction

1. `a.lua` — `ide = { foo = 1 }` (a bare global assignment at top level).
2. `b.lua` — `print(ide.foo)`.
3. Both files in the same project, both indexed.

`ide` in `b.lua` is reported *Undeclared variable 'ide'*.

## 2. Expected vs Actual Behavior

- **Expected**: a global assigned at top level in any indexed project file resolves from every
  other file in that project, exactly as `LuaGlobalDeclarationIndex` exists to support.
- **Actual**: reported undeclared, at `level="ERROR"`, `enabledByDefault="true"`.

## 3. Context / Environment

- **Confidence**: high — measured, with the definitions located in indexed source.
- **Measured on ZeroBrane Studio 2.01** (corpus project `zerobrane`, roots `src,interpreters,api,cfg`,
  72 indexed files). `inspection.LuaUndeclaredVariable=1009`, of which the per-symbol breakdown
  attributes:

  | Symbol | Count | Assigned at |
  |---|---|---|
  | `ID` | 301 | `src/editor/ids.lua:205` — `ID = setmetatable({}, ide.proto.ID)` |
  | `ide` | 90 | `src/main.lua:64` — `ide = { … }` |

  Both assignment sites are inside a declared root, so both files were indexed by the same sweep
  that reported the warnings. **391 of 1009 warnings are this defect.**
- **Not this bug** — the rest of ZeroBrane's count is legitimately unresolvable:
  `wx` (335) and `wxstc` (112) are wxLua C bindings with no Lua-side declaration anywhere in the
  tree, and `ID_*` (24) are created dynamically by `IDgen` (`src/editor/ids.lua:199`) writing into
  `_G`, which no static analysis can see. Those need a definition library — and note the
  [LuaLS addon catalog](https://github.com/LuaLS/LLS-Addons/tree/main/addons) has **no**
  wxLua/wxWidgets addon, so TARGET-08 would not cover them.

## 4. Other Notes

- **Why it matters beyond ZeroBrane**: sharing configuration or an application object through a
  global is idiomatic Lua. Any project doing it sees `level="ERROR"` highlighting throughout, which
  is the kind of noise that makes users disable the inspection outright.
- **Fix direction**: check how `LuaUndeclaredVariableInspection` consults the global index —
  whether it queries `LuaGlobalDeclarationIndex` at all for a bare name reference, and whether a
  plain `name = value` top-level assignment produces an index entry (as distinct from
  `function name()`, which does). The corpus numbers suggest assignment-form globals are the gap.
- **Regression test**: the two-file case in §1 as a unit test, then re-run the corpus. ZeroBrane's
  `symbol.LuaUndeclaredVariable.ID` and `.ide` keys should drop to zero while `wx`/`wxstc` stay —
  a precise before/after that needs no golden file.
- Related but distinct: BUG-390 (type-graph stack overflow) also fires on this corpus and makes
  the aggregate inspection counts non-reproducible; the per-symbol keys are recorded but ungated
  for that reason.
