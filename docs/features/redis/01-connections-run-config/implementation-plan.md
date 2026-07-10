---
id: "REDIS-01-PLAN"
title: "Implementation Plan"
type: "plan"
status: "todo"
parent_id: "REDIS-01"
folders:
  - "[[features/redis/01-connections-run-config/requirements|requirements]]"
---

# REDIS-01: Implementation Plan

Precondition met: [design.md](design.md) has cleared the planning bar (algorithms specified in §3,
parse formats in §4, classes named in §2, plugin.xml in §7, Open Questions empty). Every task names
the class/file it creates or edits and the design section it realizes. New code lives under
`net.internetisalie.lunar.redis` (`src/main/kotlin/net/internetisalie/lunar/redis/`); tests under
`src/test/kotlin/net/internetisalie/lunar/redis/` with the repo's `Test…` prefix convention
(grounded: `src/test/kotlin/net/internetisalie/lunar/run/Test*.kt`).

## Phases

### Phase 1: RESP protocol core (client-independent) [Must]
- **Goal**: Byte-accurate encode/decode + typed model, fully unit-testable with no sockets.
- **Tasks**:
  - [ ] Create `redis/resp/RespValue.kt` (sealed model) — realizes design §2.1.
  - [ ] Create `redis/resp/RespException.kt` — realizes design §2.10.
  - [ ] Create `redis/resp/RespCodec.kt` (`encodeCommand`, `decode(PushbackInputStream)`) —
        realizes design §3.2, §3.3, §3.4.
- **Exit criteria**: `TestRespCodec` green — covers TC-RESP-1..4 (encode array-of-bulk; decode each
  RESP2/RESP3 type; multi-byte UTF-8 bulk byte-length; fragmented/partial stream reassembly).

### Phase 2: RESP client + handshake [Must]
- **Goal**: Live TCP/TLS connection with HELLO/AUTH/SELECT negotiation, timeouts, cancellation.
- **Tasks**:
  - [x] Create `redis/resp/RespClient.kt` (`open`, `command`, `dispose`, `RespProtocol`,
        `RespTimeouts`) — realizes design §2.3, §3.1. Reader on a pooled coroutine; raw
        `InputStream`/`PushbackInputStream` (no `BufferedReader.readLine`); `checkCanceled`/`ensureActive`
        between commands (contract §2, RISK-R09). Endpoint/handshake primitives pass through a Phase-2
        `RespEndpoint`; the `LuaRedisServerConnection` adapter (design §2.4/§2.9) arrives in Phase 3.
        Handshake extracted to `redis/resp/RespHandshake.kt`; cancellation read decorator in
        `redis/resp/CancellationAwareInputStream.kt`.
- **Exit criteria**: unit test `TestRespClient` decodes canned server-byte fixtures through a piped
  socket; integration coverage deferred to Phase 6. No EDT I/O (assert via threading review).
  `TestRespClient` also covers TC-TIMEOUT-1 (`SocketTimeoutException` from the socket →
  `RespException.Timeout`) and TC-CANCEL-1 (a `command()`/connect under a `ProgressIndicator` whose
  `isCanceled == true` aborts in-flight, throwing `ProcessCanceledException`, without completing the
  read) — design §2.3, §6.

### Phase 3: Connection model, storage & credentials [Must]
- **Goal**: Persisted connection list + secret storage + Test Connection.
- **Tasks**:
  - [x] Create `redis/connection/LuaRedisServerConnection.kt` (+ `LuaRedisProvisioning`) — design §2.4.
        Includes the Phase-2-deviation adapter `toEndpoint(password)` → `RespEndpoint` (design §2.3 sync).
  - [x] Create `redis/connection/LuaRedisConnectionSettings.kt` (`@Service` project, `@State`
        `lunar-redis.xml`) — design §2.5; register `<projectService>` (design §7).
  - [x] Create `redis/connection/LuaRedisCredentialStore.kt` (PasswordSafe, subsystem "Lunar Redis")
        — design §2.9 (mirror `rocks/publish/LuaRocksApiKeyStore.kt`).
  - [x] Create `redis/connection/LuaRedisConnectionsConfigurable.kt` + connection-editor UI panel
        (host/port/TLS/auth/db; Test Connection button running §4.3 `INFO`/`HELLO` off-EDT via the
        project coroutine scope + `withBackgroundProgress`) — design §2.5, §4.3; register
        `<projectConfigurable>` (design §7). `INFO` parse extracted to `LuaRedisConnectionProbe.kt`.
