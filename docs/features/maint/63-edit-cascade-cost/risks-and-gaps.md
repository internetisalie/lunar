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

### Risk 1.1: A kept snapshot rooted inside a genuine `require` cycle differs from a rebuild (residual)
- **Impact and cause**: after an edit, a file can show findings that differ from those after a full
  rebuild. Inside a genuine cycle a module built during another's build caches the half-built
  answer as final, so answers depend on where the build started (BUG-102 §1.3, §4.2); base rebuilds
  from the edited file, while a kept snapshot is rooted wherever it was first built.
- **Likelihood**: none measured (DR-23); rows the oracle does not compare (design §2.6) are unmeasured.
- **Mitigation**: TC 13's oracle run at the change commit, with no margin and no re-run; a failure blocks the
  item on MAINT-66, which removes the cause.

### Risk 1.2: A consumed file reaches no tracker term, and a stale snapshot is served
- **Impact**: a wrong type, and so a wrong finding, until an unrelated change rebuilds it.
- **Likelihood**: low. Every route DR-18 constructed rebuilt under V3 or had no dependence to go
  stale; a bare `@alias` is not resolved at all (DR-14 A2). Residual: an index outside design
  §3.3's set read without reporting a URL (known case: `LuaFileBindingsIndex`, design §3.3).
- **Mitigation**: TC 1 to TC 8 and TC 14 pin the measured routes; a new index read in the engine
  follows TYPE-11's rule and reports the URL. TC 13 is not a staleness gate: its `"\n"` edit changes
  no other file's answer (DR-23).

### Risk 1.3: An index stamp moves backwards
- **Impact**: if an index rebuild restarts a stamp lower, the stamp sum can return to a value a
  tracker stored, and a stale snapshot would be kept.
- **Mitigation**: design §3.3, `LuaStampTotal.advance` step 3; requirements TC 16.

### Risk 1.4: The resolution count adds cost to every cached-value check
- **Measured** (DR-19): within run-to-run noise on warm `forFile` and on highlighting. Memoizing the
  stamp sum changed neither measurably, so it is not adopted.

### Risk 1.5: `checkTypes`' wall-clock cutoff makes luals findings depend on speed (BUG-104)
- **Cause**: `LuaTypeGraph.checkTypes` stops its fixed point after 5 s of wall-clock time, so a
  file that needs longer gets the findings the loop reached. It fired only during luals, and only
  luals cold digests differ between sweeps of one engine (DR-25).
- **How each digest comparison avoids it**:
  - TC 13 (oracle): `SnapshotCostVerdict.ORACLE_SKIPPED = setOf("luals")` (design §2.6). A luals
    rebuild read also costs about a minute (BUG-97), which the oracle budget could not carry.
  - TC 12 (alone digests): luals rows are written to `digests.tsv` but not compared: a luals file
    can become a candidate, and its alone digest can differ, on timing alone.
  - TC 11 (`luacheck` member, conformance): the cutoff did not fire in its run (DR-25).
  - TC 10 counts misses, not digests.
- **Residual**: a real change to luals findings is not caught until BUG-104 is fixed.

## Design Gaps

### Decision 2.1: Implement V3, gated by the after-edit oracle
V3 clears most cascade misses (DR-11, DR-12, DR-20) and matches a full rebuild on the measured
members (DR-23). Not taken: waiting for MAINT-66 (no measured file shows the dependence, and the
oracle fails the item if one does); a fallback to `MODIFICATION_COUNT` for frames that saw another
file's build (r10 loses to base, DR-08; v3p and v3r lose V3's gain, DR-21); relaxing the oracle
(its differences are what a user would see change on restart).

### Decision 2.2: Dumb mode by an offset, not a listener
Design §3.3 step 2 reproduces base's rebuild on dumb-mode enter and exit without subscribing to
`DumbService.DUMB_MODE`. A dumb value that changes on every read rebuilds every snapshot on every
read during indexing (DR-18 `dumbchanging`).

### Gap 2.3: The read 1 s after an edit does not match the cold digest
Answered (MAINT-65 DR-06, BUG-102 §4.1): a property of base inside the tnt* require cycles, so
MAINT-63-04 compares an edit read with a forced full rebuild of the same state, not a cold read.

