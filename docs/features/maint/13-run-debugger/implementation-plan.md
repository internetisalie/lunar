---
id: "MAINT-13-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-13"
folders:
  - "[[features/maint/13-run-debugger/requirements|requirements]]"
---

# MAINT-13: Implementation Plan

Coverage-only: every phase adds or extends a test file under
`src/test/kotlin/net/internetisalie/lunar/run/…`. No production code changes.

## Phases

### Phase 1: DBGp parsing & command model [Must]
- **Goal**: Cover the socket-free parsing/model surface of `LuaDebugConnection`.
- **File**: new `src/test/kotlin/net/internetisalie/lunar/run/TestLuaDebugConnectionParsing.kt`
  (plain class, `kotlin.test`; no fixture needed).
- **Tasks**:
  - [ ] `testBreakpointPatternParsesFileAndLine` — TC 1 (MAINT-13-01).
  - [ ] `testBreakpointPatternAllowsSpacesInPath` — TC 2 (MAINT-13-01).
  - [ ] `testBreakpointPatternRejectsMissingLine` — TC 3 (MAINT-13-01).
  - [ ] `testWatchpointPatternParsesFileLineIndex` — TC 4 (MAINT-13-01).
  - [ ] `testWatchpointPatternRejectsMissingIndex` — TC 5 (MAINT-13-01).
  - [ ] `testCommandToStringSingleAndArgs` — `OVER`, `SETW x>5` — TC 6, 7 (MAINT-13-02).
  - [ ] `testCommandKindModel` — `SETB` group/min/max; `EXEC` responses `OK→Extended`; `STEP` group `Run`
        — TC 8, 9, 10 (MAINT-13-02).
- **Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaDebugConnectionParsing*"`.

### Phase 2: Position mapping [Must]
- **Goal**: Round-trip line conversions + `args()`.
- **File**: extend existing `src/test/kotlin/net/internetisalie/lunar/run/TestLuaPosition.kt`.
- **Tasks**:
  - [ ] `testArgs` — `LuaPosition("main.lua", 5).args() == listOf("main.lua", "5")` — TC 12 (MAINT-13-03).
  - [ ] `testRoundTripLineConversion` — remote(IDE 10)=11 and local(11)=10 — TC 11, 13 (MAINT-13-03).
        (The two existing `testCreateRemotePosition`/`testCreateLocalPosition` already assert the shifts;
        add the explicit round-trip + `args()` cases.)
- **Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaPosition*"`.

### Phase 3: Expression range [Must]
- **Goal**: Widest-enclosing-`LuaExpr` selection in `LuaDebuggerEvaluator.getExpressionRangeAtOffset`.
- **File**: new `src/test/kotlin/net/internetisalie/lunar/run/TestLuaDebuggerEvaluator.kt`
  extending `BaseDocumentTest`.
- **Tasks**:
  - [ ] Add `private fun fakeSession(project: Project): XDebugSession` (design §3.1): `object : XDebugSession`
        overriding `getProject`→project, `getRunProfile`→null, `setPauseActionSupported`→{}; all other methods
        `TODO()`. Import `com.intellij.execution.configurations.RunProfile`,
        `com.intellij.xdebugger.XDebugSession`.
  - [ ] Helper `fun rangeAt(text: String, marker: String): String?` — `configureByText` a Lua file with a
        `<caret>` (or compute offset via `text.indexOf(marker)`), `runInEdtAndGet` calling
        `LuaDebuggerEvaluator(LuaDebuggerController(fakeSession(project)))
        .getExpressionRangeAtOffset(project, document, offset, false)`, return `range?.let { document.getText(it) }`.
  - [ ] `testSelectsWholeIndexChain` — `x = a.b.c`, caret in `c` → `"a.b.c"` — TC 14 (MAINT-13-04).
  - [ ] `testSelectsWholeBinaryExpr` — `x = 1 + 2`, caret on `1` → `"1 + 2"` — TC 15 (MAINT-13-04).
  - [ ] `testKeywordOffsetReturnsNull` — `local y = 1`, caret on `local` → `null` — TC 16 (MAINT-13-04).
- **Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaDebuggerEvaluator*"`.

### Phase 4: REPL completion & dispatch [Must / Should]
- **Goal**: Extend chunk-completion coverage and pin the execute-handler dispatch predicate.
- **Files**: extend `src/test/kotlin/net/internetisalie/lunar/run/console/TestLuaChunkCompletion.kt`;
  new `src/test/kotlin/net/internetisalie/lunar/run/console/TestLuaConsoleExecuteHandler.kt`
  (extends `BaseDocumentTest`).
- **Tasks (chunk completion, extend existing file)**:
  - [ ] `openTableIsIncomplete` — `"t = {"` → false — TC 18 (MAINT-13-05).
  - [ ] `openBlocksAreIncomplete` — `"do"`, `"repeat"` → false — TC 19 (MAINT-13-05).
  - [ ] `openParenIsIncomplete` — `"print("` → false — TC 20 (MAINT-13-05).
  - [ ] `openLongStringIsIncomplete` — `"s = [["` → false — TC 21 (MAINT-13-05).
  - [ ] `blankInputIsComplete` — `""` → true — TC 23 (MAINT-13-05).
        (`completeOneLinerIsComplete` already covers TC 17; the mid-chunk-error case TC 22
        — `"local 1x = 2"` → true — is pre-existing as `midChunkErrorIsComplete` in
        `src/test/kotlin/net/internetisalie/lunar/run/console/TestLuaChunkCompletion.kt`.)
- **Tasks (execute-handler dispatch predicate, new file)**:
  - [ ] `private fun shouldSubmit(text: String): Boolean =
        runInEdtAndGet { text.isBlank() || LuaChunkCompletion.isComplete(myFixture.project, text) }` — the
        literal predicate from `LuaConsoleExecuteHandler.kt:26`.
  - [ ] `completeChunkSubmits` — `"print(1)"` → true — TC 24 (MAINT-13-06).
  - [ ] `incompleteChunkContinues` — `"function f()"` → false — TC 25 (MAINT-13-06).
  - [ ] `blankForceSubmits` — `"   "` → true — TC 26 (MAINT-13-06).
- **Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *ChunkCompletion* --tests *ConsoleExecuteHandler*"`.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-13-01 | M | Phase 1 |
| MAINT-13-02 | M | Phase 1 |
| MAINT-13-03 | M | Phase 2 |
| MAINT-13-04 | M | Phase 3 |
| MAINT-13-05 | M | Phase 4 |
| MAINT-13-06 | S | Phase 4 |

## Verification Tasks
- [ ] Per-phase pattern runs above all green.
- [ ] Full regression: `tooling/gce-builder/gce-builder.sh run test` (no new failures vs. baseline).
- [ ] `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` on the touched test files
      (match surrounding IntelliJ-formatter style; do not mass-reformat).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: DBGp parsing & command model | done | Must |
| Phase 2: Position mapping | done | Must |
| Phase 3: Expression range | done | Must |
| Phase 4: REPL completion & dispatch | done | Must/Should |
