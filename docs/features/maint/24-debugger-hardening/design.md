---
id: "MAINT-24-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-24"
folders:
  - "[[features/maint/24-debugger-hardening/requirements|requirements]]"
---

# Technical Design: MAINT-24 — Debugger & Test-Runner Hardening

Hardening pass over the DBGp debugger transport, the `run/` payload-parsing surface, the two
Lua run-configuration classes, and the busted test-runner. Scope is strictly
`src/main/kotlin/net/internetisalie/lunar/run/**` plus the mobdebug preloader
`src/main/lua/lunar/debug.lua`. No type-engine, lexer/parser, or REDIS-02 LDB changes.

## 1. Architecture Overview

### Current State

The DBGp path was rebuilt on structured concurrency by MAINT-22: `LuaDebugConnection`
(`run/LuaDebugConnection.kt`) owns a reader coroutine (`readLoop`, line 226) and correlates one
in-flight command at a time via a `@Volatile pending: CompletableDeferred<String>` (line 193)
guarded by a `writeMutex` (line 190); `LuaDebuggerController` (`run/LuaDebuggerController.kt`)
drives it through `suspend` commands on a session `childScope`. REDIS-02 fixed the evaluator error
path (#13, `LuaDebuggerController.kt:249`) and the connect timeout (#55, `soTimeout` at
`LuaDebuggerController.kt:100`) and removed the breakpoint spin-wait (#4 — `waitForConnect`/the
`Thread.sleep(100)` loop no longer exist).

The residual defects, re-verified against `main` on 2026-07-17, are enumerated with
`file:line` evidence in `requirements.md` and re-confirmed per-finding in §8 below.

### Prior Art in This Repo

This is a hardening feature: **every component below already exists and is EDITED in place** — no
new production classes except two small pure-function objects extracted from existing code for
unit-testability, and one new run-config field. Grounded inventory:

