---
id: DEBUG-03-DESIGN
title: "Technical Design"
type: design
parent_id: DEBUG-03
status: done
priority: "low"
folders:
  - "[[features/debug/03-interactive-console/requirements|requirements]]"
---

# Technical Design: RUN-03 Interactive Console (REPL)

## 1. Architecture Overview

### Current State
No REPL. The interpreter command line is built by
`net.internetisalie.lunar.command.newProjectLuaInterpreterCommandLine(project): GeneralCommandLine?`
(uses `LuaProjectSettings.interpreter` + `LUA_PATH`). The debug subsystem already uses
`com.intellij.execution.ui.ConsoleView`/`ConsoleViewContentType`. The Lua parser is available
for trial-parsing input.

### Target State
A "Lua Console" tool launches the project interpreter in interactive mode inside a
`LanguageConsoleView` (Lua-highlighted input, RUN-03-04). Complete chunks are sent on Enter;
incomplete chunks switch to multi-line mode via a **client-side trial parse**. History persists
across sessions; stderr is visually distinct.

```
"Lua Console" action ─▶ LuaConsoleRunner.initAndRun
   ├─ process: newProjectLuaInterpreterCommandLine + "-i"  (OSProcessHandler)
   ├─ LuaConsoleView : LanguageConsoleImpl (LuaLanguage input)
   ├─ ConsoleExecuteAction → onEnter: isCompleteChunk? send : enter multi-line  (§3.1)
   └─ ConsoleHistoryController (persist id "LuaConsole")
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.run.console.LuaConsoleRunner`
- **Responsibility**: build the console + process + actions.
- **Key API** (extends `AbstractConsoleRunnerWithHistory<LuaConsoleView>`):
  ```kotlin
  class LuaConsoleRunner(project: Project)
      : AbstractConsoleRunnerWithHistory<LuaConsoleView>(project, "Lua Console", null) {
      // built once; RUN-03-08 unbuffered via `-e "io.stdout:setvbuf('no'); io.stderr:setvbuf('no')"`
      private val commandLine: GeneralCommandLine = (newProjectLuaInterpreterCommandLine(project)
          ?: error("No project Lua interpreter configured"))
          .withParameters("-e", "io.stdout:setvbuf('no'); io.stderr:setvbuf('no')", "-i")
      // AbstractConsoleRunnerWithHistory declares TWO abstract methods:
      override fun createProcess(): Process = commandLine.createProcess()
      override fun createProcessHandler(process: Process): OSProcessHandler =
          OSProcessHandler(process, commandLine.commandLineString, commandLine.charset)
      override fun createConsoleView(): LuaConsoleView = LuaConsoleView(project)
      override fun createExecuteActionHandler(): ProcessBackedConsoleExecuteActionHandler  // §3.1
  }
  ```

### 2.2 `net.internetisalie.lunar.run.console.LuaConsoleView`
- `class LuaConsoleView(project) : LanguageConsoleImpl(project, "Lua Console", LuaLanguage)` —
  the input editor is a `LuaLanguage` file, so syntax highlighting (RUN-03-04) and the existing
  `LuaCompletionContributor` (RUN-03-06) apply for free.

### 2.3 `net.internetisalie.lunar.run.console.LuaConsoleExecuteHandler`
- **Responsibility**: decide complete vs incomplete on Enter and feed the process.
- **Key API**: a `ProcessBackedConsoleExecuteActionHandler` whose `execute` checks
  `LuaChunkCompletion.isComplete(text)` (§3.1); if incomplete, keep the buffer and show the
  `>>` continuation prompt instead of submitting.

## 3. Algorithms

