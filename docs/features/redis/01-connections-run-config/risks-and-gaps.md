---
id: "REDIS-01-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "todo"
parent_id: "REDIS-01"
folders:
  - "[[features/redis/01-connections-run-config/requirements|requirements]]"
---

# REDIS-01: Risks & Gaps

Feature-scoped supplement to the epic register
[redis-risks-and-gaps.md](../redis-risks-and-gaps.md). The epic-level risks that bind REDIS-01
directly are **RISK-R09** (legacy debugger defect patterns), **RISK-R10** (Docker in CI),
**RISK-R11** (RESP scope creep — mitigated by scoping), **RISK-R12** (producer collision —
mitigated by design). This doc records only what is new or sharpened at the feature-design level;
it does not restate the epic register.

## Critical Risks

### Risk 1.1: RESP client shares/copies DBGp framing idioms (epic RISK-R09)
- **Impact**: byte/char confusion and EDT-blocking reads regress into a brand-new client.
- **Likelihood**: medium (path of least resistance is to copy `run/LuaDebugConnection.kt`).
- **Mitigation**: design §2.2/§2.3 mandate a raw `InputStream`/`PushbackInputStream` with explicit
  byte-length reads (§3.3) and **no `BufferedReader.readLine`**; the codec is a standalone,
  socket-free object unit-tested in Phase 1 before any socket exists. No code shared with
  `LuaDebugConnection`. Reviewer uses the review.md debugger checklist as DoD.

### Risk 1.2: HELLO/RESP3 negotiation misclassifies older/managed servers
- **Impact**: a wrong RESP3 assumption corrupts the decode of every subsequent reply.
- **Likelihood**: medium (Redis < 6 and some managed servers reject `HELLO 3`).
- **Mitigation**: §3.1 falls back to RESP2 on any error reply to `HELLO`; Phase 6 dual-flavor
  integration tests exercise both protocol paths; `RespProtocol` is captured per-connection.

### Risk 1.3: Session server not torn down (Docker/binary leak)
- **Impact**: orphaned `redis-server` process or Docker container after a run.
- **Likelihood**: medium.
- **Mitigation**: `LaunchedServer.stop` (§2.12) is idempotent and invoked from both `dispose()` and
  the process-termination listener; the launcher is tied to the session childScope (cancelled on
  teardown). Human checklist verifies teardown.

## Design Gaps

_None open._ Every REDIS-01 design decision is pinned in design.md. The gaps that could have been
open were resolved as follows:

- **Reply-protocol negotiation strategy** — resolved: HELLO-3-then-RESP2-fallback (§3.1), not
  RESP3-only.
- **Secret keying** — resolved: PasswordSafe keyed by connection `id` (§2.9), mirroring
  `LuaRocksApiKeyStore`.
- **Version source for the read-only gate** — resolved: `INFO server` `redis_version`/`valkey_version`
  compared with the grounded `SemanticVersion` model (§3.8), independent of REDIS-03 flavor detection.
- **Valkey provisioning specifics** — deliberately deferred to REDIS-03 (see Technical Debt); the
  `valkey-server` tool kind slot is registered here so `LocalBinary` can target it without rework.

## Technical Debt & Future Work
- **TBD: Valkey-specific provisioning (download URLs, image defaults)** — REDIS-01 registers the
  `valkey-server` kind and accepts `valkey/valkey:8` as a Docker image, but the provisioning
  strategy (`ProvisioningSpec`) for Valkey binaries lands in REDIS-03.
- **TBD: RESP3 push / pub-sub, cluster `MOVED`/`ASK`, Sentinel, SSH tunnels** — epic non-goals
  (epic RISK-R11 / Open Gaps); the client is single-node RESP2-floor by design.
- **TBD: Reply-tree `maxlen` truncation** — LDB `print` truncation is a REDIS-02 concern; REDIS-01
  renders full replies.

