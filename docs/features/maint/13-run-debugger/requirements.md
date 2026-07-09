---
id: "MAINT-13"
title: "MAINT-13: Test Coverage - Run & Debugger"
type: "feature"
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-13: Test Coverage - Run & Debugger

## Overview

Raise unit-test coverage of the run/debugger subsystem (`net.internetisalie.lunar.run`)
and the interactive REPL console (`net.internetisalie.lunar.run.console`). This is a
**coverage-only** feature — no production behavior changes. Tests target pure, deterministic
logic that can be exercised without a live DBGp socket loop or EDT-blocking I/O:

- DBGp response framing/parsing in `LuaDebugConnection` (the out-of-band pause patterns and
  the `DebugCommandKind` response/argument model),
- the `DebugCommand` wire serialization,
- breakpoint-position mapping and handler delegation (`LuaPosition`, `LuaLineBreakpointHandler`),
- the debugger evaluator's expression-range selection (`LuaDebuggerEvaluator`),
- REPL chunk-completion trial-parse and the execute-handler dispatch decision
  (`LuaChunkCompletion`, `LuaConsoleExecuteHandler`).

Parent epic: [MAINT](../requirements.md).

## Scope

### In Scope

- Unit tests for the DBGp out-of-band pause data patterns (`LuaDebugConnection.breakpointDataPattern`
  / `watchpointDataPattern`) and the `DebugCommandKind` response-kind / arg-count model.
- Unit tests for `DebugCommand.toString()` wire framing beyond the existing three cases
  (multi-arg, casing, empty-args).
- Unit tests for `LuaPosition` round-trip conversion (`createRemotePosition` 0→1 line shift,
  `createLocalPosition` 1→0 line shift, `args()`).
