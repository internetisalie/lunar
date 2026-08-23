---
id: "BUG-468"
title: "Cancelling a rename mid-flight leaves usages renamed and the declaration not — silently, with no rollback"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-468: `ProcessCanceledException` in the usage loop produces BUG-457's shape

Found 2026-08-23 by the [[REFACT-01]] Phase 6 reviewer. Promoted from a gap paragraph after that
paragraph — which this report's author wrote — was found to understate it.

## 1. The mechanism

`LuaRenameProcessor.renameElement` (`:209-215`) rewrites every usage first and the declaration last:

```kotlin
usages.forEach { usage ->
    ProgressManager.checkCanceled()
    RenameUtil.rename(usage, newName)
}
applyDeclarationRewrite()
```

A cancel at usage *k* therefore leaves *k*−1 usages on the **new** name and the declaration on the
**old** one. That is [[BUG-457]]'s shape — declaration and usages disagreeing, the file silently
broken — reached by pressing Cancel rather than by a defect in the rewrite.

**And the write action does not roll it back.** Measured: raising a `ProcessCanceledException` inside
the rename path yields `thrown = null` and leaves the file half-applied. The enclosing
`WriteCommandAction` neither restores the earlier edits nor surfaces the exception.

## 2. What this is not

It is **not** the annotation case in [[REFACT-01]]'s Gap 2.13, and it is worse. Gap 2.13's residue is
a stale `---@param` comment beside correct code. This leaves broken code.

It is **not** bounded at one usage. An earlier description said so; that bounds *latency*, not
damage — the damage is the whole file's declaration/usage agreement, regardless of *k*.

## 3. Provenance, and why the first description was wrong

The wording it replaces read *"bounded at one usage per cancellation … smaller than the annotation
case Gap 2.13 describes"* and opened *"**Measured, not inferred**"* — which was true only of the
no-rollback half. The bound, the comparison and the reachability of `checkCanceled()` were all
inferred and all wrong or unestablished.

That is the defect class this feature has spent seven review rounds finding in its own artefacts: a
plausible reassurance, stated with more confidence than its evidence, that makes shipping a defect
look like a considered decision. Recording it here rather than paraphrasing it away.

## 4. Scope

Pre-existing — the loop and its `checkCanceled()` are Phase 2/3 code, unchanged by Phases 4-6, and no
remaining REFACT-01 phase touches them. Every rename of a symbol with more than one usage is exposed.

## 5. Fix strategy

Two candidates, and the choice is a real design decision rather than a patch:

1. **Drain the rewrites into one atomic step** — resolve every edit, then apply them with no
   cancellation point in between. Matches what Phase 2's remediation already did for the
   *resolution* half.
2. **Make the partial state explicit** — if a cancel between edits is unavoidable, the refactoring
   must fail loudly rather than return with the file inconsistent.

Removing the `checkCanceled()` outright is **not** a candidate: the loop is unbounded in a large
project, and engineering-contract invariant 3 exists for it.

## 6. Test strategy

Raise `ProcessCanceledException` at usage *k* of a three-usage fixture and assert the **whole file** —
both that no usage moved and that the declaration did not. Mutation that must turn it red: restore
the current ordering.

Note the trap: asserting only that an exception surfaced is green today, because it does not.
