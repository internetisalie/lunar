---
id: "TYPE-15"
title: "15: Too Many Arguments to an Unannotated Function, as a Weak Warning"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-15: Too Many Arguments to an Unannotated Function, as a Weak Warning

## Overview
A call that passes more arguments than an unannotated function declares is silent today: `reportArity`
puts it in the `HYPOTHESIS` tier (`LuaTypeGraph.kt:860`, BUG-419 defect 4), which no inspection shows.
Lua discards the extra arguments, so the call is legal, but while typing it is usually a mistake
(MAINT-55 DR-07: `getInputLine(-1)` against `local function getInputLine()`). This feature reports it
as a weak warning from its own inspection, so it informs without claiming an error and can be turned off
on its own. A call against an annotated signature keeps its warning. Parent: [[features/type/requirements|TYPE]].

## Scope

### In Scope
- A new inspection reporting too many arguments against an unannotated signature, default level
  `WEAK WARNING`.
- A quick fix that adds the missing parameters to the declaration.

### Out of Scope
- Too few arguments against an unannotated signature: an unannotated parameter is optional (LuaLS's
  rule too), so it stays a hypothesis.
- A dot call that omits a colon method's `self` (MAINT-55's rejected missing-`self` deviation; filed by
  MAINT-55 Phase 4).
- Annotated signatures: unchanged, still `WARNING` through the existing inspection.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TYPE-15-01 | **Report** | M | A call passing more arguments than an unannotated signature's maximum is reported, with the message `reportArity` already builds. |
| TYPE-15-02 | **Weak warning, own inspection** | M | Reported by a new `localInspection` (group `Lua`, enabled, `level="WEAK WARNING"`), not by Type assignability; disabling it hides only these reports. |
| TYPE-15-03 | **Multiple returns** | M | No report when the last argument is a call or `...` and the maximum is above 0 (LuaLS's rule, DR-07). |
| TYPE-15-04 | **Variadic** | M | No report against a signature with `...`. |
| TYPE-15-05 | **Stdlib aliases** | M | A local bound to a stdlib function (`local tb = debug.traceback`) is checked against the stdlib signature, not reported against an inferred one (the `compat53` false reports in DR-07). |
| TYPE-15-06 | **Quick fix** | S | *Add parameter*: appends `p<n>` parameters to the declaration so it accepts the call. |
| TYPE-15-07 | **Conformance** | S | MAINT-55's `undeclared-arity` cases score `agree`, and that deviation is withdrawn. |

## Behavior Rules
- Severity comes from the inspection's profile level; the tier at `LuaTypeGraph.kt:860` gives this case
  its own value so the inspections can tell it apart.
- One report per call, against the call's argument list.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TYPE-15-01, -02 | `local function f() end` / `f(1)` | highlight | one weak warning on `f(1)`: `Too many arguments: expected at most 0, got 1` |
| 2 | TYPE-15-02 | case 1 with the new inspection disabled | highlight | no report |
| 3 | TYPE-15-03 | `local function f(a) end` / `f(g())` | highlight | no report |
| 4 | TYPE-15-04 | `local function f(a, ...) end` / `f(1, 2, 3)` | highlight | no report |
| 5 | TYPE-15-05 | `local tb = debug.traceback` / `tb(coroutine.running(), "m", 1)` | highlight | no report |
| 6 | TYPE-15-01 | `---@param a integer` / `local function f(a) end` / `f(1, 2)` | highlight | one warning, from Type assignability as today |
| 7 | TYPE-15-06 | case 1, *Add parameter* | apply fix | `local function f(p1) end` |
| 8 | out of scope | `local function f(a, b) end` / `f(1)` | highlight | no report |

## Acceptance Criteria
- [ ] Test cases 1–8 pass.
- [ ] The corpus sweep's new weak warnings, once BUG-74 is fixed, are sampled and the false rate recorded.
- [ ] MAINT-55's `undeclared-arity` deviation is withdrawn and its cases rescored.

## Non-Functional Requirements
- No new snapshot work: the inspection reads the errors `LuaTypesSnapshot` already computes.

## Dependencies
- **BUG-74** first: it causes 113 of the 129 reports DR-07 measured (`fun(a) | fun()` method arms).
- MAINT-55 (deviation record).
