---
id: "MAINT-26-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-26"
folders:
  - "[[features/maint/26-luacheck-pipeline/requirements|requirements]]"
---

# Technical Design: MAINT-26 — Luacheck Pipeline Correctness

## 1. Architecture Overview

### Current State

The luacheck integration is an `ExternalAnnotator` (`LuaCheckAnnotator`) that delegates
to a plain-object invoker (`LuaCheckInvoker`) which builds a command line
(`newLuaCheckCommandLine`) and runs it through the shared toolchain exec service
(`LuaToolExecutionService.capture`). Since the review's snapshot, MAINT-31 deleted the
tree-model classes and slimmed the subsystem — the transport was **already migrated** onto
`LuaToolExecutionService`, which:

- runs the process off the EDT with an assertion (`ThreadingAssertions.softAssertBackgroundThread`,
  `LuaToolExecutionService.kt:45`),
- polls the progress indicator and **kills on timeout** via `runProcessWithProgressIndicator(indicator, millis, true)`
  and `runProcess(millis, true)` — the `true` destroys on timeout (`LuaToolExecutionService.kt:66-74`),
- returns a structured `LuaExecResult(output, outcome)` where `outcome ∈ {COMPLETED, TIMED_OUT, START_FAILED, CANCELLED}`
  (`LuaExecResult.kt:5-12`); a launch failure is caught and mapped to `START_FAILED`
  (`LuaToolExecutionService.kt:59-63`).

So the review's §2.1 "5 s `waitFor` neither kills nor polls" and "PSI read from the process
listener" defects are **structurally resolved at the exec-service layer**: there is no
per-invoker `waitFor`, and no `ProcessListener` — the invoker reads `psiFile`/`virtualFile`
on the annotator thread before launch, not in a listener. What remains are **consumer-layer**
defects that this feature fixes. Re-verified 2026-07-17 against the current tree:

| Finding | Review location | **Current** location | Status now |
| :--- | :--- | :--- | :--- |
| #28 flat-token `distinct()` | `LuaCheckCommandLine.kt:42` | `LuaCheckCommandLine.kt:23` (`resolveArguments(project).distinct()`) | **Live** |
| #29 first-match-per-chunk parse | `LuaCheckInvoker.kt:35-60` | `LuaCheckInvoker.kt:28-30` now uses `stdout.lineSequence().mapNotNull(...)` | **Already fixed** (regressed into a related gap: see §4.1 — no `--ranges` two-token column handling / no unterminated-line issue remains, but ANSI + multi-line-message robustness is verified below) |
| #30 disk/editor offset skew + IOOBE | `LuaCheckAnnotator.kt:42-45` | `LuaCheckAnnotator.kt:43-44` (`file.fileDocument.getLineStartOffset(...)` on disk-file output, no clamp) | **Live** |
| #31 enable closes all blocks | `LuaInspectionSuppression.kt:87-124` | `LuaInspectionSuppression.kt:112-124` (`closeBlocks` now takes `names`) | **Already fixed** — closeBlocks receives `names` and the caller at `:88` gates on intersection; **but the intersection is not applied inside `closeBlocks`** (see §3.4) |
| #60 inline ignore over-suppresses next line | — | `LuaInspectionSuppression.kt:135` (`minOf(commentLine + 1, lineCount)`) | **Live** |
| #61 `arg` missing from `DELTA_51` | — | `LuaStandardGlobals.kt:23-25` (`DELTA_51` has no `arg`) | **Live** |
| §2.5.6 swallowed failure / exit ≥ 3 | `LuaCheckInvoker.kt:25` | `LuaCheckInvoker.kt:22-31` (`capture` result: only `.stdout` read; `outcome`/`exitCode` discarded; dead `catch (_: ExecutionException)`) | **Live** |

### Prior Art in This Repo

Searched `analysis/luacheck/*`, `analysis/inspections/*`, `toolchain/exec/*`,
`lang/formatting/external/*`, and `toolchain/probe/*`:

