---
id: INTENT-03
title: Name Suggestion Requirements
type: feature
parent_id: REFACT/INTENT
status: planned
---

# Name Suggestion Requirements

Provide context-aware variable-name suggestions derived from the right-hand-side (RHS)
expression, surfaced both in the **Rename** popup and in the **Introduce Variable** name
field. Implemented as a platform `NameSuggestionProvider`, with the existing
`LuaIntroduceVariableHandler` refactored to consume the same derivation so the two paths
agree (see [design.md](design.md) §5 "Prior Art / Integration").

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INTENT-03-01 | Contextual Suggestion | Must | planned | Suggest a variable name based on the RHS expression. For a function call, derive the name from the callee's last identifier segment; for a field access (`LuaIndexExpr`), from the trailing field name; for a bare name reference, from the name itself. |
| INTENT-03-02 | Prefix Stripping | Must | planned | Strip a leading accessor/factory prefix (`get`/`set`/`create`/`build`/`new`/`make`/`find`/`load`) from the derived name **only** when the prefix is immediately followed by an uppercase letter; lowercase that letter to form the suggestion (`getUser()` → `user`). |

## Derivation Contract (normative)

The algorithm, prefix list, case handling, dedup ownership, and fallbacks are specified
exactly in [design.md](design.md) §3 "Derivation Algorithm". Requirements below pin the
observable behavior; the test cases pin concrete input→output.

- A non-prefixed call name (e.g. `compute`) is returned unchanged (no over-stripping).
- A prefix that is NOT followed by an uppercase letter (e.g. `getter`, `settings`) is NOT
  stripped.
- Deduplication against names already in scope is the **platform's** responsibility for the
  Rename path; for the Introduce Variable path the handler's existing `uniquify` continues
  to own collision resolution. The provider returns a base candidate only (no numeric
  suffixing of its own).

## Test Cases

These are the normative input→output cases. Each `Must` requirement is covered.

### Test Case 1: Function call, prefix stripped (Introduce Variable path)
**Requirement:** INTENT-03-01, INTENT-03-02
**Input:** `print(<selection>getUser()</selection>)`
**Action:** Select `getUser()` and trigger "Introduce Variable".
**Expected Output:** Introduced declaration is `local user = getUser()`; the suggestion set
for the RHS includes `user`.

### Test Case 2: Function call, no prefix (identity)
**Requirement:** INTENT-03-01
**Input:** `compute()`
**Action:** `getSuggestedNames` invoked on the `compute()` call expression.
**Expected Output:** Suggestion set contains `compute` (unchanged — no stripping).

### Test Case 3: Method call `obj:getName()`
**Requirement:** INTENT-03-01, INTENT-03-02
**Input:** `obj:getName()`
**Action:** `getSuggestedNames` invoked on the call expression.
**Expected Output:** Suggestion set contains `name` (callee last segment `getName`, prefix
`get` stripped, `N`→`n`).

### Test Case 4: Dotted call `db.getUser()`
**Requirement:** INTENT-03-01, INTENT-03-02
**Input:** `db.getUser()`
**Action:** `getSuggestedNames` invoked on the call expression.
**Expected Output:** Suggestion set contains `user` (last identifier segment `getUser`, not
`db`; prefix stripped).

### Test Case 5: Field access `cfg.timeout` (in scope)
**Requirement:** INTENT-03-01
**Input:** `cfg.timeout`
**Action:** `getSuggestedNames` invoked on the `LuaIndexExpr` field access.
**Expected Output:** Suggestion set contains `timeout` (trailing field name; no prefix to
strip).

### Test Case 6: Prefix not followed by uppercase (no over-strip)
**Requirement:** INTENT-03-02
**Input:** `settings()`
**Action:** `getSuggestedNames` invoked on the call expression.
**Expected Output:** Suggestion set contains `settings` (the `set` prefix is followed by
lowercase `t`, so it is NOT stripped).

## See Also
- Design: [design.md](design.md)
- Implementation Plan: [implementation-plan.md](implementation-plan.md)
- Risks & Gaps: [risks-and-gaps.md](risks-and-gaps.md)
</content>
</invoke>
