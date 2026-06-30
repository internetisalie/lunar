---
id: "FORMAT-07-DESIGN"
title: "Technical Design: Stylua Compatibility"
type: "design"
parent_id: "FORMAT-07"
folders:
  - "[[features/formatting/07-stylua-compatibility/requirements|requirements]]"
---

# Technical Design: FORMAT-07 — Stylua Compatibility

## 1. Architecture Overview

### Current State
Lunar's built-in formatter (`LuaFormattingModelBuilder`) registered at
`src/main/kotlin/net/internetisalie/lunar/lang/format/LuaFormatBlock.kt:239` uses the
IntelliJ `FormattingModelBuilder` API with `LuaSpacingBuilder` and `LuaCodeStyleSettings`
(source: `src/main/kotlin/net/internetisalie/lunar/lang/format/LuaCodeStyleSettings.kt:10`).
It is the only formatter for Lua files. The Stylua tool type (`LuaToolType.STYLUA`) is
already defined (source: `src/main/kotlin/net/internetisalie/lunar/tool/LuaToolDescriptor.kt:22`)
and registered in the TOOL inventory, but no formatting integration exists.

### Prior Art in This Repo
- **`LuaFormattingModelBuilder`** (`lang/format/LuaFormatBlock.kt:239`) — built-in formatter.
  This design **extends** the formatting pipeline by registering an alternative
  `FormattingService`; the built-in formatter remains the fallback when no Stylua is bound.
- **`LuaToolManager.getEffectiveTool(project, LuaToolType.STYLUA)`** (`tool/LuaToolManager.kt:163`) —
  resolution of the bound Stylua binary. Already shipped.
- **`LuaProcessUtil.capture(cmd)`** (`util/LuaProcessUtil.kt:17`) — subprocess execution
  with `CapturingProcessHandler`. Used by Luacheck (`analysis/luacheck/LuaCheckCommandLine.kt:22`)
  and `LuaToolHealthChecker` (`tool/health/LuaToolHealthChecker.kt:51`). This design
  **reuses** `LuaProcessUtil.capture` for the Stylua CLI invocation.
- **`LuaToolEnvironment.prependToolDirsToPath(cmd, project)`** (`tool/LuaToolEnvironment.kt:40`) —
  patches `PATH` on a `GeneralCommandLine`. Optional — may be needed if Stylua requires
  Lua on `PATH`, but typically Stylua is self-contained. Not included in initial design.

### Target State
A new `StyluaFormattingService` extends `AsyncDocumentFormattingService`, registered as a
`<formattingService>` extension. On every full-file reformat of a `.lua` file:
1. `canFormat()` checks `LuaToolManager.getEffectiveTool(project, LuaToolType.STYLUA)`.
   If a valid tool is bound → claims the file. Otherwise → hands off to the built-in formatter.
2. `prepareForFormatting()` saves the document to disk so Stylua sees the file's
   directory context (for `.stylua.toml` discovery).
