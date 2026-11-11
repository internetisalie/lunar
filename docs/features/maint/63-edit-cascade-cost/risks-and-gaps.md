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

### Risk 1.1: Per-file invalidation stays no better than base after MAINT-65
- **Impact**: MAINT-63 needs a different approach, and v0.21.21 goal 1 stays held on its table.
- **Likelihood**: unknown. DR-08 attributes most edit-phase provisional builds to module-guard
  replay inside import cycles, which MAINT-65 removes; what remains has not been measured.
- **Mitigation**: MAINT-65-01's exit is a re-probe of svca under the r10 rule that misses fewer
  passes than base.

### Risk 1.2: A stale snapshot served after an edit
- **Impact**: a wrong type, and therefore a wrong finding.
- **Likelihood**: low for require, global, class and cycle routes (DR-03, DR-08: every soundness
  re-read rebuilt). Unknown for `@alias` consumers: DR-08 found them never rebuilt and did not check
  whether the served type is stale.
- **Mitigation**: the first probe on resumption (Gap 2.2) and an alias soundness check.

## Design Gaps

### Decision 2.1: MAINT-63 blocked on MAINT-65
- **Decision**: MAINT-63 is blocked on MAINT-65 (supervisor, 2026-11-11), on DR-08's evidence. The
  earlier split, which moved unclearable passes to MAINT-65 or MAINT-61 by a sweep attribution rule,
  is superseded.
- **Not taken**: early cutoff. It needs a sound interface hash of inferred exports, and its unsound
  upper bound (svcx, DR-03) still leaves 47 misses.

### Gap 2.2: The read 1 s after an edit does not match the cold digest
- **Question**: in DR-08, 33 files' findings digest at the 1 s after-edit read differs from their
  cold digest, while every settled re-read matches base. Base's 1 s after-edit digest was never
  recorded. It is not established whether this is a regression, an artefact of the 1 s deadline, or
  a pre-existing property of base.
- **Resolved by**: the first probe for whoever resumes MAINT-63. Record base's 1 s after-edit digest
  on the DR-08 subset and compare it with the cold digest.

## Technical Debt & Future Work
- **BUG-519** (definition-library snapshots never pinned) is independent of this item; most of
  those snapshots carry an in-progress hit, so per-file invalidation would not reach them either.

## Pre-Implementation De-risking Tasks

