---
id: "MAINT-63"
title: "63: Edit-Cascade Cost of After-Edit Reads"
type: "feature"
status: "in_progress"
priority: "high"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-63: Edit-Cascade Cost of After-Edit Reads

## Overview
An edit discards the cached type snapshot of every unpinned file: it depends on
`PsiModificationTracker.MODIFICATION_COUNT` (`LuaTypesSnapshot.churnDependencyFor`,
`LuaTypes.kt:407`), so the next read rebuilds the file's whole reach, mostly files that never
consumed the edited one (risks DR-10). After MAINT-65 this is still the largest single cause of
`edit-250` misses (DR-09). This item makes an unpinned snapshot depend on the sources it recorded,
so an edit discards only the snapshots that consumed the edited file or whose resolution answers it
can change (measured on subsets: DR-11, DR-12, DR-20). A kept snapshot rooted inside a genuine
`require` cycle can still differ from a full rebuild (MAINT-66); MAINT-63-04's oracle gates it
(Risk 1.1). Filed by MAINT-60 Phase 3; every pass this item owns is named in
[files-over-target.md](files-over-target.md) (MAINT-60-02), which Phase 2 regenerates. v0.21.21 is
held on this item. Parent: [[features/maint/requirements|MAINT]]. Measurement feature:
[[MAINT-60]]. Prior item: [[MAINT-65]].

## Scope
- In scope: the churn dependency of an **unpinned** snapshot, and the sweep's after-edit oracle.
  The pinned branch (TYPE-11), `forFile`'s other two dependencies and the dependency count are
  unchanged.
- Out of scope: own build cost (MAINT-61), cold reach (MAINT-62), findings-changing work
  (MAINT-64), build-root dependence inside genuine `require` cycles (MAINT-66), the `checkTypes`
  time cutoff (BUG-104), and keeping library `PsiFile`s alive across GC (risks, Technical Debt).

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-63-01 | **An edit discards only the snapshots it can affect** | M | Full | After an edit to file F, a cached unpinned snapshot stays valid when F is not among its recorded sources and none of design §3.3's resolution terms changed. Recorded sources: the frame's `urls` and `inProgressHits`, the file's own URL, and the same sets of every in-progress build it read (design §3.2). |
| MAINT-63-02 | **No stale snapshot** | M | Full | An unpinned snapshot is rebuilt when one of its recorded sources changes, when a change can alter one of design §3.3's index answers, and on a PSI event that names no file (design §3.4). A frame whose sources cannot be identified keeps `MODIFICATION_COUNT`. Reading a snapshot in dumb mode does not throw. |
| MAINT-63-03 | **Fewer edit misses, no more cold misses** | M | Not Implemented | The full MAINT-60 sweep, twice at the base commit and twice at this item's commit. A pass misses its bound (`cold-250`, `edit-250`) exactly as `SnapshotCostVerdict` counts it: status not `ok`, or time 250 ms or more. **`edit-250`**: the higher change count is more than risks DR-25's edit margin below the lower base count. **`cold-250`**: the higher change count is no more than DR-25's cold margin above the lower base count. The cold margin also absorbs the tracker's measured per-read cost (Risk 1.4), so a cold failure is a real regression signal. |
| MAINT-63-04 | **Findings unchanged** | M | Not Implemented | (a) The MAINT-54 audit's `luacheck` member and the MAINT-55 conformance results are byte-identical before and after the change. (b) Every candidate file outside luals has the same findings digest before and after the change when read alone, as MAINT-65-02 defines it (MAINT-65 design §3.2, §3.3); luals rows are recorded, not compared (risks Risk 1.5). (c) **After-edit oracle**: in the oracle run at this item's commit, a sweep invocation of its own (design §2.6), every compared row has `editDigest` equal to `rebuildDigest`, and `verdict.txt` holds no check line. There is no margin and no re-run. |