- **`LuaCheckAnnotator`** (`analysis/luacheck/LuaCheckAnnotator.kt`) — the existing
  `ExternalAnnotator`. **Extended**, not replaced: `collectInformation`, `doAnnotate`,
  and `applyProblem` are modified in place.
- **`LuaCheckInvoker`** (`analysis/luacheck/LuaCheckInvoker.kt`) — the existing invoker
  object. **Extended**: `invoke` returns a richer result type; `problemFrom` is retained.
- **`newLuaCheckCommandLine` / `resolveArguments`** (`analysis/luacheck/LuaCheckCommandLine.kt`)
  — **Extended**: `distinct()` replaced by pair-aware de-dup; stdin form gains a `--`/`--filename`
  variant.
- **`LuaToolExecutionService.capture(cmd, timeout, stdin, indicator)`**
  (`toolchain/exec/LuaToolExecutionService.kt:23-28`) — already accepts a `stdin: String?`.
  **Reused as-is** — no change. This is how stylua streams the editor buffer.
- **`StyluaFormattingTask`** (`lang/formatting/external/StyluaFormattingTask.kt`) — the
  **reference idiom** for (a) feeding editor text via stdin with a `--filename`-style flag
  (`--stdin-filepath`, `:50`) and (b) branching on `LuaExecResult.outcome`
  (`START_FAILED`/`TIMED_OUT`/`exitCode != 0`, `:54-70`). This design **mirrors** it for luacheck.
- **`LuaToolProbeImpl`** (`toolchain/probe/LuaToolProbeImpl.kt:37-48`) — second reference for
  mapping `outcome` (`TIMED_OUT`/`START_FAILED`) onto a user-facing state.
- **`LuaToolEditorNotificationProvider`** (`toolchain/health/LuaToolEditorNotificationProvider.kt`)
  — already renders the "luacheck unavailable" editor banner for the *missing-binary* case
  (engaged when the inspection is enabled, `:94`). **Reused, not duplicated**: the invoker
  does **not** re-notify for `START_FAILED`; it logs and surfaces a single WARNING annotation
  (§3.5). No new `notificationGroup` is introduced.
- **`LuaStandardGlobals` / `LuaInspectionSuppression`** (`analysis/inspections/*`) — **Extended**
  in place (one set entry; one intersection guard; one range shrink).

No new EmmyLua-style types are introduced. Every type named below is grep-verified (§ evidence inline).

### Target State

`LuaCheckInvoker.invoke` produces a `LuaCheckOutcome` (new sealed result) that distinguishes
*problems*, *launch failure*, *timeout*, and *crash exit*. `LuaCheckAnnotator` feeds the live
editor document to luacheck via stdin (`luacheck - --filename <name>`), so reported line/column
offsets index the same text the annotator ranges against; offset math is additionally clamped
to the document. Command-line de-dup preserves flag-value pairs. Suppression closes only
name-matching blocks and scopes inline `ignore` to its own line. `arg` is a Lua 5.1 global.

Component sketch:

```
LuaCheckAnnotator (ExternalAnnotator, pooled)
  ├─ collectInformation: capture name + document text on the caller thread
  ├─ doAnnotate: LuaCheckInvoker.invoke(Info) -> LuaCheckOutcome
  └─ apply: map Problems -> annotations (clamped offsets); map Failure -> one WARNING banner-annotation
LuaCheckInvoker.invoke(info) -> LuaCheckOutcome
  ├─ newLuaCheckCommandLine(project, name, dir, useStdin=true)  [pair-aware de-dup]
  └─ LuaToolExecutionService.capture(cmd, FORMAT, stdin=documentText)  -> classify(LuaExecResult)
LuaInspectionSuppression  [name-intersection close; same-line inline ignore]
LuaStandardGlobals        [DELTA_51 += "arg"]
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.analysis.luacheck.LuaCheckOutcome` (new)

