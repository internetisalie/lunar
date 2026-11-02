---
id: "MAINT-53-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-53"
folders:
  - "[[features/maint/53-member-resolution-by-receiver-type/requirements|requirements]]"
---

# MAINT-53: Risks & Gaps

## Executed De-risking

Rows ran on the libvirt builder on `2594fade` plus an uncommitted prototype. Evidence is under
`~/.cache/claude-scratch/lunar/maint-53/probe/`: the 2026-11-01 runs at its top level (prototype
`shareB-prototype.diff` with `final-src/`), the 2026-11-02 runs under `r1/` and `r2/` (the same
prototype with a switch per design choice, sources in `r1/src/`, tree diff `r1/prototype.diff`; `r2/`
adds the probe tests in `r2/src/`; its colon-guard switch was not saved, and its guarded arm is the
unconditional guard in `r1/prototype.diff`). Corpus runs under `r1/` and at the top
level used `test -PwithCorpus --rerun --no-build-cache --tests '*LuaCorpusSweepTest*'`; the `r2/` corpus
runs used CI's ratchet shape, `--tests '*Corpus*'`, with no probe class in the JVM (`r2/arm.sh`). The
corpus is lazy.nvim, luacheck, luarocks, Penlight and ZeroBrane: 473 files.

### MAINT-53-00-DR-01: Share of member sites resolving (`shareA/`, `r1/shareC/`, `r1/shareD/`)

"Today" is `multiResolve` on `2594fade` logic (`shareA/`). "With change" is the design as written,
every phase on (`r1/shareD/`, run `r1/run4.log`). A site counts as resolving when `multiResolve(false)`
returns at least one target, so this measures coverage; DR-08 judges correctness. Script:
`r1/audit/share.py shareA r1/shareD`.

| Member sites | Sites | Today | With change |
|--------------|-------|-------|-------------|
| **All calls** | 22,450 | **6,621 (29.5%)** | **8,755 (39.0%)** |
| Dot calls | 11,789 | 6,234 (52.9%) | 8,314 (70.5%) |
| Colon calls | 10,661 | 387 (3.6%) | 441 (4.1%) |
| Reads | 15,549 | 6,205 (39.9%) | 6,164 (39.6%) |
| Writes | 2,211 | 1,203 (54.4%) | 1,719 (77.7%) |

| Corpus | Calls | Today | With change |
|--------|-------|-------|-------------|
| lazy.nvim | 2,241 | 57.7% | 71.8% |
| luacheck | 2,665 | 34.4% | 66.6% |
| luarocks | 7,831 | 41.1% | 51.4% |
| Penlight | 1,976 | 31.5% | 39.9% |
| ZeroBrane | 7,737 | 7.3% | 7.2% |

Changed sites against today: calls 2,168 gained a target, 34 lost one, 619 narrowed, 202 disjoint;
reads 498 gained, 539 lost, 454 narrowed, 389 disjoint; writes 598 gained, 82 lost, 122 narrowed, 510
disjoint.

**Run-to-run variance.** The same code in run 3 (`r1/shareC/`, `r1/run3.log`) differed from run 4 in one
group only: Penlight dot calls and reads whose head is the global `assert`, typed through the luassert
definitions library (429 calls and 69 reads resolved in run 3, none in run 4; Penlight 61.6% there).
Within run 4 a second pass after invalidation (`share2`) matched exactly. The 2026-11-01 prototype run
(`shareB/`) varied in the same way on luacheck's `assert` (851 calls) and on 58 bare-receiver colon calls
in ZeroBrane's `package.lua`, which go through NAV-13's unchanged path; neither the head-write test nor
§3.2 item 1 changes either group (`r1/run4/*.guard.tsv`, `*.noskip.tsv`). A site that misses its typed
target falls back to text, so the variance moves the size of the gain, not the target a site gets when
it resolves. Cause not established (Gap 2.3).

Which runs typed the `assert` head (T) and which did not (F):

