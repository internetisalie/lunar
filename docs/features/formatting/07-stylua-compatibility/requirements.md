---
id: "FORMAT-07"
title: "FORMAT-07: Stylua Compatibility"
type: "feature"
status: "in_progress"
priority: "medium"
parent_id: "FORMAT"
folders:
  - "[[features/formatting/requirements|requirements]]"
---

# FORMAT-07: Stylua Compatibility

## Overview
Allow users to delegate code formatting to the [StyLua](https://github.com/JohnnyMorganz/StyLua)
CLI formatter — a deterministic, opinionated Lua formatter — as an alternative to Lunar's
built-in `LuaFormattingModelBuilder`. Stylua is discovered and managed through the existing
TOOL infrastructure (`LuaToolType.STYLUA`), and formatting is dispatched via the IntelliJ
Platform's `AsyncDocumentFormattingService` extension point so the CLI never blocks the EDT.

## Scope

### In Scope
- **FORMAT-07-01**: Toggle between the built-in formatter and Stylua from the existing
  TOOL settings UI (`LuaToolsConfigurable`), using the already-registered Stylua tool.
- **FORMAT-07-02**: Pipe unsaved document text through the `stylua` CLI with
  `--stdin-filepath <filename>`, parse the exit code and stdout, and apply the formatted
  text back through `AsyncFormattingRequest.onTextReady()`.
- **FORMAT-07-03**: Surface CLI errors (non-zero exit, timeout) as user-visible notifications
  without crashing the IDE, and fall back to no formatting change.
- **FORMAT-07-04**: Only offer Stylua formatting for `LuaLanguage` files when a valid
  Stylua binary is bound (via `LuaToolManager.getEffectiveTool(project, LuaToolType.STYLUA)`).

### Out of Scope
- Per-project Stylua configuration files (`.stylua.toml`) — the CLI already discovers and
  applies these from the file's working directory; Lunar does not parse or supply them.
- Partial/range formatting — initial implementation formats the whole file only.
- Auto-detect or auto-install Stylua — the user must register it through the existing
  TOOL inventory UI.
- Stylua version-specific argument handling — the `--stdin-filepath` flag is stable since
  Stylua 0.10.0; older versions are explicitly unsupported.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| FORMAT-07-01 | **Enable Stylua as formatter** | M | Full | When a valid Stylua binary is registered in the TOOL inventory and bound to the project (or globally), the Reformat Code action for `.lua` files uses Stylua instead of `LuaFormattingModelBuilder`. When no valid Stylua is bound, the built-in formatter is used as the fallback. |
| FORMAT-07-02 | **Format via Stylua CLI** | M | Full | The formatting service pipes the full document text to `stylua --stdin-filepath <filename>` and applies the returned stdout text. The document is saved to disk first (`FileDocumentManager.saveDocument`) so the CLI can detect the working-directory `.stylua.toml`. |
| FORMAT-07-03 | **Error handling** | M | Full | On non-zero exit, stderr is surfaced as a notification. On timeout (>30s), a different notification is shown. In both cases the document text is left unchanged. |
| FORMAT-07-04 | **Language filtering** | M | Full | `canFormat()` returns `true` only for `LuaLanguage` files *and* when `LuaToolManager.getEffectiveTool(project, LuaToolType.STYLUA)` returns a valid tool. Otherwise `false`, letting the built-in formatter take over. |
| FORMAT-07-05 | **User notification on first use** | C | Not Implemented | On the first successful format with Stylua, show a non-blocking notification: "Formatted with Stylua <version>" so the user knows which formatter is active. |

## Detailed Specifications

### FORMAT-07-01: Enable Stylua as formatter
- **Mechanism**: The Stylua formatting service is always registered as a `<formattingService>`.
  When `canFormat()` returns `true` (a valid Stylua is bound), the platform automatically
  prefers it over the built-in `LuaFormattingModelBuilder` for full-file formatting.
- **Settings**: No new checkbox or settings panel is needed — enabling/disabling is purely
  a function of whether a valid Stylua binary is registered and bound. Existing TOOL
  infrastructure (global binding, project binding via `projectToolBindings`) already
  provides this control.
- **Fallback**: The platform's `FormattingService` resolution naturally falls through to
  the built-in `lang.formatter` when no external service claims the file. Our `canFormat()`
  explicitly returns `false` when no valid Stylua is bound, ensuring the built-in formatter
  runs transparently.

### FORMAT-07-02: Format via Stylua CLI
- **Pre-formatting**: `prepareForFormatting()` calls `FileDocumentManager.getInstance().saveDocument(document)`
  so that a `.stylua.toml` in the file's directory is visible to the CLI.
- **CLI invocation**: Build a `GeneralCommandLine` with:
  - Executable: `effectiveTool.path`
  - Arguments: `["--stdin-filepath", virtualFile.name]` (the filename, not the full path)
  - Working directory: `virtualFile.parent.path`
  - Stdin: the document text
- **Processing**: `LuaProcessUtil.capture(cmd)` returns a `ProcessOutput`. On exit code 0,
  call `request.onTextReady(output.stdout)`. On non-zero exit, call
  `request.onError("Stylua formatting failed", stderr)`.
- **Timeout**: 30 seconds (`Duration.ofSeconds(30)`, the `AsyncDocumentFormattingService.DEFAULT_TIMEOUT`).
  The platform handles timeout internally via `AsyncDocumentFormattingSupport` and calls
  `onError` with the timeout message.

### FORMAT-07-03: Error handling
- **Non-zero exit code**: `request.onError("Stylua", stderr.firstNonBlankLine())` — shows
  a platform balloon notification. The document is not modified.
- **Execution exception** (`PROCESS_EXECUTION_EXCEPTION_CODE`): `request.onError("Stylua",
  "Could not execute stylua at <path>")`.
- **Timeout**: The platform's `AsyncDocumentFormattingSupport` calls `onError` with the
  `getTimeoutNotificationDisplayId()` message; we provide a descriptive `getName()`.

### FORMAT-07-05: User notification on first use
- Use `PropertiesComponent.getInstance()` with a key `"lunar.stylua.firstUse.notified"`
  to track whether the notification has been shown. After the first successful `onTextReady()`,
  show a `NOTIFICATION`-level balloon with message `"Formatted with Stylua <version>"`.

## Behavior Rules
1. **Precedence**: Stylua takes over only for whole-file formatting. Range formatting and
   ad-hoc formatting (quick fixes, refactorings) are not supported — `getFeatures()` returns
   an empty set, so the platform falls through to the built-in formatter for those cases.
2. **Concurrency**: The `AsyncDocumentFormattingSupport` framework handles cancellation of
   in-flight formatting tasks when a new reformat is triggered for the same document.
3. **No mutation on error**: If the CLI returns non-zero or times out, the document is
   never modified via `onTextReady(null)`.
4. **Save-before-format**: `prepareForFormatting` saves the document to disk. This is the
   standard pattern (used by Prettier, RustRover, etc.) and the platform's default
   implementation already does this.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | FORMAT-07-01, -02 | `"local x =   1\n"` with a valid Stylua path bound | Reformat code (full file) | Document text becomes `"local x = 1\n"` (Stylua output) |
| 2 | FORMAT-07-01, -04 | `"local x = 1"` with NO stylua bound | Reformat code | Falls through to built-in `LuaFormattingModelBuilder`; text formatted by Lunar, not Stylua |
| 3 | FORMAT-07-03 | Document text, Stylua binary returns exit code 1 with stderr `"syntax error"` | Reformat code | `onError("Stylua", "syntax error")` called; document text unchanged |
| 4 | FORMAT-07-03 | Stylua binary does not exist at bound path | Reformat code | `onError` called with "Could not execute stylua"; document unchanged |
| 5 | FORMAT-07-04 | A Python file with stylua bound | Reformat Python file | `canFormat()` returns `false`; built-in Python formatter runs normally |
| 6 | FORMAT-07-02 | `"return  {  a  =  1 ,  b  =  2  }\n"` with Stylua bound | Reformat code | Stylua formats to `"return { a = 1, b = 2 }\n"` |
| 7 | FORMAT-07-01 | Context menu "Reformat Code" with Stylua bound | User triggers via Ctrl+Alt+L | Stylua formats the file, not LuaFormattingModelBuilder |

## Acceptance Criteria
- [ ] Reformat Code (Ctrl+Alt+L) on a `.lua` file uses Stylua when a valid Stylua binary is bound
- [ ] Removing/disabling Stylua falls back to the built-in `LuaFormattingModelBuilder` seamlessly
- [ ] CLI errors produce a visible notification and leave the document unchanged
- [ ] Non-Lua files are never intercepted by the Stylua service
- [ ] All 7 test cases pass

## Non-Functional Requirements
- **Threading**: The `createFormattingTask()` implementation runs on a background thread
  (`FormattingTask` is dispatched by `AsyncDocumentFormattingSupport`). No EDT blocking.
- **Timeout**: 30s per the platform default; the platform's timeout infrastructure calls
  `onError` without our intervention.
- **No hard refs**: The service is stateless — it does not retain `Project`, `Document`,
  or `VirtualFile`. The tool path is looked up fresh each call.

## Dependencies
- TOOL-01 (tool inventory management) — `LuaToolType.STYLUA` is already shipped
- TOOL-02 (project binding & resolution) — `getEffectiveTool()` is already shipped
- TOOL-03 (health monitoring) — ensures Stylua validity is tracked
- The Stylua binary installed on the user's machine (version ≥ 0.10.0)

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklist: [human-verification-checklists.md](human-verification-checklists.md)