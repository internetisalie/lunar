---
id: "MAINT-24-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-24"
folders:
  - "[[features/maint/24-debugger-hardening/requirements|requirements]]"
---

# MAINT-24: Implementation Plan

Sequenced from the crash-class Musts (framing, `!!` sweep, thread-safety) through the Should
fidelity/config fixes to the Could robustness pass. Every phase leaves the build green and the full
suite passing (baseline 2123 tests / 0 failures / 1 skipped on `main` 2026-07-17). Tasks reference
`design.md` sections; no task invents new design.

## Phases

### Phase 1: DBGp byte-accurate framing [Must]
- **Goal**: multibyte UTF-8 payloads no longer desync the protocol (#5).
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.run.DbgpFraming` (object) with `readLine` / `readExactly` /
        `writeLine` — realizes design §2.1, §3.1.
  - [x] Edit `LuaDebugConnection` — replace the `InputStreamReader`/`BufferedReader` (`:186`), the
        `writer.write(...toByteArray(charset))` (`:220`), and the private char-based `readExactly`
        (`:335`) with `DbgpFraming` over the raw socket streams; `readLoop`/`handleLine` call the new
        helpers (`:229,257,302`) — realizes design §2.2.
- **Exit criteria**: `TestDbgpFraming` green (TC 1–4); full suite green.

### Phase 2: Crash-proof payload parsing — `!!` sweep + rd-gen import [Must]
- **Goal**: malformed remote data degrades gracefully; no rd-gen internal import (#7, #16, #17,
  §2.2).
- **Tasks**:
  - [x] Edit `LuaDebugValue` — delete the `com.jetbrains.rd.generator.nova.GenerationSpec.Companion.nullIfEmpty`
        import (`:24`), use `stringValue.ifEmpty { null }` (`:114`), `displayValue ?: ""` (`:103`),
        §3.4 key policy (`:90-92`) — realizes design §2.5, §3.4, §3.5 (#7, sites 2–4).
  - [x] Edit `LuaDebugVariable` — §3.4 key policy (`:58-60`) — realizes §3.5 (sites 5–6).
  - [x] Edit `LuaValue` — `identifier?.text` in `LuaField.name` (`:169`) — §3.5 (site 7).
  - [x] Edit `LuaRemoteStack` — `mapNotNull { it.checkTable() }` (`:17`); `getOrNull` in
        `LuaRemoteResultFactory` (`:129,138`); `variables[varName] ?: LuaValue.NONE` (`:145`) —
        §3.5, §3.5b (#17, sites 8–9).
  - [x] Edit `LuaDebugValueParser` — null-safe `expr.number`/`expr.string`/`field.name` under their
        existing guards (`:35,44,84`) — §3.5 (sites 10–12).
  - [x] Edit `LuaDebuggerController` — return from `runReadAction { }` in `execute`/`variables`
        (`:239,260`) — §3.5 (sites 13–14).
  - [x] Edit `LuaPosition` — absolute-path fallback for `getRelativePath` (`:43`) — §3.5a (#16,
        site 15).
  - [x] Edit `LuaLineBreakpointType` — `result.get() ?: false` (`:81`) — §3.5 (site 1).
- **Exit criteria**: `grep -c '!!' src/main/kotlin/net/internetisalie/lunar/run/` returns 0;
  `TestLuaRemoteResultFactory`, `TestLuaPosition`, `TestLuaDebugValue` extended and green; full
  suite green.

### Phase 3: Thread-safe controller state [Must]
- **Goal**: no EDT-vs-reader data race on breakpoint maps (#18).
- **Tasks**:
  - [x] Edit `LuaDebuggerController` — `myBreakpoints2Pos`/`myPos2Breakpoints` become private
        `ConcurrentHashMap`; add `fun breakpointAt(pos: LuaPosition): XBreakpoint<*>?`; route
        `onPause` (`:275`) through it (`:60-61`) — realizes design §2.3, §3.3.
- **Exit criteria**: existing debugger tests green; full suite green.

### Phase 4: Value & stack fidelity [Should]
- **Goal**: correct table indexing, frame paging, 1-based lines (#52, #53, #59).
- **Tasks**:
  - [x] Edit `LuaDebugValueParser.evaluateVarSuffixIndex` — for a numeric key, look up
        `table.indexed.getOrNull(key-1)` first, fall back to `named` (`:241-249`) — realizes design
        §2.5 note / requirements #52.
  - [x] Edit `LuaExecutionStack.computeStackFrames` — `drop(firstFrameIndex)` (`:35`) and compare
        `it.frame.file == "=[C]"` (`:37`) — realizes design §3.9.
  - [x] Edit `LuaLineBreakpointType.getDisplayText` — `sourcePosition.line + 1` (`:42`) — §3.10.
- **Exit criteria**: `TestLuaDebugValueParser`, `TestLuaExecutionStack`/`TestLuaRemoteStackFrames`,
  `TestLuaLineBreakpointType` extended and green; full suite green.

### Phase 5: Run-config integrity [Should]
- **Goal**: source-path persists; empty run configs start (#26, #56).
- **Tasks**:
  - [x] Edit `LuaRunConfiguration.LuaRunSettingsEditor.applyEditorTo` — add
        `runConfiguration.sourcePath = sourcePathField.text` (`:345-352`) — realizes design §2.6.
  - [x] Edit `LuaRunConfiguration` — override `checkConfiguration()` and add the `project.basePath`
        working-directory fallback in `getState` (`:256`) — realizes design §3.6.
- **Exit criteria**: `TestLuaRunConfiguration` extended (round-trips sourcePath; empty-workdir
  fallback) and green; full suite green.

### Phase 6: Busted runner correctness [Should]
- **Goal**: Lua-pattern rerun filter, `"`-only JSON scanner, live console output (#27b, #54,
  §2.5.7).
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.run.test.LuaPatternEscaper` (object) — realizes design §2.7,
        §3.8.
  - [x] Edit `LuaTestCommandLineState.configureBustedTargets` — one escaped `--filter` per failed
        test (`:86-90`) — §2.7, §3.8.
  - [x] Edit `LuaTestOutputToEventsConverter.findTopLevelJson` — only `"` delimits; drop the `'`
        branch + `stringChar` var (`:277-314`) — realizes design §2.8 (#54).
  - [x] Edit `LuaTestOutputToEventsConverter.processConsistentText` — forward each busted chunk live
        via `fireOnUncapturedOutput` while still buffering for terminal JSON (`:48-51`) — §2.8,
        §3.8 (§2.5.7). Terminal `processBustedOutput` no longer re-forwards the buffered raw text
        (it already streamed live) to avoid duplicating every console line — deviation from §2.8's
        "before/after" forwarding, justified by the new live stream.
- **Exit criteria**: `TestLuaPatternEscaper`, a `findTopLevelJson` apostrophe test, and a busted
  filter test green; full suite green. Live output is VNC-gated (HV-06).

### Phase 7: Robustness pass [Could]
- **Goal**: configurable port, graceful EXIT (#§2.5.7, §3).
- **Tasks**:
  - [x] Edit `LuaRunConfigurationOptions` + `LuaRunConfiguration` — add `debugPort` StoredProperty
        (default 8172), editor spinner, `MOBDEBUG_PORT` env in the debug branch (`:261-271`) —
        realizes design §2.6, §3.7.
  - [x] Edit `LuaDebuggerController.init` — set `serverPort` from the run config's `debugPort`
        (`:55,70`) — §3.7.
  - [x] Edit `src/main/lua/lunar/debug.lua` — read `MOBDEBUG_PORT` and pass `start(host, port)` —
        §3.7.
  - [x] Edit `LuaDebugProcess.stop()` — call `controller.terminate()` before `destroyProcess()`
        (`:69-73`) — realizes design §2.4 (graceful EXIT; retires the review §3 dead-code note).
- **Exit criteria**: a port round-trip unit test green; full suite green. Port binding + graceful
  EXIT are VNC-gated (HV-07, HV-08).

### Phase 8: Run to Cursor [Should]
- **Goal**: implement Run to Cursor via SETB + RUN + DELB (#6).
- **Tasks**:
  - [ ] Edit `LuaDebuggerController` — add `suspend fun runToCursor(pos: LuaPosition)` and the
        `pendingRunToCursor` one-shot in `DebugObserver.onPauseBreakpoint`; expose `workingDir` —
        realizes design §2.3, §3.2.
  - [ ] Edit `LuaDebugProcess.runToPosition` — replace `throw AbstractMethodError()` with the
        dispatch to `controller.runToCursor` (`:79-81`) — realizes design §2.4, §3.2.
- **Exit criteria**: full suite green; Run to Cursor is VNC-gated (HV-04) — the SETB/RUN/DELB
  sequencing is only honestly testable against a live debuggee.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-24-01 | M | Phase 1 |
| MAINT-24-02 | M | Phase 2 |
| MAINT-24-03 | M | Phase 3 |
| MAINT-24-04 | S | Phase 8 |
| MAINT-24-05 | S | Phase 4 |
| MAINT-24-06 | S | Phase 5 |
| MAINT-24-07 | S | Phase 6 |
| MAINT-24-08 | C | Phase 7 |

## Verification Tasks

- [ ] `TestDbgpFraming` — byte-count `readExactly` on a multibyte payload, CRLF `readLine`, short-read
      `IOException` — covers TC-01a/b/c.
- [ ] Extend `TestLuaDebugValue` / `TestLuaDebugVariable` — malformed key (non-string/non-number)
      renders without crash — covers TC-02a.
- [ ] Extend `TestLuaPosition` — un-relativizable path falls back to absolute — covers TC-02b (#16).
- [ ] Extend `TestLuaRemoteResultFactory` — `local a, b = 1` yields no IOOBE — covers TC-02c (#17).
- [ ] `grep` gate: zero `!!` in `run/` — covers TC-02d (§2.2).
- [ ] Extend `TestLuaDebugValueParser` — `t[1]` on `{10,20}` → 10 — covers TC-05a (#52).
- [ ] Extend `TestLuaExecutionStack`/`TestLuaRemoteStackFrames` — `firstFrameIndex=1` drops frame 0;
      C-frame recognized — covers TC-05b/c (#53).
- [ ] Extend `TestLuaLineBreakpointType` — `getDisplayText` shows 1-based line — covers TC-05d (#59).
- [ ] Extend `TestLuaRunConfiguration` — sourcePath apply round-trip; empty workdir → basePath;
      `checkConfiguration` throws on no runtime — covers TC-06a/b/c (#26, #56).
- [ ] `TestLuaPatternEscaper` + busted-filter test — magic chars escaped, one `--filter` per test —
      covers TC-07a (#27b).
- [ ] `findTopLevelJson` apostrophe test — JSON with `"doesn't"` parsed intact — covers TC-07b (#54).
- [ ] Debug-port round-trip unit test — covers TC-08a (C1).
- [ ] Run `human-verification-checklists.md` on the builder VM over VNC (HV-04/06/07/08) — the live
      DBGp/busted flow (MAINT-22 DoD precedent).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: DBGp framing | done | Must |
| Phase 2: `!!` sweep + rd-gen import | done | Must |
| Phase 3: Thread-safe controller state | done | Must |
| Phase 4: Value & stack fidelity | done | Should |
| Phase 5: Run-config integrity | done | Should |
| Phase 6: Busted runner correctness | done | Should |
| Phase 7: Robustness pass | done | Could |
| Phase 8: Run to Cursor | todo | Should |
