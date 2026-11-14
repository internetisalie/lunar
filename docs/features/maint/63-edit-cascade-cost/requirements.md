---
id: "MAINT-63"
title: "63: Edit-Cascade Cost of After-Edit Reads"
type: "feature"
status: "blocked"
priority: "high"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-63: Edit-Cascade Cost of After-Edit Reads

## Overview
An edit discards the cached type snapshot of every file that is not pinned, whether or not that
file consumed the edited one. Reading a file after an edit then rebuilds its whole reach. This item
makes an unpinned snapshot depend on the sources it recorded, so an edit discards only the
snapshots that consumed the edited file or whose resolution answers it can change. Filed by
MAINT-60 Phase 3; MAINT-60-02 names every pass that misses a 250 ms bound in the table of its
cause item. v0.21.21 is held on this item. Parent: [[features/maint/requirements|MAINT]].
Measurement feature: [[MAINT-60]]. Prior item: [[MAINT-65]].

**Held.** The mechanism keeps snapshots whose findings differ from a full rebuild of the same
state, which MAINT-63-04 forbids (risks Risk 1.1, DR-20). The item resumes on risks DR-22
(Decision 2.1).

## Measured Cause
After MAINT-65, edit cascade is still the largest single cause of `edit-250` misses: 150 and 154
of 293 and 311 misses in MAINT-65's two change sweeps (risks DR-09). An unpinned snapshot depends
on `PsiModificationTracker.MODIFICATION_COUNT` (`LuaTypesSnapshot.churnDependencyFor`,
`LuaTypes.kt:407`), so any edit rebuilds it. In the missed reads of DR-10, 74% of rebuild time went
to files that never consumed the edited file. Per-source dependencies cleared most of the item's
misses on subsets (DR-11, DR-12, DR-20).

## Scope
- In scope: the churn dependency of an **unpinned** snapshot, and the sweep's after-edit oracle
  (MAINT-63-04). The pinned branch (TYPE-11), `forFile`'s other two dependencies and the dependency
  count (three) are unchanged.
