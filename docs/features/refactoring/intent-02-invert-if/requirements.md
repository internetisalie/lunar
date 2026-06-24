---
id: INTENT-02
title: Invert If Requirements
type: feature
parent_id: REFACT/INTENT
status: done
folders:
  - "[[features/refactoring/requirements|requirements]]"
---

# Invert If Requirements

## Overview

The "Invert 'if' statement" intention rewrites an `if … then … else … end` statement so
that its condition is negated and its `then` / `else` branch bodies are swapped, preserving
runtime behaviour. The intention is only offered for a `LuaIfStatement` that has exactly one
`then` block and one `else` block and **no `elseif`** branches.

De Morgan distribution over `and` / `or` is explicitly **out of scope** (see
[risks-and-gaps.md](risks-and-gaps.md)): an `and`/`or` chain is negated by wrapping in
`not ( … )`, not by distributing the negation into the operands.

## Requirements Table

| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INTENT-02-01 | Invert Condition | Must | done | Negate the condition of the `if` statement per the inversion table in [design.md](design.md) §3: flip relational operators (`==`↔`~=`, `<`↔`>=`, `<=`↔`>`, `>`↔`<=`, `>=`↔`<`), unwrap a `not X` unary into `X`, and wrap any other expression (bare name, call, `and`/`or` chain) as `not ( … )`. |
| INTENT-02-02 | Swap Branches | Must | done | Swap the body of the `then` block with the body of the `else` block, preserving statement order, comments, and indentation within each block. |
| INTENT-02-03 | Applicability Gating | Must | done | Offer the intention only for a `LuaIfStatement` with exactly one `then` block, one `else` block, and no `elseif`. Do not offer it for an `if` without `else`, or an `if` containing any `elseif`. |

## Test Cases

All test cases are exercised by `LuaInvertIfIntentionTest` (see
[implementation-plan.md](implementation-plan.md) §Phase 2). The caret position is marked
with `<caret>`. Each fixture uses 4-space indentation.

### TC1: Full invert + swap, relational operator (INTENT-02-01, INTENT-02-02, INTENT-02-03)
**Input:**
```lua
if x<caret> == 1 then
    foo()
else
    bar()
end
```
**Action:** invoke "Invert 'if' statement".
**Expected Output:**
```lua
if x ~= 1 then
    bar()
else
    foo()
end
```

### TC2: `not X` condition unwraps (INTENT-02-01)
**Input:**
```lua
if not<caret> ready then
    wait()
else
    proceed()
end
```
**Expected Output:**
```lua
if ready then
    proceed()
else
    wait()
end
```

### TC3: Non-relational condition wraps as `not ( … )` (INTENT-02-01)
**Input:**
```lua
if is<caret>Valid() then
    accept()
else
    reject()
end
```
**Expected Output:**
```lua
if not (isValid()) then
    reject()
else
    accept()
end
```

### TC4: `and`/`or` chain wraps, no De Morgan distribution (INTENT-02-01)
**Input:**
```lua
if a<caret> and b then
    first()
else
    second()
end
```
**Expected Output:**
```lua
if not (a and b) then
    second()
else
    first()
end
```

### TC5: Relational `<` flips to `>=` (INTENT-02-01)
**Input:**
```lua
if x<caret> < 10 then
    low()
else
    high()
end
```
**Expected Output:**
```lua
if x >= 10 then
    high()
else
    low()
end
```

### TC6 (negative): `elseif` present → intention absent (INTENT-02-03)
**Input:**
```lua
if x<caret> == 1 then
    a()
elseif x == 2 then
    b()
else
    c()
end
```
**Assertion:** `myFixture.getAvailableIntention("Invert 'if' statement")` returns `null`
(use `getAvailableIntentions` / `findSingleIntention` with a null assertion).

### TC7 (negative): no `else` branch → intention absent (INTENT-02-03)
**Input:**
```lua
if x<caret> == 1 then
    a()
end
```
**Assertion:** intention not offered (null).

## Requirement → Coverage Matrix

| Requirement | Covering Test Cases |
|---|---|
| INTENT-02-01 | TC1, TC2, TC3, TC4, TC5 |
| INTENT-02-02 | TC1 (and incidentally every positive case) |
| INTENT-02-03 | TC1 (positive), TC6, TC7 (negative) |

## See Also
- Design: [design.md](design.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
- Risks & gaps: [risks-and-gaps.md](risks-and-gaps.md)