## Test Cases
The base commit is Phase 0's commit (implementation plan). TC 1 to TC 8 and TC 14 to TC 16 are
methods of `net.internetisalie.lunar.type.LuaSourceChangeTrackerTest` (design §2.5): each adds files
with `myFixture.addFileToProject`, reads `s = LuaTypesSnapshot.forFile(file)`, and edits with §2.5's
`append` or `replaceText`. "Kept" is `assertSame` with the earlier snapshot, "rebuilt" is
`assertNotSame`. "Settled type" is read after the edit, "rebuilt type" after `forceFullRebuild`.

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-63-01 | `a.lua` = `local M = {}\nfunction M.f() return 1 end\nreturn M\n`; `b.lua` = `local a = require("a")\nlocal x = a.f()\nreturn x\n`; `c.lua` = `local z = 1\nreturn z\n`. Read `b`, then `c` | `append(c, "\n")`, then read `b` | `b` is kept. Premise, asserted first: `b`'s frame `urls` holds `a.lua`'s URL and not `c.lua`'s |
| 2 | MAINT-63-02 | `ta.lua` = `return { k = 1 }\n`; `tb.lua` = `local a = require("ta")\nlocal x = a.k\nprint(x)\n`. The type of `x` is `number` | `replaceText(ta, "k = 1", "k = 'str'")` | the type of `x` is `string` |
| 3 | MAINT-63-02 | `b2.lua` = `local y = GLOBAL_M63\nreturn y\n`; `c2.lua` = `local q = 1\nreturn q\n`. The type of `y` is `any` | `append(c2, "GLOBAL_M63 = 'str'\n")` | the type of `y` is `string` |
| 4 | MAINT-63-02 | `cuse.lua` = `---@type M63Late\nlocal z\nprint(z.n)\n`; `late.lua` = `local q = 1\n`. The type of `z` is `{ ... }` | `append(late, "---@class M63Late\n---@field n number\n")` | the type of `z` is `M63Late` |
| 5 | MAINT-63-02 | `z.lua` = `return { k = 1 }\n`; `p2.lua` = `local z = require("z")\nlocal P = {}\nP.v = z.k\nlocal q = require("q2")\nreturn P\n`; `q2.lua` = `local p = require("p2")\nlocal w = p.v\nreturn w\n`. Read `p2`, then `q2`. Premise, asserted: `q2`'s frame `inProgressHits` holds `p2.lua`'s URL and its `urls` do not hold `z.lua`'s | `replaceText(z, "k = 1", "k = 'str'")`, then read `q2` | `q2` is rebuilt |
| 6 | MAINT-63-02 | `d.lua` = `local x = 1\nprint(x)\n`. Read `d` | in a `WriteCommandAction`, `PsiManager.getInstance(project).dropPsiCaches()`; read `d` | `d` is rebuilt |
| 7 | MAINT-63-02 | `e.lua` = `local x = 1\nprint(x)\n` | inside `DumbModeTestUtils.runInDumbModeSynchronously(project) { … }`: read `e` twice (`s1`, `s2`) and the type of `x`; after it returns, read `e` | nothing throws; `s2` is `s1`; the read after dumb mode is rebuilt relative to `s1` |
| 8 | MAINT-63-02 | the project file `f.lua` = `local x = 1\n`, and three hand-built frames: one with `unreplayedWarm += "file:///w.lua"`, one with `urls += "unidentified:consumed-file"`, one empty | `LuaTypesSnapshot.churnDependencyFor(f, frame)` in a read action, for each | the first two return `PsiModificationTracker.MODIFICATION_COUNT`; the third returns a `ModificationTracker` that is not it |
| 9 | MAINT-63-01, -02 | the existing tests `LuaTypeSourceRecorderTest`, `LuaTypeSourceRecorderCoverageTest`, `LuaTypeManagerRecordingTest`, `LuaTypesSnapshotCacheTest` and every `TypeEleven*Test` | run them, unmodified | all pass. They cover the routes this item does not change: the pinned branch and its dependencies, the target switch, the same-length edit and reentrancy of `forFile`, and source recording into frames |
| 10 | MAINT-63-03 | `test -PwithSnapshotSweep --tests '*SnapshotCostSweep*'` (oracle off), two sweeps at the base commit and two at this item's commit | over all rows of the four `sweep.jsonl` files, count each bound's misses by MAINT-63-03's rule | each bound meets MAINT-63-03's rule, and no sweep's `verdict.txt` holds a check line |
| 11 | MAINT-63-04 (a) | `test -PwithTypeAudit -PrecordConformance --tests '*LuaTypeFalseNegativeAuditTest.testLuacheckMember' --tests '*LuaCatsConformanceTest*'`, at the base commit and at this item's commit | compare `build/type-audit/luacheck.json` and `build/conformance/results.json` | byte-identical |
| 12 | MAINT-63-04 (b) | the first base sweep and the first change sweep from TC 10 | `test -PwithAloneDigest -PaloneDigestBase=<base sweep.jsonl> -PaloneDigestChange=<change sweep.jsonl> --tests '*LuaAloneDigestTest*'` at the base commit and at this item's commit | every row of `build/alone-digest/digests.tsv` whose member is not `luals` has `status` `ok` in both runs and the same `digest` in both. Zero such rows pass, and the PR records "0 candidates" |
| 13 | MAINT-63-04 (c) | `test -PwithSnapshotSweep -PwithSnapshotOracle --tests '*SnapshotCostSweep*'`, once at this item's commit; over budget, the per-member runs of implementation plan *Oracle over budget* | read its `verdict.txt` (design §2.6) | it holds no check line (a per-member run: none other than its expected coverage lines); the PR records its `oracle compared=` line. Its miss counts are not compared |
| 14 | MAINT-63-02 | the fixtures in *TC 14 fixtures* below, one method each | read the type of the named local in the reader, apply the edit, read the settled type, `forceFullRebuild`, read the rebuilt type | the settled type equals the rebuilt type, and both are the "after" type |
| 15 | MAINT-63-02 | `f.lua` = `local x = 1\n`, `g.lua` = `local y = 1\n`, and three hand-built pending frames: one with `unreplayedWarm += "file:///w.lua"`, one with `urls += "unidentified:consumed-file"`, one whose `pendingFrames` holds the first | for each, `LuaSourceSetTracker(owner, setOf(f's URL), listOf(frame))`; read its count, `append(g, "\n")`, read it again, in read actions | the count moved for all three. Control: a pending frame with only `urls += "file:///clean.lua"` does not move |
| 16 | MAINT-63-02 | a new `LuaStampTotal()` (design §2.2a) | `advance(stamps, shared)` six times, in order: `[5,5,5,5,5]`, 0 (t1); the same (t2); `[5,5,5,5,6]`, 0 (t3); `[4,5,5,5,6]`, 0 (t4: one stamp decreased); the same (t5); `[4,5,5,5,6]`, 1 (t6) | t1 = 25, t2 = 25, t3 = 26, t4 = 27, t5 = 27, t6 = 28 |