- **Exit criteria**: `TestLuaRedisConnectionSettings` round-trips a connection through XML (TC-CONN-1);
  `TestLuaRedisCredentialStore` stores/clears a password (TC-CONN-2). Test Connection verified in
  human checklist. **Status: done** — TC-CONN-1 (5 tests) + TC-CONN-2 (6 tests) + `TestRespServerInfo`
  (4 tests, §4.3 parse) green; full suite (264 test classes) green, ktlintCheck green.

### Phase 4: Server launcher (binary + Docker) [Must]
- **Goal**: Session-scoped local/Docker server lifecycle.
- **Tasks**:
  - [x] Edit `toolchain/registry/LuaToolKindRegistry.kt` — add `redis-server` + `valkey-server`
        `LuaToolKind`s — design §2.9, §4.2.
  - [x] Create `redis/connection/LuaRedisServerLauncher.kt` (`launch`, `LaunchedServer`) — design
        §2.12, §3.9. `GeneralCommandLine`/`OSProcessHandler`, `NetUtils.findAvailableSocketPort`,
        `PathEnvironmentVariableUtil.findInPath("docker")` — the same platform process API the
        existing run configs use (`rocks/run/LuaRocksRunConfiguration.kt:188`).
- **Exit criteria**: `TestLuaRedisServerLauncher` asserts the built command lines (binary + docker)
  and the "neither available" error path (TC-LAUNCH-1..3) without launching processes; real launch
  covered in Phase 6. **Status: done** — TC-LAUNCH-1..3 green; full suite (--rerun-tasks
  --no-build-cache) green; ktlintCheck green.

### Phase 5: Run configuration, executor, producer & console [Must]
- **Goal**: End-to-end "Redis Script" run with reply tree + error links.
- **Tasks**:
  - [ ] Create `redis/run/LuaRedisRunConfiguration.kt` (Type/Factory/Options/config/Editor,
        `LuaRedisExecMode`) — design §2.8, §3.7. Register `<configurationType>` (design §7).
  - [ ] Create `redis/run/LuaRedisRunConfigurationProducer.kt` (target-gated) — design §7; register
        `<runConfigurationProducer>`.
  - [ ] Create `redis/run/LuaRedisScriptExecutor.kt` + `redis/run/LuaRedisScriptShaCache.kt`
        (EVAL/EVALSHA/`_RO`, sha1, NOSCRIPT retry, version gate) — design §3.8.
  - [ ] Create `redis/console/RespReplyTreeConsole.kt` + `RespReplyTreeModel` — design §2.6, §3.5.
  - [ ] Create `redis/console/LuaRedisErrorLinkFilter.kt` — design §2.7, §3.6.
  - [ ] Create `redis/run/LuaRedisRunProfileState.kt` (orchestration, session childScope) — design
        §2.11, §5.
- **Exit criteria**: `TestLuaRedisRunConfiguration` (checkConfiguration + option round-trip, TC-RC-1),
  `TestLuaRedisRunConfigurationProducer` (target-gated true/false, TC-PROD-1),
  `TestRespReplyTreeModel` (shaping, TC-CON-1), `TestRespReplyTreeConsole` (error-class display —
  `showError(RespValue.Error("WRONGTYPE", …))` renders the `WRONGTYPE` class tag, TC-CON-2; design
  §3.4), `TestLuaRedisErrorLinkFilter` (link offset + 1→0 line, TC-CON-3),
  `TestLuaRedisScriptExecutor` (command selection table + NOSCRIPT + version gate,
  TC-SHA-1/TC-RO-1) all green.

