---
id: INSP-03
title: "03: Type Mismatch"
type: feature
parent_id: INSP
status: planned
priority: medium
folders:
  - "[[features/inspections/requirements|requirements]]"
---

# INSP-03: Type Mismatch

## Overview
Flag assignments, call arguments, and `return` values whose inferred value type is not
assignable to the declared/expected type, surfaced as a `<localInspection>`. This behaviour
is delivered by the inference engine (`LuaTypeGraph.checkCompatibility`) and exposed to the
user by two **already-shipped** inspections — `LuaTypeAssignabilityInspection` (non-return
errors) and `LuaReturnTypeMismatchInspection` (return errors). See the parent epic
[[features/inspections/requirements|Inspections]]. INSP-03 depends on `TYPE-09`
(union distribution, DONE).

## Scope

### In Scope
- Confirm and lock down (with real-flow regression tests) the scenarios below against the
  existing two inspections.
- Reconcile the requirements/test-case vocabulary to the engine's actual diagnostic message
  format (`"<actual> is not assignable to <expected>"`), not an invented `LuaTypeMismatchInspection`.

### Out of Scope
- A new third inspection class. The two existing inspections already cover the scenarios; a
  new one would duplicate them (see design §1 Prior Art). Explicitly rejected.
- Quick fixes (e.g. "change declared type"). Future work — see [risks-and-gaps.md](risks-and-gaps.md).
- Anonymous-function (`LuaFuncDef`) `@return` checking beyond what the engine already models.

## Functional Requirements
| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| INSP-03-01 | **Assignment type check** | Must | Full | A value whose inferred type is not assignable to the variable's declared `---@type` is flagged by `LuaTypeAssignabilityInspection`. |
| INSP-03-02 | **Union type check** | Must | Full | A value assignable to at least one variant of a declared union (`string\|number`) is NOT flagged; one assignable to none is flagged with a closest-match diagnostic. Delivered by `TYPE-09` union distribution in `checkCompatibility`. |
| INSP-03-03 | **Return type check** | Should | Full | A `return` value not assignable to the enclosing named/local function's `---@return` type is flagged by `LuaReturnTypeMismatchInspection`. |
| INSP-03-04 | **Argument type check** | Should | Full | A call argument not assignable to the corresponding `---@param` type (incl. arity / missing-field) is flagged by `LuaTypeAssignabilityInspection`. |

> **Status rationale.** All four rows are `Full`: the engine emits the relevant
> `ElementError`s and the two registered inspections surface them. The remaining INSP-03 work
> is **documentation reconciliation plus missing regression coverage** (the scalar
> `@type` mismatch and the union *pass* case lack a real-flow test today), not new
> production code. See design §1 and the implementation plan.

## Behavior Rules
- **No false positives on `any`/`unknown`.** `checkCompatibility` returns early when either side
  is `LuaGraphType.Any` or `LuaGraphType.Undefined` (`LuaTypeGraph.kt:282-283`), so un-inferable
  expressions never warn.
- **Element pinning.** The problem is registered on the *value* element (the RHS / argument /
  returned expression), via `ElementError.element` (`LuaTypeGraph.kt:595`).
- **Partitioning.** A given engine error is shown by exactly one inspection: returns
  (`LuaFinalStatement` / function-decl elements) go to `LuaReturnTypeMismatchInspection`; everything
  else goes to `LuaTypeAssignabilityInspection` (the `isReturnRelated` filter in both classes).

## Test Cases
| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | INSP-03-01 | `---@type string`<br>`local x = 42` | Enable `LuaTypeAssignabilityInspection`, `doHighlighting()` | A problem whose description contains `not assignable` and mentions `number`/`string`, pinned to `42`. |
| 2 | INSP-03-02 | `---@type string\|number`<br>`local x = 42` | Enable `LuaTypeAssignabilityInspection`, `doHighlighting()` | **No** assignability problem (value matches a union variant). |
| 3 | INSP-03-02 | `---@class Point` … `---@type Point\|Color`<br>`local p = { x = 1 }` | Enable `LuaTypeAssignabilityInspection`, `doHighlighting()` | A problem naming closest match `Point` and missing field `y` (covered by `testUnionClosestMatchDiagnosticOnRealCode`). |
| 4 | INSP-03-03 | `---@return number`<br>`local function f() return "x" end` | Enable `LuaReturnTypeMismatchInspection`, `doHighlighting()` | A return-mismatch problem (covered by `testReturnTypeMismatchReported`). |
| 5 | INSP-03-03 | `---@return number`<br>`local function f() return 42 end` | Enable `LuaReturnTypeMismatchInspection`, `doHighlighting()` | **No** mismatch problem (covered by `testMatchingReturnNotReported`). |
| 6 | INSP-03-04 | `---@param a number`<br>`---@param b number`<br>`local function add(a,b) end`<br>`add(1)` | Enable `LuaTypeAssignabilityInspection`, `doHighlighting()` | A `Too few arguments` problem (covered by `testArityTooFewReported`). |

## Acceptance Criteria
- [ ] INSP-03-01: a real-flow test proves the scalar `@type` mismatch (Test Case 1) is shown by `LuaTypeAssignabilityInspection`.
- [ ] INSP-03-02: a real-flow test proves the union *pass* case (Test Case 2) produces **no** warning; the closest-match case (Test Case 3) already passes.
- [ ] INSP-03-03 / -04: existing tests for returns (4, 5) and arity (6) remain green.
- [ ] No third inspection class is introduced; docs name the two existing classes.

## Non-Functional Requirements
- Inspection runs in the standard `LocalInspectionTool` read-action context; the heavy work is
  the cached `LuaTypesSnapshot.forFile(file)` graph, reused across both inspections (no extra cost).

## Dependencies
- `TYPE-09` — union distribution in `checkCompatibility` (DONE). Required for INSP-03-02.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklist: [human-verification-checklists.md](human-verification-checklists.md)