3. `createFormattingTask()` returns a `FormattingTask` that, on its background thread:
   a. Builds a `GeneralCommandLine` for `stylua --stdin-filepath <filename>`.
   b. Sets stdin to the request's `getDocumentText()`.
   c. Runs via `LuaProcessUtil.capture()`.
   d. On exit 0 → calls `request.onTextReady(output.stdout)`.
   e. On non-zero → calls `request.onError("Stylua", stderr)`.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.formatting.external.StyluaFormattingService`
- **Responsibility**: Claim `.lua` files for Stylua formatting when a valid binary is bound,
  and delegate execution to a `FormattingTask`.
- **Extends**: `com.intellij.formatting.service.AsyncDocumentFormattingService`
  (platform API, verified at `intellij-community/platform/code-style-api/src/com/intellij/formatting/service/AsyncDocumentFormattingService.java:29`)
- **Threading**: `canFormat()` / `createFormattingTask()` called on EDT (must be fast).
  Actual CLI execution runs on the platform's background worker thread via `FormattingTask`.
- **Collaborators**: `LuaToolManager`, `LuaTool`, `LuaProcessUtil`, `LuaLanguage`, `GeneralCommandLine`
- **Key API**:
  ```kotlin
  class StyluaFormattingService : AsyncDocumentFormattingService() {
      // §3.1 — decides whether to claim a file
      override fun canFormat(psiFile: PsiFile): Boolean

      // §3.2 — returns the set of supported features (empty = whole-file only)
      override fun getFeatures(): Set<FormattingService.Feature>

      // Saves the document before the CLI runs
      override fun prepareForFormatting(document: Document, formattingContext: FormattingContext)

      // §3.3 — creates the background task that invokes stylua
      override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask?

      // Notification group for errors
      override fun getNotificationGroupId(): String

      // UI name for timeouts
      override fun getName(): String
  }
  ```

### 2.2 `net.internetisalie.lunar.lang.formatting.external.StyluaFormattingTask`
- **Responsibility**: A single-use `Runnable` that executes the Stylua CLI for one formatting
  request, captures the output, and reports the result back to the platform.
- **Implements**: `AsyncDocumentFormattingService.FormattingTask`
  (platform API, verified at `AsyncDocumentFormattingService.java:97`)
- **Threading**: Runs on a background thread (dispatched by `AsyncDocumentFormattingSupport`).
  Must check `ProgressManager.checkCanceled()` if it runs under progress (it doesn't;
  `isRunUnderProgress()` returns `false` — the CLI is fast enough).
- **Collaborators**: `LuaProcessUtil`, `GeneralCommandLine`, `AsyncFormattingRequest`
- **Key constructor**:
  ```kotlin
  class StyluaFormattingTask(
      private val request: AsyncFormattingRequest,
      private val styluaPath: String,
      private val fileName: String,
      private val workingDirectory: String,
  ) : FormattingTask {
      // §3.3 — the actual CLI invocation
      override fun run()

      // Cancel support — kills the subprocess
      override fun cancel(): Boolean
  }
  ```

## 3. Algorithms

### 3.1 `canFormat()` decision algorithm
- **Input**: `PsiFile`
- **Output**: `Boolean`
- **Steps**:
  1. If `psiFile` language is not `LuaLanguage` → return `false`.
  2. Get `project = psiFile.project`.
  3. Call `LuaToolManager.getInstance().getEffectiveTool(project, LuaToolType.STYLUA)`.
  4. Return `true` if the result is non-null and `tool.isValid == true`.
  5. Return `false` otherwise (no Stylua bound, tool invalid, or tool missing).
- **Edge handling**: `getEffectiveTool` already handles project binding → global binding →
  first-valid fallback precedence (TOOL-02). If no tool of type STYLUA exists at all,
  `getEffectiveTool` returns `null` and we return `false`.

### 3.2 `getFeatures()` algorithm
- **Return**: `emptySet<FormattingService.Feature>()`
- **Rationale**: Stylua is a whole-file formatter. We do not support `FORMAT_FRAGMENTS`
  (range formatting) or `AD_HOC_FORMATTING` (quick-fix/refactoring formatting). The platform
  automatically falls through to the built-in `LuaFormattingModelBuilder` for range-based
  requests when `getFeatures()` is empty.

### 3.3 `StyluaFormattingTask.run()` — CLI invocation
- **Input**: `request.getDocumentText(): String`, `styluaPath: String`,
  `fileName: String`, `workingDirectory: String`
- **Output**: calls `request.onTextReady(formattedText)` or `request.onError(title, message)`
- **Steps**:
  1. Build `GeneralCommandLine`:
     ```
     cmd = GeneralCommandLine(styluaPath)
         .withWorkDirectory(workingDirectory)
         .withParameters("--stdin-filepath", fileName)
     ```
  2. Write `request.getDocumentText()` to a temp byte array and set it as the process stdin:
     ```kotlin
     val stdinBytes = request.getDocumentText().toByteArray(Charsets.UTF_8)
     cmd.input = ByteArrayInputStream(stdinBytes)
     ```
  3. Call `val output = LuaProcessUtil.capture(cmd, STYLUA_TIMEOUT_MS)` where `STYLUA_TIMEOUT_MS = 30_000`.
  4. Examine `output.exitCode`:
     - **Exit code 0**: Call `request.onTextReady(output.stdout)`.
     - **Exit code `PROCESS_TIMEOUT_EXCEPTION_CODE` (-1)**: Call
       `request.onError("Stylua Timeout", "Stylua did not respond within 30 seconds")`.
     - **Exit code `PROCESS_EXECUTION_EXCEPTION_CODE` (-2)**: Call
       `request.onError("Stylua", "Could not execute stylua at $styluaPath")`.
     - **Non-zero exit code**: Call
       `request.onError("Stylua", sanitizedStderr(output.stderr))`.
  5. `sanitizedStderr(stderr)` extracts the first non-blank line from stderr. If stderr is
     empty, falls back to `"Stylua exited with code ${output.exitCode}"`.
- **Complexity**: O(document size) — process I/O only.

### 3.4 `cancel()` algorithm
- **Track the `CapturingProcessHandler`** used in `run()`. On `cancel()`, call
  `processHandler.destroyProcess()`.
- **Return** `true` if the process was successfully destroyed, `false` if it already
  completed.
- **Note**: The platform's `AsyncDocumentFormattingSupport` calls `cancel()` on the
  previous task when a new reformat is triggered for the same document.

## 4. External Data & Parsing

### 4.1 Stylua CLI stdout (formatted text)
- **Format**: Plain UTF-8 text — the formatted Lua source.
- **Parse strategy**: No parsing needed. The entire `output.stdout` string is passed
  directly to `request.onTextReady(stdout)`.
- **Empty stdout**: If stdout is empty but exit code is 0, call `onTextReady("")`
  (legitimate for an empty file).
- **Failure handling**: On non-zero exit, stdout is ignored; stderr is extracted as
  the error message (see §3.3 step 4).

### 4.2 Stylua CLI stderr (error messages)
- **Format**: Plain UTF-8 text, lines with newline separators.
- **Parse strategy**: Take the first non-blank line via `stderr.lineSequence()
  .firstOrNull { it.isNotBlank() } ?: "Stylua exited with code ${exitCode}"`.
- **Maps to**: The `message` parameter of `request.onError(title, message)`.
- **Failure handling**: If both stdout and stderr are empty on non-zero exit, produce a
  synthetic message using the exit code.

### 4.3 Stylua CLI exit codes (per Stylua 0.10+)
| Exit code | Meaning | Action |
|-----------|---------|--------|
| 0 | Success | Apply `stdout` via `onTextReady` |
| 1 | Syntax error / parse failure | `onError` with stderr line |
| 2 | Internal error / bug | `onError` with stderr line |
| Other (3-255) | Unknown error | `onError` with stderr line or synthetic message |

## 5. Data Flow

### Example 1: Successful full-file reformat
1. User hits Ctrl+Alt+L on `main.lua`.
2. Platform calls `StyluaFormattingService.canFormat(psiFile)` → checks
   `LuaToolManager.getEffectiveTool(project, STYLUA)` → finds valid stylua at
   `/usr/local/bin/stylua` → returns `true`.
3. Platform calls `prepareForFormatting(document, ...)` → saves document to disk.
4. Platform calls `createFormattingTask(request)` → creates `StyluaFormattingTask`
   with `styluaPath="/usr/local/bin/stylua"`, `fileName="main.lua"`,
   `workingDirectory="/path/to/project/src"`.
5. Background thread runs `task.run()`:
   a. `GeneralCommandLine("/usr/local/bin/stylua")`
   b. Parameters: `--stdin-filepath`, `main.lua`
   c. stdin = `local x =   1\n`
   d. `LuaProcessUtil.capture(cmd, 30_000)` → exit 0, stdout = `local x = 1\n`
   e. `request.onTextReady("local x = 1\n")`
6. Platform merges the text into the editor. Editor now shows `local x = 1\n`.

### Example 2: No Stylua bound — fallback
1. User hits Ctrl+Alt+L on `main.lua`.
2. `canFormat(psiFile)` → `getEffectiveTool(project, STYLUA)` returns `null`
   (no Stylua registered) → returns `false`.
3. Platform falls through to the next `FormattingService`, which is the built-in
   `LuaFormattingModelBuilder` (via the `<lang.formatter>` EP).
4. Built-in formatter formats the file normally.

### Example 3: Stylua parse error
1. `canFormat()` returns `true`.
2. Background thread runs Stylua on `local x =` (syntax error).
3. `LuaProcessUtil.capture()` → exit code 1, stderr = `(1, 9) unexpected symbol near '='`.
4. `request.onError("Stylua", "(1, 9) unexpected symbol near '='")`.
5. Platform shows a red balloon notification. Document text is unchanged.

## 6. Edge Cases
1. **Empty file**: Stylua outputs empty string on empty input. `onTextReady("")` is valid.
2. **Binary deleted between `canFormat()` and `run()`**: `LuaProcessUtil.capture()` returns
   `PROCESS_EXECUTION_EXCEPTION_CODE` (-2). `run()` calls `onError`. The health monitor
   (TOOL-03) will mark the tool invalid on next check.
3. **Stylua produces identical output**: `onTextReady(sameText)` is safe — the platform
   ignores no-op text replacements.
4. **Very large files (>10MB)**: The 30s timeout acts as a safety net. Stylua is fast
   in practice (<1s for typical files). If a file times out, `onError` fires and the
   document is unchanged.
5. **Stylua `.stylua.toml` in non-standard location**: Stylua discovers the config file
   relative to the input file's directory. By setting the working directory to the file's
   parent and using `--stdin-filepath <filename>` (not full path), Stylua's config
   discovery works correctly.
6. **Concurrent formattings**: The platform's `AsyncDocumentFormattingSupport` cancels
   the prior task when a new one starts for the same document. `StyluaFormattingTask.cancel()`
   destroys the subprocess.

## 7. Integration Points

### 7.1 Plugin XML registration
```xml
<!-- file: src/main/resources/META-INF/plugin.xml -->
<!-- Register the Stylua formatting service as the first external formatter for Lua.
     The platform resolves FormattingService by language + canFormat(); when Stylua
     is bound it claims .lua files ahead of the built-in lang.formatter. -->
