---
id: "MAINT-22-risks"
title: "MAINT-22 Risks & De-risking"
type: "risk"
parent_id: "MAINT-22"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-22 — Risks & De-risking

## De-risking actions (do before setting the dependent phase `planned`)

### MAINT-22-00-DR-01 — Coroutines Gradle wiring `[Must]`
**Unknown:** whether `kotlinx.coroutines` resolves at compile time for `src/main` from the platform artifact alone,
and the exact bundled version if a `compileOnly` is needed.
**Action:** Phase 0 — add the scope service + one import, `gce-builder run build`; if unresolved, add one
`compileOnly(...)` at the platform-bundled version and confirm the plugin `lib/` has no second coroutines jar.
**Resolves:** MAINT-22-01. **Blocking:** yes (all later phases import coroutines).

### MAINT-22-00-DR-02 — Fake-socket test harness for the transport `[Should]`
**Unknown:** can the request/response + running state machine be unit-tested without a real Lua debuggee?
**Action:** prototype a loopback `Socket` pair (or piped reader/writer) feeding scripted DBGp lines to
`LuaDebugConnection`, asserting deferred completion and observer callbacks. Confirm `runTest`/`runBlocking` drives the
suspend `send()` deterministically.
**Resolves:** MAINT-22-03/-04 test coverage. **Blocking:** no (Phase 2 can proceed; live VNC is the ultimate gate).

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|:---------:|:------:|------------|
| R1 | **Double-bundled coroutines** → `LinkageError` at runtime | Med | High | DR-01; `compileOnly` only; assert `lib/` has one coroutines jar. |
| R2 | **State-machine regression** — the `running`/out-of-band interleaving is subtle; the debugger has no end-to-end unit test today | Med | High | Reproduce `receive()` branches line-for-line (design §4.1); DR-02 fake-socket tests; **mandatory live VNC** (MAINT-22-08). |
| R3 | **XDebugSession thread-affinity** — `breakpointReached`/`positionReached`/`rebuildViews` called from a coroutine thread | Med | Med | Wrap UI-affine calls in `withContext(Dispatchers.EDT)` if the platform asserts EDT; verify live + check `idea.log` for TWA/EDT assertions. |
| R4 | **Lost breakpoints** — removing the `registerBreakpoints` busy-wait could reorder SETB vs connect | Low | High | Drain pending breakpoints *inside* the connect launch, after `isReady`, before `resume()`; test-case #6 sets a breakpoint pre-launch. |
| R5 | **Scope leak** — session scope not cancelled → reader coroutine survives | Low | Med | `scope.cancel()` in `close()`; MAINT-22-05 test asserts single `readLoop exiting`; check for lingering thread live. |
| R6 | **`withTimeout` too tight/loose** vs the old `100ms×50` (5s) accept budget | Low | Low | Set `CONNECT_TIMEOUT_MS = 5_000` to match the prior ~5s ceiling; surface timeout via the existing error dialog. |
| R7 | **Preemption during long `run build`** on gce-builder spot VM | Med | Low | Per CLAUDE.md/AGENTS.md: re-create VM or `PROVISIONING_MODEL=ON_DEMAND`; don't run two `run`s concurrently. |

## Investigated & dismissed (not work items)
- **Luacheck on EDT** — `LuaCheckInvoker.invoke` runs only via `LuaCheckAnnotator.doAnnotate`, which the platform
  calls off-EDT by contract. Blocking `waitFor()` is correct. No change.
- **`LuaRockspecDiscoveryService.executeSynchronously()`** — synchronous read feeding a non-suspend
  `CachedValueProvider`; not a fire-and-forget prime. Converting requires restructuring the CachedValue for no gain.
  Retained (design §7).
- **`Task.Backgroundable` ×12 / UI fetch panels / `Alarm` debounce** — supported and correct; opportunistic only
  (MAINT-22-09 backlog).