### TC 14 fixtures
Each method adds the files in the order listed, reads the reader, then applies the edit. The texts
are risks DR-18's fixtures, with fixed names.

| Fixture | Files | Reader, local | Edit | Before → after |
|---|---|---|---|---|
| `gtab` | `gc14.lua` = `local q = 1\nreturn q\n`; `gb14.lua` = `local y = G14\nprint(y)\n` | `gb14.lua`, `y` | `append(gc14, "G14 = {}\n")` | `any` → `{ ... }` |
| `lclass0` | `kc14.lua` = `local q = 1\n`; `kb14.lua` = `---@type K14\nlocal z\nprint(z)\n` | `kb14.lua`, `z` | `append(kc14, "---@class K14\n")` | `undefined` → `K14` |
| `gfunc` | `fc14.lua` = `local q = 1\nreturn q\n`; `fb14.lua` = `local y = F14()\nprint(y)\n` | `fb14.lua`, `y` | `append(fc14, "function F14() return 'str' end\n")` | `undefined` → `string` |
| `method` | `mk14.lua` = `---@class M14\nlocal M14 = {}\nreturn M14\n`; `mj14.lua` = `local q = 1\n`; `mb14.lua` = `---@type M14\nlocal o\nlocal y = o:run()\nprint(y)\n` | `mb14.lua`, `y` | `append(mj14, "---@return string\nfunction M14:run() return 'str' end\n")` | `undefined` → `string` |
| `mfield2` | `od14.lua` = `---@param cb fun(x: string)\nfunction O14.f(cb) end\n`; `oe14.lua` = `local q = 1\n`; `ob14.lua` = `O14.f(function(x) print(x) end)\n` | `ob14.lua`, `x` | `append(oe14, "O14.f = O14.f\n")` | `string` → `undefined` |
| `localfn` | `lm14.lua` = `---@param cb fun(x: string)\nlocal function f(cb) end\nreturn { f = f }\n`; `lu14.lua` = `local m = require("lm14")\nm.f(function(x) print(x) end)\n` | `lu14.lua`, `x` | `replaceText(lm14, "x: string", "x: number")` | `string` → `number` |
| `newfile` | `nr14.lua` = `local m = require("nf14")\nlocal y = m.k\nprint(y)\n` | `nr14.lua`, `y` | `myFixture.addFileToProject("nf14.lua", "return { k = 'str' }\n")` | `undefined` → `string` |

