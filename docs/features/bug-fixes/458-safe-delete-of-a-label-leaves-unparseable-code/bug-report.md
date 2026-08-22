---
id: "BUG-458"
title: "Safe Delete of a `::label::` removes only the name and leaves `::::`, which no Lua version can parse"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-458: the delimiters survive the delete

Found 2026-08-22 by the [[REFACT-04]] retroactive-requirements agent. Inferred end-to-end from
control flow; the *consequence* was executed against four real interpreters.

## 1. Reproduction (predicted)

1. In a Lua 5.2+ file, write `::retry::` with a `goto retry` referring to it.
2. Safe Delete the label.

## 2. Expected vs actual

- **Expected**: the whole `::retry::` statement is removed, or the delete is refused because a
  `goto` still refers to it.
- **Actual (predicted)**: the name is removed and the delimiters remain, leaving `::::`.

Executed: `::::` is a parse error on **every** version tested — 5.1.5, 5.2.4, 5.3.6, 5.4.7 — with
`<name> expected near '::'`. So the outcome is a file that will not load.

## 3. Root cause

`refactoring/LuaSafeDeleteProcessor.kt`'s `declarationNodeFor` elevates a leaf to its enclosing
statement **only when the leaf's parent is a `LuaNameRef`**. A label's name sits under a `LuaLabel`,
not a `LuaNameRef`, so no elevation happens and only the identifier is deleted.

This is a direct consequence of the codebase's PSI shape: labels are the *only* construct with real
declaration PSI (`LuaLabelNameImpl` is the single `LuaNameDeclElement` implementor), so they take a
different path through code written around the `LuaNameRef` convention that covers everything else.

## 4. Fix strategy

Extend `declarationNodeFor` to elevate a `LuaLabelName` to its enclosing `LuaLabel` statement.

While there, check the referring-`goto` case: Safe Delete's contract is to *find usages first*, and
a label with a live `goto` should raise a conflict rather than delete and break the jump.

## 5. Test strategy

No test in the repo deletes a label. Add one asserting the resulting text parses — and prefer
asserting on the **parsed result** rather than on the string, so the test states the real
requirement ("the file is still valid Lua") rather than a formatting expectation.
