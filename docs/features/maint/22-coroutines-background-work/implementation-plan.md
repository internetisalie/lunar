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

## Phase 4 — Rockspec prime `[Should]` (MAINT-22-07) — ✅ DONE (2026-07-04)
- [x] Converted the EDT-branch prime in `RockspecSourcePathProvider` to
      `LunarCoroutineScopeService.getInstance(project).scope.launch { readAction { … } }`; dropped
      `AppExecutorUtil`/`ReadAction` imports; added the retention comment on `LuaRockspecDiscoveryService.compute()`.
- [x] Existing `*Rockspec*` suite covers the empty-then-primed cache behavior (unchanged `Result`).
- **Verified:** `gce-builder run "test --tests *Rockspec* --tests *RockspecSourcePath*"` → BUILD SUCCESSFUL.

## Phase 5 — Live verification `[Must]` (MAINT-22-08) — ✅ DONE (2026-07-04)
- [x] Drove GoLand 2026.1.3 over VNC (native gce-builder host): provisioned a PUC 5.1 env (+luasocket), registered
      the interpreter, created a Lua debug config, set a line breakpoint, ran the full flow.
- [x] Full debug flow PASSED: connect → pause at line 8 (Locals `x=10 y=20 add`) → evaluate `x+y`=**30** →
      step over (→ line 9, `total=30`) → resume (`total = 30`, exit 0) → clean terminate.
- [x] `idea.log`: exactly **one** `readLoop exiting` (no leak); **zero** EDT/threading assertions during the debug
      session. (Pre-existing, out-of-scope `lua -v`/`luarocks --version`-on-EDT SEVERE during env detect/bind is
      flagged as a separate follow-up task — not the debugger.)
- **Verified:** all `human-verification-checklists.md` debug-flow rows PASS.

## Phase 6 — Conventions doc `[Could]` (MAINT-22-09) — ✅ DONE (2026-07-04)
- [x] Extended `docs/engineering-contract.md` §2 "FUNCTIONS, ACTIONS & COROUTINES" with a concrete conventions bullet:
      `LunarCoroutineScopeService` + `childScope`, `readAction {}`/`Dispatchers.EDT`/`withBackgroundProgress`, the
      DBGp transport as the reference impl, the no-double-bundle rule, and the opportunistic-migration backlog.
- **Verified:** `python3 scripts/lint_docs.py docs` clean.

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