| Corpus | `shareA` | `shareB` | `r1/shareC` | `r1/shareD` |
|--------|----------|----------|-------------|-------------|
| Penlight | F | T | T | F |
| luacheck | F | F | T | T |
| ZeroBrane `package.lua` | F | T | F | F |

Findings sets exist for the runs of `r1/run1.log` to `r1/run3.log`, for `shareB` (`findings-b`) and for
the `r2/` runs; they agree wherever they measure the same phase, apart from [[BUG-541]] (DR-05). No findings set exists for a run with Penlight's
`assert` untyped: `r1/run4.log` (`r1/shareD/`) saved none, and the `r2/` runs carried no share probe, so
whether `assert` typed in them is not known. Whether the variance can move an inspection count is
therefore not established; the gate's stop rule covers it (Gap 2.3).

Why colon stays low: in the classification of `c7/`, most colon sites had no receiver type (6,953
untyped), or a chained or parenthesised shape (1,741 refused), and 868 were absent members, typically
`:find` on a `string`.

**Verdict:** calls move from 29.5% to 39.0% (40.9% in run 3). The gain is concentrated in dot calls.

### MAINT-53-00-DR-02: Cost

Measured on the 2026-11-01 prototype. None of its differences from the design was re-timed:

- It ran the file-binding queries for member names, which design §3.2 item 1 skips.
- It stopped at the head-write test that TC-23 rejects, so the design does more member lookups on heads
  with no write of their own. The snapshot that test read was already built, so this adds no snapshot
  builds.
- The resolve timings below (`c8/`) predate the final narrowing of the drop rule to a
  table-constructor local (§3.6).

Resolving all 40,210 member names, mean of two passes per arm (`c8/`):

| Measure, sum of corpora | Today | With change | Change |
|-------------------------|-------|-------------|--------|
| Cold resolve, ms | 31,983 | 37,271 | +16.5% |
| Snapshots warm, resolve only, ms | 19,392 | 23,232 | +19.8%, about 0.1 ms per name |
| Snapshot builds during cold resolve | 376 | 493 | +31% |

Highlighting the 20 largest files per corpus, mean of two passes per arm (`shareB/`):

| Corpus | Today, ms | With change, ms |
|--------|-----------|-----------------|
| lazy.nvim | 19,853 | 21,651 |
| luacheck | 12,658 | 12,282 |
| luarocks | 32,719 | 33,111 |
| Penlight | 10,232 | 11,558 |
| ZeroBrane | 30,829 | 30,123 |
| **Total** | **106,289** | **108,724 (+2.3%)** |

Two passes of one arm differ by up to 2,655 ms on one corpus, so the total is within noise. Total hits
matched except lazy.nvim (80 to 78).

Find Usages is the exposed path: it forces snapshots that a text search never needed. On luarocks, cold
first search, two passes per arm (`c1/luarocks.txt:52-53`):

| Name | Today, ms | With change, ms | Snapshot builds, today → with change |
|------|-----------|-----------------|--------------------------------------|
| `insert` | 191, 154 | 2,568, 2,491 | 0 → 73, 0 → 75 |
| `match` | 3,671, 4,139 | 4,752, 5,274 | 85 → 106, 84 → 106 |

**Verdict:** acceptable for highlighting. Find Usages is slower by seconds on first use (Risk 1.1). The
suite's budget tests pass unchanged (DR-05).

### MAINT-53-00-DR-03: resolve → infer → resolve

It occurs. `LuaTypesVisitor` resolves callees during a build (`propagateExpectedLambdaParams` through
`LuaExpectedCallbackResolver`).

| Corpus | Unguarded lookups inside own build | Unguarded max depth | Guarded inside build | Guarded max depth |
|--------|-----------------------------------|---------------------|----------------------|-------------------|
| lazy.nvim | 2,803 | 2 | 0 | 1 |
| luacheck | 969 | 2 | 0 | 1 |
| luarocks | 6,184 | 2 | 0 | 1 |
| Penlight | 1,362 | 2 | 0 | 1 |
| ZeroBrane | 1,698 | 2 | 0 | 1 |