- Out of scope: own build cost (MAINT-61), cold reach (MAINT-62), findings-changing work
  (MAINT-64), build-order dependence of the type engine (risks DR-22), and keeping library
  `PsiFile`s alive across GC (risks, Technical Debt).

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-63-01 | **An edit discards only the snapshots it can affect** | M | Not Implemented | After an edit to file F, a cached unpinned snapshot stays valid when F is not among its recorded sources and none of design §3.3's resolution terms changed. Recorded sources: the frame's `urls` and `inProgressHits`, the file's own URL, and the same sets of every in-progress build it read (design §3.2). |
| MAINT-63-02 | **No stale snapshot** | M | Not Implemented | An unpinned snapshot is rebuilt when one of its recorded sources changes, when a change can alter one of design §3.3's index answers, and on a PSI event that names no file (design §3.4). A frame whose sources cannot be identified keeps today's dependency, `MODIFICATION_COUNT`. Reading a snapshot in dumb mode does not throw. |
| MAINT-63-03 | **Fewer edit misses, no more cold misses** | M | Not Implemented | The full MAINT-60 sweep is used, and each commit is swept twice. Rows are counted only for members that have rows in all four sweeps; a member missing from any sweep (luals's intermittent `StackOverflowError`, risks DR-17) is excluded from all four counts and named in the PR. For each bound (`cold-250` and `edit-250`), a miss is counted exactly as `SnapshotCostVerdict` counts it: a pass misses when its status is not `ok` or its time is 250 ms or more. The margins are the spread of four base sweeps of this item's base engine code (risks DR-17), by MAINT-65's method (MAINT-65 risks DR-09). **`edit-250`**: the higher change count must be more than 36 below the lower base count. **`cold-250`**: the higher change count must be no more than 8 above the lower base count. |
| MAINT-63-04 | **Findings unchanged** | M | Not Implemented | (a) The MAINT-54 audit's `luacheck` member and the MAINT-55 conformance results are byte-identical before and after the change. (b) Every candidate file has the same findings digest before and after the change when it is read alone, as MAINT-65-02 defines it (MAINT-65 design §3.2, §3.3). (c) **After-edit oracle**: in each change sweep, every row whose edit read and rebuild read both finished `ok` has `editDigest` equal to `rebuildDigest` (design §2.6). Every mismatch is a failure: there is no margin and no re-run. |

## Test Cases
The base commit is this item's Phase 0 commit (implementation plan), which adds the sweep oracle to
this item's branch point on `main`; that already has `LuaAloneDigestTest`. TC 1 to TC 8, TC 14
and TC 15 are new methods in `net.internetisalie.lunar.type.LuaSourceChangeTrackerTest`
(design §2.5). Each uses `myFixture.addFileToProject`, reads `s = LuaTypesSnapshot.forFile(file)`,
and edits through design §2.5's `append` or `replaceText` helpers. "Kept" is `assertSame` with the
earlier snapshot; "rebuilt" is `assertNotSame`. "Settled type" is the type read after the edit;
"rebuilt type" is the type read after design §2.5's `forceFullRebuild`.

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
| 9 | MAINT-63-01, -02 | the existing tests `LuaTypeSourceRecorderTest`, `LuaTypeSourceRecorderCoverageTest`, `LuaTypeManagerRecordingTest`, `LuaTypesSnapshotCacheTest` and every `TypeEleven*Test` | run them, unmodified | all pass. They cover the routes this item does not change: the pinned branch and its three dependencies, the target switch, the same-length edit and reentrancy of `forFile`, and source recording into frames |
| 10 | MAINT-63-03 | `test -PwithSnapshotSweep --tests '*SnapshotCostSweep*'`, twice at the base commit and twice at this item's commit | over the rows of members present in all four `sweep.jsonl` files, count rows with `coldStatus != "ok"` or `coldMs >= 250`, and separately rows with `editStatus != "ok"` or `editMs >= 250` | each bound meets MAINT-63-03's rule |
| 11 | MAINT-63-04 (a) | `test -PwithTypeAudit -PrecordConformance --tests '*LuaTypeFalseNegativeAuditTest.testLuacheckMember' --tests '*LuaCatsConformanceTest*'`, at the base commit and at this item's commit | compare `build/type-audit/luacheck.json` and `build/conformance/results.json` | byte-identical |
| 12 | MAINT-63-04 (b) | the first base sweep and the first change sweep from TC 10 | run `test -PwithAloneDigest -PaloneDigestBase=<base sweep.jsonl> -PaloneDigestChange=<change sweep.jsonl> --tests '*LuaAloneDigestTest*'` at the base commit and at this item's commit | every row of `build/alone-digest/digests.tsv` has `status` `ok` in both runs and the same `digest` in both. Zero candidates leave the file empty; TC 12 then passes trivially and the PR records "0 candidates" |
| 13 | MAINT-63-04 (c) | the two change sweeps of TC 10 | read each `sweep.jsonl` and `verdict.txt` | `testZzVerdict` passed with no `oracle` check line, and `SnapshotCostVerdict.oracleMismatches(rows)` is empty. The PR records, per sweep, the compared count and the rebuild timeouts. A sweep with zero compared rows fails |
| 14 | MAINT-63-02 | the fixtures of risks DR-18, one method each: `gtab` (`append(c, "G = {}\n")`, `y` any → `{ ... }`), `lclass0` (`append(c, "---@class K\n")`, `z` undefined → `K`), `gfunc` (`append(c, "function F() return 'str' end\n")`, `y` undefined → `string`), `method` (`append(j, "---@return string\nfunction M:run() return 'str' end\n")` with `M` a hosted class in a third file, `y` undefined → `string`), `mfield2` (`function O.f(cb)` with `---@param cb fun(x: string)` in one file, `append(e, "O.f = O.f\n")` in another, `x` string → undefined), `localfn` (`---@param cb fun(x: string)\nlocal function f(cb) end\nreturn { f = f }\n` required by the reader, `string` → `number`), `newfile` (the reader requires `nf`; `addFileToProject("nf.lua", "return { k = 'str' }\n")`, `y` undefined → `string`) | read the type, apply the edit, read the settled type, `forceFullRebuild`, read the rebuilt type | the settled type equals the rebuilt type, and both are the type after the arrow |
| 15 | MAINT-63-02 | `f.lua` = `local x = 1\n`, `g.lua` = `local y = 1\n`, and three hand-built pending frames: one with `unreplayedWarm += "file:///w.lua"`, one with `urls += "unidentified:consumed-file"`, one whose `pendingFrames` holds the first | for each, `LuaSourceSetTracker(owner, setOf(f's URL), listOf(frame))`; read its count, `append(g, "\n")`, read it again, in read actions | the count moved for all three. Control: a pending frame with only `urls += "file:///clean.lua"` does not move |

### Mutation checks
TC 1 to TC 8, TC 14 and TC 15 are falsifiers, TC 13 is an oracle, TC 10 is a performance
acceptance check, and TC 9, TC 11 and TC 12 are regression checks. Every row below was executed in
the prototype (risks DR-13, DR-18, DR-20) unless it says otherwise.

| # | Mutation | Why it turns red |
|---|----------|------------------|
| 1 | The unpinned branch returns `MODIFICATION_COUNT`, as today | TC 1: `b` is rebuilt after the edit to `c` (DR-13 U1) |
| 2 | The tracker sums only the file's own URL (`ownonly`) | TC 2 / DR-18 `req`: `x` stays `number`, rebuilt type `string` |
| 3 | `resolutionCount` drops `LuaGlobalAssignmentIndex.KEY` | TC 14 `gtab`: `y` stays `any`. It is the only id that ticks there |
| 4 | `resolutionCount` drops `LuaCatsTypeNameIndex.KEY` | TC 14 `lclass0`: `z` stays undefined. Dropped with `LuaReceiverMemberIndex.KEY` too, TC 4 also turns red |
| 5 | `resolutionCount` drops `StubUpdatingIndex.INDEX_ID` together with `LuaGlobalAssignmentIndex.KEY`, or with `LuaReceiverMemberIndex.KEY` | TC 14 `gfunc`, or `method`. Dropped alone, Stubs turns nothing red: every answer-changing Stubs tick in DR-18 came with another id (`gfunc`, `method`), a recorded source (`localfn`) or a file-less event (`newfile`). With `ownonly`, `localfn` turns red; with mutation 7, `newfile` does |
| 6 | `resolutionCount` drops `LuaReceiverMemberIndex.KEY` together with `LuaMemberFieldIndex.KEY` | TC 14 `mfield2`: `x` stays `string`, rebuilt type undefined. Each alone turns nothing red: the two indexes read the same dotted assignment targets, so they tick together (DR-18) |
| 7 | `LuaSourceChangePreprocessor` ignores events whose file is null | TC 6: `d` is kept (DR-18 `nonull`) |
| 8 | `LuaSourceSetTracker` does not walk `pendingFrames` | TC 5: `q2` is kept (DR-13 M6, DR-18 `nopending`) |
| 9 | `LuaSourceSetTracker` never degrades | TC 15: no count moves (DR-18 `nodegrade`) |
| 10a | `resolutionCount` has no dumb-mode branch and no `IndexNotReadyException` catch | TC 7: `IndexNotReadyException` (DR-13 U8) |
| 10b | The dumb-mode value changes on every read | TC 7: `s2` is not `s1` (DR-18 `dumbchanging`) |
| 10c | The dumb-mode branch drops `DUMB_OFFSET` | not red (DR-18 `nooffset`): the two file-less events of dumb-mode entry and exit also move the count. The offset is kept so that the rebuild on exit does not rely on them |
| 11 | `trackerFor` drops the `unreplayedWarm` check, or the unidentified-URL check | TC 8: that frame gets a tracker (DR-18 `nowarm`, `noident`) |
| 12 | The tracker never changes (`NEVER_CHANGED`), or drops the per-source sums | TC 13: in DR-20, 37 and 39 differing rows on five members, against base's 0 |
| 13 | The unpinned branch returns `MODIFICATION_COUNT` | TC 10 red: the change sweeps then come from base's distribution, and no two of DR-17's four base sweeps are more than 36 apart, so two more cannot both fall more than 36 below the lower base sweep |
| 14 | None known for TC 11 and TC 12 | regression checks outside the falsifiers |

## Files over target
Generated from MAINT-65's two change sweeps (`r2-c-sweep1` and `r2-c-sweep2`, at the engine code
that landed as `4035acf4`), all `edit-250`. Phase 2 regenerates it from this item's change sweeps.
A file is listed when at least one sweep filed its edit pass as edit cascade under MAINT-60's
attribution (`SnapshotCostVerdict`).
*Sweep 1* and *Sweep 2* are the edit read's time, or `timeout`. *Item* is the item each sweep
filed the pass under: `MAINT-63`, `MAINT-61 (dep)` (a dependency's own build reached 250 ms),
`MAINT-61 (own)`, or `pass`. *Runs* is how many of the two sweeps filed it as edit cascade: 2/2 is
stable, 1/2 flickers. *Builds*, *Dependency* and *Dependency ms* come from the sweep that filed it
as edit cascade, the first when both did.

| Member | File | Sweep 1 | Sweep 2 | Item 1 | Item 2 | Runs | Builds | Dependency | Dependency ms |
|---|---|---|---|---|---|---|---|---|---|
| awesome | `lib/naughty/core.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 99 | `awesome/lib/wibox/widget/init.lua` | 75 |
| awesome | `lib/naughty/layout/box.lua` | timeout | timeout | MAINT-63 | MAINT-61 (dep) | 1/2 | 84 | `awesome/lib/awful/client.lua` | 185 |
| awesome | `lib/naughty/layout/legacy.lua` | timeout | timeout | MAINT-63 | MAINT-61 (dep) | 1/2 | 100 | `awesome/lib/wibox/widget/init.lua` | 80 |
| awesome | `lib/naughty/list/actions.lua` | 323 ms | timeout | MAINT-63 | MAINT-61 (dep) | 1/2 | 72 | `awesome/lib/wibox/container/border.lua` | 32 |
| awesome | `lib/naughty/list/notifications.lua` | timeout | timeout | MAINT-63 | MAINT-61 (dep) | 1/2 | 59 | `awesome/lib/wibox/layout/init.lua` | 78 |
| awesome | `lib/naughty/widget/_default.lua` | 529 ms | timeout | MAINT-63 | MAINT-61 (dep) | 1/2 | 76 | `awesome/lib/wibox/widget/init.lua` | 59 |
| awesome | `lib/naughty/widget/init.lua` | 351 ms | timeout | MAINT-63 | MAINT-61 (dep) | 1/2 | 38 | `awesome/lib/gears/shape.lua` | 36 |
| blinkcmp | `lua/blink-cmp.lua` | 573 ms | 872 ms | MAINT-63 | MAINT-63 | 2/2 | 55 | `neovim:vim/_core/shared.lua` | 61 |
| blinkcmp | `lua/blink/cmp/completion/accept/init.lua` | 596 ms | 691 ms | MAINT-63 | MAINT-63 | 2/2 | 56 | `neovim:vim/_core/shared.lua` | 125 |
| blinkcmp | `lua/blink/cmp/completion/accept/preview.lua` | 528 ms | 692 ms | MAINT-63 | MAINT-63 | 2/2 | 54 | `neovim:vim/_core/shared.lua` | 68 |
| blinkcmp | `lua/blink/cmp/completion/brackets/init.lua` | 209 ms | 607 ms | pass | MAINT-63 | 1/2 | 71 | `blinkcmp/lua/blink/cmp/completion/list.lua` | 75 |
| blinkcmp | `lua/blink/cmp/completion/brackets/semantic.lua` | 270 ms | 760 ms | MAINT-63 | MAINT-63 | 2/2 | 15 | `neovim:vim/_core/shared.lua` | 58 |
| blinkcmp | `lua/blink/cmp/completion/init.lua` | 592 ms | 700 ms | MAINT-63 | MAINT-63 | 2/2 | 59 | `blinkcmp/lua/blink/cmp/init.lua` | 69 |
| blinkcmp | `lua/blink/cmp/completion/list.lua` | 530 ms | 657 ms | MAINT-63 | MAINT-63 | 2/2 | 54 | `blinkcmp/lua/blink/cmp/init.lua` | 59 |
| blinkcmp | `lua/blink/cmp/completion/prefetch.lua` | 272 ms | 638 ms | MAINT-63 | MAINT-63 | 2/2 | 11 | `neovim:vim/_core/shared.lua` | 74 |
| blinkcmp | `lua/blink/cmp/completion/trigger/init.lua` | 644 ms | 639 ms | MAINT-63 | MAINT-63 | 2/2 | 55 | `blinkcmp/lua/blink/cmp/init.lua` | 82 |
| blinkcmp | `lua/blink/cmp/completion/windows/documentation.lua` | 545 ms | 639 ms | MAINT-63 | MAINT-63 | 2/2 | 54 | `blinkcmp/lua/blink/cmp/init.lua` | 91 |
| blinkcmp | `lua/blink/cmp/completion/windows/ghost_text/init.lua` | 614 ms | 702 ms | MAINT-63 | MAINT-63 | 2/2 | 58 | `neovim:vim/_core/editor.lua` | 79 |
| blinkcmp | `lua/blink/cmp/completion/windows/render/column.lua` | 464 ms | 640 ms | MAINT-63 | MAINT-63 | 2/2 | 56 | `blinkcmp/lua/blink/cmp/init.lua` | 67 |
| blinkcmp | `lua/blink/cmp/completion/windows/render/context.lua` | 157 ms | 590 ms | pass | MAINT-63 | 1/2 | 72 | `blinkcmp/lua/blink/cmp/init.lua` | 86 |
| blinkcmp | `lua/blink/cmp/completion/windows/render/init.lua` | 602 ms | 577 ms | MAINT-63 | MAINT-63 | 2/2 | 59 | `neovim:vim/_core/shared.lua` | 93 |
| blinkcmp | `lua/blink/cmp/completion/windows/render/text.lua` | 503 ms | 670 ms | MAINT-63 | MAINT-63 | 2/2 | 54 | `blinkcmp/lua/blink/cmp/init.lua` | 82 |
| blinkcmp | `lua/blink/cmp/config/init.lua` | 724 ms | 651 ms | MAINT-63 | MAINT-63 | 2/2 | 57 | `neovim:vim/_core/shared.lua` | 103 |
| blinkcmp | `lua/blink/cmp/config/snippets.lua` | 495 ms | 689 ms | MAINT-63 | MAINT-63 | 2/2 | 55 | `neovim:vim/_core/shared.lua` | 66 |
| blinkcmp | `lua/blink/cmp/fuzzy/build/init.lua` | 675 ms | 628 ms | MAINT-63 | MAINT-63 | 2/2 | 72 | `blinkcmp/lua/blink/cmp/init.lua` | 75 |
| blinkcmp | `lua/blink/cmp/fuzzy/init.lua` | 706 ms | 652 ms | MAINT-63 | MAINT-63 | 2/2 | 56 | `blinkcmp/lua/blink/cmp/init.lua` | 87 |
| blinkcmp | `lua/blink/cmp/fuzzy/lua/init.lua` | 173 ms | 569 ms | pass | MAINT-63 | 1/2 | 71 | `blinkcmp/lua/blink/cmp/init.lua` | 68 |
| blinkcmp | `lua/blink/cmp/fuzzy/rust/init.lua` | 429 ms | 561 ms | MAINT-63 | MAINT-63 | 2/2 | 54 | `neovim:vim/_core/shared.lua` | 54 |
| blinkcmp | `lua/blink/cmp/fuzzy/sort.lua` | 178 ms | 636 ms | pass | MAINT-63 | 1/2 | 71 | `blinkcmp/lua/blink/cmp/fuzzy/init.lua` | 65 |
| blinkcmp | `lua/blink/cmp/health.lua` | 582 ms | 608 ms | MAINT-63 | MAINT-63 | 2/2 | 58 | `blinkcmp/lua/blink/cmp/init.lua` | 69 |
| blinkcmp | `lua/blink/cmp/init.lua` | 458 ms | 634 ms | MAINT-63 | MAINT-63 | 2/2 | 54 | `blinkcmp/lua/blink/cmp/init.lua` | 70 |
| blinkcmp | `lua/blink/cmp/keymap/apply.lua` | 508 ms | 670 ms | MAINT-63 | MAINT-63 | 2/2 | 56 | `blinkcmp/lua/blink/cmp/init.lua` | 62 |
| blinkcmp | `lua/blink/cmp/keymap/init.lua` | 639 ms | 660 ms | MAINT-63 | MAINT-63 | 2/2 | 60 | `blinkcmp/lua/blink/cmp/init.lua` | 75 |
| blinkcmp | `lua/blink/cmp/lib/buffer_events.lua` | 149 ms | 702 ms | pass | MAINT-63 | 1/2 | 73 | `neovim:vim/_core/shared.lua` | 63 |
| blinkcmp | `lua/blink/cmp/lib/cmdline_events.lua` | 120 ms | 578 ms | pass | MAINT-63 | 1/2 | 70 | `blinkcmp/lua/blink/cmp/init.lua` | 80 |
| blinkcmp | `lua/blink/cmp/lib/term_events.lua` | 140 ms | 586 ms | pass | MAINT-63 | 1/2 | 71 | `blinkcmp/lua/blink/cmp/init.lua` | 86 |
| blinkcmp | `lua/blink/cmp/lib/text_edits.lua` | 477 ms | 563 ms | MAINT-63 | MAINT-63 | 2/2 | 55 | `blinkcmp/lua/blink/cmp/init.lua` | 64 |
| blinkcmp | `lua/blink/cmp/lib/window/init.lua` | 346 ms | 321 ms | MAINT-63 | MAINT-63 | 2/2 | 4 | `neovim:vim/_meta/vimfn.gen.lua` | 103 |
| blinkcmp | `lua/blink/cmp/signature/init.lua` | 544 ms | 704 ms | MAINT-63 | MAINT-63 | 2/2 | 57 | `blinkcmp/lua/blink/cmp/init.lua` | 79 |
| blinkcmp | `lua/blink/cmp/signature/trigger.lua` | 501 ms | 521 ms | MAINT-63 | MAINT-63 | 2/2 | 55 | `blinkcmp/lua/blink/cmp/init.lua` | 65 |
| blinkcmp | `lua/blink/cmp/signature/window.lua` | 473 ms | 569 ms | MAINT-63 | MAINT-63 | 2/2 | 57 | `blinkcmp/lua/blink/cmp/init.lua` | 72 |
| blinkcmp | `lua/blink/cmp/sources/buffer/init.lua` | 463 ms | 597 ms | MAINT-63 | MAINT-63 | 2/2 | 58 | `blinkcmp/lua/blink/cmp/init.lua` | 68 |
| blinkcmp | `lua/blink/cmp/sources/buffer/parser.lua` | 564 ms | 633 ms | MAINT-63 | MAINT-63 | 2/2 | 56 | `blinkcmp/lua/blink/cmp/completion/list.lua` | 82 |
| blinkcmp | `lua/blink/cmp/sources/buffer/utils.lua` | 192 ms | 562 ms | pass | MAINT-63 | 1/2 | 73 | `blinkcmp/lua/blink/cmp/init.lua` | 67 |
| blinkcmp | `lua/blink/cmp/sources/cmdline/help.lua` | 144 ms | 555 ms | pass | MAINT-63 | 1/2 | 71 | `neovim:vim/_core/editor.lua` | 68 |
| blinkcmp | `lua/blink/cmp/sources/cmdline/init.lua` | 203 ms | 616 ms | pass | MAINT-63 | 1/2 | 70 | `blinkcmp/lua/blink/cmp/init.lua` | 58 |
| blinkcmp | `lua/blink/cmp/sources/cmdline/utils.lua` | 158 ms | 646 ms | pass | MAINT-63 | 1/2 | 70 | `neovim:vim/_core/shared.lua` | 63 |
| blinkcmp | `lua/blink/cmp/sources/lib/init.lua` | 457 ms | 553 ms | MAINT-63 | MAINT-63 | 2/2 | 53 | `blinkcmp/lua/blink/cmp/init.lua` | 71 |
| blinkcmp | `lua/blink/cmp/sources/lib/provider/list.lua` | 139 ms | 586 ms | pass | MAINT-63 | 1/2 | 70 | `neovim:vim/_core/shared.lua` | 65 |
| blinkcmp | `lua/blink/cmp/sources/lib/tree.lua` | 521 ms | 719 ms | MAINT-63 | MAINT-63 | 2/2 | 55 | `blinkcmp/lua/blink/cmp/init.lua` | 64 |
| blinkcmp | `lua/blink/cmp/sources/lsp/commands.lua` | 112 ms | 661 ms | pass | MAINT-63 | 1/2 | 71 | `blinkcmp/lua/blink/cmp/init.lua` | 77 |
| blinkcmp | `lua/blink/cmp/sources/lsp/init.lua` | 207 ms | 700 ms | pass | MAINT-63 | 1/2 | 76 | `blinkcmp/lua/blink/cmp/completion/list.lua` | 92 |
| blinkcmp | `lua/blink/cmp/sources/path/init.lua` | 153 ms | 595 ms | pass | MAINT-63 | 1/2 | 70 | `blinkcmp/lua/blink/cmp/init.lua` | 54 |
| blinkcmp | `lua/blink/cmp/sources/path/lib.lua` | 201 ms | 733 ms | pass | MAINT-63 | 1/2 | 70 | `blinkcmp/lua/blink/cmp/fuzzy/init.lua` | 80 |
| blinkcmp | `lua/blink/cmp/sources/snippets/default/init.lua` | 123 ms | 568 ms | pass | MAINT-63 | 1/2 | 71 | `blinkcmp/lua/blink/cmp/init.lua` | 69 |
| blinkcmp | `lua/blink/cmp/sources/snippets/default/registry.lua` | 222 ms | 559 ms | pass | MAINT-63 | 1/2 | 71 | `neovim:vim/_core/shared.lua` | 49 |
| blinkcmp | `lua/blink/cmp/sources/snippets/luasnip.lua` | 749 ms | 791 ms | MAINT-63 | MAINT-63 | 2/2 | 71 | `neovim:vim/_core/shared.lua` | 65 |
| differ | `lua/differ/command.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 47 | `differ/lua/differ/git/init.lua` | 181 |
| differ | `lua/differ/git/init.lua` | timeout | 637 ms | MAINT-63 | MAINT-63 | 2/2 | 48 | `differ/lua/differ/git/init.lua` | 114 |
| differ | `lua/differ/git/staging/frozen.lua` | timeout | 752 ms | MAINT-63 | MAINT-63 | 2/2 | 35 | `differ/lua/differ/git/init.lua` | 119 |
| differ | `lua/differ/git/staging/init.lua` | 675 ms | 576 ms | MAINT-63 | MAINT-63 | 2/2 | 49 | `neovim:vim/_meta/api.gen.lua` | 101 |
| differ | `lua/differ/git/staging/selection.lua` | 327 ms | 169 ms | MAINT-63 | pass | 1/2 | 14 | `differ/lua/differ/model/stage.lua` | 102 |
| differ | `lua/differ/git/staging/union.lua` | 909 ms | 614 ms | MAINT-63 | MAINT-63 | 2/2 | 50 | `differ/lua/differ/git/init.lua` | 155 |
| differ | `lua/differ/git/staging/write.lua` | 661 ms | 407 ms | MAINT-63 | MAINT-63 | 2/2 | 51 | `differ/lua/differ/panel/init.lua` | 72 |
| differ | `lua/differ/health.lua` | 919 ms | 477 ms | MAINT-63 | MAINT-63 | 2/2 | 53 | `differ/lua/differ/model/stage.lua` | 91 |
| differ | `lua/differ/merge/continue.lua` | timeout | 606 ms | MAINT-63 | MAINT-63 | 2/2 | 43 | `differ/lua/differ/git/init.lua` | 174 |
| differ | `lua/differ/merge/deleted.lua` | 895 ms | 617 ms | MAINT-63 | MAINT-63 | 2/2 | 53 | `differ/lua/differ/merge/init.lua` | 150 |
| differ | `lua/differ/merge/files.lua` | 993 ms | 561 ms | MAINT-63 | MAINT-63 | 2/2 | 58 | `differ/lua/differ/merge/init.lua` | 105 |
| differ | `lua/differ/merge/init.lua` | 1004 ms | 687 ms | MAINT-63 | MAINT-63 | 2/2 | 55 | `differ/lua/differ/merge/init.lua` | 123 |
| differ | `lua/differ/merge/model.lua` | 872 ms | 547 ms | MAINT-63 | MAINT-63 | 2/2 | 50 | `differ/lua/differ/git/init.lua` | 110 |
| differ | `lua/differ/panel/init.lua` | timeout | 420 ms | MAINT-63 | MAINT-63 | 2/2 | 37 | `differ/lua/differ/model/stage.lua` | 124 |
| differ | `lua/differ/pr/checks.lua` | 731 ms | 265 ms | MAINT-63 | MAINT-63 | 2/2 | 53 | `neovim:vim/_core/shared.lua` | 88 |
| differ | `lua/differ/pr/client.lua` | 873 ms | 207 ms | MAINT-63 | pass | 1/2 | 51 | `differ/lua/differ/model/stage.lua` | 92 |
| differ | `lua/differ/pr/comment.lua` | 735 ms | 291 ms | MAINT-63 | MAINT-63 | 2/2 | 57 | `differ/lua/differ/git/init.lua` | 73 |
| differ | `lua/differ/pr/init.lua` | 808 ms | 756 ms | MAINT-63 | MAINT-63 | 2/2 | 57 | `differ/lua/differ/git/init.lua` | 80 |
| differ | `lua/differ/pr/overview.lua` | 952 ms | 350 ms | MAINT-63 | MAINT-63 | 2/2 | 55 | `differ/lua/differ/git/init.lua` | 87 |
| differ | `lua/differ/pr/review.lua` | 760 ms | 240 ms | MAINT-63 | pass | 1/2 | 55 | `differ/lua/differ/git/init.lua` | 82 |
| differ | `lua/differ/pr/threads.lua` | 763 ms | 272 ms | MAINT-63 | MAINT-63 | 2/2 | 53 | `differ/lua/differ/git/init.lua` | 78 |
| differ | `lua/differ/sidecar/init.lua` | 768 ms | 342 ms | MAINT-63 | MAINT-63 | 2/2 | 50 | `differ/lua/differ/git/init.lua` | 82 |
| factoryplanner | `modfiles/backend/handlers/generator.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 72 | `/src/modfiles/backend/calculation/LUDecomposition.lua` | 159 |
| factoryplanner | `modfiles/backend/handlers/generator_util.lua` | timeout | timeout | MAINT-61 (dep) | MAINT-63 | 1/2 | 72 | `/src/modfiles/backend/handlers/generator.lua` | 192 |
| factoryplanner | `modfiles/backend/handlers/prototyper.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 73 | `/src/modfiles/backend/calculation/LUDecomposition.lua` | 144 |
| factoryplanner | `modfiles/util/cursor.lua` | timeout | timeout | MAINT-63 | MAINT-61 (dep) | 1/2 | 67 | `/src/modfiles/backend/handlers/generator.lua` | 248 |
| factoryplanner | `modfiles/util/effects.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 68 | `/src/modfiles/backend/handlers/generator.lua` | 249 |
| gitsigns | `lua/gitsigns.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 44 | `gitsigns/lua/gitsigns/hunks.lua` | 220 |
| gitsigns | `lua/gitsigns/actions.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 48 | `gitsigns/lua/gitsigns/hunks.lua` | 192 |
| gitsigns | `lua/gitsigns/actions/blame.lua` | 233 ms | 297 ms | pass | MAINT-63 | 1/2 | 7 | `gitsigns/lua/gitsigns/async.lua` | 101 |
| gitsigns | `lua/gitsigns/actions/blame_line.lua` | 727 ms | 983 ms | MAINT-63 | MAINT-63 | 2/2 | 33 | `gitsigns/lua/gitsigns/hunks.lua` | 204 |
| gitsigns | `lua/gitsigns/actions/diff.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 46 | `gitsigns/lua/gitsigns/hunks.lua` | 173 |
| gitsigns | `lua/gitsigns/actions/diffthis.lua` | 923 ms | 902 ms | MAINT-63 | MAINT-63 | 2/2 | 47 | `gitsigns/lua/gitsigns/hunks.lua` | 173 |
| gitsigns | `lua/gitsigns/actions/nav.lua` | 874 ms | 892 ms | MAINT-63 | MAINT-63 | 2/2 | 42 | `gitsigns/lua/gitsigns/hunks.lua` | 197 |
| gitsigns | `lua/gitsigns/actions/preview.lua` | 761 ms | timeout | MAINT-63 | MAINT-61 (dep) | 1/2 | 42 | `gitsigns/lua/gitsigns/hunks.lua` | 200 |
| gitsigns | `lua/gitsigns/actions/qflist.lua` | 448 ms | 465 ms | MAINT-63 | MAINT-63 | 2/2 | 12 | `gitsigns/lua/gitsigns/git/repo.lua` | 118 |
| gitsigns | `lua/gitsigns/actions/show_commit.lua` | 482 ms | 479 ms | MAINT-63 | MAINT-63 | 2/2 | 23 | `gitsigns/lua/gitsigns/hunks.lua` | 162 |
| gitsigns | `lua/gitsigns/attach.lua` | timeout | 983 ms | MAINT-63 | MAINT-63 | 2/2 | 44 | `gitsigns/lua/gitsigns/hunks.lua` | 183 |
| gitsigns | `lua/gitsigns/cache.lua` | 456 ms | 537 ms | MAINT-63 | MAINT-63 | 2/2 | 24 | `gitsigns/lua/gitsigns/hunks.lua` | 156 |
| gitsigns | `lua/gitsigns/cli.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 48 | `gitsigns/lua/gitsigns/hunks.lua` | 175 |
| gitsigns | `lua/gitsigns/debug.lua` | 477 ms | 668 ms | MAINT-63 | MAINT-63 | 2/2 | 25 | `gitsigns/lua/gitsigns/hunks.lua` | 153 |
| gitsigns | `lua/gitsigns/deleted_preview.lua` | 891 ms | 692 ms | MAINT-63 | MAINT-63 | 2/2 | 40 | `gitsigns/lua/gitsigns/hunks.lua` | 218 |
| gitsigns | `lua/gitsigns/diff_ext.lua` | 468 ms | 440 ms | MAINT-63 | MAINT-63 | 2/2 | 24 | `gitsigns/lua/gitsigns/hunks.lua` | 179 |
| gitsigns | `lua/gitsigns/git.lua` | 323 ms | 321 ms | MAINT-63 | MAINT-63 | 2/2 | 9 | `gitsigns/lua/gitsigns/git/repo.lua` | 100 |
| gitsigns | `lua/gitsigns/git/repo.lua` | 246 ms | 309 ms | pass | MAINT-63 | 1/2 | 10 | `gitsigns/lua/gitsigns/async.lua` | 89 |
| gitsigns | `lua/gitsigns/hunk_preview.lua` | 406 ms | 316 ms | MAINT-63 | MAINT-63 | 2/2 | 24 | `neovim:vim/_core/shared.lua` | 67 |
| gitsigns | `lua/gitsigns/hunks.lua` | 375 ms | 501 ms | MAINT-63 | MAINT-63 | 2/2 | 21 | `neovim:vim/_core/shared.lua` | 50 |
| gitsigns | `lua/gitsigns/inspect.lua` | 167 ms | 267 ms | pass | MAINT-63 | 1/2 | 19 | `neovim:vim/_core/shared.lua` | 88 |
| gitsigns | `lua/gitsigns/manager.lua` | 969 ms | 807 ms | MAINT-63 | MAINT-63 | 2/2 | 39 | `gitsigns/lua/gitsigns/hunks.lua` | 226 |
| gitsigns | `lua/gitsigns/message.lua` | 206 ms | 250 ms | pass | MAINT-63 | 1/2 | 22 | `neovim:vim/_core/shared.lua` | 60 |
| gitsigns | `lua/gitsigns/render/overlay.lua` | 268 ms | 142 ms | MAINT-63 | pass | 1/2 | 20 | `neovim:vim/_core/shared.lua` | 61 |
| gitsigns | `lua/gitsigns/sign_renderer.lua` | 765 ms | 751 ms | MAINT-63 | MAINT-63 | 2/2 | 39 | `gitsigns/lua/gitsigns/hunks.lua` | 166 |
| gitsigns | `lua/gitsigns/status.lua` | 797 ms | 696 ms | MAINT-63 | MAINT-63 | 2/2 | 40 | `gitsigns/lua/gitsigns/hunks.lua` | 158 |
| gitsigns | `lua/gitsigns/unified.lua` | 631 ms | 735 ms | MAINT-63 | MAINT-63 | 2/2 | 41 | `gitsigns/lua/gitsigns/hunks.lua` | 163 |
| gitsigns | `lua/gitsigns/word_diff.lua` | 720 ms | 723 ms | MAINT-63 | MAINT-63 | 2/2 | 39 | `gitsigns/lua/gitsigns/hunks.lua` | 182 |
| kong | `kong/clustering/data_plane.lua` | 160 ms | 259 ms | pass | MAINT-63 | 1/2 | 19 | `/src/kong/clustering/config_helper.lua` | 40 |
| kong | `kong/plugins/aws-lambda/handler.lua` | 57 ms | 260 ms | pass | MAINT-63 | 1/2 | 22 | `/home/builder/lunar/.intellijPlatform/sandbox/lunar/GO-2026.2.3/system-test/lunar/definitions/openresty-17f581b2e6f2d3a30ab0e6564eb2eb40426db6b5/resty/websocket/client.lua` | 31 |
| kong | `kong/plugins/ldap-auth/access.lua` | 58 ms | 253 ms | pass | MAINT-63 | 1/2 | 23 | `/home/builder/lunar/.intellijPlatform/sandbox/lunar/GO-2026.2.3/system-test/lunar/definitions/openresty-17f581b2e6f2d3a30ab0e6564eb2eb40426db6b5/resty/websocket/client.lua` | 28 |
| kong | `kong/plugins/syslog/handler.lua` | 33 ms | 267 ms | pass | MAINT-63 | 1/2 | 22 | `/home/builder/lunar/.intellijPlatform/sandbox/lunar/GO-2026.2.3/system-test/lunar/definitions/openresty-17f581b2e6f2d3a30ab0e6564eb2eb40426db6b5/resty/websocket/client.lua` | 34 |
| ldoc | `ldoc/markdown.lua` | 329 ms | 371 ms | MAINT-61 (own) | MAINT-63 | 1/2 | 4 | `/home/builder/lunar/.intellijPlatform/sandbox/lunar/GO-2026.2.3/plugins-test/lunar/lib/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua` | 123 |
| luals | `script/vm/compiler.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 30 | `luals/script/utility.lua` | 141 |
| luals | `script/vm/def.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 48 | `luals/script/parser/guide.lua` | 116 |
| luals | `script/vm/doc.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 42 | `luals/script/utility.lua` | 106 |
| luals | `script/vm/field.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 57 | `luals/script/parser/guide.lua` | 128 |
| luals | `script/vm/function.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 49 | `luals/script/parser/guide.lua` | 112 |
| luals | `script/vm/generic.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 61 | `luals/script/parser/guide.lua` | 99 |
| luals | `script/vm/global.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 48 | `luals/script/utility.lua` | 104 |
| luals | `script/vm/infer.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 49 | `luals/script/utility.lua` | 107 |
| luals | `script/vm/library.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 51 | `luals/script/client.lua` | 89 |
| luals | `script/vm/node.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 53 | `luals/script/parser/guide.lua` | 99 |
| luals | `script/vm/operator.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 48 | `luals/script/parser/guide.lua` | 110 |
| luals | `script/vm/precompile.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 61 | `luals/script/parser/guide.lua` | 87 |
| luals | `script/vm/ref.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 50 | `luals/script/parser/guide.lua` | 98 |
| luals | `script/vm/sign.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 65 | `luals/script/parser/guide.lua` | 93 |
| luals | `script/vm/tracer.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 50 | `luals/script/parser/guide.lua` | 100 |
| luals | `script/vm/type.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 49 | `luals/script/parser/guide.lua` | 139 |
| luals | `script/vm/value.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 60 | `luals/script/parser/guide.lua` | 87 |
| luals | `script/vm/variable.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 55 | `luals/script/utility.lua` | 82 |
| luals | `script/vm/visible.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 53 | `luals/script/parser/guide.lua` | 104 |
| luals | `script/vm/vm.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 50 | `luals/script/parser/guide.lua` | 108 |
| opensmce | `src/BootScreen/BootScreen.lua` | 362 ms | 182 ms | MAINT-63 | pass | 1/2 | 10 | `/src/src/BootScreen/UI/Checkbox.lua` | 136 |
| opensmce | `src/Debug.lua` | 397 ms | 438 ms | MAINT-63 | MAINT-63 | 2/2 | 9 | `/src/src/Console.lua` | 69 |
| opensmce | `src/Game/ShotSphere.lua` | 203 ms | 293 ms | pass | MAINT-63 | 1/2 | 5 | `/src/src/Essentials/Vector2.lua` | 140 |
| opensmce | `src/Game/Sphere.lua` | 360 ms | 294 ms | MAINT-61 (own) | MAINT-63 | 1/2 | 4 | `/src/src/Essentials/Vector2.lua` | 54 |
| opensmce | `src/ResourceManager.lua` | 259 ms | 245 ms | MAINT-63 | pass | 1/2 | 17 | `/src/src/Essentials/Vector2.lua` | 52 |
| prism | `engine/core/levelbuilder.lua` | 257 ms | 225 ms | MAINT-63 | pass | 1/2 | 2 | `/src/engine/init.lua` | 57 |
| snabb | `src/lib/yang/alarms.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 43 | `snabb/src/lib/yang/path_data.lua` | 137 |
| snabb | `src/lib/yang/binary.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 47 | `snabb/src/lib/yang/schema.lua` | 176 |
| snabb | `src/lib/yang/data.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 42 | `snabb/src/lib/yang/data.lua` | 172 |
| snabb | `src/lib/yang/list.lua` | 181 ms | 299 ms | pass | MAINT-63 | 1/2 | 24 | `snabb/src/core/lib.lua` | 37 |
| snabb | `src/lib/yang/path.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 42 | `snabb/src/lib/yang/schema.lua` | 179 |
| snabb | `src/lib/yang/path_data.lua` | timeout | timeout | MAINT-63 | MAINT-61 (dep) | 1/2 | 44 | `snabb/src/lib/yang/schema.lua` | 170 |
| snabb | `src/lib/yang/rpc.lua` | timeout | 938 ms | MAINT-63 | MAINT-63 | 2/2 | 44 | `snabb/src/lib/yang/schema.lua` | 161 |
| snabb | `src/lib/yang/schema.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 44 | `snabb/src/lib/yang/schema.lua` | 187 |
| snabb | `src/lib/yang/state.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 47 | `snabb/src/lib/yang/data.lua` | 111 |
| snabb | `src/lib/yang/yang.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 45 | `snabb/src/lib/yang/data.lua` | 124 |
| telescope | `lua/telescope/actions/generate.lua` | 617 ms | 604 ms | MAINT-63 | MAINT-63 | 2/2 | 41 | `telescope/lua/telescope/actions/init.lua` | 128 |
| telescope | `lua/telescope/actions/init.lua` | 398 ms | 181 ms | MAINT-63 | pass | 1/2 | 30 | `neovim:vim/_core/shared.lua` | 74 |
| telescope | `lua/telescope/actions/layout.lua` | 266 ms | 262 ms | MAINT-63 | MAINT-63 | 2/2 | 25 | `telescope/lua/telescope/pickers/layout_strategies.lua` | 71 |
| telescope | `lua/telescope/async_job.lua` | 349 ms | 136 ms | MAINT-63 | pass | 1/2 | 21 | `neovim:vim/_core/shared.lua` | 94 |
| telescope | `lua/telescope/builtin/__diagnostics.lua` | 937 ms | 721 ms | MAINT-63 | MAINT-63 | 2/2 | 53 | `telescope/lua/telescope/pickers.lua` | 160 |
| telescope | `lua/telescope/builtin/__files.lua` | timeout | 586 ms | MAINT-63 | MAINT-63 | 2/2 | 55 | `telescope/lua/telescope/pickers.lua` | 171 |
| telescope | `lua/telescope/builtin/__git.lua` | 817 ms | 763 ms | MAINT-63 | MAINT-63 | 2/2 | 60 | `neovim:vim/_core/shared.lua` | 93 |
| telescope | `lua/telescope/builtin/__internal.lua` | 959 ms | timeout | MAINT-63 | MAINT-63 | 2/2 | 60 | `telescope/lua/telescope/builtin/init.lua` | 160 |
| telescope | `lua/telescope/command.lua` | 409 ms | 327 ms | MAINT-63 | MAINT-63 | 2/2 | 27 | `telescope/lua/telescope/builtin/init.lua` | 116 |
| telescope | `lua/telescope/finders.lua` | 460 ms | 271 ms | MAINT-63 | MAINT-63 | 2/2 | 29 | `telescope/lua/telescope/async_job.lua` | 115 |
| telescope | `lua/telescope/finders/async_oneshot_finder.lua` | 414 ms | 240 ms | MAINT-63 | pass | 1/2 | 26 | `neovim:vim/_core/shared.lua` | 76 |
| telescope | `lua/telescope/make_entry.lua` | 266 ms | 118 ms | MAINT-63 | pass | 1/2 | 24 | `neovim:vim/_core/shared.lua` | 62 |
| telescope | `lua/telescope/mappings.lua` | 300 ms | 238 ms | MAINT-63 | pass | 1/2 | 34 | `neovim:vim/_core/shared.lua` | 57 |
| telescope | `lua/telescope/pickers.lua` | 433 ms | 464 ms | MAINT-63 | MAINT-63 | 2/2 | 46 | `neovim:vim/_core/shared.lua` | 63 |
| telescope | `lua/telescope/previewers/buffer_previewer.lua` | 313 ms | 250 ms | MAINT-63 | MAINT-63 | 2/2 | 28 | `neovim:vim/_core/shared.lua` | 71 |
| telescope | `lua/telescope/previewers/init.lua` | 304 ms | 182 ms | MAINT-63 | pass | 1/2 | 29 | `neovim:vim/_core/shared.lua` | 55 |
| telescope | `lua/telescope/previewers/term_previewer.lua` | 354 ms | 146 ms | MAINT-63 | pass | 1/2 | 27 | `neovim:vim/_core/shared.lua` | 82 |
| telescope | `lua/telescope/previewers/utils.lua` | 304 ms | 135 ms | MAINT-63 | pass | 1/2 | 23 | `neovim:vim/_core/shared.lua` | 93 |
| telescope | `lua/telescope/testharness/runner.lua` | 410 ms | 353 ms | MAINT-63 | MAINT-63 | 2/2 | 22 | `telescope/lua/telescope/builtin/init.lua` | 163 |
| telescope | `lua/tests/automated/action_spec.lua` | 420 ms | 238 ms | MAINT-63 | pass | 1/2 | 30 | `telescope/lua/telescope/actions/init.lua` | 112 |
| telescope | `lua/tests/automated/command_spec.lua` | 598 ms | 308 ms | MAINT-63 | MAINT-63 | 2/2 | 28 | `telescope/lua/telescope/builtin/init.lua` | 160 |
| telescope | `lua/tests/automated/diagnostics_spec.lua` | timeout | 629 ms | MAINT-63 | MAINT-63 | 2/2 | 53 | `telescope/lua/telescope/pickers.lua` | 145 |
| telescope | `lua/tests/automated/layout_strategies_spec.lua` | 360 ms | 271 ms | MAINT-63 | MAINT-63 | 2/2 | 28 | `telescope/lua/telescope/pickers/layout_strategies.lua` | 69 |
| telescope | `lua/tests/automated/telescope_spec.lua` | 689 ms | 630 ms | MAINT-63 | MAINT-63 | 2/2 | 47 | `telescope/lua/telescope/pickers.lua` | 171 |
| telescope | `lua/tests/automated/utils_spec.lua` | 290 ms | 144 ms | MAINT-63 | pass | 1/2 | 22 | `neovim:vim/_core/shared.lua` | 86 |
| telescope | `lua/tests/helpers.lua` | 993 ms | 576 ms | MAINT-63 | MAINT-63 | 2/2 | 59 | `telescope/lua/telescope/pickers.lua` | 104 |
| telescope | `lua/tests/pickers/find_files__readme.lua` | 394 ms | 273 ms | MAINT-63 | MAINT-63 | 2/2 | 25 | `telescope/lua/telescope/builtin/init.lua` | 127 |
| telescope | `lua/tests/pickers/find_files__scrolling_descending_cycle.lua` | 302 ms | 227 ms | MAINT-63 | pass | 1/2 | 25 | `telescope/lua/telescope/builtin/init.lua` | 121 |

## See Also
- Design: [design.md](design.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
