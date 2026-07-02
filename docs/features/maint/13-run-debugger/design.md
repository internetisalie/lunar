---
id: "MAINT-13-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-13"
folders:
  - "[[features/maint/13-run-debugger/requirements|requirements]]"
---

# Technical Design: MAINT-13 — Test Coverage: Run & Debugger

This is a **test map**, not a production design — MAINT-13 adds/extends unit tests only and changes
no production code. For each target class it names the real symbols under test (with `path:line`
evidence), the test approach, and any construction seams needed. It also explicitly marks the parts
of the subsystem that are **integration-only** and out of unit scope.

## 1. Target Inventory (grounded)

All targets live under `src/main/kotlin/net/internetisalie/lunar/run`.

| # | Class / symbol | Path:line | Under test? | Approach |
|---|----------------|-----------|-------------|----------|
| 1 | `LuaDebugConnection.breakpointDataPattern` / `watchpointDataPattern` (public `companion` `Pattern`s) | `LuaDebugConnection.kt:370-371` | Yes | Match the `Pattern` objects directly against literal strings — no socket. |
| 2 | `DebugCommand.toString()` | `LuaDebugConnection.kt:138-146` | Yes | Pure string assertion. |
| 3 | `DebugCommandKind` (`group`, `minArgs`, `maxArgs`, `responses`) | `LuaDebugConnection.kt:38-132` | Yes | Read enum members; assert model. |
| 4 | `DebuggerStatus` (`code`, `isError`, `message`) | `LuaDebugConnection.kt:11-23` | Already (`TestLuaDebugConnection.kt`) | Extended lightly if useful; primarily existing. |
| 5 | `LuaPosition.createRemotePosition` / `createLocalPosition` / `args()` | `LuaPosition.kt:29-51` | Yes | `BaseDocumentTest` + `XDebuggerUtil` (as in existing `TestLuaPosition`). |
| 6 | `LuaDebuggerEvaluator.getExpressionRangeAtOffset` (+ private `findExpression`) | `LuaDebuggerEvaluator.kt:69-109` | Yes | `BaseDocumentTest` doc + a `FakeXDebugSession` seam (see §3). |
| 7 | `LuaChunkCompletion.isComplete` | `LuaChunkCompletion.kt:17-23` | Yes (extend existing) | `BaseDocumentTest` + `runInEdtAndGet` (as in existing `TestLuaChunkCompletion`). |
| 8 | `LuaConsoleExecuteHandler` dispatch predicate | `LuaConsoleExecuteHandler.kt:24-31` | Yes (predicate only) | Evaluate `text.isBlank() \|\| LuaChunkCompletion.isComplete(...)` directly. |

### Integration-only (explicitly NOT unit-tested here)

