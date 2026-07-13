---
id: "REDIS-02-PLAN"
title: "Implementation Plan"
type: "plan"
status: "todo"
parent_id: "REDIS-02"
folders:
  - "[[features/redis/02-ldb-debugger/requirements|requirements]]"
---

# REDIS-02: Implementation Plan

Sequences [design.md](design.md) into shippable, verifiable phases. **Precondition**: REDIS-01 is
`planned` and its `RespClient`/connection/launcher/run-config seams exist; REDIS-01 Phase 2 (RESP
client) and Phase 5 (run config) must be **implemented** before REDIS-02 Phase 3 starts. The two
seam amendments in design §11 (A1 `RespClient.readReply`, A2 `debugMode` option) are prerequisites
tracked as de-risking tasks (risks-and-gaps DR-06) and should land in REDIS-01 or as the first commit
of REDIS-02 Phase 3.

Every task names the class/file it creates and the design section it realizes. Package:
`net.internetisalie.lunar.redis.debug`.

## Phases

### Phase 1: LDB wire vocabulary + parsers (transport-independent) [Must]
- **Goal**: Pure, socket-free encode/parse of the LDB protocol — the highest-risk surface (RISK-R01)
  pinned by unit tests before any session exists (mirrors REDIS-01 Phase 1 sequencing).
- **Tasks**:
  - [x] Create `LdbCommand` (sealed) + `LuaRedisDebugMode` enum + `LdbWire.encode` — realizes design §2.8, §3.2.
  - [x] Create `LdbEvent` (sealed), `StopReason`/`LdbErrorKind`/`EndReason` enums, `LdbReplyParser.parse` — realizes §2.9, §3.3.
  - [x] Create `LuaLdbLocal`, `LdbValueNode` (sealed), `LdbPrintParser.parseLocals`/`parseValue` — realizes §2.9, §3.4.
  - [x] Create `LdbSessionMachine` (states + `onCommandSent`/`onEvent`) — realizes §2.11, §3.5.
- **Exit criteria**: unit tests TC-LDB-ENC-1, TC-LDB-ENC-2, TC-LDB-DEC-1..3, TC-LDB-PRINT-1,
  TC-LDB-PRINT-2, TC-LDB-SM-1, TC-LDB-SM-2, TC-LDB-SYNC-2 green; no socket/EDT dependency; no `!!`.

### Phase 2: Breakpoint type, editors, evaluator, value/stack scaffolding [Must]
- **Goal**: The XDebugger-facing structural classes (no live session yet), reusing `run/` shapes.
- **Tasks**:
  - [x] Create `LuaLdbBreakpointType` (id `"redis-lua-line"`, `canPutAt` without `!!`) — realizes §2.5, §3.9.
  - [x] Create `LuaLdbBreakpointHandler` — realizes §2.4.
  - [x] Create `LuaLdbValue : XNamedValue`, `LuaLdbStackFrame : XStackFrame`,
        `LuaLdbExecutionStack : XExecutionStack`, `LuaLdbSuspendContext : XSuspendContext` — realizes §2.12.
  - [x] Create `LuaLdbEvaluator : XDebuggerEvaluator` (reuse `run/LuaDebuggerEvaluator` expr-range algo) — realizes §2.6.
  - [x] Register `<xdebugger.breakpointType>` in `plugin.xml` (design §7).
- **Exit criteria**: TC-LDB-BP-1 green (canPutAt statement vs comment); classes compile against the
  grounded `intellij.platform.debugger.jar` supertypes; `plugin.xml` validates.

### Phase 3: Transport + controller + session lifecycle [Must]
- **Goal**: A live LDB session over the REDIS-01 `RespClient`: handshake, EVAL, pause/step/resume.
- **Tasks**:
  - [x] (Prereq) Land REDIS-01 seam amendments A1/A2 (design §11) — `RespClient.readReply`, options `debugMode`.
  - [x] Create `LuaLdbTransport` (wraps `RespClient`; `enterDebug`/`eval`/`send`) — realizes §2.10, §3.1.
  - [x] Create `LuaLdbController` (connect flow, command methods, pause raising, error/lifecycle) — realizes §2.3, §3.5, §3.6.
  - [x] Create `LuaRedisDebugProcess : XDebugProcess` (step mapping, Step Out no-op, breakpoint delegation) — realizes §2.2.
  - [x] Create `LuaRedisDebugRunner : GenericProgramRunner` — realizes §2.1.
  - [x] Register `<programRunner>` in `plugin.xml` (design §7).
- **Exit criteria**: a debug session against a local `RespClient` fake (scripted reply blocks) drives
  HANDSHAKE→ARMED→PAUSED→RUNNING→TERMINATED; error paths call `reportError`/`errorOccurred`
  (TC-LDB-ERR-1, TC-LDB-ERR-2 with a fake transport). Build green.

