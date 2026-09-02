---
id: "REFACT-09"
title: "09: Colon-method rename"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "REFACT/INTENT"
folders:
  - "[[features/refactoring/requirements|requirements]]"
---

# REFACT-09: Colon-method rename

## Overview

Rename a `function Obj:m()` declaration and every `obj:m()` call site that binds to it. This is the
half of `REFACT-01-08` that did not ship: the dotted form (`function M.run()`) renames from both the
declaration and a call site since [[BUG-465]], while the colon form is refused by name in
[LuaRenameProcessor.kt:111](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt)
with `refactoring.rename.colonMethod`:

> Renaming a `function Obj:method()` declaration is not supported yet: calls written
> `obj:method()` are not resolved, so they would be left bound to the old name.

The refusal is correct today and must stay reachable. `REFACT-01-00-DR-03` measured what happens
without it: on a plain table, a global table and `setmetatable` OO, lifting the refusal renamed the
declaration, left every call site behind, and **reported success**.

## Scope

### In Scope
- Renaming the colon form where [[TYPE-13]] reports a **declaration** for the member and
  `TYPE-13-00-DR-02`'s completeness rule allows it.
- A declaration predicate that keeps the existing refusal as its `else` branch. TYPE-13's DR-01
  deleted the receiver-classification premise: an un-annotated receiver's member already resolves,
  so what this predicate asks is whether the member carries a declaration, not what class the
  receiver is.
- The caret-on-`self` guard: `self` resolves to the method-name leaf, so a caret on `self` would
  otherwise rename the method. `REFACT-01-00-DR-03` measured the premise as holding in both
  directions and sized the guard at ~8 lines, in `substituteElementToRename` via
  `PsiUtilBase.getElementAtCaret(editor)`.

### Out of Scope
- Making un-annotated receivers resolvable — that is [[TYPE-13]], and this feature cannot start
  before it lands.
- `self` and `...` as rename targets (`REFACT-01-19`, `Won't`), and dynamic `_G["x"]` access
  (`REFACT-01-20`, `Won't`).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| `REFACT-09-01` | **Rename from the declaration caret** | M | Caret on `m` in `function Obj:m()` renames the declaration and every binding call site. |
| `REFACT-09-02` | **Rename from a call site** | M | Caret on `m` in `obj:m()` does the same, matching the dotted form's behaviour since [[BUG-465]]. |
| `REFACT-09-03` | **Refuse rather than half-rename** | M | Where [[TYPE-13]] reports no declaration for the member, or `TYPE-13-00-DR-02`'s completeness rule is not met, the rename is refused with a message naming the reason. No partial write is ever committed. |
| `REFACT-09-04` | **Caret on `self` does not rename the method** | M | With the caret on `self` inside the method body, the method is not renamed. |
| `REFACT-09-05` | **Caret on the receiver renames the receiver** | M | Caret on `obj` in `obj:m()` renames `obj`, not `m` — the invariant the dotted form already holds. |
| `REFACT-09-06` | **Atomic** | M | The rename is one undoable write action; a refusal leaves the file byte-identical. |

## Behavior Rules

- **The refusal is the default, not the fallback.** The predicate must be written so an
  unclassified receiver takes the refusal branch. A shape nobody anticipated must be refused,
  not attempted.
- **Success is never reported for a partial rename.** This is the measured failure mode; it is
  the reason the feature exists as a separate unit of work rather than a lifted guard.

## Test Cases

| # | Requirement | Given | When | Then | Mutation that turns it red |
|---|-------------|-------|------|------|---------------------------|
| 1 | `REFACT-09-01` | `local t = {}` ; `function t:m() end` ; `t:m()` | rename `m` → `n` from the declaration | both sites read `n` | drop the call-site collector → declaration renamed alone |
| 2 | `REFACT-09-02` | as above | rename from the call site | both sites read `n` | remove the usage→declaration substitution → refusal |
| 3 | `REFACT-09-03` | a member [[TYPE-13]] resolves with no declaration — `LuaMemberDeclarations.declarationOf` returns null | attempt the rename | refused, file byte-identical | invert the predicate → the half-rename returns |
| 4 | `REFACT-09-04` | caret on `self` in the body | Shift+F6 | the method is not renamed | delete the guard → the method is renamed |
| 5 | `REFACT-09-05` | caret on `obj` in `obj:m()` | Shift+F6 | `obj` renamed, `m` untouched | widen `adjustTargetElement` → `m` renamed |

## Acceptance Criteria

- [ ] [[TYPE-13]] is `done` — this feature cannot be planned to the bar before it is.
- [ ] Every `M` requirement has an executed test with a named, reachable mutation.
- [ ] The three shapes DR-03 measured half-renaming are each covered by a refusal or a correct
      rename, with the executed evidence recorded.
- [ ] The `refactoring.rename.colonMethod` message is either still reachable or deliberately
      replaced, and the doc says which.
- [ ] `REFACT-01-08` is updated to `Full` only once this ships.

## Dependencies

- **Blocked on [[TYPE-13]]** — the declaration verdict is its input. The completeness verdict is
  `TYPE-13-00-DR-02`'s, which TYPE-13 explicitly does not deliver.
- Inherits `REFACT-01`'s rename machinery: `LuaRenameProcessor`, `LuaRenameConflictDetector`,
  `LuaTargetElementEvaluator.adjustTargetElement`.
