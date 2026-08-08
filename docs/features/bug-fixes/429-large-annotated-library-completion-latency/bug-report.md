---
id: "BUG-429"
title: "Member completion on a library global blocks on a full type materialization, so time-to-first-result equals time-to-exhaustive-result"
type: "bug"
status: "todo"
parent_id: "BUG"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-429: First completion result waits for the exhaustive one

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

## The critical path

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
| BUG-429-DR-01 | Instrument `crossFileGlobalMembers`: split the 12.9 s between `resolveGlobal`, `materialize` and `getMembers` | The fix targets whichever dominates; "materialize" is inferred from the call shape, not measured |
| BUG-429-DR-02 | Determine whether one member's type can be resolved without materializing the receiver | Decides which duplicate-handling option above is available at all |
| BUG-429-DR-03 | Confirm the cost is per-declaring-file, not whole-tree | If whole-tree, an index-first pass still fixes first-result but the exhaustive number will not shrink; TARGET-10's earlier size analysis assumed per-file and never separated them |

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