<extensions defaultExtensionNs="com.intellij">
    <formattingService
        implementation="net.internetisalie.lunar.lang.formatting.external.StyluaFormattingService"
        order="first" />
</extensions>
```

### 7.2 Notification group registration
```xml
<!-- file: src/main/resources/META-INF/plugin.xml -->
<!-- Notification group for Stylua-related messages (errors, timeout, first-use). -->
<extensions defaultExtensionNs="com.intellij">
    <notificationGroup id="notification.group.lunar.stylua"
        displayType="BALLOON"
        isLogByDefault="true" />
</extensions>
```

### 7.3 Interactions with existing subsystems
| Subsystem | Interaction |
|-----------|-------------|
| `LuaToolManager` | Calls `getEffectiveTool(project, LuaToolType.STYLUA)` in `canFormat()` |
| `LuaTool` | Reads `tool.path`, `tool.isValid`, `tool.version` from the returned tool |
| `LuaProcessUtil` | `capture(cmd)` for single-invocation subprocess execution |
| `LuaFormattingModelBuilder` | Unchanged. Acts as fallback when `canFormat()` returns `false` |
| `FileDocumentManager` | `saveDocument()` in `prepareForFormatting()` |
| `PropertiesComponent` | Stores `"lunar.stylua.firstUse.notified"` flag (FOR-07-05) |
| `NotificationGroupManager` | Retrieves `"notification.group.lunar.stylua"` for error balloons |

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| FORMAT-07-01 | M | §2.1 (`canFormat`, `getEffectiveTool`), §3.1, §7.1 |
| FORMAT-07-02 | M | §2.2, §3.3, §4.1, §4.3, §5 (data flow) |
| FORMAT-07-03 | M | §3.3 (exit code handling), §4.2, §6 (edge cases) |
| FORMAT-07-04 | M | §3.1 (language check), §6 (edge case 5) |
| FORMAT-07-05 | C | §7.3 (`PropertiesComponent`), §2.1 (first-use notification in `run()`) |

## 9. Alternatives Considered
- **Replace `LuaFormattingModelBuilder` with a config toggle**: Adding a branch inside the
  existing formatter to delegate to Stylua. Rejected — `AsyncDocumentFormattingService` is
  the platform's intended mechanism for external formatters and provides cancellation, timeout,
  and document-merging infrastructure for free.
- **Use `FormattingService` (synchronous) instead of `AsyncDocumentFormattingService`**:
  Rejected — the synchronous `formatDocument()` runs on EDT, and Stylua CLI execution
  would block the UI. The async variant is purpose-built for CLI-backed formatters.
- **Parse `.stylua.toml` and mirror settings in `LuaCodeStyleSettings`**: Rejected as out
  of scope (see requirements §Out of Scope). Stylua discovers its own config file.

## 10. Open Questions

_None — feature has cleared the planning bar._
