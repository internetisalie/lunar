---
id: "BUG-466"
title: "A dotted function and a same-named field assignment silently unresolve every call site, and rename reports no conflict"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-466: `function M.run()` beside `M.run = function() end` — the call sites vanish

Measured on the builder 2026-08-23 during REFACT-01 Phase 4's remediation, with a throwaway probe,
one fixture set per case. Recorded as REFACT-01 `risks-and-gaps.md` **Gap 2.15**. This is
BUG-457's shape — a rename that rewrites the declaration and leaves the call sites — arriving
through *resolution* rather than through classification, and it is the residual the Phase-4
conflict-detection fix deliberately did **not** close (see "Why not fixed in place" below).

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

**Actual:** `a.lua` and `b.lua` are rewritten. **`c.lua` is left calling `M.run()`**, which now
resolves to nothing. No conflict, no warning, no preview entry — the refactoring reports success.

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

## Why not fixed in place (REFACT-01 Phase 4)

Adding `LuaMemberFieldNavigation.find` to C4's candidate set would make the count 2 and report the
conflict, but the collision would be **anchored on the `run` name ref of `M.run = function() end`**
— and the probe above proves that element is a member of the renamed symbol's **usage set**. The
platform deletes collision anchors from the usage set (`RenameUtil.removeConflictUsages`,
`RenameProcessor.java:248-252`), so pressing Continue would then skip rewriting it: a *second*
silent partial rename, created by the machinery meant to warn about the first. That anchoring rule
is stated as an invariant in `LuaRenameCollisionUsageInfo`'s KDoc and in design §2.4.

Closing this therefore requires a decision the feature's design does not currently carry — most
plausibly anchoring member-field hits on the enclosing `LuaAssignmentStatement` (not in the usage
set, still shown in the dialog), or making the anchor selection usage-set-aware. Either is a design
change with its own correctness argument and its own tests, not a phase-local edit.

## Fix sketch

1. Extend `LuaRenameConflictDetector.globalDeclarationsNamed` so a dotted key also reads
   `LuaMemberFieldNavigation.find` — mirroring `LuaNameReference.doMultiResolve`'s own qualified
   branch, which is where the ambiguity comes from.
2. **Anchor those hits on the enclosing declaration statement, not on the field's name ref**, and
   assert in a test that the anchored element is not one of the usages (the invariant above).
3. Regression test: the three-file fixture, asserting the `refactoring.rename.conflict.ambiguousGlobal`
   message names `M.run` and a count of 2.

## Related

- REFACT-01 `risks-and-gaps.md` Gap 2.15 (this), Gap 2.14 → [[BUG-465]].
- [[BUG-457]] — the original silent partial rename this shares its shape with.