### Phase 4: Conditional breakpoints, sync guard, Redis console tab [Must]
- **Goal**: The remaining Must behaviors on top of a working session.
- **Tasks**:
  - [x] Implement the conditional-breakpoint gate in `LuaLdbController.onPause` — realizes §3.7.
  - [x] Create `LuaLdbSyncGuard` (`requiresConfirmation`/`bannerText`/`confirm`) + wire into `connect()` — realizes §2.13, §3.8.
  - [x] Create `LuaLdbRedisConsoleTab` (reuses REDIS-01 `RespReplyTreeConsole`) + add the tab in
        `LuaRedisDebugProcess.createConsole` via `RunnerLayoutUi` — realizes §2.7.
  - [x] Banner text emitted on session start via `session.reportMessage` — realizes §3.6/§2.13.
- **Exit criteria**: TC-LDB-COND-1, TC-LDB-SYNC-1 green (unit-level: condition gate + sync-confirmation
  decision); console-tab and banner behaviors covered by human checklist §3/§4.

### Phase 5: Dual-flavor integration tests [Must]
- **Goal**: The compatibility contract against real servers (epic RISK-R01/R10).
- **Tasks**:
  - [x] Create `RedisDebugIntegrationTest` under the REDIS-01 `redisIntegrationTest` Gradle task
        (REDIS-01 impl-plan Phase 6 / DR-04) — parameterized over `redis:8` and `valkey/valkey:8`.
  - [x] Implement TC-INT-1 (break/step/print/continue), TC-INT-2 (mid-pause `redis`), TC-INT-3
        (forked abort rollback + forked-timeout end).
  - [x] Confirm observed LDB framing/session-end on both flavors matches §3.3/§3.4; record any
        divergence back into design §3.3 (DR-06 exit). **Divergence found + fixed**: both servers end
        the session with a `["<endsession>"]` block (never the assumed `"* Lua debugging session
        ended"`) and emit the real `EVAL` result / abort error as a separate trailing block;
        `LdbReplyParser` was hardened to recognize `<endsession>` (risks Risk 2.1 / DR-01).
- **Exit criteria**: `redisIntegrationTest` green on a Docker-capable host for both flavors; fails
  loudly (not skips) when Docker is absent (epic RISK-R10). **Met**: 6 debug + 2 REDIS-01 tests, 0
  failures, on `redis:8` and `valkey/valkey:8`.

## Requirement → Phase Coverage

| Acceptance criterion | Priority | Delivered in |
|----------------------|----------|--------------|
| AC-1 Debug executor starts LDB session | M | Phase 1 (wire/machine), Phase 3 (live), Phase 5 (int) |
| AC-2 Line breakpoints | M | Phase 1 (encode), Phase 2 (type/handler), Phase 3 (drain) |
| AC-3 Conditional breakpoints | M | Phase 4 |
| AC-4 Step/Resume/Stop/Rerun; Step Out disabled | M | Phase 1 (machine), Phase 3 (mapping) |
| AC-5 Variables from `print`; watches/Evaluate | M | Phase 1 (print parse), Phase 2 (value/frame/evaluator) |
| AC-6 Redis console tab | M | Phase 4, Phase 5 (TC-INT-2) |
| AC-7 Error surfacing | M | Phase 1 (parse), Phase 3 (controller reportError) |
| AC-8 Sync-mode guarding | M | Phase 4 |
| AC-9 Forked banner + server-timeout | M | Phase 1 (SessionEnded), Phase 4 (banner), Phase 5 (TC-INT-3) |
| AC-10 Dual-flavor integration tests | M | Phase 5 |

## Verification Tasks
- [x] `TestLdbWire` — TC-LDB-ENC-1, TC-LDB-ENC-2, TC-LDB-SYNC-2 (encode mapping).
- [x] `TestLdbReplyParser` — TC-LDB-DEC-1, TC-LDB-DEC-2, TC-LDB-DEC-3.
- [x] `TestLdbPrintParser` — TC-LDB-PRINT-1, TC-LDB-PRINT-2 (incl. truncation, no `!!`).
- [x] `TestLdbSessionMachine` — TC-LDB-SM-1, TC-LDB-SM-2.
- [x] `TestLuaLdbBreakpointType` — TC-LDB-BP-1 (statement vs comment; extends `BasePlatformTestCase`,
      `myFixture.configureByText` per contract §5).
- [x] `TestLuaLdbController` (fake transport) — TC-LDB-ERR-1, TC-LDB-ERR-2, TC-LDB-STEPOUT-1, TC-LDB-COND-1 (condition gate: false-resumes / true-pauses).
- [x] `TestLuaLdbSyncGuard` — TC-LDB-SYNC-1.
- [x] `RedisDebugIntegrationTest` (`redisIntegrationTest` task) — TC-INT-1, TC-INT-2, TC-INT-3.
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md) §1–§5.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: LDB wire + parsers | done | Must |
| Phase 2: Breakpoint type / structural XDebugger classes | done | Must |
| Phase 3: Transport + controller + session lifecycle | done | Must |
| Phase 4: Conditional BPs + sync guard + Redis tab | done | Must |
| Phase 5: Dual-flavor integration tests | done | Must |

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
</content>
