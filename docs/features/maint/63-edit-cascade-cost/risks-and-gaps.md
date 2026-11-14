---
id: "MAINT-63-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-63"
folders:
  - "[[features/maint/63-edit-cascade-cost/requirements|requirements]]"
---

# MAINT-63: Risks & Gaps

## Critical Risks

### Risk 1.1: A kept snapshot differs from a rebuild of the same state (blocking)
- **Impact**: after an edit, a file can show findings that differ from the findings it shows once
  everything is rebuilt, for example after a restart. MAINT-63-04's oracle (requirements TC 13)
  fails on it.
- **Likelihood**: measured, not hypothetical. V3, the design, served 21 such snapshots in 265
  compared reads on five members, all in tntcrud and tntmetrics; base served 0 (DR-20).
- **Cause**: the type engine's answers inside a `require` cycle depend on which file the build
  started from (MAINT-65 DR-06). Base rebuilds every unpinned snapshot from the edited file, so
  its answers are always rooted there. A kept snapshot is rooted wherever it was first built.
- **Mitigations tried**: falling back to `MODIFICATION_COUNT` when a frame records an in-progress
  or guard hit on another file (v3p), or a foreign hit or its own URL (v3r), left 9 differences
  and gave up the gain on gitsigns and telescope (DR-21). The differing files include some whose
  frames record no in-progress hit, guard hit or cycle at all (`crud/stats/registry_utils.lua`), so
  no rule over today's frames separates them.
- **Mitigation**: none within this item. Decision 2.1 holds the item on DR-22.

### Risk 1.2: A consumed file reaches no tracker term, and a stale snapshot is served
- **Impact**: a wrong type, and therefore a wrong finding, until an unrelated change rebuilds it.
- **Likelihood**: low on the routes measured. Each route constructed in DR-18 either rebuilt under
  V3 or had no dependence to go stale: require, late global (table, string, function), late class
  (with and without fields), late method, member-field multiplicity, local function in a required
  module, in-progress cycle, new file, `dropPsiCaches`, roots change. A bare `@alias` is not resolved
  by the type engine at all (DR-14 A2).
- **Residual**: a route that consumes another file through an index outside design §3.3's set
  without reporting its URL. The known case is `LuaFileBindingsIndex`, through
  `LuaNameReference.queryFiles` (design §3.3, Index choice).
- **Mitigation**: requirements TC 1 to TC 8 and TC 14 pin the measured routes. A new reporting site
  or index read in the type engine follows TYPE-11's rule: report the URL.

### Risk 1.3: An index stamp moves backwards
- **Impact**: if an index rebuild restarts a stamp below its earlier value, the sum could repeat a
  stored value, and a stale snapshot would be kept.
- **Mitigation**: design §3.3 step 4 keeps the last value of each stamp and ticks the structure
  epoch when any one decreases, so a decrease always moves the sum.

### Risk 1.4: The resolution count adds cost to every cached-value check
- **Measured** (DR-19): about 4 µs per warm `forFile` call, against 72 to 76 µs for the call in
  base. A highlighting pass over a 3 030-line file with 30 required modules made 1 510 checks and
  took 21 to 28 ms, against 20 to 25 ms in base. Editing that file and re-highlighting took about
  4 s in both. Memoizing the stamp sum per PSI and VFS modification count removed every stamp read
  and changed neither time measurably, so it is not adopted.

## Design Gaps

### Decision 2.1: MAINT-63 is held (blocked on DR-22)
- **Decision**: hold. Per-source dependencies clear most cascade misses (DR-11, DR-12), but they
  keep snapshots whose findings differ from a full rebuild of the same state (Risk 1.1), and no
  fallback rule over today's frames removes the difference without removing the gain (DR-21).
  The item resumes when DR-22 makes a snapshot's answers independent of where its build started,
  or makes that dependence visible in its frame.
- **Not taken**: closing MAINT-63. Edit cascade is still the largest single cause of `edit-250`
  misses (DR-09), and the mechanism is measured and ready behind DR-22.
- **Not taken**: shipping V3 with the oracle relaxed. Its differences are what a user would see
  change on restart, which is the class of defect MAINT-65 removed.

### Decision 2.2: Dumb mode by an offset, not a listener
- **Decision**: design §3.3 step 2. It reproduces base's rebuild on dumb-mode enter and exit
  without subscribing to `DumbService.DUMB_MODE`.
- **Not taken**: a dumb value that changes on every read. It rebuilds every snapshot on every read
  during indexing (DR-18, `dumbchanging`: `keptWhileDumb=false`).

