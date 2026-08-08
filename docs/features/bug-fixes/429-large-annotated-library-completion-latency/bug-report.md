---
id: "BUG-429"
title: "Member completion on a library global blocks on a full type materialization, so time-to-first-result equals time-to-exhaustive-result"
type: "bug"
status: "superseded"
parent_id: "BUG"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-429: First completion result waits for the exhaustive one

> **SUPERSEDED by [COMP-09](../../completion/09-member-enumeration/requirements.md), 2026-08-07 —
> not fixed, absorbed.** Every site in this report's fix direction is COMP-09-01/02; its DR-01/01b are
> COMP-09's DR-02/DR-05. Two items tracked one change.
>
> It was also not separable in practice, which is the part worth recording. This report proposed
> landing the two-site scan replacement "first, before building anything else" — but COMP-09-DR-01
> requires golden-filing today's exact enumeration result *before* anything replaces it, precisely
> because the natural implementation returns a superset and would silently make enumeration a new
> type source (Risk 1.1, the failure BUG-395 already hit and reverted as BUG-397). Replacing the
> scans changes enumeration, so it cannot precede that gate. "Land the small fix first" was the same
> work with a different label, minus the safety check.
>
> **This document is retained for its diagnosis, which COMP-09 references rather than restates** —
> the critical path, the four reasons it was never index-backed, and the `getAllKeys` audit. Nothing
> below is superseded as *analysis*; only the plan to act on it separately is.

Typing `wx.` against a definition library of realistic size shows nothing for **12.9 s**. The
exhaustive result taking 12.9 s is defensible; the *first* result taking 12.9 s is not. The two are
currently the same number, and that is the defect.

## Measured (`TargetTenDrSpikeTest`, gce-builder, 2026-08-07)

Wall-clock for the first `completeBasic()` after the root is indexed. Indexing is fast throughout
(1.5–2.7 s) — the cost is in completion.

| Fixture | Tree | First completion |
| :-- | :-- | --: |
| single file, 5 550 consts + 4 050 methods | 530 KiB | 25 352 ms (broad prefix) |
| same, **narrow** prefix (few candidates) | 530 KiB | 18 429 ms |
| same, class-member caret | 530 KiB | 17 680 ms |
| namespace members in root, class bodies split out | 230 KiB root + 15 files | **12 902 ms** |
| constants only | 320 KiB | 1 917 ms |
| constants only | 40 KiB | 297 ms |

Candidate count is not the driver: a narrow prefix costs 18 s where a broad one costs 25 s.

## CORRECTION 2026-08-07 — this report's critical path was wrong

Measured (`CompNineDrSpikeTest`, COMP-09 DR-02): `resolveGlobal` **9 568 ms**, `materialize`
**10 ms**, `getMembers` **0 ms** for 3 700 members. The section below states that `materialize`
"builds the complete type graph for every member … before the loop yields its first element". It
does not — materialization and enumeration together are 10 ms.

The cost is inside `resolveGlobal`: `doResolveGlobal → typeOfGlobalIn → globalTypeIn →
LuaTypesSnapshot.forFile(declaringFile)`, which builds the **whole 242 KiB library file's type
graph** to answer what type it gives one global. Corrected analysis is
[COMP-09 design §1](../../completion/09-member-enumeration/design.md).

Also measured: warm `resolveGlobal` is **0 ms**, but one keystroke in an *unrelated* file repays
**76 %** of the cost, because `typeCache` and the per-file snapshot both depend on project-wide
`PsiModificationTracker`. So it is per-keystroke, not per-session.

The section below is retained as the reasoning that was refuted — it is a clean example of a claim
read off a call shape and never run. The `getAllKeys` audit further down is unaffected: those scans
are real, they are simply inside the 10 ms and were never the headline.

## The critical path (AS ORIGINALLY WRITTEN — see correction above)

`LuaCompletionContributor.kt:370-388` already **streams** — its emit loop is
`for ((name, node) in members) { result.addElement(...) }`. It never gets to start:

```kotlin
val type = snapshot.getValueType(receiverExpr)
val members =
    if (type == LuaGraphType.Undefined) crossFileGlobalMembers(receiverExpr)   // ← this one
    else type.getMembers()
for ((name, memberNode) in members) { … result.addElement(…) }
```

