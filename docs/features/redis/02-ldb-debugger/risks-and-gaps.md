---
id: "REDIS-02-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "todo"
parent_id: "REDIS-02"
folders:
  - "[[features/redis/02-ldb-debugger/requirements|requirements]]"
---

# REDIS-02: Risks & Gaps

Feature-scoped supplement to the epic register [redis-risks-and-gaps.md](../redis-risks-and-gaps.md).
The epic-level risks that bind REDIS-02 directly are **RISK-R01** (LDB protocol is
implementation-defined), **RISK-R02** (managed services block `SCRIPT DEBUG`), **RISK-R03** (sync
mode freezes the server), **RISK-R05** (LDB functional limitations), **RISK-R09** (legacy debugger
defect patterns), and **RISK-R10** (Docker in CI). This doc records only what is new or sharpened at
the feature-design level; it does not restate the epic register.

## Critical Risks

### Risk 2.1: LDB reply framing differs from the design's parse (epic RISK-R01)
- **Impact**: `LdbReplyParser`/`LdbPrintParser` (design §3.3/§3.4) mis-parse real stop/print/session-end
  blocks, so pauses, locals, or session-end detection break on a real server.
- **Likelihood**: medium (the wire is defined by `redis-cli --ldb`, not a spec; Redis vs Valkey and
  version differences are plausible).
- **Mitigation**: the parsers are pure and unit-pinned in Phase 1 against the documented shapes; Phase 5
  dual-flavor integration tests (TC-INT-1..3) are the compatibility contract and MUST run on both
  `redis:8` and `valkey/valkey:8`; DR-06 spikes the live handshake before Phase 3 to confirm §3.3/§3.4
  and fold any divergence back into the design. The parsers degrade to `LdbEvent.Ack` on unrecognized
  blocks (never crash), so an unexpected line cannot hang or fatal-error the IDE.

### Risk 2.2: Copying MobDebug defect patterns into the new adapter (epic RISK-R09)
- **Impact**: byte/char framing confusion, EDT-blocking reads, unsynchronized cross-thread state, `!!`
  in parsing, silent error swallowing — the documented MobDebug cluster — regress into REDIS-02.
- **Likelihood**: medium (the `run/` classes are the structural template and one of them,
  `LuaLineBreakpointType.kt:81`, still uses `result.get()!!`).
- **Mitigation**: REDIS-02 shares **no** transport code with `LuaDebugConnection`; it uses the
  REDIS-01 `RespClient` (already hardened: raw byte framing, explicit UTF-8, cancellable). The
  `LdbSessionMachine` is single-threaded per session (design §2.11) — no cross-thread mutable state.
  Design §3.9 explicitly replaces the `result.get()!!` with an Elvis/`== true`. Reviewer uses the
  review.md debugger checklist as the DoD gate (AC "engineering-contract compliance verified in review").

### Risk 2.3: Sync-mode debugging blocks a shared/staging server (epic RISK-R03)
- **Impact**: external, outage-shaped harm (not just IDE misbehavior) if a developer pauses a
  `SCRIPT DEBUG SYNC` session against a shared server.
- **Likelihood**: medium (someone will try it).
- **Mitigation**: fork is the default (design §2.8/§3.8); `LuaLdbSyncGuard.requiresConfirmation`
  forces a confirmation dialog for sync-on-remote (TC-LDB-SYNC-1); the banner states the consequence
  (design §2.13). The optional hard gate `redis.debug.allowSyncOnRemote=false` (Gap 2.1) makes remote
  sync impossible without an explicit settings change.

### Risk 2.4: Rerun/restart leaves an orphaned forked session or client (epic RISK-R09 lifecycle)
- **Impact**: a leaked `RespClient`/launched local server after Rerun or an abrupt stop.
- **Likelihood**: medium.
- **Mitigation**: design §6 chooses a fresh `EVAL` per session (no LDB `restart`); teardown (design
  §3.6) disposes the transport (`RespClient.dispose`) and cancels the session scope; the REDIS-01
  `LaunchedServer.stop` [REDIS-01 §2.12] is idempotent and runs on dispose. Human checklist §5
  verifies no orphaned process/container after abort and rerun.

## Design Gaps