### Gap 2.3: The read 1 s after an edit does not match the cold digest
- **Answered (MAINT-65 DR-06)**: a property of base, dependent on build order inside the tnt*
  require cycles. This is why MAINT-63-04 compares an edit read with a forced full rebuild of the
  same state (TC 13), not with a cold read.

## Technical Debt & Future Work
- **Library snapshots lost to GC.** In V1's remaining misses, 46% of rebuild time was a file whose
  `PsiFile` or cached value had been collected, and holding the files cut V1's misses from 43 to 25
  (V2, DR-11). DR-05's "not a driver" was measured under svca, whose provisional fallback rebuilt
  most of those files anyway, and does not apply to this design. The contract forbids hard
  references to `PsiFile`, so this needs its own item and design, for example a soft-reference
  policy.
- **BUG-519** (definition-library snapshots never pinned). They take the unpinned branch and get a
  per-source tracker, which DR-10 shows is where most of the rebuild time was.
- **luals `StackOverflowError` in the sweep.** `testM00aLuals` failed with `StackOverflowError` (at
  `HashMap.java:671`) in two of the six sweeps of this engine code, and those sweeps have no luals
  rows (DR-17). Filed as BUG-103. Requirements MAINT-63-03 counts around it.

## Pre-Implementation De-risking Tasks

### Premises
| Premise | Kind | Settled by |
|---|---|---|
| Edit cascade is still a large, stable miss class after MAINT-65 | runtime | DR-09 |
| Rebuilt files are mostly non-readers of the edited file | runtime | DR-10 |
| Per-source dependencies clear most cascade misses | runtime | DR-11, DR-12, DR-20 |
| A kept snapshot has the findings of a full rebuild of the same state | runtime | DR-20: **false** for V3; DR-21: false for every fallback tried |
| Each consumption route records a URL, or moves an index stamp | runtime | DR-13, DR-14, DR-16, DR-18 |
| A preprocessor sees every PSI change that ticks `MODIFICATION_COUNT` | runtime | DR-15 |
| `getIndexModificationStamp` throws in dumb mode | runtime | DR-13 U8, DR-15 P8 |
| The base margins come from four sweeps of one engine | runtime | DR-17 |
| The named APIs exist and are not `@ApiStatus.Internal` | read | design §2, §3 |

### Variants (prototype)
| Variant | Unpinned snapshot depends on |
|---|---|
| base | `MODIFICATION_COUNT` |
| V1 | per-URL counters over the frame's `urls`, `inProgressHits` and pending frames, plus file-less PSI events and roots. A frame with absences, rescued globals or pending frames also depends on VFS structure and on an epoch that ticks on any edit that is not whitespace-only |
| V2 | V1, plus every built file's `PsiFile` held strongly |
| V3 | V1 without the VFS-structure and roots terms (DR-18 `extraterms`), and with the epoch replaced by design §3.3's resolution count. This is the design |
| v3p | V3, but a frame whose `inProgressHits` or guard hits name a file other than its own falls back to `MODIFICATION_COUNT` |
| v3r | V3, but a frame falls back when it records its own URL in `urls` (a cycle seen through replay), or an in-progress or guard hit raised while another file's build was innermost |