Unguarded runs terminated with no exception, at depth 2 (`c1/`). Fixture answers matched between arms,
so the guard's observable effect is the count. Unguarded lookups rose about 50% on lazy.nvim (8,091
against 5,288) and 55% on luarocks. NAV-13 measured the same shape raising `RootAccessor.WRITE` from
572 to 812. Depth stayed 1 across files, so typed lookups did not nest through another file's build.

**Verdict:** recursion is real and bounded. The guard removes it. Control: F14 confirms the answer after
a build is not poisoned by the mid-build text answer.

### MAINT-53-00-DR-04: Fixture matrix (`c11/fixtures.txt`, `shareB/fixtures.txt`, `r1/run1/maint53/`)

Arms: `OFF` is today, `KIND` is §3.2 only, `GUARDED` is typed only, `GUARDED+KIND` is both.

```
F1  OFF ':c' -> []                     GUARDED+KIND ':c' -> [io.lua:61 function File:close]
F1  OFF ':s' -> []                     GUARDED+KIND ':s' -> [io.lua:69 function File:setvbuf]
F1  OFF ':n' -> []                     GUARDED+KIND ':n' -> []
F2  OFF '  M.c' -> [codelens.lua:4, manage.lua:2]    GUARDED '  M.c' -> [manage.lua:2]
F3B OFF '].l' -> [legacy_lib.lua:2]    KIND '].l' -> []    GUARDED '].l' -> [legacy_lib.lua:2]
F3C OFF 'return config.t' -> [c.lua:2:LeafPsiElement]    KIND -> [c.lua:2:LuaNameRefImpl]
F4  OFF usages-of-global-m=[41]        KIND []             GUARDED [41]
F9  OFF -> [f9.lua:3 function a.b.f]   GUARDED+KIND -> [f9.lua:3 function a.b.f]
F10 all arms print(a.b.c) -> []
F11 OFF '\nx.n' -> []                  GUARDED -> [f11.lua:3 function Foo.new]
F13 GUARDED guardHits=1 typedInsideBuild=0 paramType=string   UNGUARDED typedInsideBuild=1 paramType=string
F15 OFF '\nM.g' -> [lib.lua:2]         GUARDED -> []
F17 OFF '\nM.g' -> [lib.lua:2]         GUARDED -> []   debug: headIsLexical=true recv={  } | any
F19 OFF '].p' -> [builtin.lua:114 function print]      KIND -> []
F18-2 local x x = 1 -> [Unused local variable 'x']
F18-3 local t = {} local version print(t.version) -> [Unused local variable 'version']
```

Added on 2026-11-02 (`r1/run1/maint53/fixtures.txt`, `require.txt`; run `r1/run1.log`). `FINAL` is the
design as written; `FINAL-x` is `FINAL` with one mutation, named by the TC that cites it. `B1`, `M1` ran on
the real-disk `require` harness of `LuaDeprecatedBareNameThroughRequireTest`.

```
R1  FINAL            method usages m@9=[] m@41=[51]    property usages g@9=[] g@41=[51]
R1  FINAL-noProperty property usages g@9=[41]          target(g@41)=[function g()]
R1  OFF              method usages m@9=[41]            property usages g@9=[41]
R1W walk=true  TC-01 -> []   TC-02 -> []   TC-03 -> [Unused local variable 'x']
R1W walk=false TC-01 -> [Unused local variable 'x']   TC-02 -> [Unused local variable 'x']
R1C print(x) -> [line 5 'x']; x=2 -> [line 3 'x']; x=1 -> [line 1 'x']
R16 FINAL -> [lib.lua:2 function M.gone]   FINAL-ctorAny -> []
R9L FINAL -> [manage.lua:2 function M.clear(plugins)]   FINAL-noTyped -> [codelens.lua:3, manage.lua:2]
RG  guard=false g1 -> [g1.lua:2]           guard=true g1 -> [g1.lua:2, other.lua:2]
RG  guard=false g2 -> [g2.lua:2]           guard=true g2 -> [g2.lua:2, other.lua:4]
RG  g3, g4, g5, g6: identical with and without the guard
B1  OFF          line3 [lib.lua:1 insert, main.lua:5]  line4 [lib.lua:1, main.lua:5]  line5 [lib.lua:1]  line6 [lib.lua:1]  line8 [lib.lua:1]  line9 [lib.lua:2 gins, main.lua:10]  line10 [lib.lua:2]
B1  FINAL-noSkip line3 [main.lua:5]  line4 [main.lua:5]  line5 [lib.lua:1]  line6 [lib.lua:1]  line8 [lib.lua:1]  line9 [lib.lua:2, main.lua:10]  line10 [lib.lua:2]
B1  FINAL        line3 [main.lua:5]  line4 [main.lua:5]  line5 []  line6 []  line8 []  line9 [main.lua:10]  line10 []
M1  FINAL -> [manage.lua:2]   FINAL-noTyped -> [codelens.lua:3, manage.lua:2]   FINAL-noRequire -> [codelens.lua:3, manage.lua:2]
```

