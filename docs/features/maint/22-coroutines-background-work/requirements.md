---
id: "MAINT-22"
title: "MAINT-22: Adopt Kotlin Coroutines for Background Work (debugger pilot)"
type: "feature"
status: "done"
priority: "medium"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-22: Adopt Kotlin Coroutines for Background Work (debugger pilot)

## Overview

The plugin has **no coroutine infrastructure** in `src/main` today; all concurrency is ad-hoc —
`executeOnPooledThread`, `Task.Backgroundable`, `org.jetbrains.concurrency` promises, manual
`Thread`/`synchronized`, and `Thread.sleep` poll loops. Platform build **261 (GoLand 2026.1.3)**
ships first-class structured concurrency (service `CoroutineScope` injection, `readAction {}`,
`Dispatchers.EDT`, `childScope`), now JetBrains' preferred idiom.

This feature introduces the minimum coroutine **infrastructure** and applies it to the highest-payoff,
most self-contained target — the **DBGp debugger socket loop** ([`run/`](../../../../src/main/kotlin/net/internetisalie/lunar/run/)),
whose `Thread.sleep(50)` polling, `AsyncPromise` chains, and manually-spawned pooled thread are the
least defensible async code in the repo. It also converts one clean fire-and-forget PSI-read prime.
It is deliberately **not** a repo-wide migration — `Task.Backgroundable` sites and UI fetch panels
are left as documented opportunistic work.

Parent epic: [[features/maint/requirements|MAINT]].

## Scope

### In Scope
- Coroutine **build wiring** (use the platform-bundled `kotlinx-coroutines`; never bundle a second copy).
- A project-level `CoroutineScope` service (`LunarCoroutineScopeService`) for structured, lifecycle-bound background work.
- Rewrite of the debugger transport ([`LuaDebugConnection`](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaDebugConnection.kt))
  from a single `Thread.sleep`-polled `run()` loop to a **reader coroutine** + `Channel`/`CompletableDeferred`
  + `Mutex`, eliminating all `Thread.sleep`.
- Rewrite of [`LuaDebuggerController`](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaDebuggerController.kt):
  `waitForConnect()` → `suspend fun connect()` with `withTimeout`; delete the `requests: Map<DebugCommand, AsyncPromise>`
  correlation map; own a **session-scoped** child scope cancelled on disconnect.