### Results
Probes ran on the libvirt builder at `4035acf4`, with `test --rerun --no-build-cache`. DR-14 to
DR-16 ran unmodified `4035acf4` with a throwaway test; the others ran the prototype. All probe code
is deleted; raw outputs are kept in the planner's scratch (`~/.cache/claude-scratch/maint-63/`:
`recovered/raw-dr13-16.txt`, `m1/`, `r2probe/`). In DR-10 to DR-12 a probe sweep read each file
cold, appended `"\n"` (`eof`), prepended it (`bof`) or appended a global function (`decl`), read it
with a 1 s deadline, then read it settled. A miss is an edit read at 250 ms or more, or one that did
not finish. A soundness pass emptied a temp-copy dependency of the file, re-read it and restored
it. The unit fixtures' file names carried a `b` (base) or `v` (V3) prefix.

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-09 | Re-measure from MAINT-65's acceptance sweeps (`r2-p1-sweep1/2` at its Phase 1 commit, `r2-c-sweep1/2` at its change), attributing each miss by `SnapshotCostVerdict`'s rule | Decision 2.1 | done. **Change sweeps, edit misses 293 / 311**: edit cascade (MAINT-63) **150 / 154** (measured 101 / 116, timeout 49 / 38); slow dependency (MAINT-61) 113 / 127 (measured 7 / 8, timeout 106 / 119); BUG-97 1 / 1; own build (MAINT-61) 28 / 28; unmeasured 1 / 1 (BUG-96). Cold misses 47 / 51: own 23 / 22, slow dependency 13 / 14, reach 11 / 15. **Before MAINT-65**, edit misses 458 / 481, of which edit cascade 248 / 288. **Stability**: 181 files are edit cascade in at least one change sweep. 123 are in both; 46 are in one sweep and pass in the other; 12 are edit cascade in one and another cause in the other. Of the 407 passes in MAINT-60's file table for this item, 263 now pass in both sweeps, 96 are stable edit cascade, 44 flicker and 4 are MAINT-61. In the stable rows the median edit read is 768 ms with 50 builds; the root's own build has a median of 37 ms and the slowest dependency 104 ms. A passing edit read has a median of 1 build |
| DR-10 | Classify each edit-phase rebuild by the previous snapshot's frame, on six members (telescope, gitsigns, blinkcmp, differ, luals, snabb) at base | mechanism | done. Over the 182 missed reads, rebuild self-time went to: **non-readers of the edited file 74%** (library or checkout files 66%, project files 8%), nearly all of them carrying rescued globals, in-progress hits or absences; readers 17% (luals is mostly readers: 15.7 of 25.7 s); the edited file itself 5%; pinned files 1%. An unpinned snapshot depends on `MODIFICATION_COUNT`, so the edited file's whole reach is rebuilt, including files that never consumed it |
| DR-11 | V1 and V2 on five members (gitsigns, telescope, blinkcmp, differ, snabb; 348 edit reads, `eof`) | MAINT-63-03 effect, soundness | done. **Misses: base 140, V1 43** (snabb 11 → 6, gitsigns 26 → 10, telescope 31 → 6, blinkcmp 50 → 10, differ 22 → 11). V2 25. Settled digests that differ from base's: 0. Soundness: 10/10 rebuilt in snabb's first run, and 10/10 per member in the second. V1's epoch never ticked in the edit phase. In V1's remaining misses, 46% of rebuild time was a file with no cached value (its `PsiFile` or snapshot collected) |
| DR-12 | V3 on gitsigns and telescope, `eof`, `bof` and `decl` edits; record which index stamps tick | design §3.3 | done. Misses, base → V3: gitsigns `eof` 27 → 9, `bof` 26 → 9; telescope `eof` 27 → 7, `bof` 19 → 4. On `eof` and `bof` none of the five epoch indexes ticked; on `bof` only `lunar.luacats.descriptions` ticked; `FileBindings` did not tick. `decl` (gitsigns only) ticked `Stubs` and `lunar.global.assignment` on every edit, and V3 missed 26 of 61; base was not run on `decl`. Settled digests that differ from base's (`eof`, `bof`): 0. Soundness: 10/10 rebuilt in every V3 run |
| DR-13 | Unit fixtures under base and V3, and mutants | TC 1 to TC 8 | done. **U1** (requirements TC 1): b's frame `urls=[package.lua, va.lua]`; after the edit to `c`, b kept: base `false`, V3 `true`. **U4**, a `bof` edit of `a`: b rebuilt and c kept under V3. **T1** (TC 2): `x=number`, edit `ticked=[]`, after `x=string`. **U3** (TC 3): frame `abs=[global:GLOBAL_M63v]`, edit `ticked=[lunar.global.assignment, lunar.receiver.member]`, `rebuilt=true y=string`; **M3**, the index epoch removed: `rebuilt=false y=any`. **U5**, a require cycle: q's frame `urls=[package.lua, vp.lua] inProg=[vp.lua] abs=[module:vp] pending=1`, rebuilt. **U6** (TC 5): q2's frame `urls=[package.lua, vp2.lua] inProg=[vp2.lua] abs=[module:vp2] pending=1`, rebuilt; **M6**, pending frames not walked: `rebuilt=false`. **U7b** (TC 6): rebuilt after `dropPsiCaches`; **M7b**, file-less events ignored: not rebuilt. Publishing `LuaSettingsChangedListener.TOPIC` made the next index read throw `ServiceNotReadyException` (a `ProcessCanceledException`); after `IndexingTestUtil.waitUntilIndexesAreReady` the snapshot was rebuilt. **U8** (TC 7): in dumb mode, base reads fine; V3 without a dumb branch threw `IndexNotReadyException`; with one, it read fine |
| DR-14 | `@alias` and late-class routes, unmodified `4035acf4`: frame contents and ticked indexes | Risk 1.1, TC 4 | done. **A1**, hosted alias `string` → `number`: frame `urls=[alias.lua, builtin.lua]`, `ticked=[Stubs]`, type `string` → `number`. **A2**, bare alias: type `undefined` before and after, frame `urls=[builtin.lua]`, `ticked=[]`. **A3** (TC 4), bare class declared later: `{ ... }`, frame `urls=[builtin.lua] abs=[]`, `ticked=[lunar.luacats.typename, lunar.receiver.member]`, after `M63Late`. **A4**, hosted class field `number` → `string`: frame `urls=[fclass.lua, builtin.lua]`, `ticked=[Stubs]`, type follows |
| DR-15 | Does a `PsiTreeChangePreprocessor` (filtered by `PsiModificationTrackerImpl.canAffectPsi`) see every `MODIFICATION_COUNT` tick? Unmodified `4035acf4`; `dMC` is the change in `MODIFICATION_COUNT` | design §2.3, §3.4 | done. **P1** append to `a`: `dMC=4 affecting=4 nullFile=0 urls=[a.lua]`. **P1b** `setText`: `dMC=6 affecting=6 nullFile=0`. **P2** add a file: `dMC=7 affecting=7 nullFile=3`. **P3** rename: `dMC=1 affecting=1 nullFile=1`. **P4** delete: `dMC=2 affecting=2 nullFile=2`. **P5** `dropPsiCaches`: `dMC=1 affecting=1 nullFile=1`. **P6** `dropResolveCaches`: `dMC=0 affecting=0`. **P7** roots change: `dMC=1 affecting=1 nullFile=1`. **P8** `runInDumbModeSynchronously`: `dMC=4 affecting=2 nullFile=2 dumbEvents=2`, and `getIndexModificationStamp` inside threw `IndexNotReadyException`. Every tick is a preprocessor event or a dumb-mode transition |
| DR-16 | Callee-resolution routes inside a build (`LuaExpectedCallbackResolver`), unmodified `4035acf4`: frame and ticks when the declaration's `@param` changes | Risk 1.1 | done. **L1**, global function: `x=string`, frame `urls=[ga.lua, builtin.lua] inProg=[gb.lua]`, `ticked=[Stubs]`, after `x=number`. **L2**, required module function, and **L3**, class method: `x=undefined` before and after (not seeded), each frame records the declaring file, `ticked=[Stubs]`. **L4**, a same-file local (control): `string` → `number` |
| DR-17 | Base spread for MAINT-63-03: four full sweeps of one engine, MAINT-65's change sweeps `r2-c-sweep1/2` (`6b5eb24b`) and two new sweeps at `4035acf4` (the same engine code) | MAINT-63-03 margins | done. The first `4035acf4` sweep has no luals rows: `testM00aLuals` failed with `StackOverflowError`, as it did in MAINT-65's `r2-p1-sweep1`. Over the 2 005 rows of members present in all four sweeps, **`edit-250` misses 272, 290, 308, 286 (spread 36)** and **`cold-250` misses 46, 50, 42, 44 (spread 8)**. Over all rows of the three complete sweeps: edit 293, 311, 307; cold 47, 51, 45 |
| DR-18 | Unit matrix: 15 fixtures × 22 variant and mutant configurations in one JVM, with the preprocessor registered programmatically. Each fixture reads the type, edits, reads it again (`inc`), forces a full rebuild (target tracker tick plus `dropPsiCaches`) and reads it (`reb`); `inc ≠ reb` is stale | requirements TC 1 to TC 8, TC 14; mutants | done. Base, V3 and v3p: every fixture `inc = reb`. Ticked ids per fixture: `req` none; `gtab` (`G = {}` declared later) global.assignment only; `gstr` global.assignment + receiver.member; `gfunc` Stubs + global.assignment; `lclass` typename + receiver.member; `lclass0` (bare `---@class`, no field) typename only; `method` (late `function K:run()`) Stubs + receiver.member; `mfield2` (a second `O.f = O.f` target makes `resolve()` ambiguous, `x` string → undefined) receiver.member + member.field; `localfn` (a local function in a required module, `@param` string → number) Stubs, frame records the module; `newfile` Stubs. **Stale under**: `ownonly` and `nosource` (`req`); `drop:global.assignment` (`gtab`); `drop:typename` (`lclass0`); typename + receiver.member (`lclass`, `lclass0`); Stubs + global.assignment (`gtab`, `gfunc`); Stubs + receiver.member (`method`); receiver.member + member.field (`mfield2`); Stubs + `ownonly` (`req`, `localfn`); Stubs + `nonull` (`newfile`); `noindex` (7 fixtures); `const` (10). **Not stale under any single drop of Stubs, receiver.member or member.field**: each of their answer-changing ticks came with another id, a recorded source or a file-less event. `nonull`: `dropPsiCaches` kept the snapshot (the answer cannot change there). VFS-structure and roots terms (`extraterms`) changed nothing, and a roots change rebuilt even under `const`, through another dependency. `nopending`: the cycle fixture kept (its answer is `undefined` either way). Dumb mode: `keptWhileDumb` true, false under `dumbchanging`; `nooffset` no different. Degradation: a pending frame with `unreplayedWarm`, an unidentified URL, or a nested such frame moved the count on an unrelated edit; under `nodegrade` none did. TC 8 frames: `MODIFICATION_COUNT` for warm and unidentified; `nowarm` and `noident` each give the respective frame a tracker |
| DR-19 | Per-read cost (Risk 1.4): warm `forFile` × 200 000 and `doHighlighting` on a generated file (30 `require`d modules, 1 500 typed locals), base, V3, V3 + memo, each twice | Risk 1.4 | done. Warm `forFile`: base 75.7 / 71.5 µs, V3 79.4 / 75.5, memo 74.3 / 78.0. 1 510 tracker checks per highlighting pass. Highlight median: base 24 / 21 ms, V3 25 / 23, memo 23 / 21. Append + highlight: about 4.1 s in all |
| DR-20 | After-edit oracle on tntcrud, tntqueue, tntmetrics, gitsigns, telescope: cold pass, `"\n"` edit pass (1 s reads), then per edited file a forced full rebuild (target tracker tick plus `dropPsiCaches`) and a 60 s read; compare findings digests. A second leg emptied one temp-copy dependency, compared the incremental read with a forced rebuild, and restored it | B1, MAINT-63-04 | done. Compared / differing: **base 263 / 0**; **V3 265 / 21** (tntcrud 20, tntmetrics 1); `nosource` 268 / 39; `const` 267 / 37. Edit misses on these members: base 43 to 61 over three runs (telescope alone 15 to 34), V3 23, `nosource` 15. The second leg found 0 differences for base, V3 and `nosource`, since emptying a file moves index stamps, and 24 of 219 for `const`; it is not a falsifier of per-source counters and is not planned. A rebuild forced by `dropPsiCaches` alone serves V3's own invalidation, and one forced by the target tracker alone leaves the type manager's caches (base: 30 differences, reads of 0 to 7 ms); both ticks are needed |
| DR-21 | Fallbacks for Risk 1.1, same oracle | Decision 2.1 | done. **v3p: 262 / 9 differing, edit misses 51. v3r: 263 / 9, misses 39.** Both lose V3's gain on gitsigns and telescope (v3p 25 and 25, v3r 26 and 13, against V3's 19 and 3). Their differing files include `crud/stats/registry_utils.lua` and `crud/stats/atomic_batch.lua`, whose edit-read frames record no in-progress hit, guard hit, foreign hit or own URL |
| DR-22 | Find why a kept snapshot with a clean frame (DR-21's `registry_utils.lua`) differs from a rebuild rooted at the edited file, and either make its answers independent of the build root or record the dependence in its frame | Decision 2.1, Risk 1.1 | todo. Filed as BUG-102 |

### Probes before MAINT-65
These ran at `e9eb09d6` on eight members (telescope, gitsigns, blinkcmp, kong, apisix, tntmetrics,
tntcrud, tntqueue). **svca** depended on per-URL counters plus a signature epoch, with a fallback to
`MODIFICATION_COUNT` for any snapshot that hit, or was nested under, an in-progress build. **svcr**
was svca with every library `PsiFile` held strongly. **r10** was svca with the fallback also set by
any of the type manager's re-entrancy guards, and carried through memo replay.

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-04 | Can the dependency array length vary by branch? | dependency shape | done: **no**. Returning 3 elements for one branch and 5 for the other failed `IdempotenceChecker` in tntmetrics, tntcrud and tntqueue ("… 5 != 3 … length of CachedValue dependencies"). A fixed length passed |
| DR-05 | Is library snapshot loss a cost driver under svca? | svcr | done. svca's edit-phase library rebuilds were mostly a new `PsiFile` with no cached value. Holding the files (svcr) removed those rebuilds, but misses stayed at 161 of 338, because svca's fallback rebuilt the same files. See Technical Debt for the effect under V1 |
| DR-08 | r10 on the subset | the fallback rule | done. Misses 298 of 338, against base 281. Provisional builds outnumbered per-file builds about 8:1 in the edit phase; only the module guard fired. Settled after-edit digests: 0 differ from base |

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