`B1` shows that the file bindings of a required module reach a member name and a function-name segment
whenever typed resolution and the same-file filter do not mask them (`FINAL-noSkip`); design §3.2 item 1
removes this. `RG` shows the head-write test only adds cross-file text answers for a head with no write
of its own, so the design drops it. Run 1 had the test on; run 5 (`r1/run5/`, `r1/run5.log`) repeated
every fixture with it off, and every answer outside `RG` was identical.

Added from the suite run of DR-05 (`r2/full/r2.txt`, probe tests in `r2/src/`). `CG` is the fixture of
TC-24: `local M = {}` / `M.obj = {}` / `---@param cb fun(x:string)` / `function M.obj:each(cb) end` /
`M.obj:each(function(s) return s end)` / `return M`, resolving `each` with no snapshot built, then again
after the build. `TC08` ran on the real-disk `require` harness.

```
CG   FINAL              firstResolve=[] guardHits=1 typedInsideBuild=0 paramType=unknown afterBuild=[]
CG   FINAL-noColonGuard firstResolve=[] guardHits=0 typedInsideBuild=1 paramType=unknown afterBuild=[]
TC08 FINAL              closeTargets=[]           deprecatedRanges=[]
TC08 FINAL-methodLeaf   closeTargets=[mod.lua@44] deprecatedRanges=[]
```

`CG`: the visitor resolves the suffixed colon call during the build, and the guard stops a typed lookup
there. `function M.obj:each` is not a member of `M.obj`'s type, so the call resolves to nothing after the
build as well, as it does today. `TC08`: restoring the method leaf brings back the target; the
deprecated range stays empty either way, because BUG-504's fix already removes it.

Controls that must hold, and did: F1 `io.write`, F6 `t.f`, F8 `c.x`, and F9 resolve in every arm.

**Verdict:** typed resolution and the kind fix each fix what they claim, and neither fixes the other's
bugs.

### MAINT-53-00-DR-05: Regressions and corpus deltas

**Suite.** `test --rerun --no-build-cache` on the design as written, every phase on, with the `r2/src/`
probe tests added (`r2/full/run.log`, XMLs in `r2/full/xml/`): 3,515 tests, 2 failed, 4 skipped.

| Regression | Observed | Cause |
|------------|----------|-------|
| `LuaColonCallUsageWithdrawalTest.aSameNamedGlobalFunctionLosesTheColonCallSite` | `only the method declaration's own name remains expected:<[41]> but was:<[]>` | The test pins BUG-490, as that report says |
| `LuaDeprecatedBareNameThroughRequireTest.aBareNameDoesNotMatchARequiredModulesDeprecatedMethod` | `precondition: bare 'close' resolves to M:close's name leaf` | The precondition pins BUG-512 (A) |

In the same run `LuaTypeGraphRootResolutionBudgetTest`, `LuaColonCallCrossFileFanOutTest`,
`LuaResolveCacheTest` and `LuaUnusedLocalInspectionTest` passed with no failure.

