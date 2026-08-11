---
id: "TYPE-11-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TYPE-11"
folders:
  - "[[features/type/11-library-snapshot-invalidation/requirements|requirements]]"
---

# Technical Design: TYPE-11 — Library Snapshot Invalidation

## 1. What the de-risking measured, and what it overturned

All figures below were produced on gce-builder (libvirt `debian13`) on **2026-08-09** by the harnesses
in `src/test/kotlin/net/internetisalie/lunar/type/`, against a temporary production edit that was
**reverted before commit** (see `risks-and-gaps.md` → "What the measurement ran against"). Nothing in
this section is read off a call shape; three of the five findings contradict what `requirements.md`
asserted from reading.

### 1.1 The residual is REAL, and the simple version of this feature is unsound

`requirements.md` says "Nothing above should be built until that is measured." It was. The naive
form — pin every provenance-matched library file's snapshot to a generation tracker — was built and
run against the full suite:

```
2563 tests completed, 2 failed, 1 skipped
> Task :test FAILED
BUILD FAILED in 9m 48s
```

Both failures are TYPE-11's own residual fixtures, and both are residual paths `requirements.md`
named:

```
DR-01 path1 before edit: libAlias members = [beforeEdit]
DR-01 path1 after edit:  libAlias members = [beforeEdit]      <- expected [afterEdit]
DR-01 path2 before edit: libHandle members = [beforeEdit]
DR-01 path2 after edit:  libHandle members = [afterEdit, beforeEdit]
```

```
junit.framework.AssertionFailedError: editing the project file must be reflected in the
library global's type expected:<[afterEdit]> but was:<[beforeEdit]>
junit.framework.AssertionFailedError: the removed project method must disappear;
got [afterEdit, beforeEdit]
```

**So blanket pinning ships a stale-type defect and TYPE-11-04 fails.** The design below is therefore
not the one `requirements.md` sketched; it is the alternative that document itself allows —
"leaving cross-file-resolving snapshots on the global tracker" — made exact.

**Second finding, equally load-bearing: the existing suite is not a gate for this.** 2543 pre-existing
tests passed unchanged under the unsound build. ⚠ The corpus half of that claim was **not measured** — the sweep classes never ran (see TC-11). `git status --short
src/test/resources/corpus/` was empty. The only red came from fixtures written for this de-risking.
Any future change in this area that relies on "the suite is green" is relying on nothing.

### 1.2 Residual path 2 exists, but not in the shape `requirements.md` described

`requirements.md` says a project file declaring `function LibClass:helper()` makes a
generation-pinned library snapshot stale, citing `materializeClass` → `collectMethodMembers`. The
first fixture written to that description was **green on `main` and green under pinning** — it could
not fail. Probed:

```
P2 resolveGlobal(LibClass) = []
P2 resolveType(LibClass)   = [beforeEdit]
P2 global='LibClass' in beta.lua: graphType=Table(className=null, localMembers={}, superTypes=[], isExact=false, metamethods=[])
```

With the **unhosted** `---@class` form (`---@class C` over a bare `C = {}` — BUG-400's shape, and
what every bundled stdlib stub uses) the class name never reaches the graph, so no project member is
frozen into any snapshot. `resolveType` does return the project method, but `resolveType` is memoized
in `typeCache`, which depends on project-wide `PsiModificationTracker` (`LuaTypeManagerImpl:35-45`)
and is untouched by this feature.

The **hosted** form does carry it:

```
P2H resolveGlobal(libHandle2) = [beforeEdit]
P2H global='libHandle2' in host2.lua: graphType=Union(types=[Table(className=HostClass,
    localMembers={beforeEdit=…VariableElement@27f2e992}, …), Table(className=null, …)])
```

`beforeEdit` is declared in a **project** file and is sitting inside a **library** file's snapshot
graph. The route is `freeGlobalSeed` → `resolveGlobal` → `tableToLuaType` (`LuaTypes.kt:156-172`,
which merges nominal members for a named class) → `LuaGraphType.fromLuaType`
(`LuaGraphType.kt:251`), not `materializeClass` reaching the snapshot directly.

Measured on the bundled stdlib: 22 `---@class` tags across `runtime/standard/lua-5.4/`, of which
**one** (`local File = {}` in `io.lua`) is hosted. The shape is real but rare in what ships; a fetched
LuaCATS definition library is free to use either.

### 1.3 Provenance works, but not by reference identity, and only through `originalFile`

```
DR-02 target=Target(platform=Standard, version=5.4)
      runtimeRoot=jar:///…/lunar-0.18.0.jar!/runtime/standard/lua-5.4 fileSystem=jar
DR-02 runtimeRoot children = [builtin.lua, coroutine.lua, debug.lua, io.lua, math.lua, os.lua,
      package.lua, string.lua, table.lua, utf8.lua]
DR-02 registered definition roots = [/…/system-test/lunar/definitions/luassert-d3528bb6…]
DR-02 global='wx'          vf=…/luassert-d3528bb6…/wx.lua  fs=file provisioned=true
DR-02 global='projectOnly' vf=/src/projectGlobal.lua       fs=temp provisioned=false
DR-02 copy: virtualFile=null originalFile.virtualFile=…/wx.lua byVirtualFile=false byOriginalFile=true
```

Two traps, both measured:

- **Reference identity is not reliable.** `PsiManager.findFile(vf).virtualFile === vf` is `true` for
  the library file and **`false`** for a project file in the light fixture's `temp` file system —
  `doubleEq` (`equals`) holds in both cases:
  ```
  P2-ID 'wx'          … class=VirtualFileImpl tripleEq=true  doubleEq=true
  P2-ID 'projectOnly' … class=VirtualFileImpl tripleEq=false doubleEq=true
  ```
  TYPE-11-03's wording "matched by `VirtualFile` identity" is therefore only satisfiable as
  **containment by URL**, never as `===`. §3.2 matches on `VirtualFile.url` prefixes.
- **A copy has no `virtualFile` at all** (`virtualFile=null`). Any predicate reading
  `psiFile.virtualFile` misclassifies a completion/intention copy of a library file. §3.2 reads
  `psiFile.originalFile.virtualFile`.

Also recorded: the bundled runtime root arrives over the **`jar://`** file system inside the plugin
jar, while definition libraries arrive over `file://`. Both are handled by URL-prefix containment;
`VfsUtilCore.isAncestor` also works, since it never crosses file systems.

### 1.4 The conditional rule holds, and it pins the files that matter

Under §3's rule (pin only when the recorded source set is entirely inside the provisioned set), both
residual fixtures go green and the pinnability trace shows nothing valuable is excluded:

```
DR-01 path1 after edit: libAlias members  = [afterEdit]
DR-01 path2 after edit: libHandle members = [afterEdit]
DR-01 control after project edit: projectAlias = [after] libOnly = [fromLibrary]
```

```
TYPE11-TRACE file=builtin.lua   provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=coroutine.lua provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=debug.lua     provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=io.lua        provisioned=true pinnable=true sources=1 outside=[]
TYPE11-TRACE file=math.lua      provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=os.lua        provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=package.lua   provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=string.lua    provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=table.lua     provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=utf8.lua      provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=wx.lua        provisioned=true pinnable=true sources=0 outside=[]
```

The full suite under the conditional rule:

```
BUILD SUCCESSFUL in 9m 45s
tests 2564 failures 0 skipped 1
```

⚠ **corrected 2026-08-09**: the corpus sweep was not run for that measurement. `git status --short src/test/resources/corpus/` is **not** evidence: baselines are only rewritten under `-PrecordCorpusBaseline` (`build.gradle.kts:286-288`), so that check is clean whether the sweep passed, regressed, or never ran. **The gate is the sweep itself**: `tooling/gce-builder/gce-builder.sh run "test -PwithCorpus --rerun --no-build-cache"`.

⚠⚠ **corrected again 2026-08-09 (second measurement round)** — the correction above named the **wrong comparator**, and named three classes as sweep-gated that are not:

- `BaselineRatchetTest` never reads a recorded baseline. Its 35 tests build synthetic `CorpusMetrics`
  and write throwaway files into a JUnit `TemporaryFolder` (`BaselineRatchetTest.kt:28`, `:406`, `:416`).
- `BaselineRatchetTest`, `LexerInvariantsTest` and `ParseOracleTest` are **not** behind `-PwithCorpus`.
  `excludeTestsMatching("*Corpus*")` is case-sensitive and does not match the lowercase
  `…lunar.corpus.` package segment — each class says so in its own KDoc
  (`BaselineRatchetTest.kt:19-24`, `LexerInvariantsTest.kt:8-11`, `ParseOracleTest.kt:9-11`), and each
  is deliberately named to run in the routine loop.
- The recorded baselines under `src/test/resources/corpus/` are compared in exactly one place:
  `LuaCorpusSweepTest.sweepAndRatchet` → `CorpusGuards.assertRatchet` (`LuaCorpusSweepTest.kt:97-101`),
  plus `LuaTortureCorpusTest`.

So the only classes whose presence distinguishes a sweep run from a routine one are
**`LuaCorpusSweepTest` (4 tests), `LuaTortureCorpusTest` (1) and `LuaInspectionParityTest` (1)**.
Corroborated by counting: routine 2 564 vs sweep 2 571 is a delta of **7**, not the 63 tests the
`…lunar.corpus.` package contributes in total (ratchet 35, oracle 14, lexer 8, sweep 4, parity 1,
torture 1 — measured, §1.7).

Every bundled stdlib file and DR-04's 123 KiB synthetic library is pinnable. `io.lua` records one
source (its own hosted `File` class) and that source is inside the provisioned set.

⚠ **This trace is what blockers B1 and B4 were raised against**: the rule that produced it pins a
file whose recorded set is empty *because the answer was unknown*, not because there was nothing to
record. §1.8 re-measured the same 11 files under the corrected rule — still 11/11 pinnable — and adds
the two shapes this trace cannot show.

### 1.5 DR-04 — the win is large and the target is not met

`forFile(consumer)`, medians of five, distinct consumer text per sample so the per-file-text
memoization cannot serve a warm answer. **Compare only within a row**: the no-library baseline moved
between 3.2 ms and 10.4 ms across runs on the same machine, so no cross-row ratio is quotable — the
COMP-09 §1.2 rule applies here unchanged.

