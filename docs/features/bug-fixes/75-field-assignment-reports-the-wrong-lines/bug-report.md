---
id: "BUG-75"
title: "Assigning a wrong value to an annotated class field also reports the earlier valid assignment, and `t.x = {}` reports a missing required field"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-75: Assigning a wrong value to an annotated class field also reports the earlier valid assignment, and `t.x = {}` reports a missing required field

Found by the MAINT-55 conformance suite: 4 cases (rule `field.assign`, observed `false-report`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `assign-type-mismatch-002` (`rule=field.assign source=assign-type-mismatch.lua#2`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@class A
---@field x A

---@type A
local t

t.x = {}
```

Cases in this group: `assign-type-mismatch-002`, `assign-type-mismatch-006`, `assign-type-mismatch-007`, `assign-type-mismatch-016`.

## 2. Expected vs Actual Behavior
- **Expected**: `m.x = true` (line 7) is reported once, on line 7. `t.x = {}` against `---@field x A` is silent (LuaLS marks no line).
- **Actual**: The report is repeated on the earlier, valid `m.x = 1` line (5-5, `boolean is not assignable to number`), which no conformance line marks; a `{}` assigned to a field of class type reports `Missing required field 'x'` on the assignment (assign-type-mismatch-002).
- **Recorded for `assign-type-mismatch-002`**: findings `7-7:Missing required field 'x'`; conformance lines none must be reported.

## 3. Root Cause
Two causes.

**Earlier valid assignment reported (006, 007, 016).** Each `m.x = ...` is a separate member node, but `checkTableCompatibility` wires every site bidirectionally to the class's single member node (`LuaTypeGraph.kt`, the `addEdge(valueNode, target); addEdge(target, valueNode)` pair, "invariance"). A value written at one site therefore reaches the declared demand of another: `---@type integer` on `m.x = 1` injects a declared `UseNode` anchored on that statement (`LuaTypeGraphBridge.injectTypeAnnotation`), and `true` from `m.x = true` arrives at it through the merge. `reportIncompatible` anchors at the use element, so the finding lands on line 5. Each site is also checked as its own variable, which is why line 7 is reported too. The merged values are the class member (value element is the file), the other site's target `.x`, and the other site's assigned value.

**`t.x = {}` missing field (002).** `checkTableCompatibility` reports `Missing required field` for any table value against an exact class use, including a `{}` that is only assigned to a member. LuaLS's case file marks no line: a table written to a member is not a construction of the member's class.

## 4. Fix Strategy
New `LuaMemberAssignmentSites` (`lang/psi/types/`), called from two places in `LuaTypeGraph`:
- `reportIncompatible` returns when the use is a member-assignment target (or its statement) and the value is a merged node (`isMergedValue`): the file-anchored class member, a member target of the same name, or a value inside a different assignment writing that member. The value's own site keeps its report.
- The `Missing required field` emission is skipped when the value is written directly to a member (`isWrittenToMember`).
No stub stores anything differently, so the stub version is unchanged. A `HYPOTHESIS` anchored at the sibling's value remains; hypotheses are not reports (the conformance runner and both inspections exclude them).

## 5. Test Strategy
`LuaFieldAssignmentReportTest` (light fixture, reported = non-hypothesis findings with line numbers): 006, 007 and 016 report only the wrong line; 002 is silent; a `{}` assigned to a class-typed local still reports its missing field. Conformance cases 002, 006, 007 and 016 move `false-report` to `agree`.

## 6. Related
[[BUG-44]] covers the converse (a wrong field value reported only as a hypothesis); [[BUG-481]] covers `{ ... }` against an array type.