For a library global the consumer file never binds, `type` **is** `Undefined`, so this is the only
path, and it is (`:133-139`):

```kotlin
val global = LuaTypeManager.getInstance(…).resolveGlobal(nameRef.text, nameRef)  // index-backed
return LuaGraphType.materialize(global, nameRef).getMembers()                    // NOT
```

`materialize` builds the complete type graph for every member of the declaring file — ~3 448 of them
in the wx fixture — before the loop yields its first element.

## Why it was never index-backed

Not an oversight. Three separate reasons compose, and only the fourth is a mistake.

1. **The global lookup *is* index-backed, and deliberately so.** BUG-395 (`307d6e54`, 2026-08-04)
   states it: "`LuaTypeManager.resolveGlobal` looks a name up in `LuaGlobalAssignmentIndex` under
   allScope — the point being that library files are outside projectScope". Finding the global was
   the part that needed an index, and it got one.
2. **The member index answers the opposite question.** `LuaMemberFieldIndex` predates BUG-395 by six
   weeks (`6909b9df`, NAV-12, 2026-06-24) and was built for **navigation**: given a dotted name
   `a.b`, find its declaration. Its key is therefore the *whole* dotted name and its value is
   explicitly unused (`result[it] = ""`, `LuaMemberFieldIndex.kt:71`). Completion needs the inverse —
   given a receiver, list its members — which that key shape answers only by scanning every key in
   the index. A key-direction mismatch, not a missing index.
3. **It would not have sufficed anyway.** That indexer walks `LuaAssignmentStatement` only
   (`:68-72`), so `wx.wxID_ANY = nil` is indexed but `function wx.wxFrame() end` is not — and
   functions are the bulk of the surface (10 084 methods + ~1 000 constructors + 139 free functions
   against 3 448 constants).
4. **Materializing was free at every scale that existed.** It is also the route that yields the
   *types* the lookup renders, so it was the obvious one. The bundled stdlib stubs are a few KiB;
   the whole `runtime/` tree is 424 KiB across 78 files; the largest fetched library, `love2d`, is a
   97 KiB tarball. Nothing had ever been 230 KiB in one declaring file. The design was correct for
   its inputs and nobody bounded it — TARGET-10 is the first input that makes it matter.

## A second unindexed receiver→members path, inside `materialize`

Raised by the BUG-423/424/425/426/427 session's read-only index audit; **verified here** before
folding in.

`LuaTypeManagerImpl.collectMethodMembers:420` fetches **every key in the project**, then string-matches
each one against the receiver:

```kotlin
val allKeys = StubIndex.getInstance().getAllKeys(LuaGlobalDeclarationIndex.KEY, project)
addMethodsOf(MethodScan(className, className, null), allKeys, membersMap)
decls.forEach { … addMethodsOf(MethodScan(className, localName, decl.containingFile), allKeys, …) }
```

`allKeys` is fetched **per `collectMethodMembers` call**, i.e. per class materialized — so the wx
fixture pays a ~10 000-key scan per class, not once. It is the same key-direction mismatch as the
namespace path, on the class side, and it sits *inside* `materialize`.

**This changes DR-01.** A three-way split (`resolveGlobal` / `materialize` / `getMembers`) attributes
it to graph construction and hides it. A fourth bucket is required. It also changes the fix: a
"stream cheap names first" pass fixes the namespace caret only — `f:` on a wx class still pays the
scan.

### …and the index already supports the query it is brute-forcing

`LuaFuncStubElementType.indexStub:69-75` sinks **two** keys per dotted declaration:

```kotlin
sink.occurrence(LuaGlobalDeclarationIndex.KEY, it)                       // "wx.wxFrame"
if (it.contains('.')) {
    sink.occurrence(LuaGlobalDeclarationIndex.KEY, it.substringBefore('.'))   // "wx"
}
```

So `LuaGlobalDeclarationIndex` is **already receiver-keyed**, and
`StubIndex.getElements(KEY, "wx", project, scope, LuaFuncDecl::class.java)` returns every
`function wx.*` declaration directly. The `getAllKeys` scan is not merely slow — it is emulating by
brute force a lookup the index answers in one query.

