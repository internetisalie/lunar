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

**And nothing rolls it back.** Measured: raising a `ProcessCanceledException` inside the rename path
yields `thrown = null` and leaves the file half-applied.

*Corrected 2026-08-23 — the original wording named the wrong mechanism twice.* There is no
`WriteCommandAction` on this path: the write action is `ApplicationImpl.runEdtProgressWriteAction`'s
`lock.runWriteActionBlocking`, and the command is `CommandProcessor.executeCommand` opened at
`BaseRefactoringProcessor.java:453-458`. And "did not roll back" implies rollback is something a
write action does — **no IntelliJ write action ever rolls back**. The exception dies in
`PotemkinProgress.runInSwingThread`, which is `try { … } catch (ProcessCanceledException ignore) {}`
(`PotemkinProgress.java:151-162`); `BaseRefactoringProcessor.doRefactoring` then simply `return`s.
Undo *is* recorded — one `EditorChangeAction` per `DocumentEvent` — and restores the file
byte-for-byte, but nothing invokes it and the user is told nothing.

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

Written by **Phase 2 of this feature** (`84eefb25`), not pre-existing to REFACT-01 — an earlier
version of this section said otherwise, using "pre-existing to Phase 6" to license the broader claim.
It falsifies `REFACT-01-01` (**Must**), now corrected from Full to Partial.

**Exposure starts at the *second* cancellation check, not the first** — corrected 2026-08-23. A
cancel at the first check leaves the file untouched, because the parse inside `setName` throws before
usage 1 is written. "Every rename of a symbol with more than one usage" overstated it.

## 5. Fix strategy

Two candidates, and the choice is a real design decision rather than a patch:

1. **Drain the rewrites into one atomic step** — resolve every edit, then apply them with no
   cancellation point in between. Matches what Phase 2's remediation already did for the
   *resolution* half.

   **As written this is insufficient, measured 2026-08-23.** Removing *our* cancellation point is not
   enough: with all edits prepared and the loop unwrapped, cancelling after the first `replaceChild`
   still splits the file, because `ASTNode.replaceChild` reaches a cancellation point of its own
   before `PomModelImpl.runTransaction`'s non-cancelable section (`PomModelImpl.java:112`). The
   cancellation points are not only ours. `ProgressManager.executeNonCancelableSection` around the
   apply phase is what actually closes it.
2. **Make the partial state explicit** — if a cancel between edits is unavoidable, the refactoring
   must fail loudly rather than return with the file inconsistent.

Removing the `checkCanceled()` outright is **not** a candidate: the loop is unbounded in a large
project, and engineering-contract invariant 3 exists for it.

## 6. Test strategy

Raise `ProcessCanceledException` at usage *k* of a three-usage fixture and assert the **whole file** —
both that no usage moved and that the declaration did not. Mutation that must turn it red: restore
the current ordering.

Note the trap: asserting only that an exception surfaced is green today, because it does not.
