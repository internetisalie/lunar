---
id: REFACT-05
title: "05: Rename Names Validator"
type: feature
parent_id: REFACT/INTENT
status: done
priority: "medium"
folders: ["[[features/refactoring/05-name-validator/requirements|requirements]]"]
---

# REFACT-05: Rename Names Validator

Supplies the platform `com.intellij.lang.refactoring.NamesValidator` for Lua so the Rename
refactoring (and any platform feature that consults the validator) rejects new names that are
Lua reserved keywords or are not syntactically valid Lua identifiers.

## Requirements Table

| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| REFACT-05-01 | Keyword Validation | Must | Full | The validator must report every Lua reserved word (Lua 5.1–5.4 union, incl. `goto`) as a keyword, so the platform blocks renaming an identifier to a reserved word. |
| REFACT-05-02 | Identifier Validation | Must | Full | The validator must report a string as a valid identifier iff it matches the ASCII Lua identifier grammar `^[A-Za-z_][A-Za-z0-9_]*$` **and** is not a reserved word. |

## Behaviour Contract

The validator exposes two pure boolean functions; the **error tooltips** shown during Rename
(e.g. *"'local' is a reserved keyword"*, *"'1var' is not a valid identifier"*) are produced by
the **platform** rename UI based on these booleans, not by this plugin. Unit tests therefore
assert the boolean returns of `LuaNamesValidator().isKeyword(...)` / `isIdentifier(...)`
directly (a `Project?` may be passed as `null`); they do **not** assert tooltip text.

- `isKeyword(name)` → `true` iff `name` is a Lua reserved word.
- `isIdentifier(name)` → `true` iff `name` matches `^[A-Za-z_][A-Za-z0-9_]*$` and `!isKeyword(name)`.
  By platform convention a keyword is **not** a valid identifier, so `isIdentifier` returns
  `false` for any reserved word even though it matches the character grammar.

## Test Cases

All cases call the validator directly: `val v = LuaNamesValidator()` with `project = null`.

### TC-1: Valid identifier accepted (REFACT-05-02)
- **Input:** `v.isIdentifier("foo", null)`
- **Expected Output:** `true`
- **Also:** `v.isKeyword("foo", null)` == `false`; `v.isIdentifier("_x1", null)` == `true`;
  `v.isIdentifier("X", null)` == `true`.

### TC-2: Reserved keyword rejected (REFACT-05-01)
- **Input:** `v.isKeyword("local", null)`
- **Expected Output:** `true`
- **Coupled:** `v.isIdentifier("local", null)` == `false` (keyword is not a valid identifier,
  so the platform rejects the rename).

### TC-3: Syntactically invalid identifier rejected (REFACT-05-02)
- **Input:** `v.isIdentifier("1var", null)`
- **Expected Output:** `false` (leading digit). Also `v.isKeyword("1var", null)` == `false`.
- **Also:** `v.isIdentifier("a-b", null)` == `false`; `v.isIdentifier("", null)` == `false`;
  `v.isIdentifier("foo bar", null)` == `false`.

### TC-4: `goto` edge case — 5.2+ keyword in the union (REFACT-05-01)
- **Input:** `v.isKeyword("goto", null)`
- **Expected Output:** `true` (the validator uses the full 5.1–5.4 keyword **union**, so
  `goto` is rejected regardless of the project's configured language level — see design §2).
- **Coupled:** `v.isIdentifier("goto", null)` == `false`.

### TC-5: `end` / structural keyword edge case (REFACT-05-01)
- **Input:** `v.isKeyword("end", null)`
- **Expected Output:** `true`. Coupled: `v.isIdentifier("end", null)` == `false`.

### TC-6: Near-keyword is a valid identifier (REFACT-05-02)
- **Input:** `v.isIdentifier("end_", null)` and `v.isIdentifier("End", null)`
- **Expected Output:** `true` for both (case-sensitive; not reserved). `v.isKeyword("End", null)`
  == `false`.

## Out of Scope

- Suggesting alternative names on conflict (covered by INTENT-03 name suggestion).
- Language-level-aware validation (treating `goto` as a valid identifier under Lua 5.1). The
  union is deliberately used for rename safety; see design §2 and risks-and-gaps Gap 2.1.