### Phase 6: Dockerized integration tests [Must]
- **Goal**: Compatibility contract against real servers, isolated from the default gate (RISK-R10).
- **Tasks**:
  - [ ] Edit `build.gradle.kts` — add a `redisIntegrationTest` task excluded from `build`/`test`,
        failing loudly when Docker is absent — design §7, RISK-R10/DR-04.
  - [ ] Create `RedisIntegrationTest` running EVAL + EVALSHA + `_RO` against dockerized `redis:8`
        and `valkey/valkey:8` (dual-flavor) — covers the epic dual-flavor constraint.
- **Exit criteria**: `redisIntegrationTest` green on a Docker-capable host; fails with a clear
  environment message (not skip) when Docker is unavailable.

## Requirement → Phase Coverage

| Acceptance criterion | Priority | Delivered in |
|----------------------|----------|--------------|
| RESP client (RESP2/RESP3, framing, timeouts, cancellable, no EDT I/O) | M | Phase 1–2 |
| Connection settings UI + PasswordSafe + Test Connection | M | Phase 3 |
| Launch-local (binary + Docker, session-bound, error if neither) | M | Phase 4 |
| `LuaRedisRunConfiguration` (persisted fields + checkConfiguration) | M | Phase 5 |
| Run-config producer (target-gated) | M | Phase 5 |
| Console reply tree + error class | M | Phase 5 |
| Error-link filter (`user_script:N`, 1→0-based) | M | Phase 5 |
| SCRIPT LOAD sha cache + NOSCRIPT fallback | M | Phase 5 |
| Read-only `_RO` + Redis < 7 fail-fast | M | Phase 5 |
| Unit + Docker integration tests | M | Phase 1–6 |

## Verification Tasks
- [ ] `TestRespCodec` — TC-RESP-1..4 (encode, decode-per-type, multi-byte byte-length, partial reads).
- [x] `TestRespClient` — TC-TIMEOUT-1 (`SocketTimeoutException` → `RespException.Timeout`),
      TC-CANCEL-1 (cancelled `ProgressIndicator` aborts the in-flight connect/command).
- [x] `TestLuaRedisConnectionSettings` / `TestLuaRedisCredentialStore` — TC-CONN-1/2.
- [x] `TestLuaRedisServerLauncher` — TC-LAUNCH-1..3 (binary cmd, docker cmd, neither → error).
- [ ] `TestLuaRedisRunConfiguration` / `TestLuaRedisRunConfigurationProducer` — TC-RC-1, TC-PROD-1.
- [ ] `TestLuaRedisScriptExecutor` — TC-SHA-1 (NOSCRIPT retry), TC-RO-1 (version gate).
- [ ] `TestRespReplyTreeModel` / `TestRespReplyTreeConsole` / `TestLuaRedisErrorLinkFilter` —
      TC-CON-1 (tree shaping), TC-CON-2 (error-class console display), TC-CON-3 (error-link offset + 1→0 line).
- [ ] `RedisIntegrationTest` (Phase 6, `redisIntegrationTest` task) — dual-flavor EVAL/EVALSHA/_RO.
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md): Test Connection
      (§1), TLS/AUTH handshake (§2), connect/read timeout + cancellation UX (§3), binary/Docker
      launch + teardown + "neither available" (§4), editor-menu producer + checkConfiguration (§5),
      reply-tree console + error-link click-to-source (§6), SCRIPT LOAD/NOSCRIPT + read-only version
      gate (§7).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: RESP protocol core | done | Must |
| Phase 2: RESP client + handshake | done | Must |
| Phase 3: Connection model, storage & credentials | done | Must |
| Phase 4: Server launcher (binary + Docker) | done | Must |
| Phase 5: Run config, executor, producer & console | todo | Must |
| Phase 6: Dockerized integration tests | todo | Must |