- **Responsibility**: Typed result of one luacheck run — problems, or a terminal failure to surface.
- **Threading**: pure data; produced on the pooled annotator thread, consumed in `apply`.
- **Collaborators**: `Problem` (existing, `LuaCheckModel.kt:3`), `LuaExecOutcome` (existing,
  `toolchain/exec/LuaExecResult.kt:5`).
- **Key API**:
  ```kotlin
  sealed interface LuaCheckOutcome {
      data class Problems(val problems: List<Problem>) : LuaCheckOutcome
      data class Failure(val kind: FailureKind, val detail: String) : LuaCheckOutcome
      object NotApplicable : LuaCheckOutcome            // no tool resolved / not a Lua file
  }
  enum class FailureKind { LAUNCH_FAILED, TIMED_OUT, CRASHED }   // CRASHED = exit >= 3
  ```

### 2.2 `net.internetisalie.lunar.analysis.luacheck.LuaCheckInvoker` (modified)

- **Responsibility**: Build the command, stream the editor buffer via stdin, classify the exec result.
- **Threading**: pooled (called from `LuaCheckAnnotator.doAnnotate`, which runs off the EDT
  per the `ExternalAnnotator` contract). No PSI reads inside — all PSI/VFS access happens in
  `collectInformation` (§2.4).
- **Collaborators**: `newLuaCheckCommandLine` (§2.3), `LuaToolExecutionService.getInstance().capture`
  (`toolchain/exec/LuaToolExecutionService.kt:23`), `LuaExecResult` (`toolchain/exec/LuaExecResult.kt:7`).
- **Key API**:
  ```kotlin
  object LuaCheckInvoker {
      fun invoke(info: LuaCheckAnnotator.Info): LuaCheckOutcome     // was: invoke(VirtualFile, PsiFile): List<Problem>
      // internal, unchanged signature except file arg -> String:
      private fun problemFrom(line: String, fileName: String): Problem?
      private fun classify(result: LuaExecResult, fileName: String): LuaCheckOutcome   // §3.5
  }
  ```
  `Info` (existing nested class, `LuaCheckAnnotator.kt:13`) is extended to carry the pre-read
  `fileName: String`, `workDir: VirtualFile`, `documentText: String`, and `project: Project`
  captured on the caller thread (§2.4). The old `catch (_: ExecutionException)` block is
  **deleted** — `capture` never throws `ExecutionException` (it maps launch failure to
  `START_FAILED`, `LuaToolExecutionService.kt:61-63`), so the swallow is dead code and the
  failure now reaches `classify`.

### 2.3 `net.internetisalie.lunar.analysis.luacheck.newLuaCheckCommandLine` (modified)

- **Responsibility**: Resolve the luacheck binary and assemble arguments, de-duping whole
  flag-value pairs; optionally target stdin (`-`) with a `--filename`.
- **Threading**: pooled; reads project settings (no PSI).
- **Collaborators**: `LuaToolResolver.getInstance().resolve` (`toolchain/resolve/LuaToolResolver.kt:22`),
  `LuaToolchainProjectSettings.effectiveKindOption` + `LuaKindOptionKeys.LUACHECK_ARGUMENTS`
  (unchanged), `LuaProjectSettings.getInstance(project).state.getTarget().getLuacheckStd()`
  (`platform/target/Target.kt:75`), `ParametersListUtil.parseToArray` (unchanged).