### Gap 2.4: An index stamp read throws `ServiceNotReadyException` in smart mode
Answered (design §3.3 step 4). A target switch calls `PlatformLibraryIndex.reload()`, which calls
`StubIndex.forceRebuild` (`PlatformLibraryProvider.kt:118`). Until the rebuild starts, the `Stubs`
index has a non-OK `RebuildStatus` while `DumbService.isDumb` is still false, and
`FileBasedIndexImpl.ensureUpToDate` throws `ServiceNotReadyException` on every index access,
`getIndexModificationStamp` included. `resolutionCount` treats it as `IndexNotReadyException` and
returns the dumb value, so a snapshot checked in that window is rebuilt once the index is ready.
Propagating it would throw a cancellation from every unpinned snapshot's validity check after a
target switch. `LuaTypesSnapshotCacheTest.testTargetSwitchInvalidatesSnapshot` and
`StubGlobalSeedTypeTest.testTargetSwitchInvalidatesSeededSnapshot_DR_03b` fail without the catch.

## Technical Debt & Future Work
- **Library snapshots lost to GC.** Holding the files cut V1's misses further (V2, DR-11). Filed as BUG-105.
- **BUG-519** (definition-library snapshots never pinned): they take the unpinned branch and get a
  per-source tracker.

## Pre-Implementation De-risking Tasks

### Premises
| Premise | Kind | Settled by |
|---|---|---|
| Edit cascade is still a large, stable miss class after MAINT-65 | runtime | DR-09 |
| Rebuilt files are mostly non-readers of the edited file | runtime | DR-10 |
| Per-source dependencies clear most cascade misses | runtime | DR-11, DR-12, DR-20 |
| A kept snapshot has the findings of a full rebuild of the same state | runtime | DR-23; inside a genuine cycle it can be false (Risk 1.1) |
| Each consumption route records a URL, or moves an index stamp | runtime | DR-13, DR-14, DR-16, DR-18 |
| A preprocessor sees every PSI change that ticks `MODIFICATION_COUNT` | runtime | DR-15 |
| `getIndexModificationStamp` throws in dumb mode | runtime | DR-13 U8, DR-15 P8 |
| A forced rebuild read builds the read file's own snapshot, so `oracleUnforced` never fires on correct wiring | runtime | Phase 0: tntcrud, unmutated, no `oracle` line (78 compared); without `forceFullRebuild`, 52 `rebuild read built nothing` lines |
| Base's sweep-to-sweep spread is the margin of MAINT-63-03 | runtime | DR-25 |
| `checkTypes`' cutoff fires only in luals | runtime | DR-25 |
| The named APIs exist and are not `@ApiStatus.Internal` | read | design §2, §3 |

### Variants (prototype)
| Variant | Unpinned snapshot depends on |
|---|---|
| base | `MODIFICATION_COUNT` |
| svca | per-URL counters plus a signature epoch, with a fallback to `MODIFICATION_COUNT` for any snapshot that hit, or was nested under, an in-progress build |
| r10 | svca, with the fallback also set by any of the type manager's re-entrancy guards, and carried through memo replay |
| V1 | per-URL counters over the frame's `urls`, `inProgressHits` and pending frames, plus file-less PSI events and roots; a frame with absences, rescued globals or pending frames also depends on VFS structure and on an epoch that ticks on any edit that is not whitespace-only |
| V2 | V1, plus every built file's `PsiFile` held strongly |
| V3 | V1 without the VFS-structure and roots terms (DR-18 `extraterms`), and with the epoch replaced by design §3.3's resolution count. This is the design |
| v3p | V3, but a frame whose `inProgressHits` or guard hits name a file other than its own falls back to `MODIFICATION_COUNT` |
| v3r | V3, but a frame falls back when it records its own URL in `urls`, or an in-progress or guard hit raised while another file's build was innermost |

