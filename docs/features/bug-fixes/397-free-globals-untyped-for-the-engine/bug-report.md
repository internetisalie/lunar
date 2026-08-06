---
id: "BUG-397"
title: "Free globals are typed for completion only, not for the inference engine"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-397: Free globals are typed for completion only, not for the inference engine

**FIXED 2026-08-05** (three phases + adversarial-review fixes, all on `worktree-bug-397-free-globals`).
This report was created at closure time to anchor the `[[397-free-globals-untyped-for-the-engine]]`
wikilink; the full history lives in the roadmap row (BUG-397, Wave 21) and in
`LuaTypesVisitor.visitNameRef`'s KDoc.

## Summary

`LuaTypeManager.resolveGlobal` (BUG-395) typed free globals for member completion only.
`LuaTypesVisitor.visitNameRef` deliberately declined to consult it, so hover, inlays and every
inspection saw `Undefined` for `table`, `redis`, `package` and any project-wide `Lib` declared in
another file — and the un-noded `package.path` read manufactured BUG-359's false positive through
`visitBinOpExpr`'s `graph.nil` operand fallback.

Two wire-up attempts (2026-08-04) were reverted: binding the receiver displaced the stub-derived
member route, `LuaTypeAlgebra` collapsed `any | { err: string }` to `Any`, and the checker began
arity-checking stub calls it had always skipped.

## Fix

1. **Phase 1** — `any | <structural>` keeps its structural arms; a union carrying an `Any` arm is
   *gradual*: never an assignability error, only matching arms propagate constraints.
2. **Phase 2** — declared types are authoritative: `visitIndexExpr` seeds free-global members from
   `resolveGlobal` as ValueNodes (typing, not checking); `visitFuncCall` flows a declaration-typed
   callee's declared return into call results and raises no call demand. Closes BUG-359.
3. **Phase 3** — `visitNameRef` falls back to a memoized per-name free-global seed for unbound
   names in value positions; declaration-typed receivers route members through the declared route.
4. **Review fixes** — chained access resolves hop-by-hop through declared types; an undeclared
   link leaves the rest of the chain node-less (never the bare-receiver-anchored graph path, which
   mis-resolved `A.b.c` as `A.c`).

Characterization + regression coverage: `FreeGlobalMemberTypingTest` (the three historical
regression shapes, three chain shapes), `DuplicateNilAssignabilityTest` (inverted per the landing
note), `LuaTypeAlgebraTest`. Known follow-ups from the adversarial review are recorded in the
roadmap row.