## Public Seams for Downstream Features
These are the explicit extension points REDIS-02 (LDB debugger) and REDIS-05 (Functions) build on.
Any change to them is a cross-feature contract change:
- **`RespClient`** (§2.3) — REDIS-02's debug transport opens the same client and issues
  `SCRIPT DEBUG YES|SYNC` + `EVAL` over it; the reader-loop and `command()` seam must remain public
  and coroutine-based.
- **`LuaRedisServerConnection` / `LuaRedisConnectionSettings`** (§2.4/§2.5) — the debug session and
  Functions deploy both resolve a connection by id from this project service.
- **`LuaRedisExecMode.FCALL`** (§2.8) — reserved enum slot; REDIS-05 enables it and adds
  `FUNCTION LOAD` deploy, reusing `LuaRedisScriptExecutor`'s command-selection shape (§3.8).
- **`LuaRedisServerLauncher`** (§2.12) — REDIS-02's "debug against local" escape hatch (epic
  RISK-R02) reuses this launcher.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| REDIS-00-DR-04 | Docker capability on gce-builder + `redisIntegrationTest` isolation (epic DR-04) | Risk 1.x / epic RISK-R10 | DONE (both halves). Isolation half (fe2c02a5): `redisIntegrationTest` is excluded from `build`/`test`/`check` and fails loudly with a clear Docker-unavailable message. Docker-on-builder half: `docker.io` + daemon-enable + `builder` docker-group added to the builder VM and made durable in `tooling/gce-builder/{builder-bootstrap.sh,startup-script.sh}`; `redisIntegrationTest` now runs GREEN on the builder (all 8 dual-flavor tests, `BUILD SUCCESSFUL`). Note: a stale pre-group-change GradleDaemon must be `pkill`ed once after the group is first added so a fresh daemon inherits docker access. |
| REDIS-01-DR-05 | Spike `HELLO 3` handshake + RESP2 fallback against dockerized redis:8 and valkey/valkey:8; confirm §3.1 negotiation and §4.3 `INFO` parsing on both | Risk 1.2 | exercised green locally — `redisIntegrationTest` ran against real `redis:8` + `valkey/valkey:8` on the local Docker host; both `testRedisEvalAndEvalShaAndReadOnly` and `testValkeyEvalAndEvalShaAndReadOnly` PASS. The live run surfaced a real RESP3 bug (`INFO` under `HELLO 3` returns a verbatim `=` string, previously mis-decoded as `Simple`, breaking the read-only version gate); fixed in `RespCodec.decodeVerbatim`. Now also verified GREEN on the builder (DR-04 Docker-on-builder resolved). |

## NEW Symbols Introduced (grounding ledger)
All are net-new to this repo (verified absent via grep); none are ports of EmmyLua/other-plugin APIs:
`net.internetisalie.lunar.redis.resp.{RespValue, RespCodec, RespClient, RespException, RespProtocol,
RespTimeouts}`; `…redis.connection.{LuaRedisServerConnection, LuaRedisProvisioning,
LuaRedisConnectionSettings, LuaRedisCredentialStore, LuaRedisServerLauncher, LaunchedServer,
LuaRedisConnectionsConfigurable}`; `…redis.run.{LuaRedisRunConfiguration(+Type/Factory/Options),
LuaRedisExecMode, LuaRedisRunConfigurationProducer, LuaRedisRunProfileState, LuaRedisScriptExecutor,
LuaRedisScriptShaCache}`; `…redis.console.{RespReplyTreeConsole, RespReplyTreeModel,
LuaRedisErrorLinkFilter}`; plus **[EDIT]** two `LuaToolKind`s (`redis-server`, `valkey-server`) in
`toolchain/registry/LuaToolKindRegistry.kt` and a `redisIntegrationTest` task in `build.gradle.kts`.

## Test Case Gaps
- Live AUTH/TLS handshake is only exercised in Phase 6 (Docker) — no unit coverage for the TLS
  socket path (JSSE); covered by integration + human checklist.

## See Also
- Epic register: [redis-risks-and-gaps.md](../redis-risks-and-gaps.md)
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