- Strengthened `LuaPosition` coverage (round-trip line-number conversion + `args()`), which is the
  pure, session-free part of breakpoint registration. (`LuaLineBreakpointHandler` itself needs a
  live `LuaDebugProcess`/`XDebugSession` to construct and its `registerBreakpoint` reaches a live
  controller — runtime handler delegation is covered by the integration harness, not unit-tested;
  see the design's "Integration-only" note.)
- Unit tests for `LuaDebuggerEvaluator.getExpressionRangeAtOffset` proving the widest enclosing
  `LuaExpr` range is selected under a cursor offset (e.g. `a.b.c` selects the whole index chain,
  not the leaf).
- Unit tests for `LuaConsoleExecuteHandler.runExecuteAction`'s branch **decision predicate**
  (`text.isBlank() || LuaChunkCompletion.isComplete(project, text)`) — complete/blank → submit,
  incomplete → continuation newline — evaluated directly (the branch action itself drives a live
  platform process / editor write, so only the pure predicate is unit-tested).
- Additional `LuaChunkCompletion.isComplete` cases (table literal `{`, unclosed paren, `do`/`repeat`,
  long-string `[[`, blank input).

### Out of Scope

- Any test that opens a real `ServerSocket`, spawns a Lua subprocess, or drives the
  `LuaDebugConnection.run()` background loop — those are covered by the **integration** harness
  (`LuaDebugHarness` / `TestLuaDebugHarness`) and are explicitly *not* unit tests here
  (engineering-contract §1: no EDT-blocking I/O / live DBGp loops in unit tests).
- `LuaDebuggerController` socket lifecycle (`waitForConnect`, `queueRequest`, promise wiring):
  it constructs a real `ServerSocket`/`XDebugSession` and runs pooled-thread loops — out of scope
  for unit coverage; see "Integration-only" note in the design.
- Graphical console tool-window components (`LuaConsoleView`, `LuaConsoleRunner` UI, `LuaConsoleRootType`).
- `LuaDebugValueParser` / `LuaDebugValue` / `LuaRemoteStack` (already covered by
  `TestLuaDebugValueParser`, `TestLuaDebugValue`, `TestLuaRemoteStackFrames`).

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-13-01 | **DBGp pause-data parsing** | M | Full | `LuaDebugConnection.breakpointDataPattern` extracts `(file, line)` from `"<file> <line>"` and `watchpointDataPattern` extracts `(file, line, index)` from `"<file> <line> <index>"`; malformed data does not match. |
| MAINT-13-02 | **Command model & framing** | M | Full | `DebugCommand.toString()` serializes `KIND arg1 arg2 …` (kind upper-cased, space-joined); `DebugCommandKind` exposes the correct `group`, `minArgs`/`maxArgs`, and `responses` map for representative commands. |
| MAINT-13-03 | **Breakpoint position mapping** | M | Full | `LuaPosition` converts between IDE (0-based) and remote (1-based) line numbers and emits `args()`. |
| MAINT-13-04 | **Expression-range selection** | M | Full | `LuaDebuggerEvaluator.getExpressionRangeAtOffset` returns the `TextRange` of the widest enclosing `LuaExpr` at the offset (full index chain, not a leaf), and `null` when the cursor is not inside an expression. |
| MAINT-13-05 | **REPL chunk-completion** | M | Full | `LuaChunkCompletion.isComplete` returns `false` for chunks whose only parse error is at EOF (open `function`/`if`/`do`/`repeat`/`{`/`(`/`[[`) and `true` for complete chunks and mid-chunk errors. |
| MAINT-13-06 | **REPL execute dispatch predicate** | S | Full | The predicate `LuaConsoleExecuteHandler.runExecuteAction` branches on — `text.isBlank() \|\| LuaChunkCompletion.isComplete(project, text)` — is `true` (submit) for blank / complete input and `false` (continuation newline) for incomplete input. |

## Detailed Specifications

### MAINT-13-01: DBGp pause-data parsing
`LuaDebugConnection.breakpointDataPattern` is `^(.+)\s+(\d+)$`; `watchpointDataPattern` is
`^(.+)\s+(\d+)\s+(\d+)$` (`LuaDebugConnection.kt:370-371`). Tests exercise the `Pattern`
objects directly (they are public members of the `companion object`) — no socket needed. The
last whitespace-separated number is the line (watchpoint: line then index); the greedy `(.+)`
absorbs paths that themselves contain spaces up to the final numeric group(s).

### MAINT-13-02: Command model & framing
`DebugCommand.toString()` (`LuaDebugConnection.kt:138-146`) upper-cases `kind.name` and appends
each arg space-prefixed. `DebugCommandKind` (`LuaDebugConnection.kt:38-132`) carries a `group`,
`minArgs`, `maxArgs`, and a `responses: Map<DebuggerStatus, DebuggerResponseDataKind>`. Tests assert
the model for representative kinds (`SETB`: `Config`, min/max 2; `EXEC`: `Inspect`, `OK → Extended`;
`STEP`: `Run`; `BASEDIR`: `Config`, maxArgs 1).

### MAINT-13-03: Breakpoint position mapping
`LuaPosition.createRemotePosition` adds 1 to the IDE line; `createLocalPosition` subtracts 1
(`LuaPosition.kt:41-51`); `args()` returns `[path, line.toString()]` (`:29-31`). These conversions are
the pure, session-free core of breakpoint registration and are unit-tested directly (extending the
existing `TestLuaPosition`). `LuaLineBreakpointHandler` (`LuaLineBreakpointHandler.kt:12-21`) forwards to
`LuaDebugProcess.addBreakPoint`/`removeBreakPoint`, but both `LuaLineBreakpointHandler` and
`LuaDebugProcess` require a live `XDebugSession` to construct and reach a live controller — runtime
delegation is out of unit scope (no mocking framework is on the test classpath; see the design's
"Integration-only" note).

### MAINT-13-04: Expression-range selection
`getExpressionRangeAtOffset` (`LuaDebuggerEvaluator.kt:69-96`) commits the document, finds the context
element, then `findExpression` (`:99-109`) walks up while `parent is LuaExpr`, returning the outermost
`LuaExpr`'s `textRange` (or `null` if none / if it is a `LuaStatement`). Tests build a `LuaDebuggerEvaluator`
over a mocked `LuaDebuggerController` (only `getExpressionRangeAtOffset` is exercised — it never touches the
controller), configure a Lua document via `myFixture`, and assert the returned range text.

### MAINT-13-05 / MAINT-13-06: REPL completion & dispatch
`LuaChunkCompletion.isComplete(project, text)` (`LuaChunkCompletion.kt:17-23`) trial-parses `text` as a
`repl.lua` file and returns `false` iff a `PsiErrorElement` ends at `text.length`.
`LuaConsoleExecuteHandler.runExecuteAction` (`LuaConsoleExecuteHandler.kt:24-31`) submits (super) when
`text.isBlank() || isComplete(...)`, else inserts `"\n"`. The unit test evaluates that boolean predicate
directly (the `super.runExecuteAction` call needs a live `ProcessBackedConsoleExecuteActionHandler`
process attachment and the else-branch does a `WriteCommandAction` editor insert — neither is unit scope).

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-13-01 | `data = "main.lua 42"` | `breakpointDataPattern.matcher(data)` | `matches()` true; group(1)=`main.lua`, group(2)=`42` |
| 2 | MAINT-13-01 | `data = "src/my file.lua 7"` (path with space) | `breakpointDataPattern.matcher(data)` | matches; group(1)=`src/my file.lua`, group(2)=`7` |
| 3 | MAINT-13-01 | `data = "main.lua"` (no line) | `breakpointDataPattern.matcher(data)` | `matches()` false |
| 4 | MAINT-13-01 | `data = "a.lua 3 5"` | `watchpointDataPattern.matcher(data)` | matches; group(1)=`a.lua`, group(2)=`3`, group(3)=`5` |
| 5 | MAINT-13-01 | `data = "a.lua 3"` (missing index) | `watchpointDataPattern.matcher(data)` | `matches()` false |
| 6 | MAINT-13-02 | `DebugCommand(DebugCommandKind.OVER)` | `toString()` | `"OVER"` |
| 7 | MAINT-13-02 | `DebugCommand(DebugCommandKind.SETW, listOf("x>5"))` | `toString()` | `"SETW x>5"` |
| 8 | MAINT-13-02 | `DebugCommandKind.SETB` | read `group`, `minArgs`, `maxArgs` | `Config`, `2`, `2` |
| 9 | MAINT-13-02 | `DebugCommandKind.EXEC` | read `responses[DebuggerStatus.OK]` | `DebuggerResponseDataKind.Extended` |
| 10 | MAINT-13-02 | `DebugCommandKind.STEP` | read `group` | `DebugCommandGroup.Run` |
| 11 | MAINT-13-03 | IDE `XSourcePosition` at line 10, `workingDir = project base` | `LuaPosition.createRemotePosition(pos, dir)` | `line == 11` (1-based); `path` ends with file name |
| 12 | MAINT-13-03 | `LuaPosition("main.lua", 5)` | `args()` | `["main.lua", "5"]` |
| 13 | MAINT-13-03 | virtualFile + remote line 11 | `LuaPosition.createLocalPosition(vf, 11)` | non-null; `line == 10` (0-based) |
| 14 | MAINT-13-04 | Lua doc `x = a.b.c`, offset inside `c` | `getExpressionRangeAtOffset(...)` | range text == `a.b.c` (widest enclosing `LuaExpr`) |
| 15 | MAINT-13-04 | Lua doc `x = 1 + 2`, offset on `1` | `getExpressionRangeAtOffset(...)` | range text == `1 + 2` |
| 16 | MAINT-13-04 | Lua doc `local y = 1`, offset on keyword `local` | `getExpressionRangeAtOffset(...)` | `null` (not inside an expression) |
| 17 | MAINT-13-05 | `"return 1 + 1"` | `isComplete(...)` | `true` |
| 18 | MAINT-13-05 | `"t = {"` (open table) | `isComplete(...)` | `false` |
| 19 | MAINT-13-05 | `"do"` and `"repeat"` (open blocks) | `isComplete(...)` | `false` |
| 20 | MAINT-13-05 | `"print("` (open paren) | `isComplete(...)` | `false` |
| 21 | MAINT-13-05 | `"s = [["` (open long string) | `isComplete(...)` | `false` |
| 22 | MAINT-13-05 | `"local 1x = 2"` (mid-chunk error) | `isComplete(...)` | `true` (error not at EOF) |
| 23 | MAINT-13-05 | `""` (blank) | `isComplete(...)` | `true` (no error element) |
| 24 | MAINT-13-06 | `text = "print(1)"` (complete) | evaluate the handler's dispatch predicate `text.isBlank() \|\| LuaChunkCompletion.isComplete(project, text)` | `true` (→ submit branch) |
| 25 | MAINT-13-06 | `text = "function f()"` (incomplete) | same dispatch predicate | `false` (→ continuation-newline branch) |
| 26 | MAINT-13-06 | `text = "   "` (blank) | same dispatch predicate | `true` (blank force-submit escape hatch) |

## Acceptance Criteria

- [ ] MAINT-13-01: pause-data patterns parse valid and reject malformed data (TC 1–5).
- [ ] MAINT-13-02: command framing and the `DebugCommandKind` model are asserted (TC 6–10).
- [ ] MAINT-13-03: line-number conversions round-trip and `args()` is emitted (TC 11–13).
- [ ] MAINT-13-04: the widest enclosing `LuaExpr` range is selected; non-expression → `null` (TC 14–16).
- [ ] MAINT-13-05: chunk completion classifies open blocks / literals / errors correctly (TC 17–23).
- [ ] MAINT-13-06: execute-handler dispatch predicate is verified (TC 24–26).
- [ ] `tooling/gce-builder/gce-builder.sh run test` remains green (no regressions).

## Non-Functional Requirements

- **Threading (engineering-contract §1):** no unit test opens a socket or runs a DBGp loop.
  PSI/document work runs inside EDT + read action via the existing `BaseDocumentTest` /
  `runInEdtAndGet` idiom (as in `TestLuaChunkCompletion`).
- **Fixtures (§6):** prefer `BaseDocumentTest` (`myFixture.configureByText`) over hand-built PSI.
  No mocking framework (Mockito/MockK) is on the test classpath, so live collaborators are never
  mocked: where a unit needs an `XDebugSession`, construct a hand-rolled `object : XDebugSession`
  fake (`FakeXDebugSession`) as in design §1/§3.1; the delegation paths that would require a live
  `LuaDebugProcess`/`LuaDebuggerController`/`XLineBreakpoint` are left as integration-only per the
  design's carve-out rather than unit-tested through a mock.
- **Speed:** all tests are pure/light-fixture; total added runtime is expected to be under a second.

## Dependencies

- Production classes under test: `net.internetisalie.lunar.run.{LuaDebugConnection, DebugCommand,
  DebugCommandKind, DebuggerStatus, LuaPosition, LuaDebuggerEvaluator}` and
  `net.internetisalie.lunar.run.console.{LuaChunkCompletion, LuaConsoleExecuteHandler}`.
- Test infra: `net.internetisalie.lunar.BaseDocumentTest`, `com.intellij.testFramework.runInEdtAndGet`,
  `kotlin.test` assertions (the suite's standard — no mocking framework is on the test classpath).
- No new plugin.xml registration (coverage-only feature).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