- Integration edits in [`LuaDebugProcess`](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaDebugProcess.kt)
  and [`LuaDebuggerEvaluator`](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaDebuggerEvaluator.kt)
  to consume the new suspend/scope API (bridging XDebugger's callback APIs inside launched coroutines).
- Conversion of the fire-and-forget cache prime in
  [`RockspecSourcePathProvider`](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/RockspecSourcePathProvider.kt)
  from `ReadAction.nonBlocking(...).submit(...)` to `scope.launch { readAction { … } }`.
- Behavior preservation, proven by **live VNC verification** of the full debug flow (the DoD gate;
  unit tests cannot exercise the socket protocol end-to-end).

### Out of Scope
- The **12 `Task.Backgroundable`** sites (build/provision/matrix/publish, etc.) — supported and correct;
  convert opportunistically. Guidance captured in MAINT-22-07.
- Fire-and-forget UI **fetch-then-`invokeLater`** panels (browser/detail/config) — cosmetic; opportunistic.
- The `Alarm(POOLED_THREAD)` search debounce in the package browser.
- Luacheck: **investigated and dismissed** — [`LuaCheckInvoker.invoke`](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/luacheck/LuaCheckInvoker.kt)
  runs only from [`LuaCheckAnnotator.doAnnotate`](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/luacheck/LuaCheckAnnotator.kt:30),
  which the platform invokes **off the EDT** by contract. The blocking `waitFor()` is correct; no change.
- The **synchronous** `ReadAction.nonBlocking(...).executeSynchronously()` in
  [`LuaRockspecDiscoveryService.compute()`](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/LuaRockspecDiscoveryService.kt:72)
  — it returns a value into a non-`suspend` `CachedValueProvider`; converting it needs restructuring the
  CachedValue and buys nothing. Documented as intentionally retained (see design §7).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-22-01 | **Coroutine build wiring** | M | `kotlinx.coroutines` symbols compile against the platform-bundled library with **no** runtime double-bundle; verified by a clean `run build`. |
| MAINT-22-02 | **Project scope service** | M | `LunarCoroutineScopeService` (project-level `@Service`) exposes a lifecycle-bound `CoroutineScope`; cancelled automatically on project close. |
| MAINT-22-03 | **Poll-free transport** | M | `LuaDebugConnection` contains **zero** `Thread.sleep` and no `reader.ready()` polling; incoming lines are consumed by a blocking read on a `Dispatchers.IO` reader coroutine. |
| MAINT-22-04 | **Deferred-based command correlation** | M | Request/response uses `CompletableDeferred<String>` + `Mutex`; the `AsyncPromise` map and `synchronized(this)`/`synchronized(requests)` blocks are removed. |
| MAINT-22-05 | **Session-scoped lifecycle** | M | The debugger owns a `childScope` cancelled in `close()`; no pooled thread or reader coroutine survives session termination (no leak). |
| MAINT-22-06 | **Suspend connect** | S | `connect()` is `suspend`, binds/accepts on `Dispatchers.IO`, and uses `withTimeout` instead of the `accept()` + `Thread.sleep(100)×50` loop and the `Thread.sleep(1000)` post-connect wait. |
| MAINT-22-07 | **Rockspec prime conversion** | S | `RockspecSourcePathProvider` background prime uses `scope.launch { readAction { … } }`; behavior (empty-then-primed cache) unchanged. |
| MAINT-22-08 | **Behavior preserved (live)** | M | Set/hit line breakpoint, step in/over/out, resume, evaluate expression, inspect variables, and terminate all work identically in GoLand over VNC. |
| MAINT-22-09 | **Convention docs** | C | `docs/engineering-contract.md` gains a short "Coroutines & structured concurrency" section for future opportunistic migration. |

## Detailed Specifications

### MAINT-22-03 / -04: Transport rewrite
The DBGp protocol keeps **at most one command in flight** and has a distinct **running** phase (after a
Run-group command the debuggee runs, then emits an out-of-band `202/203` pause or `401` error). The rewrite
must preserve this state machine exactly (see design §4): the reader coroutine is the sole owner of the
`BufferedReader`; `send()` publishes a `CompletableDeferred` under a `Mutex`; the reader completes it, sets
`running` for Run-group commands, and dispatches out-of-band pause/error lines to the observer.

### MAINT-22-06: Suspend connect
`withTimeout(CONNECT_TIMEOUT_MS)` around `ServerSocket.accept()` on `Dispatchers.IO` replaces the retry loop;
`setBaseDir()` is awaited (suspends), so the empirical `Thread.sleep(1000)` "settle" delay is deleted.

### MAINT-22-07: Rockspec prime
Only the `app.isDispatchThread && !app.isUnitTestMode` branch changes: instead of
`ReadAction.nonBlocking { … }.submit(AppExecutorUtil.getAppExecutorService())`, schedule
`LunarCoroutineScopeService.getInstance(project).scope.launch { readAction { forceRefreshTracker.incModificationCount(); cache.value } }`.
The returned `Result` (empty patterns + `forceRefreshTracker`) is unchanged.

## Behavior Rules
- No API that XDebugger calls (`startStepOver`, `evaluate`, breakpoint handlers) may become `suspend` —
  those platform overrides are non-suspend; they must **launch** onto the session scope and bridge back
  through the platform's callbacks (`XEvaluationCallback`, `session.breakpointReached`).
- Cancellation is cooperative: closing the session cancels the scope, which cancels the reader coroutine
  and fails all outstanding `CompletableDeferred`s (replacing the manual `setError("…closed")` sweep).
- Read actions inside coroutines use suspend `readAction {}` (or retain `runReadAction` where already
  correct) — never a raw read on the EDT.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-22-01 | Clean checkout with the new deps | `gce-builder run build` | BUILD SUCCESSFUL; no `kotlinx-coroutines` in the plugin distribution's `lib/` beyond the platform's. |
| 2 | MAINT-22-02 | Open a project | `LunarCoroutineScopeService.getInstance(project)` | Returns a service whose scope is active; becomes cancelled after project close. |
| 3 | MAINT-22-03 | Rewritten `LuaDebugConnection.kt` | `grep -c 'Thread.sleep\|reader.ready' file` | `0`. |
| 4 | MAINT-22-04 | Rewritten `run/` | `grep -rc 'AsyncPromise\|synchronized' run/` | `0` in the transport/controller (promise-map correlation removed). |
| 5 | MAINT-22-05 | A debug session running | Terminate the debuggee | Reader coroutine + server socket closed; no lingering thread (verified via `jstack`/log "run() exiting" once). |
| 6 | MAINT-22-08 | `test/` sample script with a breakpoint on line N | Launch debug, hit breakpoint, `Step Over`, evaluate `1+1`, open Variables, Resume, Stop | Breakpoint pauses at N; step advances one line; evaluate shows `2`; Variables lists locals; resume runs to completion; stop terminates cleanly (VNC screenshots). |
| 7 | MAINT-22-07 | Project with rockspecs, cache cold, call on EDT | `derivedPatterns()` | First call returns empty; a later call (after the launched prime) returns the real derived patterns. |

## Acceptance Criteria
- [x] MAINT-22-01…-07 satisfied with unit tests + `grep` assertions green.
- [x] MAINT-22-08 verified live in GoLand 2026.1.3 over VNC (full debug flow: connect → breakpoint → variables → evaluate `x+y`=30 → step over → resume → clean terminate).
- [x] Full suite green via `gce-builder run test` (incl. real-mobdebug `TestLuaDebugHarness`).
- [x] `ktlintCheck` clean on all touched files.
- [x] Lifecycle: exactly one `readLoop exiting` per session (no leak); no EDT/threading assertions during debug (R3 closed).

## Non-Functional Requirements
- **Threading:** all socket I/O on `Dispatchers.IO`; no EDT blocking; PSI reads via `readAction {}`/`runReadAction`.
- **Memory:** the controller must not retain `Project`/`Editor`/`PsiFile` beyond the session; the scope is a
  `childScope` of the project service scope and is cancelled on `close()`.
- **No double-bundle:** exactly one `kotlinx-coroutines` on the runtime classpath (the platform's).

## Dependencies
- Platform build **261** (present) for service scope injection, `readAction`, `Dispatchers.EDT`, `childScope`.
- No dependency on MAINT-21 (IJPGP bump) — independent.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Verification: [human-verification-checklists.md](human-verification-checklists.md)