Probes ran on the libvirt builder at `e9eb09d6` with `test --rerun --no-build-cache`. The probe
harness, now deleted, was a throwaway sweep over eight members (telescope, gitsigns, blinkcmp,
kong, apisix, tntmetrics, tntcrud, tntqueue: 699 files, 338 of this item's 407 passes). For each
file it ran:
- a cold read with a 60 s deadline;
- an append of `"\n"` and a read with a 1 s deadline, as the MAINT-60 sweep does;
- a settled re-read;
- a soundness pass: empty one dependency the cold read built nested, re-read, then restore.

A miss is an edit read at 250 ms or more, or one that did not finish. "Variants" swap
`dependenciesFor`'s churn dependency as follows:

| Variant | Unpinned snapshot depends on |
|---|---|
| base | today: `MODIFICATION_COUNT` |
| svc | a set of counters for the source URLs each snapshot recorded (TYPE-11 frames), plus a project epoch that ticks when any declaration signature changes; snapshots that hit an in-progress build fall back to `MODIFICATION_COUNT` |
| svca | svc, plus: a snapshot nested under a provisional ancestor also falls back |
| svcx | svc, but the source counters never change (unsound upper bound for early cutoff) |
| svcr | svca, plus every library `PsiFile` held strongly |
| r10 | svca, plus: any of the four re-entrancy guards firing (`LuaTypeManagerImpl` `resolvingModules`, `resolvingTypes`, `resolvingGlobals`; TYPE-10 `inProgressSnapshot`) marks every open TYPE-11 frame provisional, and the flag survives memo replay |

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-01 | Measure base on the subset | baseline | done: **281/338 table passes miss**. Edit median 82 ms. 9 081 nested rebuilds summed over the table rows. Soundness 71/71 rebuilt. |
| DR-02 | Measure the signature cost and the dependency size | signature cost, dependency size | done (svca counters): signature time summed to 4 161 ms over the run's edit and soundness changes, about 6 ms per file. 110 307 source-URL entries over 4 317 per-file snapshots, about 26 each. Edit-phase epoch ticks: 0 (`"\n"` changes no signature). |
| DR-03 | Measure each variant on the subset; compare findings | variant choice, MAINT-63-02 | **Superseded by DR-08 for svca:** svca's provisional rule missed the type manager's guards, so its miss counts measure an unsound variant. done. Table misses: base 281, **svca 147 / 134 / 159** (r7 / r5 / r8), svc 101, svcx 47, svcr 161. Edit median: base 82, svca 32–37, svc 29, svcx 26 ms. Cold digests: 0 differ in every variant. After-edit digests that differ from base: svca 2 (tntcrud `crud/common/utils.lua` and `crud/schema.lua`, in every run), svc 3 (gitsigns `debug.lua`, `system.lua`, blinkcmp `luasnip.lua`), svcx 49. Soundness: svca 73/73, svc 71/71, svcx 3 not rebuilt. `luacheck.json` (`c8a255eb…`) and conformance `results.json` (`5634e7ab…`, equal to the committed file) are byte-identical for base, svca and svc. |
| DR-04 | Can the dependency array length vary by branch? | dependency shape | done: **no**. A run that returned 3 elements for provisional and 5 for per-file failed `IdempotenceChecker` in tntmetrics, tntcrud and tntqueue: "Non-idempotent computation … 5 != 3 … length of CachedValue dependencies". Fixed length 5 passed. |
| DR-05 | Is library snapshot loss a cost driver? | svcr | done. svca's edit-phase library rebuilds were mostly a **new `PsiFile` instance with no cached value**: 263 + 226 + 222 + 90 + 25 tagged `newpsi`. Holding the files (svcr) removed every `newpsi` rebuild, but table misses stayed at **161**. Not a driver. |
| DR-06 | Full MAINT-60 sweep with svca (`-PwithSnapshotSweep`, 14 min, 2 026 files) | MAINT-63-01 count over all 407 | **Superseded by DR-08:** the 182 cleared passes were measured under svca's unsound provisional rule. done: **182 of 407 table passes clear, 225 still miss** (119 timeouts, 106 measured at 250 ms or more). Still missing by member: telescope 56/57, gitsigns 50/54, tntcrud 37/71, blinkcmp 22/75, differ 18/35, tntmetrics 13/38, tntqueue 10/10, snabb 9/12, prism 2/5, factoryplanner 2/2, and one each in apisix, awesome, kristal, ldoc, opensmce, weakauras. kong, rampant and restyhttp clear fully. Edit median over all files 30 ms; `verdict.txt`: `misses 485`, `release HOLD`. By that run's `ranked.tsv`: 12 `own build` and 78 `edit cascade` with a slow dependency go to MAINT-61, and 135 are `edit cascade` / MAINT-63. |
| DR-07 | Attribute svcr's edit-phase rebuilds by the probe's per-build classification (which dependency branch it took, plus whether the build was the edited file) | the old split | **Superseded by DR-08:** the classification used svca's provisional rule. done (eight members). 66% (2 320) were provisional, 18% (643) were the edited file itself, 13% (445) were true readers of the edited file, and 3% (125) were other. In r7, 56 of the 147 remaining misses were 1 s timeouts. Recorded only by the deleted probe, not by the committed sweep. |
| DR-08 | Re-measure on the subset with the r10 rule. Same run: TC reachability per `reportFile` site, and signature cost with old-text capture | Decision 2.1, Gap 2.2, Risk 1.2 | done. **Table misses: 298 of 338, against base 281.** Edit median 66 ms (base 82). Provisional builds outnumber per-file builds about 8:1 in the edit phase (8 358 against 1 090). Only the module guard (`LuaTypeManagerImpl.kt:164`) fired (2 791 times in the edit phase); the type and global guards fired 0 times. The flag spreads through `moduleCache` replay. **Digests:** cold digests 0 differ from base, and after-edit (settled) digests 0 differ from base, so DR-03's two tntcrud differences are gone. Cold ≠ settled: gitsigns `hunks.lua` only, as in base. Cold ≠ the 1 s edit read's own digest: **33 files** (2 tntmetrics, 23 tntcrud including `utils.lua` and `schema.lua`, 8 tntqueue); base's edit-read digest was not recorded. Soundness: 72/72 rebuilt. Signature: 1 534 computations, mean about 4 ms, **max 61 ms**; old-text capture max 89 µs. TC reachability: one site alone turns red for class (`:382`) and cycle (the provisional fallback). require, global and callback need two sites removed (module or global, plus `forFile`). method stays rebuilt with method and `forFile` removed, because the class site also reports. alias is **never rebuilt**, even with every site on; whether its served type is stale was not measured. |

## See Also
- Requirements: [requirements.md](requirements.md)
