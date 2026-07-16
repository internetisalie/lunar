---
id: "BUG-381"
title: "Ephemeral Redis/Valkey provisioning (Docker / local binary) is fully built but has no UI — unreachable without hand-editing XML"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-381: Ephemeral Redis/Valkey provisioning (Docker / local binary) is fully built but has no UI

## 1. Reproduction

1. Open *Settings → Languages & Frameworks → Lua → Lua Project → Redis Connections*.
2. Add a connection and look for a way to make it launch an **ephemeral Docker** Redis/Valkey
   container (`redis:8` / `valkey/valkey:8`) or a **local `redis-server` / `valkey-server` binary**
   for the run/debug session — i.e. the "dev-container-style deploy → run → tear-down" flow.
3. Also check the **Redis Script** run-configuration editor for a server-source / provisioning
   selector.

Observed: there is **no control anywhere** to choose Docker or local-binary provisioning. Every
connection created through the UI is a plain **Remote** connection; the ephemeral-server capability
can only be enabled by hand-editing `.idea/lunar-redis.xml`.

## 2. Expected vs Actual Behavior

- **Expected**: the Redis Connections form (or the run-config editor) offers a **Server** choice —
  *Remote* / *Local binary (`redis-server`/`valkey-server`)* / *Docker image* — so a user can define
  a connection that spins up a session-scoped server, runs the script/function against it, and tears
  it down on session end. This is the documented REDIS-01 provisioning story.
- **Actual**: the UI only ever produces `Remote` connections; the Docker / local-binary provisioning
  variants are unreachable from any UI.

## 3. Context / Environment

- **Confidence**: high — **root-caused** (found while investigating the REDIS connection model
  2026-07-16).
- **The capability is fully implemented end-to-end — only the UI entry point is missing:**
  - Model: `LuaRedisProvisioning` is a sealed interface with `Remote`, `LocalBinary(toolKindId)`,
    and `Docker(image)` variants —
    [`LuaRedisServerConnection.kt:45`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/connection/LuaRedisServerConnection.kt).
  - Launcher: `LuaRedisServerLauncher` acts on both non-Remote variants —
    `is LuaRedisProvisioning.LocalBinary -> launchBinary(...)` /
    `is LuaRedisProvisioning.Docker -> launchDocker(...)`
    ([`LuaRedisServerLauncher.kt:76`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/connection/LuaRedisServerLauncher.kt); `launchBinary` at :84, `launchDocker` at :99 builds `docker run --rm -d -p <port>:6379 <image>`).
  - Consumers: `LuaRedisRunProfileState` (`redis/run/LuaRedisRunProfileState.kt:135`) and the LDB
    debugger `LuaLdbController` (`redis/debug/LuaLdbController.kt:113`) both read
    `connection.provisioning` and launch/stop the server per session.
  - Persistence: `LuaRedisConnectionSettings.provisioningOf(...)`
    (`redis/connection/LuaRedisConnectionSettings.kt:85`) already round-trips all three variants
    to/from `.idea/lunar-redis.xml`.
  - Tests: `src/redisIntegrationTest/.../RedisIntegrationTest` + `RedisFunctionsIntegrationTest`
    exercise real `redis:8` / `valkey/valkey:8` Docker provisioning.
- **Root cause — the one place `provisioning` is set from user input hardcodes `Remote`:**
  [`LuaRedisConnectionsConfigurable.kt:237`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/connection/LuaRedisConnectionsConfigurable.kt)
  (`LuaRedisConnectionDraft.toConnection()` → `provisioning = LuaRedisProvisioning.Remote`). The
  form (`ConnectionForm`, `LuaRedisConnectionsConfigurable.kt:153`) has no provisioning control, and
  the run-config editor `LuaRedisSettingsEditor`
  (`redis/run/LuaRedisRunConfiguration.kt:272`) only has a `connectionCombo` that *references* an
  existing connection — no server-source selector.

## 4. Fix direction

- Add a **Server / provisioning** control to the Redis Connections form: a combo *Remote / Local
  binary / Docker*, with a conditional field — for Local binary, the tool kind
  (`redis-server` / `valkey-server`, resolved via the toolchain, cf. `LuaToolKindRegistry`); for
  Docker, an image field (default `redis:8` / `valkey/valkey:8`). Thread the chosen
  `LuaRedisProvisioning` through `LuaRedisConnectionDraft` → `toConnection()` instead of the
  hardcoded `Remote`.
- The launcher/persistence/consumers already handle the rest, so this is a UI-only addition plus
  wiring the draft field.
- Verify live via the `verify-in-ide` VNC flow (the builder has Docker per DR-04): create a Docker
  connection, run a script, confirm the container starts, the reply renders, and the container is
  removed on session end.

## 5. Other Notes

- Same "capability exists, no UI" shape as the toolchain **global-bindings** gap and BUG-362's
  platform-target control — a recurring pattern worth watching in the settings surfaces.
- Related to the REDIS connection-definition parity analysis (2026-07-16) and
  **[[../../redis/07-database-datasource-integration/requirements|REDIS-07]]** (reuse a Database
  data source as a connection source): both concern *where a connection's server comes from*. This
  bug is the smaller, self-contained half — surfacing the already-built ephemeral provisioning —
  and does not depend on REDIS-07.
- Not a crash; there is a workaround (hand-edit `.idea/lunar-redis.xml`), hence medium priority — but
  it leaves a documented, tested, differentiating feature effectively invisible to users.
