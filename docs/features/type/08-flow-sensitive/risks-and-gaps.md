---
id: "TYPE-08-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "TYPE-08"
folders:
  - "[[features/type/08-flow-sensitive/requirements|requirements]]"
---

# TYPE-08: Risks & Gaps

## Critical Risks

### Risk 1.1: Scope-injection may leak beyond the guarded block
- **Impact**: narrowed type persists after `end`, causing incorrect inference downstream.
- **Likelihood**: low — `LuaScope.child()` + try/finally already enforces block-local scope;
  the same pattern is used by `visitBlock`, `visitGenericForStatement`, and `self` injection.
- **Mitigation**: follow the exact `previousScope = scope; scope = scope.child(); try {…} finally {scope = previousScope}` pattern from existing code. Add a regression test that checks
  type of `x` *after* the `if` block.

### Risk 1.2: Graph node anchoring may cause stale/wrong error locations
- **Impact**: `VariableNode` created for narrowing uses the condition expression as its
  `PsiElement` anchor; type errors inside the block could be attributed to the wrong location.
- **Likelihood**: low — existing synthetic nodes (`self` injection in `visitFunctionBody`)
  use the same anchoring pattern without issues.
- **Mitigation**: use the guard's `LuaBinOpExpr` (the full condition) as the anchor. Test
  that error messages reference the guard expression, not the original declaration.

## Design Gaps

### Gap 2.1: Compound guards (`and`/`or`)
- **Question**: should `if type(x) == "string" and x ~= nil then` narrow `x` to `string`?
  The current design only handles single-pattern guards.
- **Options**: (a) extend `tryParseTypeofGuard` to recurse into `and` chains; (b) keep it
  simple and defer.
- **Resolved by**: DR-01 — de-risking spike to measure real-world frequency.

### Gap 2.2: `elseif` type-exhaustion interaction with union-minus
- **Question**: when the original type is `string|number` and `type(x) == "string"` matches
  the first branch, the second branch (`elseif type(x) == "number"`) should already see `x`
  narrowed to `number` (since `string` was excluded by the first guard). The current design
  re-parses all guards against the *original* type and relies on the complement logic only for
  the final `else` block. Does this produce correct behavior?
- **Options**: (a) maintain per-branch narrowed state flowing forward; (b) current design
  (each guard independently narrows from original).
- **Resolved by**: DR-02 — test the specific `elseif` chain pattern and verify.

### Gap 2.3: Loop guards (future)
- **Question**: should `while type(x) == "string" do … end` narrow `x`? The current scope
  is limited to `if`/`elseif`/`else`.
- **Options**: extend guard parsing to `LuaWhileStatement`/`LuaRepeatStatement` visitors.
- **Resolved by**: deferred to future feature; if needed, the same guard-parsing logic
  (extract to a shared utility) would apply.

## Technical Debt & Future Work
- **TBD: Compound guard support** — `and`/`or`/`not` predicate logic for multi-condition narrowing. Deferred because single-pattern guards cover the vast majority of real-world Lua type checks.
- **TBD: Loop guards** — `while`/`repeat` narrowing is out of scope for this feature.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-01 | Search `~/Documents/src/lua/test` corpus for `type(x) == "string" and` patterns to measure compound guard frequency. | Gap 2.1 | completed |
| DR-02 | Write a quick manual test: `local x; ---@type string\|number; if type(x) == "string" then … elseif type(x) == "number" then … end` — verify narrowing in each branch via snapshot inspection. | Gap 2.2 | completed |

## Test Case Gaps
- Compound condition guards (deferred, see Gap 2.1).
- Guards on table fields / upvalues (deferred out of scope).

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)