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

## Phase 0 — De-risk coroutines wiring `[Must]` (DR-01) — ✅ DONE (2026-07-04)
- [x] Add `LunarCoroutineScopeService` (§2) with `import kotlinx.coroutines.CoroutineScope` in `src/main`.
- [x] `gce-builder run compileKotlin` → **BUILD SUCCESSFUL** with **no** dependency change; the `compileOnly` fallback
      was not needed (platform exposes coroutines transitively).
- [x] No double-bundle: nothing added to `implementation`/`api`, so the platform's coroutines jar is the only copy (R1 closed).
- **Exit:** compile green; DR-01 resolved. Remaining exit item (smoke unit test for the service) folded into Phase 1.

## Phase 1 — Infrastructure `[Must]` (MAINT-22-01, -02) — ✅ DONE (2026-07-04)
- [x] Finalize `LunarCoroutineScopeService` under `util/`.
- [x] Unit test (`LunarCoroutineScopeServiceTest`): service present + active scope; `getInstance` singleton.
- **Verified:** full `gce-builder run test` green.

## Phase 2 — Transport rewrite `[Must]` (MAINT-22-03, -04) — ✅ DONE (2026-07-04)
- [x] Rewrote `LuaDebugConnection` per design §4: `+scope` ctor, `start()`, `suspend send()`, `readLoop()`, `handleLine()`,
      `DebuggerError`; deleted `run/queue/receive/send/current/started/commands`; kept `readExactly`, patterns, `close()`.
- [x] Preserved the response/out-of-band branching exactly (Case A / Case B).
- [x] `LuaDebugObserver` slimmed to the 4 out-of-band callbacks (command correlation now via `CompletableDeferred`).
- **Verified:** `grep 'Thread.sleep\|reader.ready\|synchronized' LuaDebugConnection.kt` → 0 in code (one doc-comment mention);
      the real-process `TestLuaDebugHarness` (rewritten to suspend `send()`) passes on the VM.

## Phase 3 — Controller + consumer integration `[Must]` (MAINT-22-05, -06) — ✅ DONE (2026-07-04)
- [x] Rewrote `LuaDebuggerController` per design §5: `+scope` ctor, `suspend connect()` (**`soTimeout`**, not `withTimeout` —
      a blocking `accept()` isn't cancellation-interruptible), command methods → `suspend`, deleted
      `requests`/`queueRequest`/`queueCommand` + promise-resolution observer code, `scope.cancel()` in `close()`;
      added `launchEvaluate` bridge.
- [x] Edited `LuaDebugProcess` per design §6: `sessionScope` (childScope), `launch{withBackgroundProgress{connect…}}`,
      step/resume/bp launches, removed `registerBreakpoints` busy-wait and all `Thread.sleep(100/1000)`.
- [x] Edited `LuaDebuggerEvaluator`: `.execute(...).then` → `controller.launchEvaluate(..., callback)`; removed unused `XValue`.
- [x] No remaining `org.jetbrains.concurrency.*` / `AsyncPromise` in `run/`.
- **Verified:** `compileKotlin compileTestKotlin` + full `run test` green (regression-relative).

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