- **Key API**:
  ```kotlin
  fun newLuaCheckCommandLine(
      project: Project,
      targetFileName: String,
      workDirectory: VirtualFile,
      useStdin: Boolean = true,               // NEW: default stdin
  ): GeneralCommandLine?
  private fun resolveArguments(project: Project): List<String>          // no .distinct()
  private fun dedupePairs(tokens: List<String>): List<String>           // §3.1
  ```
  When `useStdin`, the positional file token becomes `-` and `--filename <targetFileName>` is
  appended (mirrors `StyluaFormattingTask.kt:50`'s `--stdin-filepath`). When `!useStdin`
  (fallback / batch inspection), the positional token stays `targetFileName` (current behavior).

### 2.4 `net.internetisalie.lunar.analysis.luacheck.LuaCheckAnnotator` (modified)

- **Responsibility**: Collect the document text + filename on the caller thread, invoke luacheck,
  map problems (clamped) and failures to annotations.
- **Threading**: `collectInformation` runs under the platform's read access (annotator contract);
  `doAnnotate` runs on a pooled thread; `apply` runs under read access on the EDT-adjacent apply
  phase. All PSI/document reads are in `collectInformation` — **none in `doAnnotate`**.
- **Collaborators**: `LuaCheckInvoker.invoke` (§2.2), `PsiFile.fileDocument`
  (`applyProblem`, existing), `TextRange`, `AnnotationHolder`, `HighlightSeverity`.
- **Key API**:
  ```kotlin
  class Info(
      val fileName: String,
      val workDir: VirtualFile,
      val documentText: String,
      val project: Project,
      val documentLineCount: Int,
      val lineStartOffsets: IntArray,          // precomputed from the SAME document (§3.3)
  )
  override fun collectInformation(psiFile: PsiFile): Info?
  override fun doAnnotate(collectedInfo: Info?): LuaCheckOutcome?
  override fun apply(file: PsiFile, annotationResult: LuaCheckOutcome, holder: AnnotationHolder)
  private fun applyProblem(problem: Problem, info: Info, holder: AnnotationHolder)     // clamped (§3.3)
  private fun applyFailure(failure: LuaCheckOutcome.Failure, info: Info, holder: AnnotationHolder)  // §3.5
  ```
  `Results` (existing nested class, `LuaCheckAnnotator.kt:18`) is **removed** — replaced by
  `LuaCheckOutcome`.

### 2.5 `net.internetisalie.lunar.analysis.inspections.LuaInspectionSuppression` (modified)

- **Responsibility**: Close only disable-blocks whose names intersect an `enable`'s names;
  scope inline `-- luacheck: ignore` to its own line.
- **Threading**: read action (cached per `PsiFile` via `CachedValuesManager`, unchanged).
- **Key API**: `closeBlocks(open, ranges, enableLine, names)` (existing, `:112`) gains the
  §3.4 intersection filter; `parseLuacheck` (existing, `:126`) changes its range end from
  `minOf(commentLine + 1, lineCount)` to `commentLine`.

### 2.6 `net.internetisalie.lunar.analysis.inspections.LuaStandardGlobals` (modified)

- **Responsibility**: Allowlist of built-in globals per level.
- **Key API**: `DELTA_51` (existing, `:23`) gains `"arg"`.

## 3. Algorithms

### 3.1 Flag-value pair de-dup (#28, MAINT-26-01)

- **Input → Output**: `List<String>` of already-flattened tokens → `List<String>` with
  duplicate *whole occurrences* removed, order preserved, value tokens never dropped.
- **Rule**: `distinct()` was added April 2025 ("Process handling and Interpreters",
  `d94f82d7`) as a naive guard against emitting the same flag twice when configured args and
  `DEFAULT_ARGS` overlap. It de-dups the **flat token list**, so `--ignore 611 --max-line-length 611`
  loses the second `611`. Replace with pair-aware de-dup that operates on the assembled token
  list from `resolveArguments` (configured args → `--std <std>` → `DEFAULT_ARGS = ["--codes","--ranges"]`).
- **Steps** (`dedupePairs(tokens)`):
  1. Walk `tokens` left to right with index `i`, building `result` and a `Set<String> seen`.
  2. Classify `tokens[i]`:
     - If it starts with `--` (or `-` followed by a non-digit) **and** the next token exists and
       does **not** start with `-`, treat `tokens[i] + " " + tokens[i+1]` as one *pair key*.
       If the pair key is in `seen`, skip both; else add both to `result`, record the key, advance `i += 2`.
     - Otherwise treat `tokens[i]` as a lone flag; key = the token itself. Skip if in `seen`,
       else append and record; `i += 1`.
  3. Return `result`.
- **Edge handling**: a bare valueless flag (`--codes`) de-dups by itself; a flag whose value is
  itself a flag (`--std --codes` — never emitted by `resolveArguments`) treats `--std` as lone.
  `-` (the stdin positional, appended *after* de-dup in §2.3) is never passed through `dedupePairs`.
- **Complexity**: O(n) single pass.

### 3.2 Robust output parsing (#29, MAINT-26-02)

- **Input → Output**: full `stdout: String` from `capture` → `List<Problem>`.
- **Current form** (`LuaCheckInvoker.kt:28-30`) already splits the *whole captured stdout* with
  `lineSequence()` and `mapNotNull { problemFrom(line) }` — there is **no** streaming
  `ProcessListener`, so the "first-match-per-chunk / trailing-`\n`-required" mechanics from the
  review no longer exist (`capture` returns the complete buffer at termination). This algorithm
  **formalizes and hardens** the existing pass:
  - **Steps**: `stdout.lineSequence().mapNotNull { problemFrom(it, fileName) }.toList()`.
  - `problemFrom` (retained) applies `LINE_PATTERN = (.+?):(\d+):(\d+)-(\d+):(.+)` per line and
    strips ANSI with `ANSI_PATTERN = \[[;\d]*m` (existing, `LuaCheckInvoker.kt:13-14,39`).
  - **Empty/None path**: no matching lines → empty list → `LuaCheckOutcome.Problems(emptyList())`.
- **Rationale for keeping accumulate-then-split**: the annotator collects the entire result once
  per pass (`ExternalAnnotator.doAnnotate` returns a single value); there is no incremental UI to
  feed, so buffer-and-split at termination is exactly right (vs. a streaming `findAll`).

### 3.3 Editor-accurate offsets — stdin primary, clamp secondary (#30, MAINT-26-03)

**Design decision (stdin vs clamp): stdin is primary; clamping is defense-in-depth.**
Verified against the vendored luacheck source (`../tools/luacheck/src/luacheck/main.lua (external-local checkout, one level above the repo root — not git-tracked)`):
- `parser:argument("files", "... Pass '-' to check stdin.")` (`main.lua:33`);
- `if file == "-" then input.file = io.stdin` (`main.lua:305-306`);
- `parser:option("--filename", "Use another filename in output and for selecting configuration overrides.")`
  (`main.lua:218`), and `local input = {filename = args.filename}` (`main.lua:303`).

So `luacheck - --filename foo.lua` reads the *buffer we send* and reports offsets against it —
eliminating the disk/editor skew at the source, exactly as `StyluaFormattingTask` does with
`--stdin-filepath`. `LuaCheckInvoker.invoke` passes `info.documentText` as `capture(cmd, FORMAT, stdin = info.documentText)`.

- **Steps** (`applyProblem`, clamp guard against a residual off-by / config edge):
  1. `line = problem.lineStart.coerceIn(0, info.documentLineCount - 1)`.
  2. `lineStart = info.lineStartOffsets[line]`; `lineEndExclusive = if (line + 1 < offsets.size) offsets[line+1] else documentText.length`.
  3. `startOffset = (lineStart + problem.columnStart).coerceIn(lineStart, lineEndExclusive)`.
  4. `endOffset = (lineStart + problem.columnEnd + 1).coerceIn(startOffset + 1, lineEndExclusive).coerceAtMost(documentText.length)`.
  5. `range = TextRange(startOffset, endOffset)`; create the WARNING annotation.
- **Rule**: `lineStartOffsets` is precomputed in `collectInformation` from the **same** document
  the range applies against — never re-derived from `file.fileDocument` in `apply` (removes the
  IOOBE where disk had more lines than the buffer). `coerceIn` guarantees `TextRange` invariants
  (`0 <= start <= end <= length`) so a malformed report can never throw.

### 3.4 Name-intersection block closing (#31, MAINT-26-04)

- **Input → Output**: an `enable: <names>` at `enableLine` + the open-block stack → closed
  `SuppressionRange`s for **only** intersecting blocks; non-intersecting blocks stay open.
- **Current gap**: `closeBlocks` (`LuaInspectionSuppression.kt:112-124`) receives `names` but
  **ignores it** — it iterates and closes *every* open block. The caller already gates entry at
  `:88` (`if (keyword == "enable") closeBlocks(...)`) but passes the enable's `names` without
  the inner filter using them.
- **Steps** (revised `closeBlocks`):
  1. For each `block` in `open` (iterator):
     - If `block.allDiagnostics` **or** `names.isEmpty()` **or** `block.names.intersect(names).isNotEmpty()`:
       add `SuppressionRange(block.startLine, enableLine - 1, block.names, block.allDiagnostics)`; remove it.
     - Else: leave the block open.
- **Rule**: a bare `---@diagnostic enable` (no names → `names.isEmpty()`, handled at `:82-91` via
  `DIAGNOSTIC_BARE_REGEX`) closes all blocks (Lua-LS semantics); a named `enable: undefined-global`
  closes only blocks that disabled `undefined-global` (via `DIAGNOSTIC_ID` membership) or share a name.

### 3.5 Failure classification & surfacing (§2.5.6, MAINT-26-06)

- **Input → Output**: `LuaExecResult` → `LuaCheckOutcome`.
- **Steps** (`classify(result, fileName)`), mirroring `StyluaFormattingTask.handleResult` (`:54-70`):
  1. `result.outcome == START_FAILED` → `Failure(LAUNCH_FAILED, "Could not execute luacheck")`.
  2. `result.outcome == TIMED_OUT` → `Failure(TIMED_OUT, "luacheck did not respond within ${FORMAT.millis/1000}s")`.
  3. `result.outcome == CANCELLED` → `NotApplicable` (user cancelled the pass; no annotation).
  4. Else (`COMPLETED`): inspect `result.exitCode`:
     - `0` (no problems) or `1` (problems found) → `Problems(parse(result.stdout, fileName))`.
       *(luacheck exit convention: 0 = clean, 1 = warnings/errors reported on stdout, ≥2 = fatal.)*
     - `>= 2` → `Failure(CRASHED, firstNonBlank(result.stderr) ?: "luacheck exited with code ${result.exitCode}")`.
- **Surfacing** (`applyFailure`): create **one** annotation, severity `HighlightSeverity.WARNING`,
  ranged on the whole file (`TextRange(0, minOf(1, documentText.length))` when empty, else
  `TextRange(0, documentText.length)`), message = the `Failure.detail`. Also `LOG.warn(detail)`
  (`Logger`, contract §2). No `Notification` is raised — the missing-binary case is already
  covered by the editor banner (`LuaToolEditorNotificationProvider`, §Prior Art); a second popup
  would double-report. This keeps §2.5.6 "missing binary reads as clean" and "exit ≥ 3 reads as
  clean" both visibly surfaced without duplicating the banner.

### 3.6 Inline `-- luacheck: ignore` same-line scope (#60, MAINT-26-04)

- **Rule**: `parseLuacheck` (`:135`) currently emits `SuppressionRange(commentLine, minOf(commentLine + 1, lineCount), ...)`
  — a two-line range that over-suppresses the *following* source line. Luacheck's inline
  `-- luacheck: ignore` applies to the **line the comment is on** only. Change the end line to
  `commentLine`, yielding `SuppressionRange(commentLine, commentLine, names, allDiagnostics)`.

### 3.7 `arg` in Lua 5.1 (#61, MAINT-26-05)

- **Rule**: `arg` is the standalone-interpreter script-arguments table, a global in Lua 5.1
  (Lua 5.1 Reference Manual §6, *Lua Stand-alone*). Add `"arg"` to `DELTA_51`
  (`LuaStandardGlobals.kt:23-25`). Because `deltaFor` maps both `LUA50` and `LUA51` to
  `DELTA_51`, both levels gain it. Verified consumers: `LuaUndeclaredNames.isExemptGlobal`
  (`:30`), `LuaGlobalCreationInspection` (`:82`), `LuaSpellcheckSuppressions` (`:30`) — all call
  `LuaStandardGlobals.contains`, so the single set edit fixes the false "undeclared `arg`" across
  all three without touching them.

## 4. External Data & Parsing

### 4.1 `luacheck --codes --ranges` output (per line)

- **Format** (one problem per line; `--filename` sets the leading path to what we pass):
  ```
  foo.lua:12:7-13: (W211) unused variable 'x'
  ```
  Fields: `<filename>:<line>:<colStart>-<colEnd>: (<code>) <message>`. Lines are 1-based;
  columns are 1-based inclusive. Non-matching lines (summary banner "Total: N warnings",
  blank lines) are ignored.
- **Parse strategy**: line regex `LINE_PATTERN = (.+?):(\d+):(\d+)-(\d+):(.+)` (existing,
  `LuaCheckInvoker.kt:13`), ANSI-stripped with `ANSI_PATTERN = \[[;\d]*m` (`:14`). Group 2 = line,
  3 = colStart, 4 = colEnd, 5 = message (code kept inline in the message). Offsets converted to
  0-based in `problemFrom` (`- 1`, existing `:43-46`).
- **Maps to**: `Problem(lineStart, lineEnd = lineStart, columnStart, columnEnd, message, file = fileName)`
  (`LuaCheckModel.kt:3`).
- **Failure handling**: exit ≥ 2 → stderr is the diagnostic (bad `.luacheckrc`, invalid `--std`);
  classified as `CRASHED` (§3.5), not parsed as problems.

## 5. Data Flow

### Example 1: unsaved buffer with an unused local

1. User types `local x = 1` (unsaved). Annotator `collectInformation` reads the **document**
   text `"local x = 1\n"`, `fileName = "test.lua"`, `workDir = parent`, precomputes
   `lineStartOffsets = [0, 12]`.
2. `doAnnotate` → `LuaCheckInvoker.invoke(info)` → `newLuaCheckCommandLine(project, "test.lua", workDir, useStdin=true)`
   builds `luacheck --std <std> --codes --ranges - --filename test.lua` (pair-deduped).
3. `capture(cmd, FORMAT, stdin = "local x = 1\n")`. luacheck reads stdin, reports
   `test.lua:1:7-7: (W211) unused variable 'x'`, exit 1.
4. `classify` → `Problems([Problem(lineStart=0, colStart=6, colEnd=6, ...)])`.
5. `apply` → `applyProblem` clamps `startOffset = 0 + 6 = 6`, `endOffset = 6 + 1 = 7`,
   `TextRange(6,7)` over `x` — correctly placed even though the file was never saved.

### Example 2: luacheck binary missing

1. `collectInformation` succeeds (Lua file). `doAnnotate` builds the command; `capture` cannot
   start the process → `LuaExecResult(outcome = START_FAILED)`.
2. `classify` → `Failure(LAUNCH_FAILED, "Could not execute luacheck")`.
3. `apply` → `applyFailure` → one file-wide WARNING annotation + `LOG.warn`. The editor banner
   (existing) already offers "Configure toolchain". No silent green.

### Example 3: `---@diagnostic enable: undefined-global` with two open blocks

1. `---@diagnostic disable: undefined-global` opens block A (names `{undefined-global}`).
2. `---@diagnostic disable: unused` opens block B (names `{unused}`).
3. `---@diagnostic enable: undefined-global` → `closeBlocks(names = {undefined-global})`:
   block A intersects → closed; block B does not → **stays open**. (Previously both closed.)

## 6. Edge Cases

- **Empty document**: `documentText = ""`, `lineStartOffsets = [0]`, `documentLineCount = 1`;
  `applyFailure` ranges `TextRange(0,0)` (allowed). No problems.
- **luacheck reports a line beyond the buffer** (stale config / BOM): `coerceIn` clamps to the
  last valid line; never throws IOOBE.
- **colEnd < colStart** (degenerate range): step 4 forces `endOffset >= startOffset + 1`.
- **Cancelled pass** (user edits again): `CANCELLED` → `NotApplicable` → no annotation flicker.
- **`--filename` with spaces**: passed as a single `GeneralCommandLine` parameter — no shell quoting needed.
- **`resolveArguments` produces no duplicate pairs** (common case): `dedupePairs` is a no-op passthrough.
- **Bare `---@diagnostic enable`** (no names): `DIAGNOSTIC_BARE_REGEX` path → `names.isEmpty()`
  → §3.4 closes all blocks (unchanged, correct).

## 7. Integration Points

No new registrations. The existing declarations are unchanged (verified `plugin.xml:329-342`):

```xml
<!-- plugin.xml (existing, unchanged) -->
<externalAnnotator language="Lua"
    implementationClass="net.internetisalie.lunar.analysis.luacheck.LuaCheckAnnotator"/>
<localInspection language="Lua" shortName="LuaCheck" displayName="LuaCheck"
    groupPath="Lua" groupName="Luacheck" enabledByDefault="true" level="WARNING"
    unfair="true"
    implementationClass="net.internetisalie.lunar.analysis.luacheck.LuaCheckInspection"/>
```

- Settings read: `LuaProjectSettings.getInstance(project).state.getTarget().getLuacheckStd()`
  (`Target.kt:75`), `.languageLevel` (for `LuaStandardGlobals` consumers), `.additionalGlobals`.
- Toolchain: `LuaToolResolver.resolve(project, "luacheck")`, `LuaKindOptionKeys.LUACHECK_ARGUMENTS`.
- Exec: `LuaToolExecutionService.getInstance().capture(cmd, LuaExecTimeout.FORMAT, stdin)`.
- No new `notificationGroup`, index, or EP.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-26-01 Command-line fidelity | M | §2.3, §3.1 |
| MAINT-26-02 Robust output parsing | M | §2.2, §3.2, §4.1 |
| MAINT-26-03 Editor-accurate offsets | M | §2.3 (stdin), §2.4, §3.3 |
| MAINT-26-04 Suppression scoping | S | §2.5, §3.4, §3.6 |
| MAINT-26-05 Stdlib accuracy | S | §2.6, §3.7 |
| MAINT-26-06 Process hygiene | S | §2.1, §2.2, §2.4, §3.5 |

## 9. Alternatives Considered

- **#30 clamp-only vs stdin**: clamp-only leaves unsaved-buffer offsets skewed (luacheck still
  reads stale disk text). Stdin fixes the root cause and matches the in-repo `StyluaFormattingTask`
  idiom; clamping is retained only as a throw-proof guard. Chosen: **stdin primary + clamp guard**.
- **#28 drop `distinct()` entirely vs pair-aware**: dropping it re-admits the duplicate-flag case
  the guard was added for (April 2025). Pair-aware de-dup keeps that protection without corrupting
  values. Chosen: **pair-aware**.
- **§2.5.6 Notification vs annotation**: a `Notification` popup per failed pass would spam on every
  keystroke and duplicate the existing editor banner. A single file-wide WARNING annotation is
  quiet, in-context, and cleared automatically on the next clean pass. Chosen: **annotation + log**.
- **#29 streaming `findAll` vs accumulate-and-split**: `ExternalAnnotator` yields one result per
  pass; streaming buys nothing. Chosen: **accumulate-and-split** (already the shape).

## 10. Open Questions

None — feature has cleared the planning bar. (The one external unknown, luacheck stdin+filename support, was resolved against the vendored source `tools/luacheck/src/luacheck/main.lua` lines 33/218/303-306; see design section 3.3 and DR-01.)
