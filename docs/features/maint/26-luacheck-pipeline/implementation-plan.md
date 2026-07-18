---
id: "MAINT-26-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-26"
folders:
  - "[[features/maint/26-luacheck-pipeline/requirements|requirements]]"
---

# MAINT-26: Implementation Plan

All work is confined to `analysis/luacheck/*` and two files under `analysis/inspections/*`.
No `run/`, no type engine, no lexer. Each phase leaves the build green and is independently
testable. Phases are ordered so the pure-function fixes (no annotator plumbing) land first.

## Phases

### Phase 1: Command-line fidelity & stdlib (`arg`) [Must]
- **Goal**: Correct argument de-dup and the Lua 5.1 `arg` global — both pure-function, no
  annotator changes.
- **Tasks**:
  - [ ] Edit `net.internetisalie.lunar.analysis.luacheck.LuaCheckCommandLine` — remove
        `.distinct()` (`:23`); add `private fun dedupePairs(tokens: List<String>): List<String>`
        realizing design §3.1; apply it in `newLuaCheckCommandLine` before adding the positional
        token. Keep the existing `resolveArguments` assembly order.
  - [ ] Edit `net.internetisalie.lunar.analysis.inspections.LuaStandardGlobals` — add `"arg"`
        to `DELTA_51` (§3.7). No other change.
- **Exit criteria**: `LuaCheckCommandLineTest` still green; new pure tests for `dedupePairs`
  (TC1–TC2) and `LuaStandardGlobals.contains("arg", LUA51)` (TC8) pass.

### Phase 2: Suppression scoping [Should]
- **Goal**: Name-intersection block close (#31) and same-line inline ignore (#60).
- **Tasks**:
  - [ ] Edit `net.internetisalie.lunar.analysis.inspections.LuaInspectionSuppression` —
        `closeBlocks` (`:112`) applies the §3.4 intersection filter (close a block iff
        `allDiagnostics || names.isEmpty() || block.names.intersect(names).isNotEmpty()`).
  - [ ] Same file — `parseLuacheck` (`:135`) range end changes from `minOf(commentLine + 1, lineCount)`
        to `commentLine` (§3.6).
- **Exit criteria**: new `LuaInspectionSuppressionTest` cases TC6 (non-intersecting enable leaves
  a block open) and TC7 (inline ignore does not suppress the next line) pass.

### Phase 3: Outcome model & failure classification [Should]
- **Goal**: The typed result and §2.5.6 surfacing, without yet changing the offset math.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.analysis.luacheck.LuaCheckOutcome` (§2.1) —
        `sealed interface` + `FailureKind` enum.
  - [ ] Edit `net.internetisalie.lunar.analysis.luacheck.LuaCheckInvoker` — `invoke` returns
        `LuaCheckOutcome`; delete the dead `catch (_: ExecutionException)`; add
        `classify(result, fileName)` (§3.5); change `problemFrom` to take `fileName: String`.
        Parsing stays `stdout.lineSequence().mapNotNull { problemFrom(it, fileName) }` (§3.2).
- **Exit criteria**: new `LuaCheckInvokerClassifyTest` cases TC10 (START_FAILED→LAUNCH_FAILED),
  TC11 (exit 2→CRASHED), TC12 (exit 1→Problems) pass with a stubbed `LuaExecResult`.

### Phase 4: Stdin offsets & annotator wiring [Must]
- **Goal**: Editor-accurate offsets via stdin + clamped `applyProblem`; failure annotation.
- **Tasks**:
  - [ ] Edit `LuaCheckAnnotator.Info` (§2.4) — carry `fileName`, `workDir`, `documentText`,
        `project`, `documentLineCount`, `lineStartOffsets`. `collectInformation` reads them from
        `psiFile.fileDocument` / `psiFile.virtualFile` on the caller thread (no reads in `doAnnotate`).
  - [ ] Edit `newLuaCheckCommandLine` (§2.3) — add `useStdin: Boolean = true`; when set, emit
        `-` positional + `--filename <targetFileName>`.
  - [ ] Edit `LuaCheckInvoker.invoke(info)` — call `capture(cmd, FORMAT, stdin = info.documentText)`.
  - [ ] Edit `LuaCheckAnnotator.doAnnotate`/`apply` — consume `LuaCheckOutcome`; `applyProblem`
        uses the §3.3 clamped offset math against `info.lineStartOffsets`; `applyFailure` emits the
        single file-wide WARNING + `LOG.warn` (§3.5). Remove the now-unused `Results` class.
- **Exit criteria**: `LuaCheckAnnotatorTest` updated + green; new real-flow tests TC3 (unsaved
  buffer offset), TC4 (line-beyond-buffer clamp, no IOOBE), TC5 (missing binary → one WARNING) pass.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-26-01 Command-line fidelity | M | Phase 1 |
| MAINT-26-02 Robust output parsing | M | Phase 3 |
| MAINT-26-03 Editor-accurate offsets | M | Phase 4 |
| MAINT-26-04 Suppression scoping | S | Phase 2 |
| MAINT-26-05 Stdlib accuracy | S | Phase 1 |
| MAINT-26-06 Process hygiene | S | Phase 3, Phase 4 |

## Verification Tasks
- [ ] `LuaCheckCommandLineTest` — extend with `dedupePairs` cases — covers TC1, TC2.
- [ ] New `LuaCheckInvokerClassifyTest` — stub `LuaExecResult` outcomes — covers TC10–TC12.
- [ ] New/extended `LuaInspectionSuppressionTest` (`BasePlatformTestCase`, `configureByText`) —
      covers TC6, TC7.
- [ ] New `LuaStandardGlobalsTest` — covers TC8, TC9.
- [ ] Extended `LuaCheckAnnotatorTest` — covers TC3, TC4, TC5 (annotator drives a fake exec via
      the DR-02 seam).
- [ ] Run `human-verification-checklists.md` (live GoLand: unsaved-buffer ranges, missing-binary
      banner + annotation, `arg` no longer flagged).
- [ ] Full-suite gate via `gce-builder`; regression-relative to baseline 2123/0/1 (main 590acc29).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Command-line fidelity & stdlib | done | Must |
| Phase 2: Suppression scoping | done | Should |
| Phase 3: Outcome model & failure classification | done | Should |
| Phase 4: Stdin offsets & annotator wiring | todo | Must |
