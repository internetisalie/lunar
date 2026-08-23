---
id: "BUG-466"
title: "A dotted function and a same-named field assignment silently unresolve every call site, and rename reports no conflict"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-466: `function M.run()` beside `M.run = function() end` — the call sites vanish

Measured on the builder 2026-08-23 during REFACT-01 Phase 4's remediation, with a throwaway probe,
one fixture set per case. Recorded as REFACT-01 `risks-and-gaps.md` **Gap 2.15**. This is
BUG-457's shape — a rename that rewrites the declaration and leaves the call sites — arriving
through *resolution* rather than through classification.

**Status: fixed in REFACT-01 Phase 4 (2026-08-23).** It was filed as deferred for one stated
reason, that reason was false, and it was closed in the same phase once that was established. Both
halves are kept below, because the false reason is the more useful record.

## Reproduction

```lua
-- a.lua
function M.run() end       -- caret here, Rename to `start`
-- b.lua
M.run = function() end
-- c.lua
M.run()
```

**Expected:** either all three sites are rewritten, or the conflicts dialog says why they will not
be.

**Actual, as found:** `a.lua` and `b.lua` are rewritten. **`c.lua` is left calling `M.run()`**,
which now resolves to nothing. No conflict, no warning, no preview entry — the refactoring reports
success.

**After the fix:** the conflicts dialog reports `'M.run' is declared in 2 places` before anything is
written. On Continue, `a.lua` and `b.lua` are rewritten and `c.lua` is not — the same edit, but no
longer silent. Repairing `c.lua` would mean changing resolution, not conflict detection.

## Mechanism — measured, not read

`LuaNameReference.doMultiResolve`'s qualified branch consults **two** sources for a dotted name:
`LuaGlobalDeclarationIndex` under `M.run` (the stub of `function M.run()`) and
`LuaMemberFieldNavigation.find(project, "M.run")` (the assignment in `b.lua`). With both present
`multiResolve` yields two results, so `resolve()` returns null and `isReferenceTo` is false —
exactly the mechanism REFACT-01 design §3.4's C4 rule exists to report.

`ReferencesSearch` on the declaration leaf, printed per reference:

| Project | References found |
| :-- | :-- |
| declaration + `c.lua` call site (control) | **1** — `c.lua`'s call site |
| declaration + `b.lua` field assignment + `c.lua` call site | **1** — `b.lua`'s *assignment target*; the call site is **gone** |

The count is unchanged, which is why a count-based check would miss this: the composition changed.

`LuaRenameConflictDetector.collisions(...)` returns **0** for the second project. C4 counts only
`LuaGlobalDeclarationIndex` hits for the qualified key — one — so `declarations.size < 2` and the
rule returns early.

## Why it was not fixed in place at first — and why that reason was false

**The objection, as recorded.** Adding `LuaMemberFieldNavigation.find` to C4's candidate set would
make the count 2 and report the conflict, but the collision would be anchored on the `run` name ref
of `M.run = function() end` — which the probe above proves is in the renamed symbol's **usage
set**. The platform was said to delete collision anchors from that set, so pressing Continue would
skip rewriting it: a *second* silent partial rename, created by the machinery meant to warn about
the first.

**The platform does not do that.** `RenameUtil.removeConflictUsages`
(`platform/refactoring/src/com/intellij/refactoring/rename/RenameUtil.java:297-307`) iterates the
usage set and removes only `usageInfo instanceof UnresolvableCollisionUsageInfo` — collision
*objects*, not usages that share an anchor *element*. It could not do otherwise: `UsageInfo.equals`
(`platform/core-api/src/com/intellij/usageView/UsageInfo.java:348-359`) opens with
`if (o == null || !getClass().equals(o.getClass())) return false`, so a real usage and a collision
usage on the same element are never equal and both survive the `LinkedHashSet`
`RenameProcessor.preprocessUsages` builds (`RenameProcessor.java:246-252`).

Measured against that exact call: `removedCount=1`, and the real usage on the anchor survives. Then
measured end to end in Lunar's own harness —
`LuaRenameConflictTest.testCollisionAnchoredOnAUsageIsStillRewritten` anchors a collision on
`b.lua`'s field, presses Continue through `BaseRefactoringProcessor.ConflictsInTestsException
.withIgnoredConflicts`, and asserts `b.lua` is rewritten to `M.start = function() end`.

**Anchoring a collision on a usage does not skip rewriting it. The second silent partial rename
does not exist.** The claim had reached five documents and two KDocs, and was the sole stated reason
a measured data-loss path was shipping. It is the same shape as REFACT-01 design §3.3's
"cannot happen in practice because `LuaNamesValidator` gates the dialog first" — a plausible safety
claim derived from reading, which made shipping a defect look like a considered decision.

## Fix as applied

1. `LuaRenameConflictDetector.globalDeclarationsNamed` gained a third candidate source,
   `LuaMemberFieldNavigation.find`, so C3/C4's candidate set is exactly the set
   `LuaNameReference.doMultiResolve`'s qualified branch consults — which is where the ambiguity
   comes from, and what makes C4's count agree with the resolve it reports on. **No shape guard is
   needed:** `LuaGlobalAssignmentIndex` records undotted names only and `LuaMemberFieldIndex` dotted
   only, so exactly one of the two navigation lookups can hit for any key and the other is an index
   read that returns nothing. One term, in one expression.
2. The hits are anchored **on the field's own name ref**, not on the enclosing
   `LuaAssignmentStatement` as the original sketch proposed. That step existed only to dodge the
   hazard above; with the hazard void, the field is the better anchor — it is the rival declaration
   the user must look at, and it is still rewritten on Continue.
3. `LuaMemberFieldNavigation.find` gained `ProgressManager.checkCanceled()` at the two levels whose bodies can force work (parse a file, walk its statements). It
   now has a rename-time caller, which is when users cancel, and its loops parse and then walk a
   whole file each — matching what `LuaGlobalAssignmentNavigation.find` already does.
4. Tests: `LuaRenameConflictTest.testDottedFunctionBesideAFieldAssignmentIsReported` (the three-file
   fixture, asserting the `refactoring.rename.conflict.ambiguousGlobal` message names `M.run` with a
   count of 2) and `testCollisionAnchoredOnAUsageIsStillRewritten` (the anchor survives Continue).
   **Mutation-proved:** dropping the new term compiles and reddens both — the first with
   `conflictsFromRenamingTo`'s "applied silently", the second with zero anchors — while the other
   ten cases in the class stay green. Neither is green on the parent commit; the second asserts the
   anchor *before* renaming precisely so that it cannot be.

**What is reported, not repaired.** `c.lua`'s call site is still not rewritten: it is not a findable
reference while `multiResolve` is ambiguous. C4's job is to warn before anything is written, and the
test asserts `c.lua` unchanged so that boundary is explicit rather than assumed.

## Related

- REFACT-01 `risks-and-gaps.md` Gap 2.15 (this), Gap 2.14 → [[BUG-465]].
- [[BUG-457]] — the original silent partial rename this shares its shape with.