### Gap 2.1: `redis.debug.allowSyncOnRemote` hard gate — Should, not Must
- **Question**: should remote sync be *refused* by default (hard gate) or only *confirmed*?
- **Options / leaning**: ship the confirmation dialog as the Must (AC-8); add the
  `redis.debug.allowSyncOnRemote` application setting (default false → refuse) as a Should in Phase 4.
  Leaning: implement the setting but default it to **confirm** for the first release to avoid blocking
  a legitimate local-but-remote-shaped workflow, and revisit after telemetry.
- **Resolved by**: DR-07 (below); fold the final default into design §3.8 before setting `planned` on
  the sync-hard-gate task. The confirmation path (Must) is already fully specified and unblocked.

### Gap 2.2: One-click "switch to local" affordance for a `SCRIPT DEBUG`-refusing server (epic RISK-R02)
- **Question**: when a managed server rejects `SCRIPT DEBUG`, the design surfaces an actionable error
  (§4.2). Should there be a one-click action that creates/selects a launch-local connection?
- **Options / leaning**: for the Must, the actionable **message** (with the launch-local hint) is
  sufficient (AC-7). The one-click switch is a UX nicety deferred to a follow-up.
- **Resolved by**: deferred (Technical Debt below); tracked against epic RISK-R02. Not a bar blocker —
  the error message unblocks the user manually.

## Technical Debt & Future Work
- **TBD: one-click "debug against local" switch** (Gap 2.2) — the actionable error ships in REDIS-02;
  the one-click connection switch is future UX.
- **TBD: debugging `FCALL` (Functions)** — LDB cannot debug Function invocations (epic RISK-R05); the
  Debug executor stays disabled for `FCALL` mode (REDIS-01's `execMode == FCALL` is already rejected
  in `checkConfiguration`). Revisit if upstream adds FCALL debugging.
- **TBD: watch expressions persistence / multi-frame stacks** — LDB exposes a single active frame
  (design §2.12); a richer call stack is out of scope until LDB exposes one.
- **TBD: `maxlen` as a debugger setting** (epic RISK-R05) — the truncation marker is shown (design
  §3.4); mapping LDB `maxlen` to a user setting is future work.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| REDIS-00-DR-01 | LDB handshake spike vs dockerized redis:8 + valkey:8 (fork + sync); confirm §3.3/§3.4 framing + session-end lines; record divergence in design | Risk 2.1 / epic RISK-R01 | todo |
| REDIS-02-DR-06 | Land + verify REDIS-01 seam amendments A1 (`RespClient.readReply`) and A2 (`debugMode` option) before Phase 3; or confirm the `whole` fallback (design §11) suffices | design §11 | todo |
| REDIS-02-DR-07 | Decide `redis.debug.allowSyncOnRemote` default (refuse vs confirm) | Gap 2.1 / epic RISK-R03 | todo |

## Required REDIS-01 Seam Amendments (summary; full detail in design §11)
- **A1**: add `suspend fun readReply(): RespValue` to `RespClient` [REDIS-01 §2.3] (additive; the read
  half of `command`). Non-blocking on REDIS-01 if declined — `whole` fallback documented.
- **A2**: add `debugMode` (`string("FORKED")`) to `LuaRedisRunConfigurationOptions` [REDIS-01 §2.8] +
  a `var debugMode: LuaRedisDebugMode` bridge and a settings-editor control (additive; Run ignores it).

## Test Case Gaps
- Interactive UI behaviors (breakpoint-hit gutter/frame visuals, Variables/Watch/Evaluate panels,
  sync confirmation dialog + banners, mid-pause Redis tab, fork-vs-sync lifecycle) are not
  unit-testable; covered by [human-verification-checklists.md](human-verification-checklists.md) §1–§5
  and cross-referenced from the requirements AC→TC matrix.
- The live `SCRIPT DEBUG`-refused path (managed server, epic RISK-R02) has no dockerized equivalent
  (local servers permit it); covered by design §4.2 + human checklist §3 (manual, optional against a
  managed instance).

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Epic register: [../redis-risks-and-gaps.md](../redis-risks-and-gaps.md)
- REDIS-01 risks (seams): [../01-connections-run-config/risks-and-gaps.md](../01-connections-run-config/risks-and-gaps.md)
</content>