| build | arm A, no definition library | arm B, 123 KiB definition library |
| :-- | --: | --: |
| unpinned (today's behaviour) | 10 440 µs | **349 700 µs** |
| blanket pin (unsound, §1.1) | 3 175 µs | 16 657 µs |
| §3 conditional rule | 9 655 µs | **32 081 µs** |
| §3 conditional rule, re-run | 8 132 µs | 42 002 µs |

Raw samples for the last two rows:

```
DR-04 arm A … samples(us)=[7528, 9303, 9655, 9940, 14896]  median=9655us
DR-04 arm B … samples(us)=[26651, 31017, 32081, 32830, 858515] median=32081us
DR-04 arm A … samples(us)=[7450, 7997, 8132, 8675, 14476]  median=8132us
DR-04 arm B … samples(us)=[30665, 37554, 42002, 49221, 843603] median=42002us
```

**Direction: the 123 KiB library stops costing hundreds of milliseconds per keystroke.** What is
*not* achieved is `requirements.md`'s DR-04 success criterion of "landing near the 9 ms no-library
baseline": arm B lands at 3–5× arm A in the same run. `requirements.md` predicted this outcome and
named the reason — every free global still re-runs `resolveGlobal` + `graphTypeToLuaType`, which
builds a fresh `visited` map per call and walks the library table's full member set. That residual
cost is **out of scope here** and is filed as a follow-up in `risks-and-gaps.md`.

### 1.6 DR-05 — the dumb-mode staleness class did NOT reproduce

`requirements.md` asserts that a snapshot built while `DumbService.isDumb` bakes in `resolveGlobal`'s
nulls and that "under a generation dependency it is sticky until the next roots/target tick". The
first half is true; the second is not, in this harness:

```
DR-05 while dumb: libDumb graph type = Undefined psi=488188368 stamp=0 valid=true
DR-05 after dumb: psi=488188368 stamp=1 sameInstance=true oldValid=true oldStamp=1
                  graph=Table(className=null, localMembers={fromLibrary=…}, …)
DR-05 after leaving dumb mode: libDumb members = [fromLibrary]
```

The nulls **are** baked in while dumb (`Undefined`). They do not survive, and the mechanism is not the
churn tracker: the same `PsiFile` instance's `modificationStamp` moves **0 → 1** when the fixture
leaves dumb mode, and `forFile` passes `psiFile` itself as a dependency —
`CachedValueBase` converts a `PsiElement` dependency to `containingFile.modificationStamp`. Two
mutations confirm it (§ `risks-and-gaps.md` mutation table): removing the `!isDumb` term left the test
green, and replacing the generation tracker with `ModificationTracker.NEVER_CHANGED` **also** left it
green.

**Consequence for this design: TYPE-11-05's guard is retained (§3.4), and the DR-05 harness above is
explicitly NOT a gate** — two mutations left it green, so calling it one would be the exact "test that
cannot fail" this plan exists to avoid. Whether the stamp move is platform behaviour or a
`DumbModeTestUtils` artifact remains a tracked de-risking task (`risks-and-gaps.md` → DR-06).

⚠ **What does not follow — and was wrongly allowed to, until Step 9 blocker B5.** "The staleness does
not reproduce" is a fact about the *outcome*. It says nothing about the *decision*, which is a pure
predicate over a file and a frame and is directly assertable: under dumb mode the recorded frame is
empty, an empty frame on a provisioned file clears §3.3 steps 2–7, and so step 1 is the only thing
that can reject it. `isPinnable(libraryFile, SourceFrame())` is `false` with step 1 and `true`
without it. TYPE-11-05 therefore **does** get a gate, on the decision rather than the outcome; see
§1.9 and TC-16.

### 1.7 The premise behind the recorder, examined by execution (2026-08-09, second round)

The recorder (§2.1, §3.1, §3.5, §3.6) is the most complex thing in this design, and §3.5 justifies its
widest rule — "report **every** file `typeOfGlobalIn` visits" — by naming **one** constraint: that
`doResolveGlobal` searches project scope first (BUG-427, `LuaTypeManagerImpl:162-163`). A design whose
most complex part exists to survive one removable constraint is exactly the "unexamined premise" the
planning skill warns about, and §9 did not list removing it. So it was removed and measured.

**The scaffold** (reverted; itemised in `risks-and-gaps.md` → "What the measurement ran against"):
when the *resolving context file* is plugin-provisioned, `doResolveGlobal` skips the project-scope
pass entirely and searches a provisioned-only candidate set —
`FileBasedIndex.getContainingFiles(LuaGlobalAssignmentIndex.KEY, name, allScope)` filtered to files
under a provenance root. Paired with **blanket** pinning (provenance + `!isDumb`, no source recording,
no conditional): the design of §3.3 minus §3.1/§3.5/§3.6.

```
> Task :test
TypeElevenDr01ResidualTest > testAProjectDeclaredMethodOnAStubClassTracksThatProjectFile FAILED
    junit.framework.AssertionFailedError at TypeElevenDr01ResidualTest.kt:139
TypeElevenDr01ResidualTest > testALibraryGlobalTypedFromAProjectGlobalTracksThatProjectFile FAILED
    junit.framework.AssertionFailedError at TypeElevenDr01ResidualTest.kt:88

2571 tests completed, 2 failed, 1 skipped
> Task :test FAILED
BUILD FAILED in 19m 50s
```

```
DR-01 path1 before edit: libAlias members = []
  junit.framework.AssertionFailedError: the library global must take the project declaration's
  members expected:<[beforeEdit]> but was:<[]>

DR-01 path2 before edit: libHandle members = [beforeEdit]
DR-01 path2 after edit:  libHandle members = [afterEdit, beforeEdit]
  junit.framework.AssertionFailedError: the removed project method must disappear;
  got [afterEdit, beforeEdit]

DR-01 control after project edit: projectAlias = [after] libOnly = [fromLibrary]   (green)
```

**The recorder survives. Three things were established, none of them by reading:**

1. **The restriction is live, and it deletes the behaviour rather than fixing it.** Residual path 1's
   *pre-condition* moved from `[beforeEdit]` (on `main`, and under blanket pinning) to `[]`: a library
   file's free global that only a project file declares now resolves to nothing at all. That is the
   scaffold's mutation proof — and it is also the reason the restriction is not shippable on its own
   terms. It is a user-visible resolution change, well outside a cache-lifetime feature's remit.
2. **Residual path 2 is untouched, because it never goes through `typeOfGlobalIn`.** `[afterEdit,
   beforeEdit]` is the identical blanket-pin symptom. The project method reaches the library file's
   snapshot through the **nominal** channel: `freeGlobalSeed` → `resolveGlobal("HostGlobal")` — which
   the restriction *allows*, both files being provisioned — → `globalTypeIn` → `graphTypeToLuaType`
   → `tableToLuaType` (`LuaTypes.kt:156-172`) → `resolveType("HostClass")` → `materializeClass` →
   `collectMethodMembers`, which reads `StubIndex.getAllKeys(LuaGlobalDeclarationIndex.KEY, project)`
   **project-wide with no scope argument at all** (`LuaTypeManagerImpl:427-432`), and is then frozen
   into the graph by `LuaGraphType.fromLuaType` (`LuaGraphType.kt:251`). No restriction on global
   *scope* can reach a channel that takes no scope.
3. **Nothing that should have survived broke.** No BUG-427 fixture failed — a project file
   reassigning a stdlib global is project-context resolution, which the restriction does not touch.
   Library→library resolution still works: `libHandle = HostGlobal` across two provisioned files gave
   `[beforeEdit]` before the edit.

**Consequence for §3.5.** The over-approximation rule stays, but its stated justification was too
narrow. The recorder is not there to survive BUG-427's ordering; BUG-427's ordering could be deleted
tomorrow and blanket pinning would still be unsound. It is there to survive **project-wide nominal
class materialization**, which is why the `materializeClass` (`:262`) and `addMethodsOf` (`:467`)
report sites — not the `typeOfGlobalIn` one — are the load-bearing rows of that table.

Corpus results in the same run, all green under a build that demonstrably serves stale types:

```
BaselineRatchetTest      tests=35 failures=0      LuaCorpusSweepTest     tests=4  failures=0
LexerInvariantsTest      tests=8  failures=0      LuaInspectionParityTest tests=1 failures=0
ParseOracleTest          tests=14 failures=0      LuaTortureCorpusTest   tests=1  failures=0
```

DR-04 in the same run (medians of five, compare only within the row): arm A 2 992 µs, arm B
13 888 µs — recorded, not a gate (§1.5's rule).

### 1.8 The recorder under-records in two directions, and both ship stale types (2026-08-10, third round)

Step 9 raised two blockers against §3 as written above. Both are **absence** defects: an
under-recorded source set makes a file look *more* pinnable than it is. Both were reproduced against
`main` @ `07a8fa44` with a scaffold that is the design of §3.1–§3.6 in full (recorder, provenance,
conditional decision, six report sites, `sourceCache` replay) and a mode switch; the scaffold is
itemised in `risks-and-gaps.md` and was reverted before commit.

#### B1 — a resolution that answers nothing reports no source

`TypeElevenDr11LateDeclarationTest`: library `alpha.lua` is `libAlias = sharedByProject`, and **no
file declares `sharedByProject` when the snapshot is built**. `resolveGlobal` returns null, so
`typeOfGlobalIn` visits nothing and reports nothing; §3.1/§3.5 record an **empty** set, §3.3 step 3
(`sources.any { !provisioned }`) is vacuously satisfied, and the file is pinned. The user then writes
the declaration:

```
TYPE11-REVIEW file=alpha.lua provisioned=true pinnable=true sources=0 outside=[] misses=[] mode=cond
DR-11 before: libAlias = [] declaring = []
DR-11 after:  libAlias = [] declaring = [shared.lua] roots 3 -> 3

TypeElevenDr11LateDeclarationTest > testADeclarationWrittenAfterTheLibrarySnapshotWasBuiltStillReachesIt FAILED
junit.framework.AssertionFailedError: a project declaration written AFTER the library snapshot was
built must still reach it expected:<[afterDeclared]> but was:<[]>
```

`roots 3 -> 3` is the fixture refusing to be healed by a roots tick, so the verdict is about the
recorded-nothing case and nothing else. **§3.3's sentence "which the global tracker guarantees
happens" was false**: `PsiModificationTracker.MODIFICATION_COUNT` is precisely the dependency the pin
removes, so a pinned file is never re-judged. §3.4 identified this inversion for dumb mode ("records
zero sources — which would otherwise make it maximally pinnable, precisely when it is least
trustworthy") and did not generalise it to a failed smart-mode resolution.

#### B4 — a nested `forFile` served warm contributes no sources

`TypeElevenDr12WarmInnerSnapshotTest`. §3.6 argues correctly that without replay the feature is
unsound because `resolveGlobal` is memoized project-wide. `LuaTypesSnapshot.forFile` is memoized too
and had **no** analogous replay. The interleaving needs no roots tick, only an ordering inside one
modification epoch: `b.lua` (library) is `bGlobal = projectSeed` with `projectSeed` declared in
project file `p.lua`; something asks for another global `b.lua` declares, so `forFile(b.lua)` is
warm; then `a.lua` (library, `aAlias = bGlobal`) is built.

```
TYPE11-REVIEW file=b.lua provisioned=true pinnable=false sources=1 outside=[p.lua] misses=[] warm=[] mode=cond
DR-12 warm-up: bOther = [fromB]
TYPE11-REVIEW file=a.lua provisioned=true pinnable=true  sources=1 outside=[]      misses=[] warm=[b.lua] mode=cond
DR-12 before edit: aAlias = [beforeEdit]
DR-12 after edit:  aAlias = [beforeEdit]

TypeElevenDr12WarmInnerSnapshotTest > testALibraryWhoseInnerLibrarySnapshotWasServedWarmStillTracksTheProjectFile FAILED
junit.framework.AssertionFailedError: editing the project file must be reflected through the
two-library chain, even though the inner library snapshot was served warm when the outer one was
built expected:<[afterEdit]> but was:<[beforeEdit]>
```

`b.lua` is correctly judged unpinnable (`outside=[p.lua]`) and `a.lua` is pinned on the same tick
with `sources=1 outside=[]`, while transitively embedding `p.lua`'s type through `freeGlobalSeed`.
The `warm=[b.lua]` column names the cause: `forFile(b.lua)` was a cache hit, so no frame was opened.

#### The rule, and what it costs

The unifying principle is that the recorder must distinguish **"no sources"** from **"sources
unknown"**, and only the former may be pinned: *an incomplete recording is not pinnable*. The
scaffold records a verdict under every candidate rule at once, so one run prices all of them. Every
file below is provisioned; `misses` is every resolution that answered nothing, `warm` every nested
`forFile` served from cache.

```
REVIEW-COST mode=guarded decisions=11 provisioned=11
file=builtin.lua   sources=0 outside=0 misses=0 globalMisses=[] warm=0 → cond=true b1all=true b1globals=true b4=true guarded=true
file=coroutine.lua sources=0 outside=0 misses=0 globalMisses=[] warm=0 → cond=true b1all=true b1globals=true b4=true guarded=true
file=debug.lua     sources=0 outside=0 misses=0 globalMisses=[] warm=0 → cond=true b1all=true b1globals=true b4=true guarded=true
file=io.lua        sources=1 outside=0 misses=5 globalMisses=[] warm=0 → cond=true b1all=FALSE b1globals=true b4=true guarded=true
file=math.lua      sources=0 outside=0 misses=0 globalMisses=[] warm=0 → cond=true b1all=true b1globals=true b4=true guarded=true
file=os.lua        sources=0 outside=0 misses=0 globalMisses=[] warm=0 → cond=true b1all=true b1globals=true b4=true guarded=true
file=package.lua   sources=0 outside=0 misses=0 globalMisses=[] warm=0 → cond=true b1all=true b1globals=true b4=true guarded=true
file=string.lua    sources=0 outside=0 misses=0 globalMisses=[] warm=0 → cond=true b1all=true b1globals=true b4=true guarded=true
file=table.lua     sources=0 outside=0 misses=0 globalMisses=[] warm=0 → cond=true b1all=true b1globals=true b4=true guarded=true
file=utf8.lua      sources=0 outside=0 misses=0 globalMisses=[] warm=0 → cond=true b1all=true b1globals=true b4=true guarded=true
file=wx.lua        sources=0 outside=0 misses=0 globalMisses=[] warm=0 → cond=true b1all=true b1globals=true b4=true guarded=true

REVIEW-COST TOTALS provisioned=11 cond=11 plusB1all=10 plusB1globals=11 plusB4=11 conservative=10 guarded=11
```

**The conservative rule is nearly free, and with one refinement it is exactly free.** All 10 bundled
stdlib files plus the 123 KiB definition library stay pinnable. The single loss under the literal
rule ("any resolution that returned nothing") is `io.lua`, and its five misses are

```
misses=[type:boolean|nil, type:fun(): string, type:integer|nil, type:string|integer, type:string|number|nil]
```

— `resolveType` called with **unparsed type expressions**, not with a name any user could ever
declare. `boolean|nil` will never become a declaration, so treating its absence as a dependency buys
nothing and costs a pin. Restricting the absence rule to **global** resolutions costs zero files
(`globalMisses=[]` for all 11), which is why §3.3 states it that way.

`plusB4=11` is measured on files built cold; it is **not** evidence that the naive B4 rule is free in
general. A library file whose inner library snapshot is itself pinned stays warm across ticks, so a
blanket "warm nested `forFile` ⇒ unpinnable" would strip the pin from every library→library chain
permanently. §3.7 therefore replays the inner file's recorded set instead, which is exactly what
§3.6 already does for `resolveX` and is measured at zero pin cost (`guarded=11`).

#### Both blockers closed, and the fix is live for the right reason

Re-run with the rule applied (`mode=guarded`, same scaffold, same fixtures): **BUILD SUCCESSFUL**,
DR-11 and DR-12 both green — and the traces show each is green because the rule fired, not because
pinning was switched off (11/11 files still pinned in the same run):

```
TYPE11-REVIEW file=alpha.lua provisioned=true pinnable=false sources=0 outside=[] misses=[global:sharedByProject] mode=guarded
DR-11 after: libAlias = [afterDeclared] declaring = [shared.lua] roots 3 -> 3

TYPE11-REVIEW file=a.lua provisioned=true pinnable=false sources=2 outside=[p.lua] warm=[b.lua] warmUnreplayed=[] mode=guarded
DR-12 after edit: aAlias = [afterEdit]
```

`a.lua` moved from `sources=1 outside=[]` under `cond` to `sources=2 outside=[p.lua]` under
`guarded`: the warm inner frame was replayed, and the project dependency it hides became visible.

#### The alternative to blanket-unpinnable for B1, priced

The candidate was "depend on something that ticks when a global declaration appears". The only
platform API with that shape is `FileBasedIndex.getIndexModificationStamp(ID, Project)` — the sole
`…ModificationStamp` member of `FileBasedIndex` (`javap` on
`GoLand/lib/intellij.platform.indexing.jar`), and it compiles and answers against the platform the
tests run on, which is how the figures below were produced rather than read. Probed on
`LuaGlobalAssignmentIndex.KEY`:

```
REVIEW-STAMP LuaGlobalAssignmentIndex stamp: before=16 afterUnrelatedEdit=16 afterNewDeclaration=17
             declaringBrandNewGlobal=[newGlobal.lua]
```

It behaves as hoped — still across an ordinary local-only edit, moving when a file declaring a global
appears. It is nevertheless **not** adopted: it is a second invalidation axis to reason about, it
would tick for every project-side global assignment (ordinary Lua code) rather than only for the name
in question, an index query inside a `CachedValue` validity check runs while dumb, and the platform
does not document the stamp as monotonic — a `ModificationTracker` must be. The measured cost of the
simple rule is **zero pinned files**, so none of that risk is worth taking. Recorded in §9.

### 1.9 Two guards that could not fire (2026-08-11, fourth round)

Step 9's remaining blockers were both about **a test that cannot go red** — the same defect class as
§1.8, one level out: there the *rule* was vacuously satisfied, here the *guards* are. Neither is a
production defect; both are defects in what this plan promised would catch one.

#### B3 — the coverage guard matches text that is not in the file

Phase 3's `LuaTypeSourceRecorderCoverageTest` was specified to count three literal chains in
`LuaTypeManagerImpl.kt`. Counted against the real file (553 lines, `main` @ `75707e78`):

| Matcher as specified | Counts | Actual sites |
| :-- | --: | --: |
| `FileBasedIndex.getInstance().getContainingFiles` | **0** | 2 |
| `PsiManager.getInstance(project).findFile` | **1** | 2 |
| `StubIndex.getElements` | 3 | 3 |

Two distinct causes, and the review named only the first:

- **ktlint wraps the chain.** Both `getContainingFiles` sites are written `FileBasedIndex` ⏎
  `.getInstance()` ⏎ `.getContainingFiles(…)` (`:171-173`, `:355-357`), so the one-line literal
  appears nowhere. That matcher counts 0 and would count 0 forever — a recorded expectation of 0 that
  a new site cannot move, because a new site would be wrapped too.
- **The receiver is not always the same text.** `:175` calls `PsiManager.getInstance(project).findFile`
  and `:358` calls `psiManager.findFile` through a local. Even unwrapped, a receiver-qualified matcher
  under-counts by one *today*. The review did not find this one.

`StubIndex.getElements` counts correctly and is still not safe: re-wrapping one existing site takes it
`3 → 2`, so a routine `ktlintFormat` would present as a **removed** call site. A guard whose count
drops when nothing was deleted is worse than no guard.

The matcher that works — comments stripped, then all whitespace removed, then count the member name
with its opening paren — measured on the same file:

```
.findFile(            2
.getElements(         3
.getContainingFiles(  2                                     TOTAL 7
```

and proven to move in the two directions that matter: injecting one synthetic
`PsiManager.getInstance(project).findFile(…)` takes `.findFile(` `2 → 3`; re-wrapping
`StubIndex.getElements(` across lines leaves `.getElements(` at `3` while the chain literal falls to
`2`. Comment stripping is **prophylactic, not load-bearing today** — measured, the counts are `2 / 3 / 2`
with or without it, because no comment in this file currently writes one of these members followed by
an opening paren. It is specified anyway because the failure it prevents is a *false red*: a future
KDoc that writes `.findFile(…)` in prose would inflate the count and fail a build that added no call
site. Stated as prophylaxis rather than as a measured effect, since an earlier draft of this very
section claimed the latter and was wrong.

The recorded totals are therefore **2 / 3 / 2**, and they are *call sites of the three doors*, not the
six `reportFile` insertion points of §3.5. The two sets are not equal and are not meant to be; the
guard's question is "did a new way of reading another file appear", and the author answers whether it
needs a report.

#### B5 — the dumb-mode guard is gateable after all, by asserting the decision

§1.6 concluded that TYPE-11-05 has no reproducing test, and §3.4 called the guard "insurance, not a
fix for a demonstrated defect". The first half stands: the **outcome** does not reproduce, because the
library `PsiFile`'s own `modificationStamp` moves when dumb mode ends and rebuilds the snapshot
regardless. But "the staleness is not reproducible" was allowed to imply "the guard is not testable",
and that does not follow. **The guard is a decision, and the decision is assertable.**

Under dumb mode `resolveGlobal` returns null before the absence is reported (§3.6's early-returns
rule), so a dumb build records an **empty** frame. On a provisioned file an empty frame passes §3.3
steps 2, 3, 4 and 5 — every one of them — so step 1 is the *only* thing standing between a dumb build
and a pin. That is precisely the condition under which an assertion has to move when the step is
removed:

```
isPinnable(libraryFile, SourceFrame()) == false     under runInDumbModeSynchronously
                                          == true       with §3.3 step 1 deleted
```

DR-05's existing trace already grounds the premise this rests on — `libDumb graph type = Undefined`
inside the dumb block is `resolveGlobal` having returned null, which is the empty frame. What was
missing was not evidence; it was an **extraction**. §3.3 now names `isPinnable` as a pure
`(PsiFile, SourceFrame) → Boolean` so the decision can be asked without staging the outcome.

The test must assert both halves or it gates a state that never occurs: (a) the predicate on an
explicitly-empty frame, which is the mutation-detecting assertion, and (b) that the frame a **real**
dumb build registers in `snapshotFrames` is in fact empty. (a) alone would be a well-formed assertion
about a hypothetical — the shape of harness §1.8 and COMP-09 were both caught writing.

This is the same correction in both blockers: a guard is not a guard until it has been shown red.
`risks-and-gaps.md`'s mutation ledger is where that evidence goes, and neither of these had an entry.

### 1.10 Two more channels into the same defect (2026-08-11, fifth round)

Step 9's second full round raised two blockers against §3 **as accepted after B1 and B4**. Both were
reproduced before being fixed, and both cost nothing. Full scaffold table, pasted red output and
mutation rows in `risks-and-gaps.md` "Fourth measurement round"; baseline **2 565 tests, 0 failures**
on unmodified `main` before any edit.

#### V1 — the in-progress nested snapshot

§3.7 dismissed `LuaTypesVisitor.inProgressSnapshot` as "the same file's own in-flight build". It is a
**map keyed on the requested file** (`LuaTypesVisitor.kt:1483-1487`), and `buildSnapshot` adds an
entry for every file whose build is on the thread's stack (`:1507-1518`). Reachable, and measured:

```
TYPE11-DR14 inProgress hit file=file:///…/luassert-…/outer.lua depth=5
```

Fixture — two library files in a genuine mutual-reference cycle, seeded from a project file:

```lua
outer.lua:  OuterSeed  = projectSeed      -- statement 1, resolves fully
            OuterGlobal = InnerSeed       -- statement 2, nests into inner.lua
inner.lua:  InnerSeed  = OuterSeed        -- re-enters outer.lua, mid-build
```

Under the post-B1/B4 rule (`mode=guarded`, no V1 guard):

```
editing the project file must be reflected in InnerSeed's type, even though InnerSeed was built
while outer.lua's build was still in progress on the same thread expected:<[afterEdit]> but was:<[beforeEdit]>
```

`inner.lua`'s own build is a **normal cold** `forFile`, correctly judged on `sources=1 outside=[]`.
What never arrives is `outer.lua`'s own dependency on `p.lua` — reported into outer's frame *before
inner's frame was pushed*, so §3.1 step 3's "report into every open frame" cannot reach back for it,
and the re-entrant `forFile(outer.lua)` that would carry it skips recording entirely. Fixed by §3.3
step 6; green with `inProgressHits=[…/outer.lua]` and `InnerSeed = [afterEdit]`.

#### V2 — a resolution that succeeded, and is out-ranked later

`doResolveGlobal` is `typeOfGlobalIn(projectScope) ?: typeOfGlobalIn(allScope)`
(`LuaTypeManagerImpl.kt:162-163`). A library global that only *another library* declares resolves
through the fallback, so the call succeeds and step 5 never fires — the frame records `{lib.lua}`,
fully provisioned, and the file is pinned. The user then declares that name in a project file, which
out-ranks the library for every unpinned caller:

```
a project declaration written AFTER a library snapshot resolved via the all-scope fallback must
out-rank that library's answer, exactly as it would for a fresh build expected:<[afterProject]> but was:<[beforeEdit]>
```

This is B1's shape with a **successful** resolution instead of an empty one, which is exactly why
step 4's wording ("a resolution that answered nothing") did not cover it. Fixed by §3.1 step 5b +
§3.3 step 7.

#### What the fixes cost

```
REVIEW-COST TOTALS provisioned=11 cond=11 guarded=11 b14=11 dr15broad=11 dr15rescued=11
```

**Zero lost pins, every rule.** Two variants of V2's rule were priced together: `dr15broad` (report
whenever project scope alone answers null) and `dr15rescued` (only when the fallback then rescues it).
They tie on every fixture tried; **`dr15rescued` is adopted** because `dr15broad`'s extra trigger is
the both-scopes-null case step 4 already covers, so it duplicates bookkeeping without ever saving a
pin. `getIndexModificationStamp` was reconsidered and re-rejected for DR-13's reasons — with the
simple rule at zero cost, there is nothing for it to buy.

⚠ **The zero is structural, and should not be banked.** None of the 10 bundled stubs or the 123 KiB
definition library reference another library file's global from inside their own build, so none of
them reaches either interleaving — `doResolveGlobal` is never even entered for the 11 files' own
construction. These rules are free *on what ships today*. A future multi-file definition library that
does cross-reference would pay, and that is the correct behaviour, not a regression.

#### The harness defect this round caught

The first combined run reported V1's assertion **green** — the opposite of every isolated run. Cause:
an earlier test class's teardown had a project edit in flight when `inner.lua`'s chain reached back
through `outer.lua` and recomputed everything fresh. A false green for a reason unrelated to the rule
under test. Every V1/V2 verdict here is therefore from a **single-class** run, and the general lesson
is the one this ledger keeps relearning: a green that arrives for an unexamined reason is not evidence.

### Prior Art in This Repo

| Component | file:line | Relationship |
| :-- | :-- | :-- |
| `LuaTypesSnapshot.forFile` | `lang/psi/types/LuaTypes.kt:212-224` | **Edited** — the churn dependency becomes conditional. Not replaced; the reentrancy guard, the `psiFile` dependency and `targetModificationTracker` all stay. |
| `LuaTypeManagerImpl.typeCache` / `moduleCache` / `globalCache` | `lang/psi/types/LuaTypeManagerImpl.kt:34-68` | **Extended** — each gains a parallel source-URL record. Their `PsiModificationTracker` dependency is deliberately unchanged. |
| `RuntimeLibraryProvider.getLibraryRoot` | `platform/target/RuntimeLibraryProvider.kt:25-30` | **Reused** as provenance source 1. It is the single root both `LuaLibraryProvider` (`lang/library/LuaLibraryProvider.kt:19`) and `PlatformLibraryProvider.getSupportLibraries` (`project/PlatformLibraryProvider.kt:47`) are built from, so matching it covers both without asking either. |
| `LuaDefinitionLibraryProvider.getRootsToWatch` | `definitions/LuaDefinitionLibraryProvider.kt:44` | **Reused** as provenance source 2. |
| `LuaRocksLibraryProvider` | `rocks/library/LuaRocksLibraryProvider.kt` | **Deliberately excluded** — out of v1 scope per `requirements.md`. |
| `PlatformLibraryProvider.getExternalLibraries` | `project/PlatformLibraryProvider.kt:52-69` | **Deliberately excluded** — its "Search Trees" are arbitrary user source paths from `PathConfiguration.getStaticSourcePathPatterns`, i.e. mutable project-adjacent code, not a plugin-provisioned immutable library. |
| `ProjectFileIndex.isInLibrary` | platform API | **Not used**, per TYPE-11-03. Never needed once provenance answers the question. |
| `CompNineDr20Test` | `src/test/kotlin/net/internetisalie/lunar/definitions/CompNineDr20Test.kt` | **Superseded for this feature** by `TypeElevenDr04LatencyTest`, which uses the same shape over a provenance-matched library instead of an anonymous test provider. DR-20 is left where it stands. |
| `LibraryRootTestCase` | `src/test/kotlin/net/internetisalie/lunar/definitions/LibraryRootTestCase.kt` | **Not used** by TYPE-11 tests. Its anonymous `AdditionalLibraryRootsProvider` is invisible to provenance by construction, so a TYPE-11 test built on it would validate the mechanism against a path no user hits. `TypeElevenDefinitionLibraryTestCase` replaces it for this feature only. |

### Current State

`LuaTypesSnapshot.forFile` (`LuaTypes.kt:212-224`) memoizes a file's type graph with three
dependencies: the `PsiFile`, `PsiModificationTracker.MODIFICATION_COUNT`, and the project's
`targetModificationTracker`. The second is project-wide, so one keystroke in one project file
discards every cached snapshot, including those of files the user cannot edit.

### Target State

`forFile` keeps the same three-dependency shape. Only the middle dependency changes, and only when a
per-build predicate says it is safe:

```
buildSnapshot runs inside LuaTypeSourceRecorder.recording { … }
        │
        ├─ every cross-file consumption in LuaTypeManagerImpl reports its file URL
        │  (into EVERY open frame, so nesting is transitive)
        │
        ├─ every global resolution that answers NOTHING reports an absence (§1.8 B1)
        │
        ├─ every nested forFile served warm replays that snapshot's recorded frame (§1.8 B4, §3.7);
        │  if the frame is gone, the frame is marked incomplete
        │
        └─ forFile receives (snapshot, frame: SourceFrame)
                 │
                 ├─ pinnable = smart mode ∧ this file is provisioned
                 │             ∧ every recorded source is provisioned
                 │             ∧ nothing was recorded as unknown (no absence, no unreplayed warm hit)
                 │
                 ├─ pinnable   → dependencies = [psiFile, ProjectRootModificationTracker, targetTracker]
                 └─ otherwise  → dependencies = [psiFile, PsiModificationTracker.MODIFICATION_COUNT, targetTracker]
```

**The invariant, stated once because everything below is a consequence of it**: the recorder must
distinguish **"no sources"** from **"sources unknown"**, and only the first may be pinned. An
incomplete recording is not pinnable. Both directions of under-recording (§1.8) are the same defect.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.psi.types.LuaTypeSourceRecorder`

- **Responsibility**: record, for the duration of one `buildSnapshot`, the URL of every file whose
  content was consumed to answer a cross-file type question **and every point at which the answer to
  that question was unknown** (§1.8).
- **Threading**: none of its own. It is a `ThreadLocal` stack and is only ever touched under the read
  action that `forFile` already runs inside. It holds `String` URLs, never `VirtualFile`/`PsiFile`
  references, so it cannot retain heavy framework objects (engineering contract §4). The one
  exception is `snapshotFrames`, which is keyed on `LuaTypes` (a snapshot, not a framework object)
  through a `WeakHashMap` so a discarded snapshot takes its frame with it.
- **Collaborators**: `LuaTypesSnapshot.forFile` (opens frames, registers and replays snapshot
  frames), `LuaTypeManagerImpl` (reports sources, absences and cached-answer replays).
- **Key API**:
  ```kotlin
  object LuaTypeSourceRecorder {
      class SourceFrame {
          val urls: MutableSet<String>            // files consumed
          val absences: MutableSet<String>        // "global:<name>" — a resolution that answered nothing
          val unreplayedWarm: MutableSet<String>  // a warm nested forFile whose frame was gone
          val inProgressHits: MutableSet<String>  // a nested forFile served mid-build (§1.10 V1)
          val rescuedGlobals: MutableSet<String>  // project scope empty, all-scope answered (§1.10 V2)
          fun absorb(other: SourceFrame)          // union of all five sets
      }

      /** Snapshot instance → the frame recorded when it was built. Weak keys (§3.7). */
      val snapshotFrames: MutableMap<LuaTypes, SourceFrame>

      fun <T> recording(body: () -> T): Pair<T, SourceFrame>
      fun report(urls: Collection<String>)
      fun reportFile(file: PsiFile?)
      fun reportAbsence(key: String)
      fun reportRescuedGlobal(key: String)
      fun reportInProgressHit(file: PsiFile)
      fun reportWarmSnapshot(file: PsiFile, served: LuaTypes)
      fun replay(frame: SourceFrame)
      fun depth(): Int
  }
  ```
  Every `report*` writes into **every** open frame, so nesting is transitive (§3.1 step 3).

### 2.2 `net.internetisalie.lunar.lang.psi.types.LuaLibraryProvenance`

- **Responsibility**: answer "did this plugin provision this file?" for a `PsiFile` or a URL.
- **Threading**: read action. Reads settings and the VFS only; no PSI, no writes. The root list is
  memoized per project, so the classloader resource lookup in `RuntimeLibraryProvider.getLibraryRoot`
  and the catalog load in `LuaDefinitionLibraryProvider.getRootsToWatch` run once per generation,
  not once per snapshot build.
- **Collaborators**: `RuntimeLibraryProvider`, `LuaDefinitionLibraryProvider`, `LuaProjectSettings`,
  `ProjectRootModificationTracker`, `CachedValuesManager`.
- **Registration**: light `@Service(Service.Level.PROJECT)` — annotation-registered, no `plugin.xml`
  entry, matching `LuaProjectSettings` (`settings/LuaProjectSettings.kt:17`) and
  `RockspecSourcePathProvider` (`rocks/RockspecSourcePathProvider.kt:21`).
- **Key API**:
  ```kotlin
  @Service(Service.Level.PROJECT)
  class LuaLibraryProvenance(private val targetProject: Project) {
      fun generationTracker(): ModificationTracker
      fun isProvisioned(file: PsiFile): Boolean
      fun isProvisionedUrl(url: String): Boolean
      companion object {
          fun getInstance(project: Project): LuaLibraryProvenance =
              project.getService(LuaLibraryProvenance::class.java)
      }
  }
  ```

### 2.3 `net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot.Companion.forFile` (edited)

- **Responsibility**: unchanged — compute or return the cached snapshot. Gains the pinnable decision.
- **Threading**: read action (unchanged; callers already hold one).
- **Collaborators**: `LuaTypeSourceRecorder`, `LuaLibraryProvenance`, `DumbService`.
- **Key API**: `forFile`'s signature is unchanged — `fun forFile(file: PsiFile): LuaTypes` — and one
  companion member is added:
  ```kotlin
  internal fun isPinnable(psiFile: PsiFile, frame: LuaTypeSourceRecorder.SourceFrame): Boolean
  ```
  §3.3 steps 1–8, with step 9 as its only production caller. `internal` rather than `private`
  deliberately: the Kotlin test source set is a friend module, so a test can call it directly (the
  same seam `LuaCheckInvoker.classify` and `LuaShellExecOptionsCustomizer.prependInReverse` already
  use). Without this extraction TYPE-11-05's guard has no assertion that goes red when it is deleted —
  §1.9 B5. Tests pass a **fresh** `SourceFrame()` for the empty case; `SourceFrame` holds three mutable
  sets, so there is no shared `EMPTY` constant to be accidentally written through.

### 2.4 `net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl` (edited)

- **Responsibility**: unchanged, plus reporting every cross-file consumption to §2.1.
- **Threading**: unchanged.
- **Key API**: no public signature changes. Two private helpers are added:
  ```kotlin
  private fun <T> recordUnder(key: String, body: () -> T): T
  private fun replaySources(key: String)
  ```
  and one new field mirroring the existing caches exactly:
  ```kotlin
  private val sourceCache: CachedValue<MutableMap<String, LuaTypeSourceRecorder.SourceFrame>>
  ```
  The cached value is the **whole frame**, not just its URLs: an absence must replay too, or a
  memoized `resolveGlobal` that answered null replays as "no sources" and re-creates B1 through the
  cache (§3.6).

## 3. Algorithms

### 3.1 Source recording

- **Input → Output**: a `buildSnapshot` invocation → a `SourceFrame` (consumed URLs, absences,
  unreplayed warm hits, in-progress hits, rescued globals).
- **Steps**:
  1. `recording` pushes a fresh `SourceFrame` onto a `ThreadLocal<ArrayDeque<SourceFrame>>`.
  2. It runs `body()`, then pops the frame in a `finally` and returns `body`'s result paired with the
     frame.
  3. `report(urls)` adds every URL to **every** frame currently on the stack, not only the innermost.
     `reportAbsence` and the unreplayed-warm mark do the same, into their own sets.
  4. `reportFile(file)` reads `file?.originalFile?.virtualFile?.url` and delegates to `report`;
     a null at any step is a no-op.
  5. `reportAbsence("global:$name")` is called from `resolveGlobal` on every path that returns
     `null` — cache hit on a stored null, reentrancy guard, and a computed null (§3.6). Absence is
     recorded for **global** resolution only; §1.8 measured that widening it to `resolveType` costs
     `io.lua` its pin for names like `boolean|nil` that can never be declared.
  5b. `reportRescuedGlobal("global:$name")` is called from `doResolveGlobal` when the **project-scope**
     pass returns null *and* the all-scope fallback then answers (§1.10 V2). The overall call
     succeeds, so step 5 never fires — yet the answer is one a future project declaration will
     out-rank (`LuaTypeManagerImpl.kt:162-163`, project scope first, BUG-427). Measured cost: zero
     of the 11 shipped files.
  5c. `reportInProgressHit(file)` is called from `forFile` when `LuaTypesVisitor.inProgressSnapshot`
     answers non-null at `depth() > 0` (§1.10 V1, §3.7).
  6. `replay(frame)` absorbs a stored frame into every open frame — **all five sets**, so an absence
     or an incompleteness recorded once keeps propagating (§3.6, §3.7).
- **Rules / edge handling**:
  - **Stack-wide reporting is the whole point of step 3.** `forFile(libraryA)` can nest inside
    `forFile(libraryB)` through `resolveGlobal`; if only the innermost frame were filled, `libraryB`
    would be judged pinnable while transitively depending on a project file through `libraryA`.
  - An empty stack is a no-op: the type manager is called from many places that are not snapshot
    builds, and those must cost nothing.
  - ⚠ **`LuaTypesVisitor.inProgressSnapshot` does not only guard reentrancy into the *same* file.**
    An earlier draft of this bullet said it did, and §3.7 drew the same false conclusion; both are
    corrected by §1.10 V1, which measured the hit being served for a file two frames out. It returns
    before any frame is opened, which is exactly why step 5c has to report it explicitly.
- **Complexity / bounds**: O(frames × urls). Frame depth is bounded by the type manager's own
  reentrancy guards (`resolvingGlobals`, `resolvingTypes`, `resolvingModules`); in the measured runs
  the recorded set was 0 or 1 entries per stdlib file.

### 3.2 The provenance predicate

- **Input → Output**: `PsiFile` or `String` URL → `Boolean`.
- **Steps**:
  1. Compute the root URL list, memoized per project via
     `CachedValuesManager.getManager(project).getCachedValue(project) { … }` with dependencies
     `ProjectRootModificationTracker.getInstance(project)` and
     `LuaProjectSettings.getInstance(project).state.targetModificationTracker`:
     ```kotlin
     val target = LuaProjectSettings.getInstance(project).state.getTarget()
     val runtimeRoot = RuntimeLibraryProvider(project).getLibraryRoot(target)
     val definitionRoots =
         AdditionalLibraryRootsProvider.EP_NAME.extensionList
             .filterIsInstance<LuaDefinitionLibraryProvider>()
             .flatMap { it.getRootsToWatch(project) }
     (listOfNotNull(runtimeRoot) + definitionRoots).map { it.url }
     ```
  2. `isProvisioned(file)` reads `file.originalFile.virtualFile?.url`; a null URL returns `false`.
  3. `isProvisionedUrl(url)` returns `rootUrls.any { url == it || url.startsWith("$it/") }`.
- **Rules / edge handling**:
  - **`originalFile`, not `virtualFile`** — measured: a `PsiFile.copy()` of a library file has
    `virtualFile == null` (§1.3). Reading `virtualFile` would silently classify every completion copy
    as unprovisioned. That direction is safe (no staleness, just no speedup), which is exactly why it
    would never be noticed.
  - **URL prefix, not reference identity and not `equals` on `VirtualFile`** — measured: `===` is
    false for a light-fixture project file even when `PsiManager.findFile` was handed that very file
    (§1.3). The `"$it/"` suffix in the prefix test is required: without it, a sibling root
    `…/luassert-abc` would match a file under `…/luassert-abcdef`.
  - The list is taken from the **EP-registered** `LuaDefinitionLibraryProvider` instances, not a
    freshly constructed one. A locally constructed provider would pass a unit test while the feature
    was dead in a running IDE — the failure mode `LuaDefinitionLibraryProviderTest
    .testProviderIsRegisteredWithThePlatform` already exists to catch.
  - `RuntimeLibraryProvider.getLibraryRoot` returns `null` for a target with no bundled resources
    (e.g. PANDOC); `listOfNotNull` handles it and provenance simply has one source.
- **Complexity / bounds**: O(roots) per query, roots ≤ 1 + number of enabled definition libraries.
  The expensive part (classloader resource lookup, catalog load) runs once per generation.

### 3.3 The pinnable decision

- **Input → Output**: `(PsiFile, SourceFrame)` → the churn dependency object.
- **Steps**, in this order, each short-circuiting:
  1. `if (DumbService.isDumb(project)) → not pinnable` (§3.4).
  2. `if (!provenance.isProvisioned(psiFile)) → not pinnable`.
  3. `if (frame.urls.any { !provenance.isProvisionedUrl(it) }) → not pinnable`.
  4. `if (frame.absences.isNotEmpty()) → not pinnable` (§1.8 B1).
  5. `if (frame.unreplayedWarm.isNotEmpty()) → not pinnable` (§1.8 B4 / §3.7).
  6. `if (frame.inProgressHits.isNotEmpty()) → not pinnable` (§1.10 V1 / §3.7).
  7. `if (frame.rescuedGlobals.isNotEmpty()) → not pinnable` (§1.10 V2 / §3.1 step 5b).
  8. Otherwise pinnable.
  9. Pinnable → churn is `provenance.generationTracker()`; not pinnable → churn is
     `PsiModificationTracker.MODIFICATION_COUNT`.
- **Steps 1–8 are a named function, not an inlined condition.** `internal fun isPinnable(psiFile:
  PsiFile, frame: SourceFrame): Boolean`, with step 9 as its only caller. This is not organisational
  taste — it is what makes TYPE-11-05 testable (§1.9 B5). The decision is a pure function of its two
  arguments plus `DumbService`/provenance state, so a test can ask it directly instead of staging an
  outcome that §1.6 measured to be unreproducible. Inlining these steps into the
  `CachedValueProvider` would leave the dumb-mode guard with no assertion that goes red when it is
  deleted, which is how it survived three rounds of review unproven.
- **Rules / edge handling**:
  - **Steps 4 through 7 are the "sources unknown" half of the invariant, and they are not optional.**
    Step 3 alone is vacuously true for an empty set, which is exactly the state a failed resolution
    (B1), a warm inner snapshot (B4), an in-progress nested snapshot (V1) and a project-scope pass
    that found nothing before the all-scope fallback answered (V2) all leave behind. **Measured red
    all four ways** — §1.8 for B1/B4, §1.10 for V1/V2 — and each fix measured at **zero** lost pins
    among the 11 shipped files. Four channels, one defect: a frame that looks empty because nothing
    was recorded, not because nothing was consumed.
  - **This is the answer to "what happens to a snapshot that resolved cross-file into a project
    file": nothing changes for it.** It keeps today's dependency exactly, so it keeps today's
    correctness exactly. There is no attempt to track *which* project file, no scoped tracker, and no
    partial invalidation — a snapshot is either a pure function of provisioned content or it is on
    the global tracker.
  - The tests are ordered cheapest-first and short-circuit. Step 2 rejects every project file
    before any set iteration; project files are the overwhelming majority of `forFile` calls.
  - The decision is made **inside** the `CachedValueProvider`, so it is re-evaluated on every rebuild
    — but a **pinned** file only rebuilds on a generation tick. Earlier wording here claimed a
    re-judgement "which the global tracker guarantees happens"; that is false for exactly the files
    the feature pins, because `MODIFICATION_COUNT` is the dependency the pin removes. **A pin must
    therefore be correct at the moment it is taken; there is no second chance.** That is why steps 4
    and 5 exist and why an unpinned file — which does rebuild on every tick — is the only one allowed
    to be re-judged later.
  - An unpinnable verdict is never sticky in the harmful direction: a file on
    `MODIFICATION_COUNT` rebuilds on the next PSI tick and is re-judged then, so a pin lost to a
    transient incompleteness is regained at the next edit.
- **Complexity / bounds**: O(|sources| × |roots|), both measured at ≤ 2 for the shipped stubs
  (§1.8: 10 of 11 files record no sources at all).

### 3.4 The dumb-mode guard

- **Input → Output**: `Project` → the boolean in step 1 of §3.3.
- **Steps**: `!DumbService.isDumb(project)`.
- **Rules / edge handling**: while indexing, `resolveGlobal` returns `null` unconditionally
  (`LuaTypeManagerImpl:141`) and `resolveType` likewise (`LuaTypeManagerImpl:84`), so a snapshot built
  then records **zero** sources — which would otherwise make it maximally pinnable, precisely when it
  is least trustworthy. The guard exists to close that inversion.
  **This is one instance of the general rule, not a special case.** A dumb-mode build is a build
  whose sources are unknown, which is what §3.3 steps 4 through 7 also cover; §1.8 B1 is the same
  inversion in smart mode, and the fact that this section named the inversion without generalising it
  is what let B1 through review. The `isDumb` test is kept as its own step because it is cheaper than
  inspecting the frame and because `resolveGlobal`'s dumb-mode return is *earlier* than the absence
  report (see §3.6's "early returns" rule), so it would not otherwise be recorded.
  **It is not backed by a reproduced *staleness*** (§1.6): with the guard removed the DR-05 harness
  stayed green, because the file's own `modificationStamp` moves when dumb mode ends. It is kept
  because it costs one boolean, it cannot be wrong, and the alternative is relying on an accident of
  `modificationStamp` behaviour that no test pins.
  **It is nevertheless gated** (§1.9 B5, TC-16). The decision is asserted directly —
  `isPinnable(libraryFile, SourceFrame())` is `false` under
  `DumbModeTestUtils.runInDumbModeSynchronously` and `true` with this step deleted, because an empty
  frame on a provisioned file clears steps 2–7 and leaves step 1 as the sole rejector. The companion
  assertion — that a real dumb build registers an empty frame in `snapshotFrames` — is what stops
  that from being a claim about a hypothetical.
- **Complexity / bounds**: O(1).

### 3.5 Cross-file consumption sites that must report

Every site below is a place `LuaTypeManagerImpl` turns another file's content into type information.
Missing one produces a snapshot judged pinnable while depending on an unrecorded file — a silent
stale-type defect, not a crash. The list is exhaustive as of this design; §6 states the guard.

This table covers only what a resolution **found**. The complementary half — what it failed to find,
and what it took from a cache without contributing — is §3.1 steps 5–6 with §3.6 and §3.7, and is
just as load-bearing: §1.8 measured both under-recordings shipping a stale type with every site below
correctly wired.

| Site (`LuaTypeManagerImpl`) | Line | Reports |
| :-- | --: | :-- |
| `typeOfGlobalIn` — each candidate file visited | 176 | `.onEach { LuaTypeSourceRecorder.reportFile(it) }` inserted after the existing `.filter { it != exclude }` |
| `getModuleType` — the module file, before the stub fast path | 203 | `reportFile(psiFile)` as the first statement |
| `materializeClass` — every declaring file | 262 | `decls.forEach { reportFile(it.containingFile) }` |
| `materializeUnhostedClass` — every `---@class` tag's file | 308 | `tags.forEach { reportFile(it.containingFile) }` |
| `addMethodsOf` — the selected `LuaFuncDecl`'s file | 467 | `reportFile(decl.containingFile)` after the `firstOrNull` |
| `doResolveType` — the alias declaration's file | 250 | `reportFile(aliasDecls.first().containingFile)` |

`typeOfGlobalIn` reports **every file it visits**, not only the one that yielded a type. This is a
deliberate over-approximation: `doResolveGlobal` searches project scope first (BUG-427,
`LuaTypeManagerImpl:162`), so a project file that declares the name but gives it no useful type is
still a file whose future content can change the answer. Over-approximating costs a lost pin; under-
approximating costs a stale type.

**That ordering is not, however, what the recorder exists for** — §1.7 removed it and blanket pinning
stayed unsound. The rows that carry the weight are `materializeClass` (`:262`) and `addMethodsOf`
(`:467`): `collectMethodMembers` reads `StubIndex.getAllKeys(LuaGlobalDeclarationIndex.KEY, project)`
**project-wide with no scope argument** (`:427-432`), so a project file extends a stub-defined class
no matter how global resolution is scoped. Deleting a row from this table on the grounds that
"resolution no longer reaches project scope" would be a stale-type defect; §1.7 is the measurement
that says so.

### 3.6 Cache replay

- **Input → Output**: a door name + argument → the `SourceFrame` recorded when that answer was
  computed.
- **Steps**:
  1. Add `sourceCache`, a `CachedValue<MutableMap<String, SourceFrame>>` built exactly like the three
     existing caches (`CachedValuesManager.getManager(project).createCachedValue({ … }, false)` with
     `PsiModificationTracker.getInstance(project)` as the sole dependency and a
     `Collections.synchronizedMap`).
  2. Key format: `"type:$name"`, `"module:$moduleName"`, `"global:$name"`. Three flat namespaces in
     one map; the prefixes exist because the same string can be a class name and a global name.
  3. On a **miss**, wrap the `doResolveX` call in
     `recordUnder(key) { … }`, which runs it inside `LuaTypeSourceRecorder.recording` and stores the
     resulting set under `key`.
  4. On a **hit**, call `replaySources(key)` **before** returning the cached type, which re-reports
     the stored URLs into whatever frames are currently open.
- **Rules / edge handling**:
  - Without step 4 the feature is unsound. `resolveGlobal("wx")` is memoized project-wide; the first
    snapshot build to ask pays and records, and every later build gets the type with no sources — so
    a library file that depends on a project global through a cache hit would be judged pinnable.
  - `sourceCache` shares the three existing caches' `PsiModificationTracker` dependency, so entries
    are discarded on exactly the same tick as the types they describe. They cannot drift apart.
  - **The stored value is the whole frame.** A `resolveGlobal` that answered `null` recorded an
    absence; if replay carried only URLs, a cache hit on that null would replay as "no sources at
    all" and re-create B1 through the cache. The cache-hit path therefore reports the absence again
    (§3.1 step 5) as well as replaying.
  - The early returns that precede each cache (a primitive in `resolveType`, the dumb-mode guard, the
    reentrancy guards) return before both the cache read and the record. The primitive and dumb-mode
    returns consume no file and stay as they are — dumb mode is covered by §3.4 instead. The
    **reentrancy** return in `resolveGlobal` is an answer of `null`, so it reports an absence before
    returning.

### 3.7 Snapshot replay

- **Input → Output**: a warm `forFile` hit → the `SourceFrame` recorded when that snapshot was built.
- **Steps**:
  1. When `forFile` computes a snapshot, store `snapshotFrames[snapshot] = frame` (weak-keyed) before
     returning the `CachedValueProvider.Result`.
  2. `forFile` records whether its provider ran: set a local `var computed = false` and assign `true`
     as the provider's first statement.
  3. If the provider did **not** run and `LuaTypeSourceRecorder.depth() > 0` — i.e. this was a nested
     cache hit inside somebody else's build — call `reportWarmSnapshot(psiFile, served)`.
  4. `reportWarmSnapshot` looks the served snapshot up in `snapshotFrames`. Found → `replay(frame)`,
     so the inner file's sources, absences and incompleteness all propagate outward. Not found →
     add the URL to `unreplayedWarm`, which §3.3 step 5 turns into "not pinnable".
- **Rules / edge handling**:
  - **This is §3.6's argument applied to the second memoized door.** §3.6 says that without replay
    "a later build gets the type with no sources"; `forFile` is memoized on exactly the same footing
    and had no replay, which is §1.8 B4. Measured: `a.lua` went from `sources=1 outside=[]` (pinned,
    stale) to `sources=2 outside=[p.lua]` (correctly unpinned) once the frame was replayed.
  - **Replay, not blanket-unpinnable.** Treating every warm inner hit as unknown is sound but costs
    real pins: an inner *library* file that is itself pinned stays warm across ticks, so every
    library→library chain would lose its pin permanently. Replay costs none (§1.8: `guarded=11`).
  - The replayed frame is always consistent with the served snapshot, because they are discarded
    together: the frame is keyed on the snapshot instance, and an invalidated `CachedValue` drops the
    instance. A frame can only be missing if the snapshot outlived its weak entry, which is the
    `unreplayedWarm` case and is handled conservatively.
  - ⚠ **The in-progress path is a third memoized door, not a self-loop.** This bullet used to read
    "it is the same file's own in-flight build, whose frame is the very frame currently open" and
    conclude that `LuaTypesVisitor.inProgressSnapshot` (`LuaTypes.kt:214`) needs no treatment.
    **That is refuted by execution — §1.10 V1.** The guard is a map keyed on the *requested* file
    (`LuaTypesVisitor.kt:1483-1487`) and `buildSnapshot` adds an entry for **every** file whose build
    is on the current thread's stack (`:1507-1518`), so the hit is served for a file two frames
    further out, whose frame is emphatically *not* the one currently open. Measured:
    `TYPE11-DR14 inProgress hit file=…/outer.lua depth=5`.
  - **So it reports, and it still returns early.** The early return stays — it is the cycle-breaker
    and removing it would recurse — but whenever it answers non-null at `depth() > 0` it calls
    `reportInProgressHit(file)` into every open frame, and §3.3 step 6 makes the outer file
    unpinnable. Unlike §3.7's warm case there is nothing to *replay*: the served snapshot is still
    being built, so its frame is incomplete by construction and no union could complete it. That is
    why this door gets the conservative treatment `unreplayedWarm` gets rather than the replay
    treatment the finished caches get.
  - Measured cost: **zero of the 11 shipped files** (`b14=11`) — though for a structural reason worth
    stating rather than banking, namely that none of the bundled stubs or the definition library
    reference another library file's global at all, so none of them reaches the interleaving. The
    rule is free on what ships; it is not evidence the rule is free in general.
- **Complexity / bounds**: one map lookup per nested warm hit; the replay is three set unions.

## 4. External Data & Parsing

**None.** This feature consumes no CLI output, no network response and no file format. Its only
external inputs are platform APIs (`ProjectRootModificationTracker`, `DumbService`,
`AdditionalLibraryRootsProvider`) and this plugin's own settings. The one string format it defines is
the `sourceCache` key (§3.6 step 2), which is produced and consumed in the same class.

## 5. Data Flow

### Example 1: keystroke in a project file, 123 KiB definition library registered (DR-04 arm B)

1. The user types a character in `consumer.lua`. `PsiModificationTracker.MODIFICATION_COUNT` ticks.
2. Completion asks for `LuaTypesSnapshot.forFile(consumer.lua)`. `consumer.lua` is not provisioned
   (§3.3 step 2), so its snapshot was on the global tracker and is gone. It rebuilds.
3. The rebuild opens a recorder frame and meets the free global `wx`.
   `resolveGlobal("wx")` misses `globalCache` (the tick cleared it), so it recomputes:
   `typeOfGlobalIn` reports `…/luassert-…/wx.lua`, then calls `globalTypeIn` → `forFile(wx.lua)`.
4. `forFile(wx.lua)` finds its cached value **still valid**: its churn dependency is
   `ProjectRootModificationTracker`, which did not tick. The 123 KiB graph is not rebuilt.
   *This is the entire feature.*
5. `resolveGlobal` stores its answer in `globalCache` and its source set under `"global:wx"`.
6. `consumer.lua`'s snapshot completes. Its recorded sources include `wx.lua`, which is provisioned —
   but `consumer.lua` itself is not, so step 2 already decided: global tracker.

### Example 2: the same keystroke, but a project file shadows a library global (DR-01 path 1)

1. `alpha.lua` (library) contains `libAlias = sharedByProject`; `shared.lua` (project) declares
   `sharedByProject`.
2. `forFile(alpha.lua)` opens a frame, meets the free global `sharedByProject`, and
   `typeOfGlobalIn(projectScope, …)` reports `/src/shared.lua` into the frame.
3. §3.3 step 3 sees `/src/shared.lua` outside the provisioned roots → **not pinnable** →
   `alpha.lua`'s snapshot stays on `MODIFICATION_COUNT`.
4. The user edits `shared.lua`. `alpha.lua`'s snapshot is discarded with everything else, and
   `libAlias` re-infers to the new type. Measured: `DR-01 path1 after edit: libAlias members =
   [afterEdit]`.

### Example 3: the user enables a definition library

**This scenario is traced by reading, not by running** — every TYPE-11 fixture announces the roots
change itself, so none of them exercises the chain below. It is `risks-and-gaps.md` Gap 2.3 and
implementation-plan Phase 3 covers it.

1. `LuaDefinitionLibraryEnabler.apply` persists the id and calls
   `LuaProjectSettings.notifyDefinitionRootsChanged()` (`LuaProjectSettings.kt:199-205`), which
   publishes `LuaSettingsChangedListener.TOPIC`; `LuaSettingsChangeListener.onSettingsChanged`
   (`project/LuaSettingsChangeListener.kt:32`) runs `PlatformLibraryIndex.reload()`, which calls
   `ProjectRootManagerEx.makeRootsChange` (`project/PlatformLibraryProvider.kt:149`).
2. `ProjectRootModificationTracker` ticks. Every pinned library snapshot is discarded, and so is
   `LuaLibraryProvenance`'s memoized root list (same dependency), so the new tree is provisioned from
   its first query.
3. Non-pinned snapshots are unaffected by this tick and are still governed by `MODIFICATION_COUNT`,
   as they are today.

## 6. Edge Cases

| Case | Handling |
| :-- | :-- |
| A completion/intention **copy** of a library file | `originalFile.virtualFile` matches the root, so the copy is judged provisioned (§3.2). Measured: `byVirtualFile=false byOriginalFile=true`. |
| A scratch file, or any `PsiFile` with no `VirtualFile` | `isProvisioned` returns `false`; global tracker; today's behaviour. |
| The bundled runtime root lives in a `jar://` file system | URL-prefix containment is file-system agnostic. Measured: `runtimeRoot=jar:///…/lunar-0.18.0.jar!/runtime/standard/lua-5.4`. |
| A target with no bundled resources (PANDOC) | `getLibraryRoot` returns `null`; `listOfNotNull` drops it; definition libraries still provision. |
| The user switches target (Lua ↔ REDIS) | `targetModificationTracker` is a dependency of both the snapshot and the memoized root list, so both are discarded (REDIS-04 §3.1a behaviour preserved). |
| A LuaRocks tree | Never provisioned in v1 → always on the global tracker → behaviour identical to today. |
| A library file that `require`s a project module | `getModuleType` reports the project file (§3.5) → not pinnable. |
| Indexing in progress | Not pinnable (§3.4). |
| A library file whose free global **nothing** declares yet | `resolveGlobal` answers null → absence recorded → not pinnable (§3.3 step 4). Measured red without it: §1.8 B1. Once the declaration exists the file records it as an ordinary source and is judged on where it lives. |
| A library file whose free global nothing will **ever** declare (a typo, or an optional runtime) | Permanently not pinnable. Measured cost on what ships: **zero files** — no bundled stdlib stub and not the 123 KiB library records a single `global:` absence (§1.8). |
| A nested `forFile` served warm | The inner snapshot's recorded frame is replayed (§3.7), so the outer file is judged on the union. Measured red without it: §1.8 B4. |
| A nested `forFile` served warm whose frame has been collected | `unreplayedWarm` → not pinnable (§3.3 step 5). |
| `resolveType` called with an unparsed type expression (`boolean\|nil`, `fun(): string`) | Not an absence. §3.1 step 5 records absences for **global** resolution only; `io.lua` produces five such `resolveType` misses per build and none of them is a declaration anybody can write (§1.8). |
| A **new** cross-file consumption site added later to `LuaTypeManagerImpl` | Not automatically covered. `LuaTypeSourceRecorderCoverageTest` (implementation plan Phase 3) fails if the count of `.findFile(` / `.getElements(` / `.getContainingFiles(` call sites moves off the recorded **2 / 3 / 2**, forcing the author to decide whether the new site needs a `reportFile`. Matched on comment-stripped, whitespace-collapsed text — the literal chains this row used to name count 0 and 1 against the real file (§1.9 B3). See `risks-and-gaps.md` Risk 1.1. |
| A `LuaTypeReference` resolved lazily after the frame closed | Its source escapes recording. Bounded, not eliminated — see `risks-and-gaps.md` Risk 1.3 and DR-07. |

## 7. Integration Points

**No `plugin.xml` change is required, and that is a deliberate check rather than an omission.**

- `LuaLibraryProvenance` is a light `@Service(Service.Level.PROJECT)`. Light services are registered
  by the annotation and must **not** appear in `plugin.xml`; adding a `<projectService>` entry for one
  is an error. Precedent in this repo: `LuaProjectSettings` (`settings/LuaProjectSettings.kt:17`),
  `RockspecSourcePathProvider` (`rocks/RockspecSourcePathProvider.kt:21`).
- `LuaTypeSourceRecorder` is a Kotlin `object` with no platform lifecycle.
- `LuaTypeManagerImpl` is already registered and its registration is unchanged:
  ```xml
  <!-- src/main/resources/META-INF/plugin.xml:556-558 — UNCHANGED -->
  <projectService
          serviceInterface="net.internetisalie.lunar.lang.psi.types.LuaTypeManager"
          serviceImplementation="net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl" />
  ```
- The four `additionalLibraryRootsProvider` registrations (`plugin.xml:516-520`) are read but not
  changed. `LuaDefinitionLibraryProvider` (line 520) must stay registered or provenance loses source 2;
  that is already pinned by `LuaDefinitionLibraryProviderTest.testProviderIsRegisteredWithThePlatform`.

Platform APIs used, all verified present by compiling the measurement build against them:

| Symbol | Package |
| :-- | :-- |
| `ProjectRootModificationTracker.getInstance(Project)` | `com.intellij.openapi.roots` |
| `AdditionalLibraryRootsProvider.EP_NAME.extensionList` | `com.intellij.openapi.roots` |
| `DumbService.isDumb(Project)` | `com.intellij.openapi.project` |
| `ModificationTracker` | `com.intellij.openapi.util` |
| `CachedValuesManager` / `CachedValueProvider.Result` | `com.intellij.psi.util` |
| `PsiModificationTracker.MODIFICATION_COUNT` | `com.intellij.psi.util` |
| `VfsUtilCore.isAncestor` | `com.intellij.openapi.vfs` |

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) | Acceptance |
| :-- | :-- | :-- | :-- |
| TYPE-11-01 — a platform-library snapshot survives an unrelated edit | M | §2.2, §2.3, §3.2, §3.3 | `TypeElevenDr04LatencyTest` arm B; §1.5 |
| TYPE-11-02 — every generation signal invalidates it | M | §3.2 step 1, §3.3 step 5, §5 example 3 | `TypeElevenGenerationSignalTest` (plan Phase 3) |
| TYPE-11-03 — identification is by provenance | M | §3.2 | `TypeElevenDr02ProvenanceTest`, 5 assertions, all mutation-proved |
| TYPE-11-04 — no new stale-type defect | M | §3.1, §3.3, §3.5, §3.6 | `TypeElevenDr01ResidualTest` (3 tests). **It is the only gate.** Measured (TYPE-11-DR-09): under the rejected blanket-pin build the full suite *and* all four corpus baselines pass — `2571 tests completed, 2 failed`, both of them these fixtures. The suite and the sweep show that nothing *else* moved; neither can detect this defect class, because it needs an edit after a snapshot is built and the sweep is a single pass (`risks-and-gaps.md` → "DR-09 measured"). |
| TYPE-11-05 — a dumb-mode build is never cached across the generation | M | §3.4 | `TypeElevenDumbModeDecisionTest` (plan Phase 3) — asserts the **decision**, `isPinnable(libraryFile, SourceFrame()) == false` under dumb mode, mutation-red when §3.3 step 1 is deleted (§1.9 B5). The **outcome** does not reproduce and `TypeElevenDr05DumbModeTest` remains explicitly not a gate (§1.6); whether the stamp move is platform behaviour is still `risks-and-gaps.md` DR-06. |
| TYPE-11-06 — an incomplete recording is never pinned | M | §3.1 steps 5–5b–6, §3.3 steps 4–7, §3.6, §3.7 | Four channels, each measured red under §3 without its guard and green with it: `TypeElevenDr11LateDeclarationTest` (absence, §1.8 B1), `TypeElevenDr12WarmInnerSnapshotTest` (warm inner, §1.8 B4), `TypeElevenDr14InProgressTest` (in-progress inner, §1.10 V1), `TypeElevenDr15LateLibraryAnswerTest` (rescued global, §1.10 V2). |

### 8.1 Acceptance cases, as input → output

`requirements.md` states the requirements but carries no test-case table; these are the concrete
cases, every one of which is already an executable fixture. Paths are relative to
`src/test/kotlin/net/internetisalie/lunar/type/`.

| # | Requirement | Input | Expected output |
| --: | :-- | :-- | :-- |
| TC-1 | TYPE-11-01 | Definition library `luassert-…/wx.lua` = 123 KiB `---@meta` (one `---@class wx`, 3 400 fields, 200 methods). Five consumer files with **distinct** text, each `local pad$i = $i` + `wx.wxC_0 = $i`; time `LuaTypesSnapshot.forFile(consumer$i)` for each. | Median of the five at least 5× below the same harness's `main` figure, compared against **arm A of the same run**. `TypeElevenDr04LatencyTest`. |
| TC-2 | TYPE-11-02 (roots) | Library installed and enabled; snapshot built; then the enabled-library list is emptied and a roots change announced. | `resolveGlobal("wx")` no longer returns the library type. `TypeElevenGenerationSignalTest` case (a), plan Phase 3. |
| TC-3 | TYPE-11-02 (target) | Pinned library snapshot; then `LuaProjectSettings.setTarget(Target(REDIS, …))`. | The snapshot is rebuilt against the new target's stubs. `TypeElevenGenerationSignalTest` case (b). |
| TC-4 | TYPE-11-02 (no false tick) | Pinned library snapshot; edit an unrelated project file. | `ProjectRootModificationTracker.modificationCount` unchanged **and** the library snapshot instance is not rebuilt. `TypeElevenGenerationSignalTest` case (c). |
| TC-5 | TYPE-11-03 | `FileBasedIndex.getContainingFiles(LuaGlobalAssignmentIndex.KEY, "wx", allScope)` → `…/luassert-…/wx.lua`; same query for `projectOnly` → `/src/projectGlobal.lua`. | `isProvisioned` = `true` and `false` respectively. `TypeElevenDr02ProvenanceTest.testEveryFileResolveGlobalWouldVisitIsClassifiedByProvenance`. |
| TC-6 | TYPE-11-03 (copy) | `PsiManager.findFile(wx.lua).copy() as PsiFile`. | `isProvisioned(copy.virtualFile)` = `false`; `isProvisioned(copy.originalFile.virtualFile)` = `true`. `…testACopyOfALibraryFileIsOnlyMatchedThroughOriginalFile`. |
| TC-7 | TYPE-11-03 (live binding) | The EP-registered `LuaDefinitionLibraryProvider` instances' `getRootsToWatch(project)` after installing `luassert`. | Contains the seeded root. `…testTheSeededLibraryReachesTheProjectThroughTheRegisteredProvider`. |
| TC-8 | TYPE-11-04 (residual 1) | Library `alpha.lua` = `libAlias = sharedByProject`; project `shared.lua` = `sharedByProject = { beforeEdit = 1 }` → rewritten to `{ afterEdit = 1 }`. | `resolveGlobal("libAlias").getMembers().keys` = `[beforeEdit]` before, `[afterEdit]` after. `TypeElevenDr01ResidualTest`. |
| TC-9 | TYPE-11-04 (residual 2) | Library `host.lua` = `---@class HostClass` / `local HostClass = {}` / `HostGlobal = HostClass`, `host2.lua` = `libHandle = HostGlobal`; project `ext.lua` = `function HostClass:beforeEdit() end` → rewritten to `afterEdit`. | `resolveGlobal("libHandle").getMembers().keys` contains `afterEdit` and **not** `beforeEdit`. `TypeElevenDr01ResidualTest`. |
| TC-10 | TYPE-11-04 (project→project) | Project `a.lua` = `projectShared = { before = 1 }`, `b.lua` = `projectAlias = projectShared`; rewrite `a.lua` to `{ after = 1 }`. | `resolveGlobal("projectAlias").getMembers().keys` = `[after]`. `…testAProjectToProjectDependencyIsNeverPinned`. |
| TC-11 | TYPE-11-04 (suite) | The full suite, **and the corpus sweep run explicitly**: `run "test -PwithCorpus --rerun --no-build-cache"`. | 0 failures, and **`LuaCorpusSweepTest`, `LuaTortureCorpusTest` and `LuaInspectionParityTest` present in `build/test-results/test/`** — those three are the only classes the `-PwithCorpus` filter gates, so they are the only ones whose presence proves the sweep ran (§1.4 ⚠⚠; `BaselineRatchetTest`, `LexerInvariantsTest` and `ParseOracleTest` appear under a plain `test` run too and prove nothing). Their **absence** is the failure mode, and it is silent. Check the timestamps: `--rerun` does not clear `build/test-results/test/`, so an aborted run leaves the previous run's XML in place — measured, and it is how this exercise nearly mis-read its own first result. Reference on `69ad6b57`: **2 571 tests, 0 failures**, sweep 4/0, parity 1/0, torture 1/0. |
| TC-12 | TYPE-11-05 (outcome) | Library `delta.lua` = `libDumb = sharedByLibrary`, `deltaSource.lua` = `sharedByLibrary = { fromLibrary = 1 }`; build `forFile(delta.lua)` inside `DumbModeTestUtils.runInDumbModeSynchronously`, then leave dumb mode. | `resolveGlobal("libDumb").getMembers().keys` = `[fromLibrary]`. `TypeElevenDr05DumbModeTest` — **records, does not gate** (§1.6): two mutations left it green. The gate for TYPE-11-05 is TC-16. |
| TC-13 | TYPE-11-06 (absence) | Library `alpha.lua` = `libAlias = sharedByProject` installed while **no** file declares `sharedByProject` (asserted: `FileBasedIndex.getContainingFiles(LuaGlobalAssignmentIndex.KEY, "sharedByProject", allScope)` is empty); read `libAlias`; then add project `shared.lua` = `sharedByProject = { afterDeclared = 1 }` with the roots tracker asserted still. | `resolveGlobal("libAlias").getMembers().keys` = `[afterDeclared]`. Under §3 without step 4 it is `[]` — §1.8. `TypeElevenDr11LateDeclarationTest`. |
| TC-14 | TYPE-11-06 (warm inner) | Library `b.lua` = `bOther = { fromB = 1 }` + `bGlobal = projectSeed`, library `a.lua` = `aAlias = bGlobal`, project `p.lua` = `projectSeed = { beforeEdit = 1 }`. Resolve `bOther` first (warms `forFile(b.lua)`), then resolve `aAlias`, then rewrite `p.lua` to `{ afterEdit = 1 }` with the roots tracker asserted still. | `resolveGlobal("aAlias").getMembers().keys` = `[afterEdit]`. Under §3 without §3.7 it is `[beforeEdit]` — §1.8. `TypeElevenDr12WarmInnerSnapshotTest`. |
| TC-15 | TYPE-11-06 (cost) | The 10 bundled stdlib files plus the 123 KiB definition library, each built once in a clean epoch. | All 11 still pinnable under the rule (`guarded=11`), i.e. the rule costs nothing on what ships. Recorded by the review scaffold in §1.8; the shipped equivalent is `LuaTypeSourceRecorderCoverageTest`'s sibling assertion in plan Phase 3. |
| TC-16 | TYPE-11-05 (decision) | The TC-12 fixture. Inside `DumbModeTestUtils.runInDumbModeSynchronously`: (a) call `isPinnable(delta.lua, SourceFrame())` directly; (b) build `forFile(delta.lua)` and read the frame it registered in `snapshotFrames`. | (a) `false` — and `true` with §3.3 step 1 deleted, which is the stated mutation. (b) that frame is **empty**, so (a) is a claim about the state a dumb build actually produces and not about a hypothetical one. `TypeElevenDumbModeDecisionTest`, plan Phase 3 (§1.9 B5). |
| TC-18 | TYPE-11-06 (in-progress inner) | Library `outer.lua` = `OuterSeed = projectSeed` + `OuterGlobal = InnerSeed`, library `inner.lua` = `InnerSeed = OuterSeed`, project `p.lua` = `projectSeed = { beforeEdit = 1 }`. Resolve `OuterGlobal` (drives the cycle), then rewrite `p.lua` to `{ afterEdit = 1 }` with the roots tracker asserted still. | `resolveGlobal("InnerSeed").getMembers().keys` = `[afterEdit]`. Under §3 without step 6 it is `[beforeEdit]` — §1.10 V1. `TypeElevenDr14InProgressTest`. |
| TC-19 | TYPE-11-06 (rescued global) | Library `alpha.lua` = `libAlias = sharedByLibrary`, library `lib.lua` = `sharedByLibrary = { beforeEdit = 1 }`, project `shared.lua` declaring nothing relevant (asserted: project scope for `sharedByLibrary` is empty at build time); read `libAlias`, then rewrite `shared.lua` to declare it, roots tracker asserted still. | `resolveGlobal("libAlias").getMembers().keys` = `[afterProject]`. Under §3 without step 7 it is `[beforeEdit]` — §1.10 V2. `TypeElevenDr15LateLibraryAnswerTest`. |
| TC-17 | Risk 1.1 (coverage) | `LuaTypeManagerImpl.kt` read as text, comments stripped, all whitespace removed; count `.findFile(`, `.getElements(`, `.getContainingFiles(`. | `2`, `3`, `2`. Mutation: injecting one `PsiManager.getInstance(project).findFile(…)` takes `.findFile(` to `3` and fails the test. The literal chains this case replaced counted `1`, `3` and **`0`** against the same file (§1.9 B3). `LuaTypeSourceRecorderCoverageTest`, plan Phase 3. |

## 9. Alternatives Considered

| Option | Why not |
| :-- | :-- |
| **Blanket pin every provisioned library file** (what `requirements.md` sketched) | Measured unsound: §1.1, two residual paths fire, `BUILD FAILED`. |
| **Remove the constraint instead of surviving it: make a provisioned file's globals resolve in provisioned scope only, then blanket-pin** (no recorder, no source set, no conditional) | **Measured and rejected — §1.7.** `2571 tests completed, 2 failed`. It does not make blanket pinning sound: residual path 2 does not travel through `typeOfGlobalIn` at all but through `materializeClass` → `collectMethodMembers`, which queries `StubIndex.getAllKeys(…, project)` with no scope argument (`LuaTypeManagerImpl:427-432`), so `[afterEdit, beforeEdit]` is unchanged. And it is not free: residual path 1's library global stops resolving altogether (`[beforeEdit]` → `[]`), a user-visible resolution change outside this feature's remit. |
| **Composite the generation tracker out of more signals** (roots + target + dumb + a rocks signal) | Does not touch the residual at all. The stale content comes from a *project* file whose change ticks none of those signals, however many are added. `requirements.md` says this itself. |
| **A scoped tracker per dependency set** | Requires materialising and invalidating a per-file dependency graph across the whole project. Strictly more machinery than §3.3, and §1.4 shows §3.3 already pins every file that costs anything. Revisit only if the trace shows valuable files being excluded. |
| **Pin only files that made no cross-file resolution at all** | Simpler (no recorder plumbing into the manager) but strictly weaker: it also excludes library→library dependencies, which are safe under a shared generation tracker. It would have excluded `io.lua` (`sources=1`) for no correctness gain. |
| **Identify library files with `ProjectFileIndex.isInLibrary`** | Ruled out by TYPE-11-03; behaviour for this plugin's `SyntheticLibrary` roots is unverified, and provenance answers the question without needing to find out. |
| **Match files by `VirtualFile` reference identity** | Measured false for a project file the index itself supplied (§1.3). |
| **Record only the files a resolution *used*, and treat an empty set as "no dependencies"** (what §3 said before this round) | **Measured unsound — §1.8.** An empty set is produced both by "this file consumes nothing" and by "the answer was unknown", and the second is where staleness lives. Two fixtures red: `expected:<[afterDeclared]> but was:<[]>` and `expected:<[afterEdit]> but was:<[beforeEdit]>`. |
| **Treat *any* resolution that answered nothing as an absence, `resolveType` included** | **Measured and rejected on cost — §1.8.** It strips `io.lua`'s pin (11 → 10 pinnable) for five `resolveType` misses on unparsed type expressions (`boolean\|nil`, `fun(): string`), none of which can ever become a declaration. Restricting the rule to global resolution costs zero files. |
| **Treat any warm nested `forFile` as unknown (blanket-unpinnable) instead of replaying its frame** | Sound but not free: an inner library file that is itself pinned stays warm across ticks, so every library→library chain loses its pin permanently. Replay reproduces the same verdict at zero cost — measured `guarded=11` vs `cond=11` on the same run (§1.8). |
| **Depend on `FileBasedIndex.getIndexModificationStamp(LuaGlobalAssignmentIndex.KEY, project)` so an absence can stay pinned until a global declaration appears** | **Priced and rejected — §1.8.** The API exists and behaves as hoped (`before=16 afterUnrelatedEdit=16 afterNewDeclaration=17`), but it adds a second invalidation axis that ticks for every project-side global assignment, requires an index query inside a `CachedValue` validity check (dumb-mode hostile), and is not documented monotonic, which `ModificationTracker` requires. The rule it would optimise costs **zero** pinned files, so there is nothing to buy. |
| **Extend the scope to LuaRocks trees now** | Out of v1 scope per `requirements.md`. Rocks are mutable in place and `RockspecSourcePathProvider.forceRefreshTracker`'s behaviour on `luarocks install` is unverified (TYPE-11-DR-03, deliberately not run). |

## 10. Open Questions

_None — every unresolved item is a tracked de-risking task in `risks-and-gaps.md` (DR-06, DR-07, DR-08)._