- `LuaDebugConnection` (`run/LuaDebugConnection.kt`) — DBGp transport; the reader/framing lives in
  `readLoop` (line 226) and `BufferedReader.readExactly` (line 335). **Edited** (#5).
- `LuaDebuggerController` (`run/LuaDebuggerController.kt`) — session controller; breakpoint maps
  `myBreakpoints2Pos`/`myPos2Breakpoints` (lines 60-61), `serverPort` (line 55), `execute`
  (line 206), `variables` (line 254). **Edited** (#6 support, #18, C1 port, `!!`).
- `LuaDebugProcess` (`run/LuaDebugProcess.kt`) — `XDebugProcess`; `runToPosition` throws
  `AbstractMethodError()` (line 79-81), `stop()` (line 69). **Edited** (#6, §2.5.7 graceful EXIT).
- `LuaDebugValue` (`run/LuaDebugValue.kt`) — `nullIfEmpty` import (line 24), `!!` at 90/92/103.
  **Edited** (#7, §2.2).
- `LuaPosition` (`run/LuaPosition.kt`) — `getRelativePath(...)!!` (line 43). **Edited** (#16).
- `LuaRemoteStack` (`run/LuaRemoteStack.kt`) — `checkTable()!!` (line 17), `LuaRemoteResultFactory`
  `exprList[index]` (lines 129,138). **Edited** (#17, §2.2).
- `LuaDebugValueParser` (`run/LuaDebugValueParser.kt`) — `evaluateVarSuffixIndex` (lines 226-261,
  numeric key at 243). **Edited** (#52, §2.2).
- `LuaExecutionStack` (`run/LuaExecutionStack.kt`) — `computeStackFrames` ignores `firstFrameIndex`
  (line 34), C-frame compare (line 37). **Edited** (#53).
- `LuaLineBreakpointType` (`run/LuaLineBreakpointType.kt`) — `getDisplayText` 0-based (line 42),
  `result.get()!!` (line 81). **Edited** (#59, §2.2).
- `LuaDebugVariable` (`run/LuaDebugVariable.kt`) — `!!` at 58/60. **Edited** (§2.2).
- `LuaValue` (`run/LuaValue.kt`) — `LuaTable` (line 123), `getByName`/`getByIndex`, `identifier!!`
  at `LuaField.name` (line 169). **Edited** (§2.2, and provides #52 helper).
- `LuaRunConfiguration` (`run/LuaRunConfiguration.kt`) — `applyEditorTo` (line 345, no sourcePath
  write-back), no `checkConfiguration()`, `serverPort` env plumbing in `getState` (line 261-271).
  **Edited** (#26, #56, C1).
- `LuaTestCommandLineState` (`run/test/LuaTestCommandLineState.kt`) — `configureBustedTargets`
  filter uses `Regex.escape` (line 88). **Edited** (#27b).
- `LuaTestOutputToEventsConverter` (`run/test/LuaTestOutputToEventsConverter.kt`) —
  `findTopLevelJson` (line 277, `'` delimiter at 295), busted buffering (`processConsistentText`
  line 50 / `flushBufferOnProcessTermination` line 54). **Edited** (#54, §2.5.7).
- `LuaTestRunConfiguration.checkConfiguration()` (`run/test/LuaTestRunConfiguration.kt:257`) — the
  exact idiom (`RuntimeConfigurationException`) reused for #56.
- Mobdebug preloader `src/main/lua/lunar/debug.lua` + `src/main/lua/mobdebug/init.lua` — mobdebug's
  `port` already reads env `MOBDEBUG_PORT` (`init.lua:25`), `start(host, port)` (`init.lua:1063`).
  Used by C1 (configurable port). **Edited** (`lunar/debug.lua`).

New symbols this feature introduces (all in `run/`, grounded plan below): the `DbgpFraming` object
(§2.1), the `LuaPatternEscaper` object (§2.7), and a `debugPort` StoredProperty on
`LuaRunConfigurationOptions` (§2.6). Nothing is borrowed from EmmyLua.

### Target State

Same architecture, defects removed. Two behaviours become newly correct-by-construction:
byte-accurate DBGp framing (§3.1) and thread-safe controller state (§3.3). Two pure functions are
extracted so they can be unit-tested without a socket, mirroring the existing socket-free
`TestLuaDebugConnectionParsing` precedent.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.run.DbgpFraming` (NEW — pure object, extracted for testing)

- **Responsibility**: byte-accurate DBGp line/length-prefixed framing over raw streams (#5).
- **Threading**: called only from the reader coroutine (`Dispatchers.IO`); performs blocking reads.
- **Collaborators**: `java.io.InputStream`, `java.io.OutputStream`, `kotlin.text.Charsets.UTF_8`.
- **Key API**:
  ```kotlin
  object DbgpFraming {
      val CHARSET: java.nio.charset.Charset = Charsets.UTF_8
      // Read one newline-terminated line as raw bytes, decode UTF-8. Returns null at EOF.
      fun readLine(input: java.io.InputStream): String?
      // Read exactly [byteCount] raw bytes then decode UTF-8 (the DBGp length prefix is BYTES).
      fun readExactly(input: java.io.InputStream, byteCount: Int): String
      // Encode a command line as UTF-8 bytes with a trailing '\n' and flush.
      fun writeLine(output: java.io.OutputStream, line: String)
  }
  ```

### 2.2 `net.internetisalie.lunar.run.LuaDebugConnection` (EDITED, #5, §2.2)

- **Responsibility**: unchanged (DBGp transport). Framing delegated to `DbgpFraming`.
- **Threading**: unchanged — reader coroutine on `Dispatchers.IO`, `send` under `writeMutex`.
- **Change**: replace the `BufferedReader`/`InputStreamReader` (`line 186`) and `writer.write(...
  .toByteArray(charset))` (line 220) and the char-based private `readExactly` (line 335) with
  `DbgpFraming` over the raw `socket.getInputStream()` / `socket.getOutputStream()`. `readLoop`
  (line 226) calls `DbgpFraming.readLine(input)`; the two `reader.readExactly(data.toInt())` sites
  (lines 257, 302) call `DbgpFraming.readExactly(input, data.toInt())`. The `handleLine` "unknown
  status" throw (line 247) is retained (a malformed status is a protocol desync, not a payload
  field — logging + closing is correct; it already `?: throw IOException`).
- **Stream ownership & buffering**: the raw `socket.getInputStream()` is wrapped ONCE in a
  `BufferedInputStream` owned by `LuaDebugConnection` (created where the `BufferedReader` is
  today, line 186) and passed to every `DbgpFraming` call — per-byte `readLine` reads hit the
  buffer, not the socket. The connection (not `DbgpFraming`) closes the stream on teardown,
  exactly as it closes the reader today; `DbgpFraming` never closes what it is handed.

### 2.3 `net.internetisalie.lunar.run.LuaDebuggerController` (EDITED, #6 support, #18, C1, §2.2)

- **Responsibility**: unchanged.
- **Threading**: EDT toggles breakpoints (`addBreakPoint`/`removeBreakPoint` reached from
  `LuaDebugProcess`), reader coroutine fires observer callbacks — hence the maps are concurrently
  accessed (#18).
- **Changes**:
  - `myBreakpoints2Pos` / `myPos2Breakpoints` (lines 60-61): change type to
    `java.util.concurrent.ConcurrentHashMap`, visibility to `private`, expose read-only lookups
    (`fun breakpointAt(pos: LuaPosition): XBreakpoint<*>?`). `ConcurrentHashMap` forbids null keys —
    `addBreakPoint`/`removeBreakPoint` already early-`return` on a null `sourcePosition` (lines
    187/197), so no null key/value ever reaches the maps.
  - `execute` (line 239) / `variables` (line 260) `return luaDebugValue!!` / `luaRemoteStack!!`:
    move the local into the `runReadAction { }` result via a `Ref`/direct return so the `!!` is
    removed (see §3.5).
  - C1: `serverPort` (line 55) becomes constructor-derived from the run config (§3.7).
  - #6 support: add `suspend fun runToCursor(pos: LuaPosition)` (§3.2).

### 2.4 `net.internetisalie.lunar.run.LuaDebugProcess` (EDITED, #6, §2.5.7)

- **Responsibility**: unchanged (`XDebugProcess`).
- **Threading**: `runToPosition` is called on the EDT; work is dispatched onto `sessionScope`.
- **Changes**:
  - `runToPosition` (lines 79-81): replace `throw AbstractMethodError()` with the §3.2 sequence.
  - `stop()` (lines 69-73): call `controller.terminate()` (already implemented,
    `LuaDebuggerController.kt:117`, sends `EXIT` then `close()`) before `destroyProcess()` so the
    debuggee gets a graceful `EXIT` (§2.5.7, retires the dead-code note in review §3 for
    `terminate()`/`EXIT`).

### 2.5 `net.internetisalie.lunar.run.LuaDebugValue` / `LuaDebugVariable` (EDITED, #7, §2.2)

- **Change (#7)**: delete the import
  `com.jetbrains.rd.generator.nova.GenerationSpec.Companion.nullIfEmpty` (`LuaDebugValue.kt:24`) and
  replace `stringValue.nullIfEmpty()` (line 114) with `stringValue.ifEmpty { null }` (Kotlin
  stdlib). The private `presentation` getter's `displayValue!!` (line 103) becomes
  `displayValue ?: ""`.
- **Change (§2.2)**: `computeChildren` in both files (`LuaDebugValue.kt:90-92`,
  `LuaDebugVariable.kt:58-60`) replaces `stringValue!!` / `numberValue!!` with the §3.4 key policy.

### 2.6 `net.internetisalie.lunar.run.LuaRunConfiguration` (EDITED, #26, #56, C1)

- **Change (#26)**: add `runConfiguration.sourcePath = sourcePathField.text` to `applyEditorTo`
  (after line 351). The field, getter/setter, and `resetEditorFrom` wiring already exist
  (`sourcePathField` line 302, `sourcePath` property line 195, `resetEditorFrom` line 339).
- **Change (#56)**: override `checkConfiguration()` on `LuaRunConfiguration` (mirroring
  `LuaTestRunConfiguration.kt:257`), and apply a `project.basePath` fallback for an empty working
  directory in `getState` (§3.6).
- **Change (C1)**: add `debugPort: StoredProperty<Int>` to `LuaRunConfigurationOptions` (default
  `8172`), a `var debugPort: Int` on `LuaRunConfiguration`, a spinner field on
  `LuaRunSettingsEditor`, and pass it as `MOBDEBUG_PORT` env + to `LuaDebuggerController` (§3.7).

### 2.7 `net.internetisalie.lunar.run.test.LuaPatternEscaper` (NEW — pure object) + `LuaTestCommandLineState` (EDITED, #27b)

- **Responsibility**: escape a busted test name into a literal Lua-pattern fragment (#27b).
- **Threading**: pure, no context.
- **Key API**:
  ```kotlin
  object LuaPatternEscaper {
      // Prefix each Lua magic char with '%' so busted's --filter matches the name literally.
      fun escape(name: String): String
  }
  ```
- **`configureBustedTargets` change**: `LuaTestCommandLineState.kt:86-90` — for each failed test,
  emit a separate `--filter=<LuaPatternEscaper.escape(name)>` parameter (busted allows repeated
  `--filter`, OR-combined) instead of one `Regex.escape`-joined `|` alternation.

### 2.8 `net.internetisalie.lunar.run.test.LuaTestOutputToEventsConverter` (EDITED, #54, §2.5.7)

- **Change (#54)**: `findTopLevelJson` (line 295) — only `"` toggles `inString`; drop the
  `|| char == '\''` branch and the `stringChar` var (always `"`).
- **Change (§2.5.7)**: `processConsistentText` (line 50) — for the BUSTED branch, in addition to
  appending to `myBuffer` for terminal JSON parsing, forward each chunk live to the console via
  `fireOnUncapturedOutput(text, outputType)` so output is visible during the run instead of only at
  termination (§3.8). The structured test-tree still builds from the terminal JSON (busted
  `--output=json` emits one report object at process end; per-test streaming events are not possible
  from that formatter — documented in §6 and risks-and-gaps DR-02).

## 3. Algorithms

### 3.1 DBGp byte-accurate framing (#5)

- **Input → Output**: `InputStream` → decoded `String` (line or fixed-length payload).
- **`readLine(input)` steps**:
  1. Allocate a growable `java.io.ByteArrayOutputStream`.
  2. Loop: `val b = input.read()`. If `b == -1` and the buffer is empty, return `null` (EOF). If
     `b == -1` with buffered bytes, break (last unterminated line).
  3. If `b == '\n'.code` (0x0A), break. Else if `b == '\r'.code` (0x0D), continue (skip — DBGp uses
     bare `\n`; tolerate CRLF). Else `write(b)`.
  4. Return `buffer.toByteArray().toString(CHARSET)`.
- **`readExactly(input, byteCount)` steps**:
  1. `require(byteCount >= 0)`; if `byteCount == 0` return `""`.
  2. `val buf = ByteArray(byteCount)`; `var off = 0`.
  3. Loop while `off < byteCount`: `val n = input.read(buf, off, byteCount - off)`; if `n == -1`
     throw `IOException("connection closed after $off of $byteCount bytes")`; `off += n`.
  4. Return `buf.toString(CHARSET)`.
- **`writeLine(output, line)`**: `output.write((line + "\n").toByteArray(CHARSET)); output.flush()`.
- **Rules / edge handling**: the DBGp length prefix (`data.toInt()` at call sites) counts **bytes**,
  so `readExactly` reads bytes and decodes once — a multibyte UTF-8 payload no longer desyncs. All
  reads/writes use the explicit `UTF_8` charset (never the platform default).
- **Complexity**: O(payload bytes); no busy-poll (the previous `reader.ready()` note is moot — the
  coroutine reader blocks on `read`).

### 3.2 Run to Cursor (#6)

- **Input → Output**: `XSourcePosition` (the cursor line) → the debuggee runs to that line then
  pauses, without leaving a permanent breakpoint.
- **`LuaDebugProcess.runToPosition(position, context)` steps** (on the EDT, dispatch to
  `sessionScope`):
  1. `val pos = LuaPosition.createRemotePosition(position, workingDir)` — reuse the existing
     converter (`LuaPosition.kt:41`). `workingDir` is already held by the controller
     (`LuaDebuggerController.kt:64`); expose it via a controller accessor.
  2. `sessionScope.launch { controller.runToCursor(pos) }`.
- **`LuaDebuggerController.runToCursor(pos)` steps** (`suspend`):
  1. If `breakpointAt(pos) != null` (a user breakpoint already covers the line), just
     `sendCommand(RUN)` and return (SETB/DELB would clobber the user's breakpoint).
  2. Else: `sendCommand(DebugCommand(SETB, pos.args()))` — temporary breakpoint.
  3. `sendCommand(DebugCommand(RUN))` — run; the debuggee pauses at `pos` and the reader coroutine
     fires `onPauseBreakpoint` → the normal pause flow (`variables()` + `positionReached`).
  4. Register a one-shot: after the RUN command returns (its `200 OK` acknowledgement), the pause
     arrives out-of-band; to remove the temporary breakpoint we send `DELB` from the pause handler.
     Concretely: set `@Volatile private var pendingRunToCursor: LuaPosition? = null` before RUN;
     in `DebugObserver.onPauseBreakpoint`, if `pendingRunToCursor == pos`, `sendCommand(DELB,
     pos.args())` and clear it, then proceed with the normal pause.
- **Rules / edge handling**: `SETB`/`RUN`/`DELB` are existing `DebugCommandKind`s
  (`LuaDebugConnection.kt:61,66,105`) with the same arg contract as `addBreakPoint`
  (`LuaDebuggerController.kt:193`). If the session terminates before the pause, `close()` cancels
  the scope and `pendingRunToCursor` is dropped with the controller — no leaked breakpoint on the
  IDE side (the debuggee process is gone).
- **Cursor line never reached, session still alive**: the temporary `SETB` simply remains
  armed until hit or session end (scope-cancel clears the one-shot) — identical to the
  platform's own Run-to-Cursor semantics; no extra timeout is introduced. Verified live via HV-04.


### 3.3 Thread-safe controller state (#18)

- **Rule**: the coroutine model already makes `LuaDebugConnection.pending`/`pendingKind`/`running`
  `@Volatile` (lines 192-199) — the connection half of #18 is **already fixed**; do NOT add
  `synchronized`. The residual defect is the two public `HashMap`s on the controller.
- **Steps**: replace `HashMap` with `ConcurrentHashMap` (lock-free, coroutine-safe), make them
  `private`, and route all writes through the existing `addBreakPoint`/`removeBreakPoint` and reads
  through `breakpointAt(pos)`. No `@Volatile`/`synchronized` bolt-ons — the map itself provides the
  memory-visibility guarantee.

### 3.4 Crash-proof value key derivation (§2.2, `LuaDebugValue`/`LuaDebugVariable`)

- **Input → Output**: `field: Pair<LuaValue, LuaValue>` (key, value) → a display key `String`.
- **Steps** (replaces `stringValue!!` / `numberValue!!`):
  ```kotlin
  val key = when (field.first.kind) {
      LuaValueKind.String -> field.first.stringValue ?: "?"
      LuaValueKind.Number -> "[" + (field.first.numberValue?.toInt() ?: 0) + "]"
      else -> "[" + field.first.toDisplayString() + "]"
  }
  ```
- **Rule**: a malformed key never crashes; it degrades to `"?"` / `"[0]"` / a bracketed display
  string. `toDisplayString()` exists (`LuaValue.kt:69`).

### 3.5 `!!` removal policy — the full run/ sweep (§2.2, #7, #16, #17)

Fifteen `!!` sites exist in `run/` (grep on 2026-07-17). Per-site policy (degrade gracefully,
never crash on malformed remote data):

| # | Site | Policy |
|---|------|--------|
| 1 | `LuaLineBreakpointType.kt:81` `result.get()!!` | `result.get() ?: false` — `Ref<Boolean?>` default is already `false`. |
| 2 | `LuaDebugValue.kt:90` `stringValue!!` | §3.4 key policy. |
| 3 | `LuaDebugValue.kt:92` `numberValue!!` | §3.4 key policy. |
| 4 | `LuaDebugValue.kt:103` `displayValue!!` | `displayValue ?: ""`. |
| 5 | `LuaDebugVariable.kt:58` `stringValue!!` | §3.4 key policy. |
| 6 | `LuaDebugVariable.kt:60` `numberValue!!` | §3.4 key policy. |
| 7 | `LuaValue.kt:169` `identifier!!.text` | already guarded by `if (identifier != null)` — replace with `identifier?.text` inside that branch (the guard makes it safe but the contract bans the operator). |
| 8 | `LuaRemoteStack.kt:17` `checkTable()!!` | `mapNotNull { it.checkTable() }` — skip non-table stack entries (#17). |
| 9 | `LuaRemoteStack.kt:145` `variables[varName]!!` | inside `if (!variables.containsKey(varName))` else-branch; replace with `variables[varName] ?: LuaValue.NONE`. |
| 10 | `LuaDebugValueParser.kt:35` `expr.number!!` | inside `expr.number != null` guard → `expr.number?.text ?: return null`. |
| 11 | `LuaDebugValueParser.kt:44` `expr.string!!` | inside `expr.string != null` guard → `expr.string?.text ?: return null`. |
| 12 | `LuaDebugValueParser.kt:84` `field.name!!` | inside `field.name != null` guard → capture into a local `val fieldName = field.name` before the `if`. |
| 13 | `LuaDebuggerController.kt:239` `luaDebugValue!!` | return the value from `runReadAction { }` (it returns a value): `return runReadAction { … }`. |
| 14 | `LuaDebuggerController.kt:260` `luaRemoteStack!!` | same — `return runReadAction { LuaRemoteStack.create(session.project, text) }`. |
| 15 | `LuaPosition.kt:43` `getRelativePath(...)!!` | §3.5a below (#16). |

- **3.5a (#16) `LuaPosition.createRemotePosition`**: `FileUtil.getRelativePath(workingDir,
  File(path))` may return `null` (different root/drive). Policy:
  `val rel = FileUtil.getRelativePath(workingDir, target) ?: target.path` then
  `.replace('\\','/')`. Falls back to the absolute path so breakpoint registration never dies.
- **3.5b (#17) `LuaRemoteResultFactory` `exprList[index]`** (`LuaRemoteStack.kt:129,138`): replace
  `statement.exprList?.exprList[index]` with `statement.exprList?.exprList?.getOrNull(index)` and
  `...?.getOrNull(0)`. Handles `local a, b = 1` (fewer exprs than names) without IOOBE.
- **Out of scope**: the ~20 non-`run/` `!!` sites in review §2.2 (LuaCATS renderer, luacheck,
  settings, psi) — those belong to MAINT-25/26/27/28, not here.

### 3.6 Run-config validation & working-directory fallback (#56)

- **`LuaRunConfiguration.checkConfiguration()`** (new override, mirrors
  `LuaTestRunConfiguration.kt:257`):
  1. `if (resolveInterpreter() == null) throw RuntimeConfigurationException("No Lua runtime is
     configured…")` (reuse the message from `getState` line 240).
  2. `if (options.scriptName.isNullOrEmpty()) throw RuntimeConfigurationWarning("No script file
     configured")` — warning (REPL mode `-v -i` is valid, per `getState` line 250).
- **Working-directory fallback** in `getState.startProcess` (line 256): replace
  `commandLine.withWorkDirectory(workingDirectory)` with
  ```kotlin
  val workDir = workingDirectory?.takeIf { it.isNotEmpty() } ?: project.basePath
  if (!workDir.isNullOrEmpty()) commandLine.withWorkDirectory(workDir)
  ```
  matching the busted/lunity state's existing basePath fallback (`LuaTestCommandLineState.kt:69`).

### 3.7 Configurable debug port (C1, §2.5.7 sleep note)

- **Store**: add `myDebugPort: StoredProperty<Int>` (default `8172`) to
  `LuaRunConfigurationOptions` via `property(8172).provideDelegate(this, "debugPort")` (the
  `BaseState.property(defaultValue: Int)` overload, verified `intellij-community`
  `platform/projectModel-api/.../BaseState.kt:118`; mirrors the file's existing
  `string("").provideDelegate(...)` pattern), exposed as `var debugPort: Int` on options and
  `LuaRunConfiguration`. Add an `com.intellij.ui.JBIntSpinner`-backed field to `LuaRunSettingsEditor`
  ("Debug port", default 8172), wired in `resetEditorFrom`/`applyEditorTo`.
- **Flow**: in `getState.startProcess` debug branch (line 261-271), add
  `commandLine.withEnvironment("MOBDEBUG_PORT", debugPort.toString())`. mobdebug's `port` already
  reads `MOBDEBUG_PORT` (`src/main/lua/mobdebug/init.lua:25`) so `debugger.start()` binds the
  chosen port — no preloader change strictly required, but `lunar/debug.lua` is updated to read the
  env explicitly and pass `start(host, port)` for clarity (defensive, mobdebug ≥ some builds ignore
  the module default after a prior `start`).
- **Controller side**: `LuaDebuggerController.serverPort` (line 55) is set from
  `(session.runProfile as? LuaRunConfiguration)?.debugPort ?: 8172` in `init` (the controller
  already reads `session.runProfile as? LuaRunConfiguration` at line 70).
- **Sleep race**: the review's `Thread.sleep(1000)` post-connect note (`:130-134`) refers to code
  that **no longer exists** — the MAINT-22 rewrite replaced it with `withBackgroundProgress` +
  suspend `connect()` (`LuaDebugProcess.kt:109-119`). No action; recorded as mooted in §8. The dead
  `InterruptedException` catch note is likewise **already gone** (verified: no `catch
  (InterruptedException)` in `LuaDebuggerController.kt`).

### 3.8 Busted rerun filter & live output (#27b, §2.5.7)

- **`LuaPatternEscaper.escape(name)`**: Lua patterns treat these as magic:
  `( ) . % + - * ? [ ] ^ $`. Steps: build a `StringBuilder`; for each char, if it is in the magic
  set, append `'%'` then the char, else append the char. (Distinct from Java regex `\Q…\E`, which
  busted does not understand.)
- **`configureBustedTargets`** (`LuaTestCommandLineState.kt:86-90`): for each name in
  `failedTests.split(',')` that is non-blank, add one parameter
  `"--filter=" + LuaPatternEscaper.escape(name)`. Busted OR-combines repeated `--filter` flags.
- **Live output (§2.5.7)**: see §2.8 — forward each busted chunk to the console during the run.

## 4. External Data & Parsing

### 4.1 DBGp wire protocol (mobdebug)

- **Format**: line protocol. A response line is `<status message>[ <data>]\n`, e.g. `200 OK`,
  `202 Paused main.lua 12`. For `Extended`-data commands the `<data>` field is a decimal **byte
  count** followed by exactly that many payload bytes (no trailing newline in the count).
- **Parse strategy**: `DbgpFraming.readLine` for the status line (§3.1), `DbgpFraming.readExactly`
  for the counted payload. Status parsed by `DebuggerStatus.entries.firstOrNull { … }`
  (`LuaDebugConnection.kt:246`, unchanged). Pause data parsed by the existing
  `breakpointDataPattern` / `watchpointDataPattern` (`LuaDebugConnection.kt:347-348`, unchanged).
- **Maps to**: `LuaPosition`, `LuaRemoteStack`, `LuaDebugValue` via `LuaDebugValueParser`.
- **Failure handling**: unknown status → `IOException` (desync → close, existing). Short read →
  `IOException` (§3.1). Malformed payload fields → graceful nulls (§3.4/§3.5), never a crash.

### 4.2 Busted `--output=json` report

- **Format**: a single top-level JSON object printed at process end, interleaved with arbitrary
  stdout/stderr the test code produced (which may contain apostrophes, e.g. `doesn't`). Shape:
  `{ "successes":[…], "failures":[…], "errors":[…], "pendings":[…] }`, each item
  `{ "name":"Suite → test", "duration":0.001, "trace":{ "source":"@file", "currentline":N },
  "message":"…" }`.
- **Parse strategy**: `findTopLevelJson` brace-scans for the outermost `{…}`, honoring only `"`
  string delimiters (#54), then Gson `JsonParser` (`processBustedJson`, unchanged). The pre-JSON and
  post-JSON text is forwarded as console output.
- **Failure handling**: no JSON found → whole buffer to console (existing else-branch, line 165).
  Gson parse error → raw to console (existing catch, line 159).

## 5. Data Flow

### Example 1: Breakpoint hit with a UTF-8 variable value (#5, §2.2)

`SETB` sent → debuggee runs → `202 Paused file.lua 3` line read by `DbgpFraming.readLine` →
`onPauseBreakpoint` → `variables()` sends `STACK` → the `200 OK <byteCount>` line, then
`DbgpFraming.readExactly(input, byteCount)` reads exactly the UTF-8 bytes (a `"café"` value no
longer under-reads) → `LuaRemoteStack.create` → `LuaStackFrame.computeChildren` builds
`LuaDebugValue`s whose keys come from §3.4 (no `!!`) → variables panel populated.

### Example 2: Run to Cursor on a line without a breakpoint (#6)

User invokes Run to Cursor at `file.lua:20` → `runToPosition` → `runToCursor(pos)` → `SETB
file.lua 20`, `pendingRunToCursor = pos`, `RUN` → debuggee pauses at line 20 → `onPauseBreakpoint`
sees `pendingRunToCursor == pos` → `DELB file.lua 20` + clear → normal `positionReached`. No
AbstractMethodError dialog; no leftover breakpoint.

### Example 3: Rerun failed busted test named `handles user's input` (#27b, #54)

Fail list `["handles user's input"]` → `LuaPatternEscaper.escape` leaves it literal (no magic
chars) → `--filter=handles user's input` → busted re-runs only that test. Its report contains
`"doesn't"`; `findTopLevelJson` no longer treats the `'` as a string toggle, so the JSON object is
found intact and the test tree renders.

## 6. Edge Cases

- **Multibyte payload spanning multiple TCP reads**: `readExactly` loops until `byteCount` bytes
  arrive (§3.1) — TCP fragmentation handled.
- **CRLF from a Windows debuggee**: `readLine` skips `\r` (§3.1).
- **`local a, b = 1` in the returned stack (#17)**: `getOrNull` yields `null` for `b`, skipped.
- **C stack frame (#53a)**: frame `{ nil, "=[C]", -1, -1, "C", "", "[C]" }` — `frame.file` (index 1)
  is `"=[C]"`, `frame.path` (index 6) is `"[C]"`. §3.9 fixes the compare to use `frame.file`.
- **`firstFrameIndex > 0` offset request (#53b)**: `computeStackFrames` must `drop(firstFrameIndex)`
  before mapping so paged frame requests do not duplicate frames.
- **Busted json streaming**: busted's json formatter emits one object at the end — per-test live
  events are not achievable from it (DR-02); §2.8 gives live *console* output as the honest middle
  ground.
- **`ConcurrentHashMap` null key (#18)**: guarded by the existing early-return on null
  `sourcePosition`; documented in §3.3.

### 3.9 Stack-frame fidelity (#53)

- **(a)** `LuaExecutionStack.computeStackFrames` (line 37): change `it.frame.path == "=[C]"` to
  `it.frame.file == "=[C]"` (index-1 field; `LuaRemoteStack.kt:71-72` exposes `file`). The C-frame
  branch then actually fires (currently `path` = `"[C]"` never equals `"=[C]"`).
- **(b)** Before `.map`, `val entries = stack.entries.drop(firstFrameIndex)` (line 35). Prevents
  duplicated frames on paged requests.

### 3.10 Breakpoint display 1-based line (#59)

- `LuaLineBreakpointType.getDisplayText` (line 42): `sourcePosition.line` is 0-based; render
  `sourcePosition.line + 1`. Result: `"Line ${sourcePosition.line + 1} in file $displayPath"`.

### 3.11 Positional table indexing in evaluation (#52)

- **Input → Output**: evaluation expression `t[K]` where `K` is an integer and `t` is a
  `LuaTable` → the K-th positional element, else the named member.
- **Steps** (`LuaDebugValueParser.evaluateVarSuffixIndex`, line 243):
  1. If the subscript key parses as an `Int` `k`, first try `table.indexed.getOrNull(k - 1)`
     (`LuaTable.indexed` is 0-based `MutableList<LuaValue>`; Lua subscripts are 1-based).
  2. On `null`, fall back to the existing `table.getByName(key.toString())` lookup (preserves
     string-keyed and mixed-table behavior).
  3. Non-integer keys keep the current `getByName` path unchanged.
- **Rules / edge handling**: `k <= 0` falls through to the named lookup (Lua allows `t[0]` as a
  hash key); no exception on out-of-range (returns nil-equivalent, matching debugger semantics).

## 7. Integration Points

No new `plugin.xml` extension points. All edited classes are already registered:

```xml
<!-- plugin.xml (existing, unchanged) -->
<configurationType implementation="net.internetisalie.lunar.run.LuaRunConfigurationType"/>          <!-- :587 -->
<configurationType implementation="net.internetisalie.lunar.run.test.LuaTestRunConfigurationType"/> <!-- :589 -->
<programRunner implementation="net.internetisalie.lunar.run.LuaDebugRunner"/>                        <!-- :640 -->
<xdebugger.breakpointType implementation="net.internetisalie.lunar.run.LuaLineBreakpointType"/>      <!-- :642 -->
```

- The busted converter is wired via `LuaTestConsoleProperties.createTestEventsConverter`
  (`LuaTestConsoleProperties.kt:26`) — no registration change.
- The rerun action is wired via `LuaTestConsoleProperties.createRerunFailedTestsAction`
  (`LuaTestConsoleProperties.kt:34`) — confirmed present; #27b only touches the filter string.
- New settings key: `debugPort` (Int, default 8172) is a per-run-config `StoredProperty` persisted
  in the standard run-config XML (`LuaRunConfigurationOptions`), NOT a project/app setting — no
  `@State` change.

## 8. Requirement Coverage

Findings re-verified against `main` on 2026-07-17 (evidence = `file:line` above):

| Requirement | Priority | Implemented by (section) | Findings (status vs. review) |
|-------------|----------|--------------------------|------------------------------|
| MAINT-24-01 | M | §2.1, §2.2, §3.1, §4.1 | #5 STILL PRESENT (`LuaDebugConnection.kt:186,257,302,335`) |
| MAINT-24-02 | M | §2.5, §3.4, §3.5 (table) | #7 present (`LuaDebugValue.kt:24,114`); #16 present (`LuaPosition.kt:43`); #17 present (`LuaRemoteStack.kt:17,129,138`); §2.2 = 15 `run/` `!!` sites |
| MAINT-24-03 | M | §2.3, §3.3 | #18 controller maps present (`LuaDebuggerController.kt:60-61`); connection half MOOT (already `@Volatile`, `LuaDebugConnection.kt:192-199`) |
| MAINT-24-04 | S | §2.3, §2.4, §3.2 | #6 present (`LuaDebugProcess.kt:80`) |
| MAINT-24-05 | S | §3.9, §3.10, §3.11 | #52 present (`LuaDebugValueParser.kt:243`); #53a+b present (`LuaExecutionStack.kt:35,37`); #59 present (`LuaLineBreakpointType.kt:42`) |
| MAINT-24-06 | S | §2.6, §3.6 | #26 present (`LuaRunConfiguration.kt:345-352`); #56 present (no `checkConfiguration`, `:256`) |
| MAINT-24-07 | S | §2.7, §2.8, §3.8, §4.2 | #27b present (`LuaTestCommandLineState.kt:88`); #54 present (`…Converter.kt:295`); §2.5.7 streaming present (`…Converter.kt:50,54`) |
| MAINT-24-08 | C | §2.4, §3.7 | port hard-coded (`LuaDebuggerController.kt:55`); `Thread.sleep(1000)` note MOOT (removed by MAINT-22); dead `InterruptedException` catch MOOT (already gone); graceful EXIT via existing `terminate()` (`:117`) |

## 9. Alternatives Considered

- **Keep `BufferedReader` but count bytes separately**: rejected — a `Reader` cannot expose the raw
  byte boundary the length prefix needs; a byte-level `InputStream` is the only correct framing.
- **Run to Cursor as a no-op with a status message** (the review's fallback): rejected — the SETB/
  RUN/DELB sequence is cheap and gives real behaviour; the commands already exist.
- **`synchronized`/`@Volatile` on the controller maps**: rejected — `ConcurrentHashMap` is the
  coroutine-idiomatic, lock-free choice (engineering contract §2 COROUTINE CONVENTIONS; do not bolt
  raw `synchronized` onto the coroutine model).
- **Per-test streaming busted events**: not possible from `--output=json` (DR-02); live console
  output chosen instead.
- **Project-level debug-port setting**: rejected — the port pairs with a specific launched process,
  so it belongs on the run configuration (like `workingDirectory`).

## 10. Open Questions

None — feature has cleared the planning bar.
