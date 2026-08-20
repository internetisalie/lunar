---
id: "BUG-436"
title: "Five whole-project indexers accept only `*.lua`, so `.rockspec` / `.luacheckrc` / `.busted` declarations are invisible"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-436: the Lua indexers re-state the file-type registration and get it wrong

`LuaFileType` is registered for **four** things (`plugin.xml:99-100`):

```xml
extensions="lua;rockspec"
fileNames=".luacheckrc;.busted"
```

Every whole-project index re-states that as `file.extension == "lua"`, which is the first
half of the first half. Anything declared in a `.rockspec`, a `.luacheckrc` or a `.busted`
file is therefore absent from the index — not stale, **absent**, and absent in the direction
no gate looks.

## Sites

| file | line | consequence |
| :-- | :-- | :-- |
| `lang/indexing/LuaGlobalAssignmentIndex.kt` | `:63` | **the load-bearing one** — completion cannot see a global declared in a `.rockspec` at all, because this index selects the candidate declaring file |
| `lang/indexing/LuaCatsTypeNameIndex.kt` | `:58` | a bare `---@class` in a `.rockspec` is invisible to Go to Class / Go to Symbol |
| `lang/indexing/LuaMemberFieldIndex.kt` | `:52` | `@field` members declared in those files are missing |
| `lang/indexing/LuaDescriptionIndex.kt` | `:48` | descriptions missing for the same |
| `lang/indexing/LuaFileBindingsIndex.kt` | `:87` | ANDs the extension test **on top of** a correct `LuaFileInputFilter` — the file-type check is already there and is then narrowed away |
| `platform/target/RuntimeLibraryProvider.kt` | `:43` | not an index; filters library files the same way |

`LuaReceiverMemberIndex` had the same defect and is **already fixed** (`fcce5966`) — it now
uses `DefaultFileTypeSpecificInputFilter(LuaFileType)`, which is the shape to copy.

## Measured

Found during COMP-09 Phase 3 review. A `Widget` receiver declaring one member in each of
`.lua`, `.rockspec`, `.luacheckrc` and `.busted`, through the `@class` door:

```
with the narrow filter:  [fromLua, same]
with the file-type filter: [fromBusted, fromLua, fromLuacheckrc, fromRockspec, same]
```

## The fix, and the platform trap in it

Replace each with `DefaultFileTypeSpecificInputFilter(LuaFileType)` and **bump that index's
`getVersion()`** — the index content changes and a persisted index would otherwise mask both
the defect and the fix.

**Instantiate it; never subclass it.** `RequiredIndexesEvaluator.toHint` converts the filter
into a real file-type predicate only when
`filter.javaClass == DefaultFileTypeSpecificInputFilter::class.java`. A subclass silently
degrades to accept-everything — a performance regression that no test would show.

## Fixed 2026-08-20 — and two things the report did not predict

All six sites closed. Red before: the `@class` door returned `[FromLua]` and
`LuaGlobalAssignmentIndex` listed `[w.lua]` where four declaring files exist. Gated by
`LuaFileTypeRegistrationIndexTest`, which asserts **exact sets** for the reason this report gives —
a subset defect is invisible to every superset-shaped instrument in the area.

### 1. Two latent `notNullChild` defects, surfaced by the version bumps

`LuaDescriptionIndex` read `owner.funcName.text` / `owner.nameRef.text`, and `LuaFileBindingsIndex`
mapped over `it.nameRef.identifier`. Those are Grammar-Kit `@NotNull` getters, and `notNullChild`
does **not** return null on a missing child — it calls `LOG.error` (`PsiElementBase:293`), a reported
IDE exception in production. Latent until the `getVersion` bumps forced a rebuild; three tests went
red, including `TestLuaTypeEnginePhase1.testComplexPhase1File` (its fixture writes
`local function repeat(count)`, and `repeat` is a keyword, so the parse yields a `LuaLocalFuncDecl`
with no name node at all).

**This is inside the fix's blast radius, not scope creep**: widening to `.rockspec` / `.luacheckrc` /
`.busted` makes malformed input *more* likely, not less. Same defect class as [[BUG-441]]'s.

### 2. "Replace each with `DefaultFileTypeSpecificInputFilter`" is wrong for `LuaFileBindingsIndex`

The first cut followed that instruction literally and replaced the whole filter, discarding
`LuaFileInputFilter`'s `url.startsWith("file:")` guard with a confident note calling it "anomalous,
not protective". **It is protective.** Dropping it admits the in-memory `temp://` files a
`BasePlatformTestCase` fixture lives on, which changed what the index binds and cost
`LuaParameterInlayHintsTest.testStdlibAssertDoesNotShowHints` its stdlib resolution for `assert`.

Only the ANDed extension test should go there; the guard stays. That index keeps its non-hinted
filter as a result — adding the `toHint` fast path is a separate change needing its own measurement.

### Mutation proof — 1 of 3 CAUGHT, and the two survivors are recorded, not hidden

| mutation | result |
| :-- | :-- |
| restore the narrow filter on `LuaGlobalAssignmentIndex` | **CAUGHT** — `testGlobalAssignmentsAreIndexedInEveryRegistration` |
| restore it on `LuaCatsTypeNameIndex` | **SURVIVED at first** — the assertion used `getAllKeys`, which is not really project-scoped in a shared test JVM (`MemberEnumerationWorkBoundGateTest`'s KDoc records the same trap; DR-11 measured 4 145 stub keys in *both* arms of a comparison). Rewritten to `getContainingFiles` and now CAUGHT |
| restore the ANDed narrowing on `LuaFileBindingsIndex` | **SURVIVED — still uncovered** |

**The `LuaFileBindingsIndex` gap is real and open.** Three fixtures were tried; all three asserted
nothing, because that index records neither a global nor a bare `local` for the shapes used —
`getFileData` came back empty for the `.lua` control too, so the assertion could not distinguish the
fix from its absence. It was removed rather than left looking like coverage, with the reason in the
test file. Closing it needs the fixture that actually produces a record, most likely a `require`
across files, which is what the index exists to resolve.

The `getAllKeys` survivor is the more instructive one: it is a test that could not fail, written into
a method whose whole purpose was to catch a subset defect, in a file whose KDoc argues for exact-set
assertions. Only the mutation found it.

### Corpus

4/4 green with exactly one `IMPROVED` line, and it is this fix working:
`inspection.LuaUndeclaredVariable: 615 → 608` on luacheck — seven false "undeclared variable" reports
gone, because globals declared in that project's `.luacheckrc` files are now indexed. Baseline
re-recorded. (Read the `IMPROVED` lines: the ratchet only *fails* on regressions —
`CorpusMetrics.kt:283-284`, where MORE hits is the regression.)

## Why this went unnoticed

Every instrument in this area is built to catch a **superset**: the COMP-09 golden diffs for
added rows, the corpus ratchet stops on movement, and COMP-09-06's acceptance is "if any
baseline moves, enumeration has become a type source: stop". A **subset** — members that
quietly stop existing — passes all of them. Worth remembering when designing the gate for
this fix: assert exact sets on a fixture that declares members in all four file kinds, and
mutation-prove it by restoring the narrow filter.

## Scope

Deliberately **not** fixed inside COMP-09 Phase 3's remediation: five indexers plus a
provider is its own change, on a defect that predates the feature. Related: [[COMP-09]]
Phase 3, and `fcce5966` for the worked example.