**That shrinks the fix considerably**, and reorders it:

1. **Replace the scan with the query it emulates** (`collectMethodMembers`). No new index, no new
   emission machinery, no intended behaviour change — the same elements, found directly.
2. **Then re-measure.** Fix A's new index shrinks to covering *assignments* only
   (`wx.wxID_ANY = nil`), since `LuaMemberFieldIndex` is qualified-name-keyed and functions are
   already handled by (1).
3. **Only then decide whether the two-phase emit is needed at all.** It is the most invasive part of
   this plan and may be unnecessary once the two scans are gone. Do not build it before (2) is
   measured.

### Two more instances, found by auditing every `getAllKeys` site

The handoff's audit named one. Scanning all nine `getAllKeys`/`processAllKeys` call sites in
`src/main` finds **two more of the same shape**, both on the LuaCATS `@class` path — which matters
disproportionately here, because a definition library is essentially nothing *but* `@class`
declarations.

| Site | Shape | Verdict |
| :-- | :-- | :-- |
| `LuaTypeManagerImpl:328-332` (`materializeUnhostedClass`) | **second** `getAllKeys(LuaGlobalDeclarationIndex.KEY)` → `addMethodsOf` | **Same defect**, not in the handoff. Fixed by the same receiver-keyed `getElements`. This is the `---@class` materialization path, so for wx it is likely to dominate `:420`. |
| `LuaTypeManagerImpl:337-348` (`catsClassTags`) | `getContainingFiles(LuaCatsTypeNameIndex.KEY, name)` ✓ then `PsiTreeUtil.findChildrenOfType(file, LuaCatsClassTag)` over the **whole file**, filtering by name | **Narrower but real.** Index-narrowed to the right files, then linear within them. A 230 KiB `wx.lua` with hundreds of `@class` tags pays a full-file PSI walk per class resolved. |
| `LuaCatsTypeNavigation:32` | `processAllKeys` | **Legitimate** — Go-to-Class name enumeration genuinely needs every name. Its `processElements` is properly keyed (`getContainingFiles(KEY, name, scope)`). Not a gap; recorded so nobody "fixes" it. |
| `GlobalSymbolRankingService:51-72` | `getAllKeys` ×2 | **Already addressed** — the comment records it was scanned per completion invocation and is now cached (§2.5.5). Precedent for this fix, and worth reading before repeating the approach. |
| `LuaGotoSymbolContributor`, `LuaDocSearchEverywhereContributor`, `LuaHierarchyUtil` | enumeration APIs | **Legitimate** — Search Everywhere, Goto Symbol and hierarchy all need the full key set by definition. |

So there are **two** `addMethodsOf` feeds to fix (`:328` and `:421/424`), not one, and a third,
different narrowing to consider in `catsClassTags`. DR-01's fourth bucket should distinguish them.

## Constraint inherited from BUG-395 — do not repeat its reverted experiment

That commit records: "Deliberately not wired into `visitNameRef`, which would type free globals for
the whole engine: that was tried and reverted, because handing the checker types it never had turns
previously-unchecked calls into checked ones and regressed four suites at once. Filed as BUG-397."

Any fast path added here must stay confined to **completion presentation**. It must not become a
source of types for the checker, or it reproduces exactly that regression — now against ~10 000
wxLua contracts rather than four suites.

## Fix direction

Two independent pieces. The first is the defect; the second is what makes the first possible.

**A. A receiver-keyed member-name index.** A sibling of the existing three
(`LuaMemberFieldIndex` / `LuaGlobalAssignmentIndex` / `LuaGlobalDeclarationIndex`), keyed on the
**receiver** (`wx`) with the member name as the value, and indexing **both** dotted
`LuaAssignmentStatement` targets and dotted function definitions — closing gap 3 above. Answers
"members of `wx`" in one stub-level lookup, no type graph.

**B. Emit index-backed names before materializing.** `crossFileGlobalMembers` becomes two phases on
the existing streaming loop: add the indexed names immediately, then materialize and add the typed
members. First result in milliseconds; exhaustive result still ~13 s, which is the correct shape.