**Why Phase 1 ships first.** The 2026-11-01 prototype, which had the kind fix and not the §3.1 walk,
raised luarocks `LuaUnusedLocal` from 121 to 126 and Penlight's from 55 to 56
(`c11/TEST-net.internetisalie.lunar.corpus.LuaCorpusSweepTest.xml`). The kind fix removed accidental
reads: `.string` and `rockspec.version` were counted as reads of the locals `string` and `version`.

- **True, 4:** `local string` on line 1 of luarocks `core/vers.lua`, `search.lua`, `upload/api.lua` and
  `loader.lua`. Each file has no other use of `string` (grep on the builder).
- **False, 2:** `version` in luarocks `cmd/make.lua:88` and `rest` in Penlight `lapp.lua:221`, both read.
  The read binds to an assignment in a nested block, and the inspection tests only for a declaration
  leaf (R1C in DR-04 shows the chain). This is BUG-24. The accidental reads hid it, so §3.1 ships first.

**Corpus deltas, phase by phase, in CI's shape** (`r2/arm.sh`: record with `-PwithCorpus
-PrecordCorpusBaseline --tests '*Corpus*' --rerun --no-build-cache` and copy the recording back). Each
row is the `git diff` of the recorded baselines against the previous phase's recording; Phase 1 is
against the committed baselines. Diffs: `r2/p1.diff`, `r2/p12.diff`, `r2/p123.diff`.

| Phase | `inspection.*` rows that move | `symbol.*` rows |
|-------|-------------------------------|-----------------|
| 1 | `LuaUnusedLocal`: lazy.nvim 3 → row removed, luacheck 27 → 20, luarocks 121 → 96, Penlight 55 → 24, ZeroBrane 7 → 4 | `symbol.LuaUnusedLocal.*` only |
| 2 | `LuaUnusedLocal`: luarocks 96 → 100 | `symbol.LuaUnusedLocal.string` 46 → 50 |
| 3 | `LuaDeprecatedApi`: lazy.nvim 6 → 3 | none |

ZeroBrane's `inspection.LuaSuspiciousConcatenation` and `symbol.LuaSuspiciousConcatenation.nil` read 1,
3, 1 and 1 across the committed baselines and the three recordings: the `menu_project.lua:96`, `:136`
pair of [[BUG-541]], which no phase touches.

The findings behind each row (`r2/p1/findings/` and so on, diffed with `r1/findings/fdiff.py`) equal
those of the sweep-only runs `r1/run2.log`, `r1/run1.log` and `r1/run3.log` for the same phase, except
for the BUG-541 pair, so the earlier shape gave the same deltas. `r1/findings/findings-p123` also equals
the 2026-11-01 prototype's set (`findings-b`). The luarocks +4 are the four true
findings above; Phase 2's ratchet reports them as a regression against Phase 1's baselines. The
lazy.nvim deprecated hits are the three [[BUG-508]] reported.

**Phase 1's cleared findings, sampled.** 10 of the 69 `LuaUnusedLocal` findings Phase 1 clears, seed 53
(`r2/audit/p1cleared.py`), read by hand: in each, the local is assigned in a nested block or function
and read after it (for example Penlight `input.lua:152` `err`, luarocks `build/builtin.lua:261`
`errcode`, ZeroBrane `util.lua:334` `bytes`). Each was a false finding.

### MAINT-53-00-DR-06: Rejected alternatives

| Alternative | Measured | Result |
|-------------|----------|--------|
| `LuaTypeManager.resolveModule` for a required module, which types it through `getFileReturnType` (`LuaTypeManagerImpl.kt:281`) | Calls resolving: Penlight 53.4% against 61.6%, lazy.nvim 69.2% against 71.8%, luarocks 51.0% against 51.4% (`shareB/*.alt.tsv`) | Rejected: lacks `function M.x` members |
| Carrying `sourceElement` through `LuaGraphType.fromLuaType` | Moved `LuaTypeGoldenMemberFilesTest`, `LuaTypeGoldenShapesTest` and five NAV-13 colon tests, one of them the BUG-490 pin (`suite1.log`) | Rejected |
| Drop on any lexical head | `table.insert` usages 357 to 343 and penlight `sub` 9 to 7 (`c8/`) | Rejected: loses `local string = string` |
| No drop rule at all | `M.gone()` on a constructed local keeps `lib.lua`'s `function M.gone` (F15 `OFF`); the constructor's type has no `gone` (F17 debug: `recv={  } \| any`), so nothing else removes it | Rejected: leaves BUG-508 |
| Constructor-local drop (chosen) | `insert` 357 = 357, `printout` 143 = 143, `def_fields` 127 = 127; deprecated −3 (`c10/`) | Chosen |
| Head-write test in `headType` (no type for a head with no write of its own) | `RG` (DR-04); on the corpus it changed 348 writes (227 of them lost every target), 82 reads and 2 calls (`r1/run4/*.guard.tsv` against `r1/shareD/`) | Rejected: adds cross-file text answers, prevents nothing (TC-23) |

### MAINT-53-00-DR-07: Do BUG-490 and BUG-511 share the cause?

- **BUG-490: no, not this cause.** F4 stays `[41]` with typed resolution alone and becomes `[]` with the
  kind fix alone. Its cause is the wrong-kind cause of BUG-512 (B), (C), and Phase 2 fixes it.
- **BUG-511: no.** F5 was identical in every arm: `K` offered `[K, Knr]`, text `K`. It is a completion
  candidate defect outside `LuaNameReference`. This also executes that report's prediction: the class
  local is offered. With one candidate, `Knr` inserted `require("src.noret") Knr`, which that report
  does not describe.

### MAINT-53-00-DR-08: Are the changed targets right?

Read by hand, 2026-11-02: 30 sites per class, sampled with seed 53 from the sites whose targets differ
between `shareA/` (today) and `shareB/` (the 2026-11-01 prototype). Samples and verdicts are in
`r1/audit/` (`sample-*.txt`, `verdicts.txt`), produced by `r1/audit/classify.py` and `show.py`.

| Class | Removed target | New or kept target |
|-------|----------------|--------------------|
| Lost (calls, reads) | 30 of 30 wrong: a same-named local, parameter or loop variable | — |
| Disjoint calls | 30 of 30 wrong | 30 of 30 right |
| Disjoint reads | 30 of 30 wrong: a same-named local or parameter | 23 right through the type or the same file; 7 cross-file text matches on the receiver's name |
| Narrowed (calls, reads) | another module's same-named member, a definition behind an alias (`dir.path = core.path`), or another write of the same field | 30 of 30 right; a field written in several places keeps its declaring write |
| Gained (calls, reads) | — | 30 of 30 right |

The seven cross-file text matches come from the text fallback, which MAINT-53-08 keeps unchanged; each
is the answer text resolution already gives that key today.

Writes were sampled the same way, 30 per class with seed 53, from the sites whose targets differ between
`shareA/` and `r1/shareD/` (the design as written): `r2/audit/sample-write-*.txt`, produced by
`r1/audit/classify2.py shareA r1/shareD AD` and `r2/audit/show2.py` (`show.py` with a path fix).

| Class | Removed target | New or kept target |
|-------|----------------|--------------------|
| Gained writes | — | 30 of 30 resolve to the write itself |
| Lost writes | 30 of 30 wrong: a same-named local, parameter or function, or another module's same-named member | — |
| Narrowed writes | other writes of the same field, another module's same-named member, or a library entry | 30 of 30 right: the write `declaringNodeOf` picks (§3.4), or the library declaration for a field of a global table (`package.path`, `io.popen`). In six (#9, #12, #22, #25–27) a table constructor declares the field and the kept target is a later write, which today's text path also returns |
| Disjoint writes | 30 of 30 wrong: a same-named local or parameter, or a write other than the declaring one | No wrong target from the typed path: 27 a write of the same field in the same file; #4 a write in a required module (`config.lua:265`, typed through `require`); 2 (#15, #29) also include a cross-file write on the receiver's name, which only the text key produces |

The final design differs from `shareB/` in §3.2 item 1 and in dropping the head-write test. §3.2 item 1
changed no corpus site (`r1/run4/*.noskip.tsv` equals `r1/shareD/`). The head-write test's differences
were sampled for reads only, 12 sites (`r1/audit/sample-guard-reads.txt`), and in each the target without
the test is the member's declaration.

**Decision rule** for this audit: a wrong target produced by the typed or kind path is a design defect
and returns the plan to Step 9 review. A cross-file target that only the text key can produce is
MAINT-53-08 behaviour, not a defect of this feature. Implementation runs no re-audit; a wrong target
found later is filed as a BUG.

**Verdict:** no wrong target from the typed or kind path in the sample.

## Critical Risks

### Risk 1.1: Find Usages slows on first use
- **Impact**: seconds added to a cold search on a large project (DR-02).
- **Likelihood**: high.
- **Mitigation**: accepted, since the cost is one-time per PSI generation and the search runs in the
  background. Reconsider if Gap 2.4 finds that resolve results are not being cached.

### Risk 1.2: Changed targets may be wrong
- **Impact**: the changed sites of DR-01. A wrong one misdirects navigation, rename or an inspection.
- **Likelihood**: low: the DR-08 sample found none from the typed or kind path.
- **Mitigation**: none during implementation beyond the TCs. The residual risk is DR-08's sample size.

## Design Gaps

No gap holds a design decision for the delivered phases. Each is a deferred improvement or an audit.

### Gap 2.1: `getFileReturnType` lacks `function M.x`
- **Question**: if it included them, `requiredModuleType` could shrink to two lines.
- **Options / leaning**: engine-wide effect, so a separate feature.
- **Resolved by**: not in this feature; needs its own ID.

### Gap 2.2: A `string` value has no methods
- **Question**: absent members, typically on `string`, are a large share of colon sites (DR-01). Would
  typing them through the `string` library lift the colon share?
- **Options / leaning**: separate feature. The gain is not established.
- **Resolved by**: not in this feature; needs its own ID.

### Gap 2.3: Global-head types vary between runs
- **Question**: why does the global `assert` (luassert) type in one corpus run and not another (DR-01)?
  Within a run the answer is stable across invalidation.
- **Options / leaning**: a type-engine ordering effect outside `LuaMemberResolution`: NAV-13's untouched
  colon path varies too. A miss falls back to text, so it bounds the gain, not correctness.
- **Resolved by**: not in this feature. If it ever moves an inspection count, the corpus gate's stop
  rule (`implementation-plan.md`) catches it as a difference from DR-05.

### Gap 2.4: Is `ResolveCache` effective?
- **Question**: the second, cached pass was about as slow as the first with snapshots warm, in both arms
  (`cachedMs` against `warmSnapshotUncachedMs`, `c8/`). `LuaNameRefBaseImpl.getReference` builds a new
  `LuaNameReference` per call, and `LuaResolveCacheTest` reuses one instance, so per-call callers may
  miss the cache. Read, not executed.
- **Options / leaning**: if true, it is a cheap fix that helps all resolution and shrinks Risk 1.1.
- **Resolved by**: file as a BUG with a counter probe. Does not block this feature.

## Not Established

- Target correctness rests on a 30-per-class sample of the prototype's changes (DR-08), not a full audit,
  and the head-write test's write differences were not sampled.
- Costs were not measured in a live IDE, the highlight figures are from a headless fixture, and they
  predate the design differences DR-02 lists.
- The cause of the run-to-run variance in DR-01 (Gap 2.3).
- Whether renaming a global renames a same-named method, the consequence BUG-490 infers, was not run.

## Technical Debt & Future Work

- **TBD: chained calls** `a:m1():m2()`: no value node exists. They are most of the refused colon shapes
  in DR-01.
- **A method declared as `function a.b:m()`** is not a member of `a.b`'s type (DR-04 `CG`), so `a.b:m()`
  still resolves to nothing, as it does today.
