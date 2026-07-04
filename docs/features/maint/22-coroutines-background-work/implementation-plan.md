---
id: "MAINT-22-plan"
title: "MAINT-22 Implementation Plan: Coroutines for Background Work"
type: "plan"
parent_id: "MAINT-22"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-22 Implementation Plan

Phases are ordered so the transport is rewritten and unit-testable **before** the socket protocol is exercised
live. Each phase ends at a compiling, suite-green state.

## Phase 0 — De-risk coroutines wiring `[Must]` (DR-01)
- [ ] Add `LunarCoroutineScopeService` (§2) and one throwaway `import kotlinx.coroutines.CoroutineScope` in `src/main`.
- [ ] `gce-builder run build`. If compile fails on the coroutines import, add the single `compileOnly(...)` line at the
      platform-bundled version (design §1) and re-build; **never** `implementation`.
- [ ] Confirm the built plugin `lib/` contains no extra `kotlinx-coroutines-*` jar (no double-bundle).
- **Exit:** build green; scope service resolvable from a smoke unit test (`project.service<LunarCoroutineScopeService>()` non-null, scope active).

## Phase 1 — Infrastructure `[Must]` (MAINT-22-01, -02)
- [ ] Finalize `LunarCoroutineScopeService` under `util/`.
- [ ] Unit test: service present, scope cancels on project close (light-fixture `BasePlatformTestCase`).
- **Verify:** `gce-builder run "test --tests *LunarCoroutineScopeService*"`.

## Phase 2 — Transport rewrite `[Must]` (MAINT-22-03, -04)
- [ ] Rewrite `LuaDebugConnection` per design §4: `+scope` ctor, `start()`, `suspend send()`, `readLoop()`, `handleLine()`,
      `DebuggerError`; delete `run/queue/receive/send/current/started/commands`; keep `readExactly`, patterns, `close()`.
- [ ] Preserve the response/out-of-band branching exactly (Case A / Case B).
- [ ] Unit test the state machine with a **fake socket pair** (`java.net.Socket` loopback or piped streams):
      OK response completes the deferred; Extended payload read by length; Run-group sets `running`; a following
      `202 <file> <line>` invokes `observer.onPauseBreakpoint` with parsed `LuaPosition`.
- **Verify:** `grep -c 'Thread.sleep\|reader.ready\|synchronized' LuaDebugConnection.kt` → `0`; new tests green.

## Phase 3 — Controller + consumer integration `[Must]` (MAINT-22-05, -06)
- [ ] Rewrite `LuaDebuggerController` per design §5: `+scope` ctor, `suspend connect()` with `withTimeout`, command
      methods → `suspend`, delete `requests`/`queueRequest`/`queueCommand` and the promise-resolution observer code,
      `scope.cancel()` in `close()`.
- [ ] Edit `LuaDebugProcess` per design §6: `sessionScope`, `launch{withBackgroundProgress{connect…}}`, step/resume/bp
      launches, remove `registerBreakpoints` busy-wait and `Thread.sleep(100/1000)`.
- [ ] Edit `LuaDebuggerEvaluator`: `+scope`, `execute(...).then` → `launch{ callback.evaluated / errorOccurred }`.
- [ ] Confirm no remaining `org.jetbrains.concurrency.*` / `AsyncPromise` imports in `run/` transport+controller.
- **Verify:** `gce-builder run test` green (regression-relative); `grep -rc 'AsyncPromise' run/` → `0`.

## Phase 4 — Rockspec prime `[Should]` (MAINT-22-07)
- [ ] Convert the EDT-branch prime in `RockspecSourcePathProvider` to `scope.launch { readAction { … } }`; drop
      `AppExecutorUtil`/`ReadAction` imports; add the retention comment on `LuaRockspecDiscoveryService.compute()`.
- [ ] Unit test: cold cache on EDT returns empty; after the launched prime settles, real patterns are returned
      (reuse the existing `RockspecSourcePathProvider` test seam `testDiscoverySeam`).
- **Verify:** `gce-builder run "test --tests *RockspecSourcePath*"`.

## Phase 5 — Live verification `[Must]` (MAINT-22-08)
- [ ] Follow the `verify-in-ide` skill: containerized GoLand over VNC, inject plugin jar + license + interpreter,
      open the Lua test project, run the full debug flow in [human-verification-checklists.md](human-verification-checklists.md).
- [ ] Archive screenshots under this feature folder.
- **Verify:** all checklist rows PASS; `idea.log` shows one clean `readLoop`/`run() exiting` per session, no leaked thread.

## Phase 6 — Conventions doc `[Could]` (MAINT-22-09)
- [ ] Add a "Coroutines & structured concurrency" subsection to `docs/engineering-contract.md`: prefer service
      `CoroutineScope` injection + `childScope`; `readAction {}`/`Dispatchers.EDT`; when to keep `Task.Backgroundable`
      (user-visible determinate progress); the no-double-bundle rule. List the opportunistic backlog
      (12 `Task.Backgroundable`, UI fetch panels, `Alarm` debounce).
- **Verify:** `python3 scripts/lint_docs.py docs`.

## Rollback
Each phase is an atomic commit. Phases 2-3 are one logical unit (the debugger won't compile between them on a shared
branch) — land them together or behind a short-lived branch. Reverting Phase 2+3 restores the promise/`Thread.sleep`
transport wholesale.

## Definition of Done
- MAINT-22-01…-09 acceptance criteria met.
- `gce-builder run test` green; `run build` green (incl. `:checkStatus`/`:lintDocs` after status regen).
- `ktlintFormat ktlintCheck` clean on touched files.
- Live VNC debug flow verified with archived evidence.
- `docs/status.md` regenerated (`python3 scripts/gen_status.py`).