### Mutation checks
TC 1 to TC 8 and TC 14 to TC 16 are falsifiers, TC 13 is an oracle, TC 10 is a performance check,
and TC 9, TC 11 and TC 12 are regression checks. TC 13 is not a staleness gate (risks Risk 1.2).
Rows cite the prototype run that executed them; "not yet run" names the phase that runs them.

| # | Mutation | Why it turns red |
|---|----------|------------------|
| 1 | The unpinned branch returns `MODIFICATION_COUNT`, as today | TC 1: `b` is rebuilt (DR-13 U1) |
| 2 | The tracker sums only the file's own URL (`ownonly`) | TC 2: `x` stays `number` (DR-18 `req`) |
| 3 | `resolutionCount` drops `LuaGlobalAssignmentIndex.KEY` | TC 14 `gtab`: `y` stays `any`; the only id that ticks there |
| 4 | `resolutionCount` drops `LuaCatsTypeNameIndex.KEY` | TC 14 `lclass0`: `z` stays undefined. Dropped with `LuaReceiverMemberIndex.KEY` too, TC 4 also turns red |
| 5 | `resolutionCount` drops `StubUpdatingIndex.INDEX_ID` together with one of: `LuaGlobalAssignmentIndex.KEY`; `LuaReceiverMemberIndex.KEY`; the per-source sums (`ownonly`); the null-file branch (mutation 7) | respectively TC 14 `gfunc`, `method`, `localfn`, `newfile`. Dropped alone, Stubs turns nothing red: each answer-changing Stubs tick in DR-18 came with another id, a recorded source or a file-less event |
| 6 | `resolutionCount` drops `LuaReceiverMemberIndex.KEY` together with `LuaMemberFieldIndex.KEY` | TC 14 `mfield2`: `x` stays `string`. Each alone turns nothing red: they tick together (DR-18) |
| 7 | `LuaSourceChangePreprocessor` ignores events whose file is null | TC 6: `d` is kept (DR-18 `nonull`) |
| 8 | `LuaSourceSetTracker` does not walk `pendingFrames` | TC 5: `q2` is kept (DR-13 M6) |
| 9 | `LuaSourceSetTracker` never degrades | TC 15: no count moves (DR-18 `nodegrade`) |
| 10a | `resolutionCount` has no dumb-mode branch and no `IndexNotReadyException` catch | TC 7: `IndexNotReadyException` (DR-13 U8) |
| 10b | The dumb-mode value changes on every read | TC 7: `s2` is not `s1` (DR-18 `dumbchanging`) |
| 10c | The dumb-mode branch drops `DUMB_OFFSET` | **not a falsifier**: stays green (DR-18 `nooffset`), because dumb-mode entry and exit also fire file-less events. The offset is kept so the rebuild on exit does not rely on them |
| 11 | `trackerFor` drops the `unreplayedWarm` check, or the unidentified-URL check | TC 8: that frame gets a tracker (DR-18 `nowarm`, `noident`) |
| 12 | The tracker never changes (`NEVER_CHANGED`), or drops the per-source sums (`nosource`) | TC 2: `x` stays `number` (DR-18 `req`). TC 13 stays green under both (DR-23) |
| 13 | The unpinned branch returns `MODIFICATION_COUNT` | Argued, not run: TC 10 red, because the change sweeps then come from base's distribution, so the higher of them is not more than base's spread, the margin (DR-25), below the lower base sweep |
| 14 | None known for TC 11 and TC 12 | regression checks |
| 15 | `resolveModuleCandidates` accepts every `init.lua` for a dotless name (BUG-102's resolver rule reverted) | TC 13: an `oracle: edit digest` line on tntcrud (DR-20 ran this engine code). Not yet run on the implemented oracle: implementation plan Phase 2 |
| 16 | `LuaStampTotal.advance` raises `offset` by 1 instead of to `highWater + 1`; or drops the `changed` condition | TC 16: t4 = 26, equal to t3; or t2 = 26. |
| 17 | `toRow` takes `editDigest` from the cold read | design §2.6 O4. Run in Phase 0: `testO4RowTakesEachDigestFromItsRead` red |
| 18 | `rebuildPass` reads without `forceFullRebuild` | an `oracle: rebuild read built nothing` line on tntcrud: the read returns the snapshot the edit read cached, so digests alone cannot catch it. Run in Phase 0 on tntcrud: 52 such lines, and none unmutated |
