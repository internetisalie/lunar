---
id: "COMP-09-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "COMP-09"
folders:
  - "[[features/completion/09-member-enumeration/requirements|requirements]]"
---

# Technical Design: COMP-09 — Member Enumeration

## 1. What the de-risking measured, and what it overturned

Run 2026-08-07 on gce-builder (`CompNineDrSpikeTest`). Two results contradict what BUG-429 and an
earlier revision of COMP-09-01 asserted. Both assertions were read off call shapes; neither survived
being run.

### 1.1 The critical path is NOT `materialize` (DR-02)

```
resolveGlobal   9 568 ms      <- 99.9 %
materialize        10 ms
getMembers          0 ms      (3 700 members)
```

BUG-429 stated that `LuaGraphType.materialize(global).getMembers()` "builds the complete type graph
for every member … before the loop yields its first element". **It does not.** Materialization and
enumeration together are 10 ms. The entire cost is `LuaTypeManager.resolveGlobal`, and inside it:

```
resolveGlobal → doResolveGlobal → typeOfGlobalIn(scope) → globalTypeIn(file)
                                                          → LuaTypesSnapshot.forFile(declaringFile)
```

`LuaTypesSnapshot.forFile` builds the **entire per-file type graph for the 242 KiB declaring
library file** in order to answer one question: what type does it give the global `wx`.

**This reframes the capability.** The missing thing is not only "enumerate members from an index" —
enumeration is already fast. It is *"answer a symbol's type without building its declaring file's
whole graph"*. The two `getAllKeys` scans are real (§1.3) but they are inside the 10 ms, and fixing
them would not have moved the headline number at all.

### 1.2 The cost is per-keystroke, not per-session (DR-02b/c)

| | ms |
| :-- | --: |
| cold | 800 |
| warm, no PSI change | **0** |
| after one keystroke **in the consumer file** | **608** |

(Constants-only fixture, hence 800 ms not 9 568 ms; the **ratio** is the finding — 76 % repaid.)

`typeCache` (`LuaTypeManagerImpl:34-44`) and the per-file snapshot (`LuaTypes:215-222`) both depend
on project-wide `PsiModificationTracker`, so an edit anywhere invalidates the *library's* snapshot.
Typing `wx.wxF` therefore pays the full graph build on each character. The NFR's per-keystroke clause
was right, but the mechanism is cache invalidation forcing recomputation — not, as it stated,
cancellation and restart.

### 1.3 The receiver key is dot-only (DR-06)

Raised in review against COMP-09-01's claim that swapping `collectMethodMembers`' scan for
`getElements(KEY, receiver)` is "a strict simplification". Measured:

```
keys under ColonHost : [ColonHost, ColonHost.staticDot, ColonHost:dotless, ColonHost:scale]
getElements(KEY, "ColonHost") -> [ColonHost.staticDot]        <- dot form only
getElements(KEY, "wx")        -> [wx.wxFileExists, wx.wxFrame]
```

`LuaFuncStubElementType:69-75` sinks a receiver key only when the name contains `'.'`, while
`memberNameOf:466` matches `receiver.` **and** `receiver:`. So the swap would silently drop every
colon-declared method — a correctness regression, not a simplification. `function C:m()` is the
dominant idiomatic form for class methods.

**And one step further than the review went:** the `ColonHost` receiver key exists *only because
`ColonHost.staticDot` happens to use the dot form*. A class whose members are all colon-declared has
**no receiver key at all**. The keying is incidental to member style, not a property of the class.

### 1.4 DR-01 is incomplete — recorded, not glossed

The golden enumeration captured `wx` (3 members) but `resolveGlobal` returns **null** for `wxFrame`
and `ColonHost`: both are `local` + `---@class`, so they are types, not globals, and need
`resolveType`. The harness used one entry point for two questions. DR-01 must be redone across
**both** before any code changes, because it is the guard for COMP-09-07 — and per §1.3 its fixture
must contain colon-declared methods or it will certify their loss as behaviour-preserving.

## 2. Consequences for the plan

| Was | Now |
| :-- | :-- |
| "Replace the two `getAllKeys` scans" is the first increment | Those scans are inside a 10 ms region. Real, but not the headline. Demoted to a work-bound fix (COMP-09-09), not a latency one |
| Fix direction is index-backed member enumeration | Fix direction is **avoiding the declaring file's whole-graph build** to answer one symbol's type. Enumeration is already fast |
| Swapping the scan is a strict simplification | It is a correctness regression until `indexStub` also sinks `substringBefore(':')` — a **stub index format change**: version bump, full reindex |
| Per-keystroke concern justified by cancel/restart | Justified by measured cache invalidation: 76 % of the cost repaid on an unrelated edit |

## 3. Open questions this design cannot yet answer

Deliberately unresolved — each needs a measurement that has not been taken, and inventing answers
here is what produced §1.1 and §1.3.

1. **Can a global's type be answered without `LuaTypesSnapshot.forFile`?** `LuaGlobalAssignmentIndex`
   already names the declaring *file*. What is missing is its type without the graph. Whether a stub
   can carry enough is unknown.
2. **Is the `@class` receiver path also dominated by `forFile`, or by the `getAllKeys` scans?**
   DR-01's harness could not reach it (§1.4), so the `@class` path is **unmeasured**.
3. **Does narrowing invalidation help more than indexing?** If the snapshot survived unrelated edits,
   the cost would be once per session per file. That may be a smaller change than a new index — and
   it is not obviously safe.
4. **Where does incremental yield apply, given enumeration is 10 ms?** Possibly nowhere: if the cost
   moves out of the critical path entirely, streaming solves a problem that no longer exists.

## 4. Requirement coverage

Deferred until §3 is answered. Writing a coverage table now would map requirements onto components
chosen for reasons §1 just refuted.
