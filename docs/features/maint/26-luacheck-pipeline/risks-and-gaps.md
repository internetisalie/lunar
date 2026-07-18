---
id: "MAINT-26-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-26"
folders:
  - "[[features/maint/26-luacheck-pipeline/requirements|requirements]]"
---

# MAINT-26: Risks & Gaps

## Critical Risks

### Risk 1.1: luacheck `.luacheckrc` config-override selection differs under `--filename`
- **Impact**: Under stdin, luacheck selects config overrides by the value of `--filename`, not by
  a real path (`main.lua:218`, "for selecting configuration overrides"). If a project's
  `.luacheckrc` keys overrides on directory globs, passing the bare file name (not the
  workdir-relative path) could apply the wrong override set.
- **Likelihood**: low
- **Mitigation**: pass the workdir-relative path as `--filename` (the invoker already computes
  `virtualFile.name`; use the path relative to `workDir` when it is a descendant). The command
  still runs with `withWorkDirectory(workDir.path)` so `.luacheckrc` discovery is unchanged.
  Covered by DR-03.

### Risk 1.2: Fixing the `distinct()` regression re-admits duplicate flags in overlapping config
- **Impact**: If a user sets `LUACHECK_ARGUMENTS = "--codes"` and `DEFAULT_ARGS` also has
  `--codes`, dropping `distinct()` naively would double it. `dedupePairs` (§3.1) must de-dup
  lone flags too, or luacheck may warn on repeated flags.
- **Likelihood**: medium
- **Mitigation**: `dedupePairs` keys lone flags by the token itself and pairs by `flag+value`
  (§3.1 step 2); the April-2025 protection is preserved for both shapes. Covered by TC1/TC2.

## Design Gaps

### Gap 2.1: Real-flow annotator test seam for a fake luacheck
- **Question**: `LuaToolExecutionService` is a non-`open` app `@Service` with a `final capture`
  (`LuaToolExecutionService.kt:21-23`), so it cannot be swapped via `replaceService` with a
  subclass. How does `LuaCheckAnnotatorTest` drive `doHighlighting` without a real luacheck?
- **Options / leaning**:
  1. **Shell-fake command** (leaning): the existing `LuaToolExecutionServiceTest` runs
     `/bin/sh -c "echo ...; exit N"` through the *real* `capture` (`:32,42,49`). The annotator
     test can point the resolved tool at a `/bin/sh -c` script that echoes luacheck-shaped lines
     and reads stdin — exercising the whole stdin + parse + offset path end to end. Requires the
     tool registry to accept an arbitrary exe path (as `LuaCheckCommandLineTest.seedToolAt` already does).
  2. **Split the pure logic** so the annotator's offset math (`applyProblem`) and the invoker's
     `classify`/`problemFrom` are unit-tested directly (TC3–TC5, TC10–TC12 as pure tests), and the
     annotator wiring gets one `configureByText` smoke test only.
- **Resolved by**: DR-02 — spike the shell-fake seam; if the tempdir `/bin/sh` script is flaky in
  the light fixture, fall back to option 2. Fold the chosen seam into the Phase 3/4 tests.

## Technical Debt & Future Work
- **TBD: batch-inspection path (`getPairedBatchInspectionShortName`)** — the `unfair`
  `LuaCheckInspection` batch run (Analyze > Inspect Code) still goes through the on-disk file
  (no editor document). The stdin path is editor-only; batch is left on the `useStdin = false`
  fallback (§2.3). Not a regression; parked.
- **TBD: `.luacheckrc` JSON-schema interplay** — SCHEMA-03 provides `.luacheckrc` completion; no
  cross-impact here, but a future feature could validate `--std` values against the target.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| MAINT-26-00-DR-01 | Verify luacheck supports `- --filename` stdin. **DONE**: confirmed in vendored source `../tools/luacheck/src/luacheck/main.lua (external-local checkout, one level above the repo root — not git-tracked):33` (`'-'` = stdin), `:218` (`--filename`), `:303-306` (`io.stdin`). Stdin is the primary #30 fix; clamping is the guard. | §3.3 stdin decision | done |
| MAINT-26-00-DR-02 | Spike the annotator real-flow test seam: point the seeded luacheck tool at a `/bin/sh -c` echo-script (option 1) and confirm `doHighlighting` produces the clamped ranges; else split pure logic (option 2). | Gap 2.1 | done |
| MAINT-26-00-DR-03 | Confirm `--filename` value: pass workdir-relative path (not bare name) so `.luacheckrc` overrides resolve as they do for the on-disk run; add a fixture with a directory-scoped override. | Risk 1.1 | deferred |


**DR-03 deferral (2026-07-17, review remediation):** MAINT-26-03's offset accuracy (#30) is delivered — the annotator runs with `withWorkDirectory(virtualFile.parent)` and `--filename <name>`, which is self-consistent: `.luacheckrc` discovery searches upward from the file's own directory (common case works), and diagnostics report the correct name. Only **directory-glob-scoped** `.luacheckrc` overrides under stdin (Risk 1.1, likelihood **low**) are unaddressed — resolving them would require switching the workdir to the project root and passing a root-relative `--filename`, which risks regressing `.luacheckrc` *discovery* for the common case. That trade-off is exactly what the DR-03 empirical spike (directory-scoped-override fixture) was meant to settle; it is deferred as a tracked follow-up rather than shipped unverified.

## Test Case Gaps
- No test currently exercises a **non-zero, non-1 exit** through the annotator (only the pure
  `classify` TC11 covers it). If DR-02 lands option 1, add an annotator-level CRASHED smoke test.
- No coverage for a `--filename` containing a space; add to `LuaCheckCommandLineTest`.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