### Results
Probes ran on the libvirt builder with `test --rerun --no-build-cache`; probe code is deleted. Raw
outputs are in `~/.cache/claude-scratch/maint-63/` (`recovered/`, `m1/`, `r2probe/`, `v102/`,
`sweeps-bceb/`, `sweeps-r2/`, `tc11-r2/`), except DR-18's TC 8 leg and DR-19, recorded only in their rows. DR-10 to DR-12's
probe sweep read each file cold, appended `"\n"` (`eof`), prepended it (`bof`) or appended a global
function (`decl`), then read it with a 1 s deadline and again settled. Unit fixture names carry a `b`
(base) or `v` (V3) prefix.

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-04 | Can the dependency array length vary by branch? (`e9eb09d6`) | dependency shape | done: **no**. 3 elements for one branch and 5 for the other failed `IdempotenceChecker` in tntmetrics, tntcrud and tntqueue ("… 5 != 3 … length of CachedValue dependencies"); a fixed length passed |
| DR-08 | r10 on MAINT-65's eight-member subset (`e9eb09d6`) | the fallback rule | done. Misses 298 of 338, against base 281. Provisional builds outnumbered per-file builds about 8:1 in the edit phase; only the module guard fired |
| DR-09 | Re-measure from MAINT-65's acceptance sweeps (`r2-p1-sweep1/2` at its Phase 1 commit, `r2-c-sweep1/2` at its change), attributing each miss by `SnapshotCostVerdict`'s rule | Decision 2.1 | done. **Change sweeps, edit misses 293 / 311**: edit cascade (MAINT-63) **150 / 154**; slow dependency (MAINT-61) 113 / 127; own build (MAINT-61) 28 / 28; BUG-97 1 / 1; unmeasured (BUG-96) 1 / 1. Before MAINT-65, edit cascade was 248 / 288 of 458 / 481. 123 files are edit cascade in both change sweeps, 58 in one |
| DR-10 | Classify each edit-phase rebuild by the previous snapshot's frame, on telescope, gitsigns, blinkcmp, differ, luals and snabb at base (`4035acf4`) | mechanism | done. Over the 182 missed reads, rebuild self-time went to non-readers of the edited file 74% (library or checkout files 66%, project files 8%), readers 17%, the edited file 5%, pinned files 1% |
| DR-11 | V1 and V2 on gitsigns, telescope, blinkcmp, differ, snabb (348 edit reads, `eof`) | MAINT-63-03 effect, soundness | done. **Misses: base 140, V1 43, V2 25.** Settled digests that differ from base's: 0. Soundness: 10/10 rebuilt per member. In V1's remaining misses, 46% of rebuild time was a file whose `PsiFile` or snapshot had been collected |
| DR-12 | V3 on gitsigns and telescope, `eof`, `bof` and `decl` edits; record which index stamps tick | design §3.3 | done. Misses, base → V3: gitsigns `eof` 27 → 9, `bof` 26 → 9; telescope `eof` 27 → 7, `bof` 19 → 4. On `eof` and `bof` none of design §3.3's indexes ticked; `decl` ticked `Stubs` and `lunar.global.assignment` on every edit. Settled digests that differ from base's: 0. Soundness: 10/10 rebuilt in every V3 run |
| DR-13 | Unit fixtures under base and V3, and mutants | TC 1 to TC 8 | done. **U1** (TC 1): b's frame `urls=[package.lua, va.lua]`; after the edit to `c`, b kept: base `false`, V3 `true`. **T1** (TC 2): `x=number`, then `x=string`. **U3** (TC 3): `abs=[global:GLOBAL_M63v]`, `ticked=[lunar.global.assignment, lunar.receiver.member]`, `rebuilt=true y=string`; index epoch removed: `rebuilt=false y=any`. **U6** (TC 5): q2's frame `urls=[package.lua, vp2.lua] inProg=[vp2.lua] abs=[module:vp2] pending=1`, rebuilt; **M6**, pending frames not walked: `rebuilt=false`. **U7b** (TC 6): rebuilt after `dropPsiCaches`; file-less events ignored: not rebuilt. Publishing `LuaSettingsChangedListener.TOPIC` made the next index read throw `ServiceNotReadyException` (a `ProcessCanceledException`). **U8** (TC 7): in dumb mode V3 without a dumb branch threw `IndexNotReadyException`; with one, it read fine |
| DR-14 | `@alias` and late-class routes, unmodified `4035acf4` | Risk 1.2, TC 4 | done. **A1**, hosted alias `string` → `number`: `ticked=[Stubs]`, type follows. **A2**, bare alias: `undefined` before and after, `ticked=[]`. **A3** (TC 4), bare class declared later: `{ ... }` → `M63Late`, `ticked=[lunar.luacats.typename, lunar.receiver.member]`. **A4**, hosted class field: `ticked=[Stubs]`, type follows |
| DR-15 | Does a `PsiTreeChangePreprocessor` filtered by `canAffectPsi` see every `MODIFICATION_COUNT` tick? Unmodified `4035acf4`; `dMC` is the change in `MODIFICATION_COUNT` | design §2.3, §3.4 | done. **P1** append: `dMC=4 affecting=4 nullFile=0 urls=[a.lua]`. **P1b** `setText`: `dMC=6 affecting=6`. **P2** add a file: `dMC=7 affecting=7 nullFile=3`. **P3** rename: `dMC=1 affecting=1 nullFile=1`. **P4** delete: `dMC=2 affecting=2 nullFile=2`. **P5** `dropPsiCaches`: `dMC=1 affecting=1 nullFile=1`. **P6** `dropResolveCaches`: `dMC=0`. **P7** roots change: `dMC=1 affecting=1 nullFile=1`. **P8** dumb mode: `dMC=4 affecting=2 nullFile=2 dumbEvents=2`, and `getIndexModificationStamp` threw `IndexNotReadyException` |
| DR-16 | Callee-resolution routes inside a build (`LuaExpectedCallbackResolver`), unmodified `4035acf4`, when the declaration's `@param` changes | Risk 1.2 | done. **L1**, global function: `x=string` → `number`, frame `urls=[ga.lua, builtin.lua] inProg=[gb.lua]`, `ticked=[Stubs]`. **L2** (required module function) and **L3** (class method): `undefined` before and after, each frame records the declaring file, `ticked=[Stubs]`. **L4**, same-file local: `string` → `number` |
| DR-17 | Two full sweeps at `4035acf4` | base's sweep-to-sweep spread | done. On the 2 005 rows the two share: `edit-250` 308, 286; `cold-250` 42, 44. The edit spread of 22 is not comparable with DR-25's: the first sweep lost its luals rows to `StackOverflowError` (BUG-103), and neither sweep sampled sim-app or other JVMs' CPU time |
| DR-18 | Unit matrix: 15 fixtures, each under every variant and mutant configuration, in one JVM with the preprocessor registered programmatically. Each reads the type, edits, reads it again (`inc`), forces a full rebuild and reads it (`reb`); `inc ≠ reb` is stale | TC 1 to TC 8, TC 14; mutants | done. Base, V3, v3p: every fixture `inc = reb`. Ticked ids: `req` none; `gtab` global.assignment; `gstr` global.assignment + receiver.member; `gfunc` Stubs + global.assignment; `lclass` typename + receiver.member; `lclass0` typename; `method` Stubs + receiver.member; `mfield2` (`x` string → undefined) receiver.member + member.field; `localfn` Stubs, frame records the module; `newfile` Stubs. **Stale under**: `ownonly`, `nosource` (`req`); drop global.assignment (`gtab`); drop typename (`lclass0`); typename + receiver.member (`lclass`, `lclass0`); Stubs + global.assignment (`gtab`, `gfunc`); Stubs + receiver.member (`method`); receiver.member + member.field (`mfield2`); Stubs + `ownonly` (`req`, `localfn`); Stubs + `nonull` (`newfile`); `noindex`; `const`. **Not stale under any single drop of Stubs, receiver.member or member.field.** `extraterms` changed nothing. `nopending`: the cycle fixture kept. Dumb mode: `keptWhileDumb` true, false under `dumbchanging`; `nooffset` no different. A pending frame with `unreplayedWarm`, an unidentified URL, or a nested such frame moved the count on an unrelated edit; under `nodegrade` none did. TC 8: `MODIFICATION_COUNT` for warm and unidentified frames; `nowarm` and `noident` give that frame a tracker |
| DR-19 | Per-read cost: warm `forFile` × 200 000 and `doHighlighting` on a generated file (30 `require`d modules, 1 500 typed locals), base, V3, V3 + memo, each twice | Risk 1.4 | done. Warm `forFile`: base 75.7 / 71.5 µs, V3 79.4 / 75.5, memo 74.3 / 78.0. 1 510 tracker checks per highlighting pass. Highlight median: base 24 / 21 ms, V3 25 / 23, memo 23 / 21. Append + highlight: about 4.1 s in all |
| DR-20 | After-edit oracle on tntcrud, tntqueue, tntmetrics, gitsigns, telescope at `4035acf4`: cold pass, `"\n"` edit pass (1 s reads), then per file a forced full rebuild and a 60 s read; compare findings digests | MAINT-63-04 (c) | done. Compared / differing: base 263 / 0; **V3 265 / 21** (tntcrud 20, tntmetrics 1); `nosource` 268 / 39; `const` 267 / 37. A rebuild forced by `dropPsiCaches` alone serves V3's own invalidation; one forced by the target tracker alone leaves the type manager's caches (base: 30 differences); both ticks are needed |
| DR-21 | v3p and v3r, same oracle | Decision 2.1 | done. v3p 262 / 9 differing, edit misses 51; v3r 263 / 9, misses 39; both lose V3's gain on gitsigns and telescope (v3p 25 and 25, v3r 26 and 13, V3 19 and 3) |
| DR-23 | DR-20's oracle at `1f5f43e4` with the V3 prototype, whose engine differs from DR-20's only by BUG-102's resolver rule; DR-20's differences were cycles that rule fabricated (BUG-102 §4.1). `base` is the prototype switched to base | Decision 2.1, Risk 1.1 | done. Compared / differing (tntcrud, tntqueue, tntmetrics, gitsigns, telescope): base 78/0, 14/0, 42/0, 56/0, 73/0; **V3** 78/0, 14/0, 42/0, 60/0, 73/0; `nosource` 78/0, 14/0, 42/0, 61/0, 73/0; `const` 78/0, 14/0, 42/0, 61/0, 73/0. An appended `"\n"` changes no other file's answer, so `nosource` and `const` do not turn the oracle red at this engine |
| DR-24 | Two full sweeps (`test -PwithSnapshotSweep --tests '*SnapshotCostSweep*' --rerun --no-build-cache`) at unmodified `1f5f43e4`; sim-app sampled each minute, none ran | base's sweep-to-sweep spread before BUG-103's fix | done. All 2 026 rows, luals present in both: **`edit-250` 268, 261; `cold-250` 53, 52**. One cold digest differs between the two: luals `script/vm/infer.lua` |
| DR-25 | Four full timing sweeps (DR-24's command) at unmodified `bceb8e3e`, sampled each minute: 46 samples, all clean (no sim-app process, and no JVM whose arguments contain neither `/home/builder/lunar` nor `/opt/cache/gradle`; 1-minute load 0.03 to 3.97). Phase 2's sample rule is narrower in what it excludes. Then TC 11's command once, its `idea.log` kept | MAINT-63-03 margins, Risk 1.5 | done. All 2 026 rows, luals present in all four: **`edit-250` 276, 265, 266, 265; `cold-250` 54, 55, 51, 52. Margins: edit 11, cold 4**, the spread of the four. The only cold digests that differ between any two are luals `script/vm/compiler.lua` and `script/vm/infer.lua`. `Type checking exceeded time limit` was logged once in each of two other sweeps of this commit (`sweeps-bceb/`, not margins: their samples counted other JVMs by an older rule), during `testM00aLuals` both times, and not at all in TC 11's run. The spread at `1f5f43e4` is DR-24's |
| DR-26 | TC 11's command three times at `353974b7` and three times at `8dad422c`, with `luacheck.json` kept from each run | TC 11 | done. `cells` and `records` were identical in all six. `coverage.unresolvedCallee` was 1777, 1776 and 1777 at base and 1776, 1777 and 1777 at the change; `noTag` moved by the same one. TC 11 leaves those two counts out (BUG-106). Kotlin daemon pid 114758, idle, added about 0.13 CPU-seconds a minute during a discarded base sweep, which the earlier sample rule counted as dirty |
