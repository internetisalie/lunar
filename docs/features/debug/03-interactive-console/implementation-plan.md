---
id: DEBUG-03-PLAN
title: "Implementation Plan"
type: plan
parent_id: DEBUG-03
status: done
priority: "low"
folders:
  - "[[features/debug/03-interactive-console/requirements|requirements]]"
---

# Implementation Plan: RUN-03 Interactive Console (REPL)

`LanguageConsoleView` + the project interpreter. Phases map to requirement IDs.

## Phase 1: Console + process [Must] — RUN-03-01/02/04
- [ ] `LuaConsoleView : LanguageConsoleImpl` (Lua input editor).
- [ ] `LuaConsoleRunner : AbstractConsoleRunnerWithHistory` launching
      `newProjectLuaInterpreterCommandLine(project).withParameters("-i")` (+ unbuffered `-e`).
- [ ] `LuaConsoleAction` + `<action>` under Tools.
- [ ] Tests: console opens, input editor is `LuaLanguage` (highlighting present).

## Phase 2: Incomplete-input detection [Must] — RUN-03-03
- [ ] `LuaChunkCompletion.isComplete` (§3.1) via `PsiFileFactory` trial parse + EOF-error check.
- [ ] `LuaConsoleExecuteHandler` wiring Enter → submit/continue; `>>` continuation prompt.
- [ ] Unit tests: TC-RUN-03-01 (complete one-liner), TC-RUN-03-02 (incomplete block → multi-line),
      TC-RUN-03-03 (mid-chunk syntax error → submitted).

## Phase 3: History, output, completion [Should/Could] — RUN-03-05/06/07/08
- [ ] `ConsoleHistoryController` (persist id "LuaConsole").
- [ ] stdout/stderr → NORMAL/ERROR content types (§3.2).
- [ ] Confirm `LuaCompletionContributor` fires in the console input.

## Verification Tasks
- Unit: `LuaChunkCompletion.isComplete` table (TC-RUN-03-01/02/03).
- Manual: evaluate `1+1`, define a multi-line function, trigger an error (red), Up/Down history
  across a restart.