- **`LuaDebugConnection.run()` / `send()` / `receive()` loop** (`:190-338`) — drives a real `Socket`
  and blocking `BufferedReader`. Covered by `TestLuaDebugHarness` + `LuaDebugHarness.kt`
  (`src/test/kotlin/.../run/`), which spawns a real Lua+mobdebug subprocess. Only the pure `Pattern`
  members it uses are unit-tested (target #1).
- **`LuaDebuggerController`** (`LuaDebuggerController.kt:46`) — `waitForConnect` opens a `ServerSocket`
  on 8172 and runs pooled-thread loops; `queueRequest`/`execute`/`variables` wire `AsyncPromise`s to a
  live connection. Not unit scope. (A `FakeXDebugSession` lets us *construct* one for target #6, but no
  controller method is invoked.)
- **`LuaLineBreakpointHandler`** (`LuaLineBreakpointHandler.kt:7`) — requires a live `LuaDebugProcess`
  (which needs a live `XDebugSession` + `ExecutionResult`), and `addBreakPoint` reaches a live controller
  (`LuaDebugProcess.kt:166-177`). Runtime delegation is integration-only; the pure position mapping it
  depends on is covered via `LuaPosition` (target #5). No mocking framework (Mockito/MockK) is on the
  test classpath (`build.gradle.kts` — no `mockito`/`mockk` dependency), so a "verify forwarded call"
  test is not available.
- **`LuaConsoleRunner` / `LuaConsoleView` / `LuaConsoleAction`** — UI/tool-window; out of scope.

## 2. Prior Art in This Repo (extend, don't duplicate)

Existing run/console tests (`glob src/test/**/net/internetisalie/lunar/run/**`):

- `TestLuaDebugConnection.kt` — has `testDebugCommandToString` (3 cases) and `testDebuggerStatus`.
  **Extended** by target #1/#2/#3 (new cases go in a new `TestLuaDebugConnectionParsing.kt` to keep the
  file focused; see plan). No duplication of the three existing `toString` cases.
- `TestLuaPosition.kt` — has `testCreateRemotePosition`, `testCreateLocalPosition`. **Extended** with an
  `args()` case and an explicit round-trip assertion.
- `TestLuaChunkCompletion.kt` — has complete/incomplete/mid-chunk cases. **Extended** with table/paren/
  `do`/`repeat`/long-string/blank cases (new `@Test` methods in the same file).
- `TestLuaLineBreakpointHandler.kt` — a no-op `assertNotNull(class)` placeholder. **Left as-is** (its
  target is integration-only per §1); MAINT-13 does not add a fake-delegation test for it.
- No existing test for `LuaDebuggerEvaluator` (grep `src/test` for `LuaDebuggerEvaluator` → none) — target
  #6 is **new** coverage.
- No existing test for `LuaConsoleExecuteHandler` (grep → none) — target #8 is **new** coverage.

## 3. Construction Seams

### 3.1 `FakeXDebugSession` (test-only helper, for target #6)
`LuaDebuggerEvaluator(myController: LuaDebuggerController)` (`LuaDebuggerEvaluator.kt:36`) requires a
`LuaDebuggerController`, whose constructor (`LuaDebuggerController.kt:46-77`) reads only these `session`
members in `init`: `setPauseActionSupported(Boolean)`, `runProfile` (cast `as? LuaRunConfiguration` — a
plain `null` is fine), `project`, and `project.basePath`. `getExpressionRangeAtOffset` never touches the
controller. So the test supplies a minimal `object : XDebugSession` implementing all interface methods
(`XDebugSession` is an interface, `xdebugger-api/.../XDebugSession.java:42`); the load-bearing overrides are:

```kotlin
private fun fakeSession(project: Project): XDebugSession = object : XDebugSession {
    override fun getProject(): Project = project
    override fun getRunProfile(): RunProfile? = null
    override fun setPauseActionSupported(isSupported: Boolean) {}
    // …all other XDebugSession methods → TODO("not used")
}
```

The remaining ~40 interface methods throw `NotImplementedError` (never invoked). This mirrors the suite's
existing `object : …` fakes (e.g. `TestLuaRunnerTest.kt:404` `object : TestFrameworkRunningModel`,
`:416` `object : ComponentContainer`). `project` is `myFixture.project` from `BaseDocumentTest`.

> **Alternative considered & rejected**: platform `XDebuggerTestUtil`/`XDebugSessionImpl` — heavier, not
> currently used anywhere in the suite, and unnecessary since no controller/session method is exercised.

### 3.2 Dispatch predicate (target #8)
`LuaConsoleExecuteHandler.runExecuteAction` (`LuaConsoleExecuteHandler.kt:24-31`) branches on
`text.isBlank() || LuaChunkCompletion.isComplete(project, text)`. Constructing the handler needs a
`ProcessHandler`, and taking either branch drives a live process attachment (`super.runExecuteAction`) or a
`WriteCommandAction` editor write — neither unit-appropriate. The test therefore asserts the **predicate**
itself (a pure boolean over `LuaChunkCompletion.isComplete`, target #7), which is exactly the branch key.
This is honest coverage of the decision logic without a fictional `shouldSubmit` accessor.

## 4. Test Approach Per Requirement

### MAINT-13-01 — DBGp pause-data parsing (target #1)
`LuaDebugConnection.breakpointDataPattern` = `^(.+)\s+(\d+)$`; `watchpointDataPattern` =
`^(.+)\s+(\d+)\s+(\d+)$` (`:370-371`), both **public** members of the `companion object` (`:369`), so
reachable as `LuaDebugConnection.breakpointDataPattern`. Tests call `.matcher(data)` and assert
`matches()` plus `group(n)`. Covers greedy path-with-space (TC 2) and rejection of missing numeric groups
(TC 3, 5). No socket, no `run()` loop.

### MAINT-13-02 — Command model & framing (targets #2, #3)
- `DebugCommand.toString()`: assert `OVER` (TC 6), `SETW x>5` (TC 7). Complements the existing 3 cases.
- `DebugCommandKind`: `SETB.group == Config`, `SETB.minArgs == 2`, `SETB.maxArgs == 2` (`:51-55`, TC 8);
  `EXEC.responses[DebuggerStatus.OK] == DebuggerResponseDataKind.Extended` (`:84-93`, TC 9);
  `STEP.group == DebugCommandGroup.Run` (`:101-106`, TC 10).

### MAINT-13-03 — Position mapping (target #5)
Extend `TestLuaPosition`: `createRemotePosition` of IDE line 10 → `line == 11` and `path` endsWith file
name (TC 11); `LuaPosition("main.lua", 5).args() == listOf("main.lua", "5")` (`:29-31`, TC 12);
`createLocalPosition(vf, 11)?.line == 10` (`:48-51`, TC 13). Uses `myFixture.configureByText` +
`XDebuggerUtil.getInstance().createPosition` exactly as the existing cases.

### MAINT-13-04 — Expression range (target #6)
Build `LuaDebuggerEvaluator(LuaDebuggerController(fakeSession(project)))`. Configure a Lua file via
`myFixture.configureByText`, take its `document` (`PsiDocumentManager.getDocument`) and an offset (via the
`<caret>` marker or a substring index). Call `getExpressionRangeAtOffset(project, document, offset, false)`
inside EDT (`runInEdtAndGet`) and assert `document.getText(range)`:
- `x = a.b.c`, caret in `c` → `a.b.c` — `findExpression` (`:99-109`) walks up while `parent is LuaExpr`,
  so the whole `LuaIndexExpr` chain is returned, not the leaf name (TC 14).
- `x = 1 + 2`, caret on `1` → `1 + 2` (outermost `LuaBinOpExpr` — `src/main/gen/.../psi/LuaBinOpExpr.java`) (TC 15).
- `local y = 1`, caret on the `local` keyword → `null` — no enclosing `LuaExpr`, or it is a
  `LuaStatement` (the `expression is LuaStatement` guard, `:106`) (TC 16).

`LuaExpr` and `LuaStatement` are real PSI types: `src/main/gen/net/internetisalie/lunar/lang/psi/LuaExpr.java`
and `.../LuaStatement.java` (imported at `LuaDebuggerEvaluator.kt:33-34`).

### MAINT-13-05 — Chunk completion (target #7)
Extend `TestLuaChunkCompletion` with new `@Test` methods reusing its `isComplete(text)` helper
(`runInEdtAndGet { LuaChunkCompletion.isComplete(myFixture.project, text) }`):
open table `"t = {"` → false (TC 18); `"do"` / `"repeat"` → false (TC 19); `"print("` → false (TC 20);
long string `"s = [["` → false (TC 21); `"local 1x = 2"` → true (mid-chunk error, TC 22); `""` → true
(no `PsiErrorElement`, TC 23). Mechanism: `isComplete` returns `false` iff a `PsiErrorElement` ends at
`text.length` (`LuaChunkCompletion.kt:20-22`).

### MAINT-13-06 — Dispatch predicate (target #8)
In `BaseDocumentTest`, evaluate `text.isBlank() || LuaChunkCompletion.isComplete(myFixture.project, text)`
(the literal predicate from `LuaConsoleExecuteHandler.kt:26`): `"print(1)"` → true (TC 24),
`"function f()"` → false (TC 25), `"   "` → true (TC 26). Documents that the handler submits on true and
inserts a continuation newline on false.

## 5. External Data & Parsing
The only "external" format touched is the DBGp textual response line consumed by `receive()`, parsed by
the two `Pattern`s in target #1. Their grammar (grounded at `:370-371`):
- breakpoint pause data: `<file> <line>` — `(.+)` (greedy, may contain spaces) `\s+` `(\d+)`.
- watchpoint pause data: `<file> <line> <index>` — `(.+)` `\s+` `(\d+)` `\s+` `(\d+)`.
No new parser is introduced; tests assert the existing patterns' capture groups.

## 6. Requirement Coverage

| Requirement | Priority | Covered by (section / TCs) |
|-------------|----------|----------------------------|
| MAINT-13-01 | M | §4 MAINT-13-01 (TC 1–5) |
| MAINT-13-02 | M | §4 MAINT-13-02 (TC 6–10) |
| MAINT-13-03 | M | §4 MAINT-13-03 (TC 11–13) |
| MAINT-13-04 | M | §3.1, §4 MAINT-13-04 (TC 14–16) |
| MAINT-13-05 | M | §4 MAINT-13-05 (TC 17–23) |
| MAINT-13-06 | S | §3.2, §4 MAINT-13-06 (TC 24–26) |

## 7. Integration Points
No `plugin.xml` change, no new production symbols, no new extension points — coverage-only. New/edited
files are all under `src/test/kotlin/net/internetisalie/lunar/run/…`. The one new test helper
(`FakeXDebugSession`, §3.1) is test-scoped (either a `private fun` in the evaluator test or a small
top-level helper in the same test package).

## 8. Non-Functional / Contract Compliance
- **Threading (contract §1):** no unit test opens a socket, spawns a process, or runs the DBGp loop.
  PSI/document access is inside EDT + read action via `BaseDocumentTest` + `runInEdtAndGet`.
- **Fixtures (contract §6):** light `BasePlatformTestCase`-derived `BaseDocumentTest` and `object :` fakes;
  no mocking framework (none is available).
- **No production edits:** all changes are additive test code.

## 9. Open Questions

_None — feature has cleared the planning bar._