### 3.1 Incomplete-input detection (`LuaChunkCompletion.isComplete`) — RUN-03-03
- **Input → Output**: the accumulated input `String` → `Boolean` (true = ready to evaluate).
- **Steps** (client-side trial parse, no interpreter round-trip):
  1. `val file = PsiFileFactory.getInstance(project).createFileFromText("repl.lua",
     LuaFileType, text)`.
  2. Find `PsiErrorElement`s via `PsiTreeUtil.findChildOfType`.
  3. **Incomplete** (return false) iff there is an error element whose `textRange.endOffset ==
     text.length` (the error is at EOF — an unclosed `function`/`if`/`do`/`(`/long-string), i.e.
     more input could complete it.
  4. Otherwise **complete** (return true) — no error, or the error is mid-chunk (a real syntax
     error, which the interpreter will report).
- **Multi-line UX**: while incomplete, the prompt shows `>>` and Enter appends a newline; a
  blank line force-submits (escape hatch). `Shift+Enter` always inserts a literal newline.

### 3.2 Output routing — RUN-03-07
- The `ProcessHandler` listener prints `ProcessOutputTypes.STDOUT` text with
  `ConsoleViewContentType.NORMAL_OUTPUT` and `STDERR` with `ConsoleViewContentType.ERROR_OUTPUT`
  (distinct color). The `>` / `>>` prompts use `ConsoleViewContentType.USER_INPUT`.

## 4. External Data & Parsing
- Input parsing is the trial PSI parse (§3.1) — reuses the plugin's Lua parser, no new format.
- Interpreter stdout/stderr are rendered verbatim (no parsing).

## 5. Data Flow

### Example: multi-line (RUN-03-03, requirements §5.2)
`function greet(name)` → `isComplete` false (error at EOF: missing `end`) → `>>` prompt →
`return "Hello, "..name` → still incomplete → `end` → complete → whole chunk sent to `lua -i`
stdin → output rendered.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| No interpreter configured | runner errors with a notification "Configure a Lua interpreter". |
| Real syntax error (not EOF) | `isComplete` true → sent → interpreter prints the error to stderr (red). |
| Unterminated long string `[[` | error at EOF → multi-line continues. |
| Process exits (`os.exit()`) | console shows the termination; a Rerun action restarts. |
| Very large output | `LanguageConsoleImpl` handles buffering/cycling. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml -->
<actions>
  <action id="Lua.Console"
          class="net.internetisalie.lunar.run.console.LuaConsoleAction"
          text="Lua Console" description="Open an interactive Lua REPL">
    <add-to-group group-id="ToolsMenu" anchor="last"/>
  </action>
</actions>
```
- `LuaConsoleAction : AnAction` → `LuaConsoleRunner(project).initAndRun()`.
- History persistence: `ConsoleHistoryController(LuaConsoleRootType, "LuaConsole", consoleView)
  .install()` (RUN-03-05) — persists under the IDE's console-history store.
- Reuses `newProjectLuaInterpreterCommandLine`, `LuaLanguage`, `LuaFileType`,
  `LuaCompletionContributor`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| RUN-03-01 Native console UI | M | §2.2 (`LanguageConsoleImpl`) |
| RUN-03-02 SDK integration | M | §2.1 (`newProjectLuaInterpreterCommandLine`) |
| RUN-03-03 Incomplete input | M | §3.1 |
| RUN-03-04 Input highlighting | M | §2.2 (Lua console editor) |
| RUN-03-05 History | S | §7 (`ConsoleHistoryController`) |
| RUN-03-06 Completion | S | §2.2 (existing contributor) |
| RUN-03-07 Stderr differentiation | S | §3.2 |
| RUN-03-08 Unbuffered output | C | §2.1 (`setvbuf('no')`) |

## 9. Alternatives Considered
- **Client-side trial parse vs `load`/`<eof>` bootstrap**: client-side parse (the plugin already
  has a Lua parser) avoids a subprocess round-trip per keystroke and a bundled bootstrap script;
  the `load`/`<eof>` approach is the fallback if the trial parse mis-detects (`RUN-03-DR-01`).
- **`lua -i` vs a custom bootstrap**: `-i` keeps a persistent session (RUN-03-06) with no extra
  script; a bootstrap is only needed for richer I/O (`RUN-03-DR-02`).

## 10. Open Questions

_None — feature has cleared the planning bar._
