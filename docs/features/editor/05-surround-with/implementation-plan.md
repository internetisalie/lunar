---
id: "EDITOR-05-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "EDITOR-05"
folders:
  - "[[features/editor/05-surround-with/requirements|requirements]]"
---

# EDITOR-05: Implementation Plan

Sequences the design into three shippable, independently testable phases. All new files live in a
new package `net.internetisalie.lunar.lang.surround`. Each phase leaves the build green.

## Phases

### Phase 1: Shared helpers + descriptor + `if` surrounder [Must]
- **Goal**: End-to-end `Surround With → if` works via the real EP; the reusable block helpers
  (for EDITOR-06) and the descriptor/base skeleton exist.
- **Tasks**:
  - [x] Create (or extend, if EDITOR-06 landed first) the shared
        `net.internetisalie.lunar.lang.editor.LuaBlockStructure` with this feature's range/replace
        half — design §2.1, §3.3 (`enclosingBlock`, `statementsInRange`, `statementsText`,
        `replaceStatements`).
  - [x] Create `net.internetisalie.lunar.lang.surround.LuaStatementSurrounder` (abstract) + the
        `WrappedTemplate` data class — realizes design §2.3, §3.1, §3.2.
  - [x] Create `net.internetisalie.lunar.lang.surround.LuaStatementsSurroundDescriptor` — realizes §2.2.
  - [x] Create `net.internetisalie.lunar.lang.surround.LuaIfSurrounder` — realizes §2.4 (`if`).
  - [x] Register `<lang.surroundDescriptor language="Lua" implementationClass="…LuaStatementsSurroundDescriptor"/>`
        in `src/main/resources/META-INF/plugin.xml` — realizes design §7.
- **Exit criteria**: `LuaSurroundWithTest` "if" case (TC-1) passes: selection wrapped in
  `if <caret> then … end`, body reindented, caret in condition.

### Phase 2: Loop + block + function surrounders [Should]
- **Goal**: `while`, numeric `for`, generic `for`, `function`, `do` surrounders.
- **Tasks**:
  - [x] Create `LuaWhileSurrounder` — realizes §2.4 (`while`).
  - [x] Create `LuaNumericForSurrounder` — realizes §2.4 (numeric `for`).
  - [x] Create `LuaGenericForSurrounder` — realizes §2.4 (generic `for`).
  - [x] Create `LuaFunctionSurrounder` — realizes §2.4 (`function`).
  - [x] Create `LuaDoSurrounder` — realizes §2.4 (`do`).
  - [x] Add all five to `LuaStatementsSurroundDescriptor.getSurrounders()` in the §3.4 order.
- **Exit criteria**: `LuaSurroundWithTest` TC-2…TC-6 pass (one per new surrounder).

### Phase 3: `pcall` surrounder [Could]
- **Goal**: `pcall(function() … end)` wrapping.
- **Tasks**:
  - [x] Create `LuaPcallSurrounder` — realizes §2.4 (`pcall`).
  - [x] Append it to `getSurrounders()` (last, per §3.4).
- **Exit criteria**: `LuaSurroundWithTest` TC-7 passes.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| EDITOR-05-01 (`if`) | M | Phase 1 |
| EDITOR-05-02 (`while`/`for`) | S | Phase 2 |
| EDITOR-05-03 (`function`) | S | Phase 2 |
| EDITOR-05-04 (`do`) | S | Phase 2 |
| EDITOR-05-05 (`pcall`) | C | Phase 3 |

## Verification Tasks
Real-flow tests per the epic DoD (line 62–68): drive the platform machinery via
`com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler.invoke(project, editor, file, surrounder)`
(the pattern from intellij-community `SurroundTestCase`), then `myFixture.checkResult(...)` on document
text and assert caret via `myFixture.editor.caretModel.offset`. Engine-only assertions do not satisfy
the gate.

- [x] Create `src/test/kotlin/net/internetisalie/lunar/lang/surround/LuaSurroundWithTest.kt`
      (`BasePlatformTestCase`) with one test per surrounder — covers TC-1…TC-7.
  - TC-1 (`if`): input `foo()\nbar()` selected → `if <caret> then\n    foo()\n    bar()\nend`.
  - TC-2 (`while`): → `while <caret> do\n    foo()\nend`.
  - TC-3 (numeric `for`): → `for <caret> = 1, 10 do\n    foo()\nend`.
  - TC-4 (generic `for`): → `for <caret> in pairs(t) do\n    foo()\nend`.
  - TC-5 (`function`): → `function()\n    <caret>foo()\nend`.
  - TC-6 (`do`): → `do\n    <caret>foo()\nend`.
  - TC-7 (`pcall`): → `pcall(function()\n    <caret>foo()\nend)`.
- [x] Negative test: partial (statement-splitting) selection → `getElementsToSurround` returns empty
      (assert `LuaStatementsSurroundDescriptor().getElementsToSurround(...)` is empty).
- [x] **VNC-verified 2026-07-13** (GoLand on lunar-builder): Ctrl+Alt+T opens the *Surround With* picker
      listing all seven templates in order (if / while / for (numeric) / for (generic) / function / do /
      pcall), with the COMP-07 `$SELECTION$` live templates (surr_if/for/do/fn) shown as a *separate* group
      below — the two EPs coexist. Applying **if** to a two-statement selection wraps them as
      `if  then / <body> / end`, re-indents the body to 4 spaces, and lands the caret in the condition slot.

## Implementation Notes (as-built)

Two intentional simplifications vs the design, both hardening Risk 1.2:
- **No `WrappedTemplate` data class.** Each surrounder overrides a single `wrap(bodyText): String`; the
  caret marker is one shared constant `LuaBlockStructure.CARET` (`__lunar_surround_caret__`, a valid
  identifier) embedded directly in header templates. Fewer moving parts, same behavior.
- **Structural caret resolution, not offset arithmetic.** `LuaBlockStructure.caretAfterWrap` reads the
  caret site from the *post-reformat* PSI: for header templates it finds the `CARET` sentinel node,
  records its start offset, then deletes it; for body templates (`function`/`do`/`pcall`) it returns the
  wrapped body's first statement start. Indentation-safe by construction, eliminating the pre/post-reformat
  offset drift the design flagged (Risk 1.2) rather than mapping a cached index.

Header templates embed the sentinel as a real identifier (condition / loop var), so the wrapped text
always parses (`exprStatement ::= expr`, `expr ::= … | funcDef` — a bare `function() … end` is a valid
Lua *statement* in Lunar's grammar) and `findChildOfType(LuaStatement)` returns the outer wrapper.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Helpers + descriptor + `if` | done | Must |
| Phase 2: Loop + block + function surrounders | done | Should |
| Phase 3: `pcall` surrounder | done | Could |