**Open, and a DR rather than an assertion — how the two phases combine without duplicates.**
`CompletionResultSet` does not dedup by lookup string, and a name emitted cheaply then again with a
type would appear twice. Candidates, to be decided by measurement, not by argument:

- track the cheaply-emitted names and have the typed pass skip them (simplest; those rows never gain
  type text);
- give the cheap elements a `LookupElement` subclass whose `renderElement` resolves that member's
  type on demand — `renderElement` is called per *visible row*, so only ~15 rows pay. Requires
  knowing whether a single member's type can be resolved without materializing the whole receiver,
  which is **not** established;
- emit cheap names only above a size threshold, so small libraries keep today's exact behaviour.

## De-risking, before implementing

| ID | Action | Why |
| :-- | :-- | :-- |
| BUG-429-DR-01 | Instrument `crossFileGlobalMembers` with **four** buckets: `resolveGlobal`, `collectMethodMembers`'s key scan, the rest of `materialize`, `getMembers` | The fix targets whichever dominates. The fourth bucket exists because the class-side scan is inside `materialize` and a three-way split hides it |
| BUG-429-DR-01b | Replace `collectMethodMembers`'s `getAllKeys` scan with the receiver-keyed `getElements` the index already supports, and re-measure before building anything else | May remove most of the cost on its own, and would make the two-phase emit unnecessary |
| BUG-429-DR-02 | Determine whether one member's type can be resolved without materializing the receiver | Decides which duplicate-handling option above is available at all |
| BUG-429-DR-03 | Confirm the cost is per-declaring-file, not whole-tree | If whole-tree, an index-first pass still fixes first-result but the exhaustive number will not shrink; TARGET-10's earlier size analysis assumed per-file and never separated them |

## Benchmarking hazard

`LuaGlobalAssignmentIndex.getVersion()` is now **2** (was 1, bumped by BUG-427). The first run after
pulling forces a full reindex — do not take a timing across that boundary. Confirmed at
`LuaGlobalAssignmentIndex.kt:54`.

`doResolveGlobal` also changed under this analysis (`426ac162`/`a40a3e19`): it is now two phases,
`typeOfGlobalIn(projectScope)` then `typeOfGlobalIn(allScope)`, so that a project's own
`assert = require("luassert")` beats a bundled stub once bare `function f() end` became indexable.
For a library global like `wx` projectScope misses and allScope hits, so behaviour is preserved —
but it is two index queries now, not one, and BUG-395's quoted rationale describes the reason, not
the current shape. Re-read `LuaTypeManagerImpl.kt:140-152` before instrumenting.

## Not a resolution hole — do not "fix" it as one

`LuaMemberFieldIndex` indexing only `LuaAssignmentStatement` is real (reason 3 above) but it is
**redundancy asymmetry, not a lookup gap**. `LuaNameReference:95-111` runs the
`LuaGlobalDeclarationIndex` qualified-name lookup — which covers `function wx.wxFrame() end` — and
`LuaMemberFieldNavigation` alongside it, which covers `wx.wxField = value`. Between the two,
lookup-by-qualified-name is complete. Fix A should still index both forms for the *receiver*
direction; just do not expect a navigation bug to disappear with it, and do not add a
navigation-regression test that would only ever have passed.

## The same missing direction, elsewhere

Metamethods have no index and no cross-file path at all — only the per-file graph from
`setmetatable`. Recorded as a known limitation in BUG-426, and irrelevant to wxLua (independently
confirmed: `genwxbind.lua` emits no metamethod mapping). Noted because it is the third instance of
this shape and a future fix should consider them together.

## Verification

- A test registering a ~250 KiB annotated root asserting **time-to-first-element** under a stated
  budget, written to fail today. Time-to-first-element, not time-to-completion — the whole point.
- The exhaustive set must be unchanged: same names, same types, same order-independent content as
  today, on a small fixture where both paths are fast.
- No duplicate lookup strings for any receiver.
- The four corpus baselines must not move. If any type error appears or disappears, the fast path
  has leaked into the checker and this is BUG-397 again.
- `LuaLibraryGlobalCompletionTest` and `LuaLibraryModuleResolutionTest` stay green — they are the
  regressions that BUG-394/395/398/399 left behind and they cover the path being changed.
