---
id: "REDIS-03"
parent_id: "REDIS"
type: "feature"
status: "done"
folders:
  - "[[features/redis/requirements|requirements]]"
title: "REDIS-03: Valkey Runtime Target"
---

# REDIS-03: Valkey Runtime Target

**Requirement**: Valkey as a first-class runtime target: platform registry entries, stdlib
stubs including the `server.*` namespace, flavor detection on connect, and a portability
inspection.
**Priority**: Should
**Status**: Implemented

---

## Overview

Valkey (the Linux Foundation Redis fork) retains full Redis 7.2 Lua script compatibility
and adds a `server` alias namespace (`server.call`, `server.pcall`, …) plus the
`SERVER_NAME`, `SERVER_VERSION`, and `SERVER_VERSION_NUM` globals
([Valkey Lua API](https://valkey.io/topics/lua-api/), [migration notes](https://valkey.io/topics/migration/)).
The TARGET epic's `PlatformVersionRegistry` was designed for exactly this kind of addition.

Scripts written against `server.*` are not portable to Redis; scripts written against
`redis.*` run on both. An inspection makes that visible at edit time.

### Target mapping additions

| Platform | Version | Implicit Language Level | Library Root |
| :--- | :--- | :--- | :--- |
| **Valkey** | 7.2 | Lua 5.1 | `runtime/valkey/valkey-7.2/` |
| **Valkey** | 8   | Lua 5.1 | `runtime/valkey/valkey-8/` |

Stub layout: the Redis 7 stdlib stubs are the shared base; the Valkey roots add the
`server.*` namespace mirror and the `SERVER_*` globals (no duplication of shared API docs —
the Valkey stubs `require`/alias the shared definitions where the stub format allows).

## Acceptance Criteria

- [x] **AC-1** — `PlatformVersionRegistry` gains a `VALKEY` platform with versions `7.2` and
      `8`, implicit language level Lua 5.1, and luacheck std mapping (reuse `redis7`-compatible
      std; document that no dedicated `valkey` std exists in luacheck)
- [x] **AC-2** — Any platform enumeration (`LuaPlatform.entries`, `PlatformVersionRegistry
      .platforms()`, and the env→target sync path) surfaces `VALKEY` with its versions; legacy
      `lunar.xml` settings migration is unaffected (an old file with no `VALKEY` tag still
      deserializes and falls back gracefully)
- [x] **AC-3** — Valkey library roots ship `server.*` stubs (call/pcall/error_reply/
      status_reply/sha1hex/log/setresp/breakpoint/debug/set_repl/acl_check_cmd — full mirror
      of the `redis.*` surface) and `SERVER_NAME`/`SERVER_VERSION`/`SERVER_VERSION_NUM`
      globals, with quick documentation; the `server` stub inherits the `redis` field surface
      via `---@class server : redis` (no duplication of shared API docs)
- [x] **AC-4** — `redis.*` remains fully resolvable under the Valkey target (compatibility
      namespace); `KEYS`/`ARGV` remain resolvable under the Valkey target
- [x] **AC-5** — REDIS-01 connections detect server flavor + version via `HELLO`/`INFO` and
      warn (once per session, non-modal) when the connected flavor mismatches the project
      target platform (e.g. Valkey server, Redis target)
- [x] **AC-6** — New inspection "Valkey-only API under Redis target": flags `server.*` and
      `SERVER_*` usage when the project target platform is Redis; quick fix rewrites
      `server.<x>` → `redis.<x>` where a 1:1 equivalent exists
- [x] **AC-7** — Inspection is target-aware in both directions: no warnings for `server.*` /
      `SERVER_*` under the Valkey target
- [x] **AC-8** — Unit tests: registry round-trip, stub resolution for both namespaces,
      inspection positive/negative cases under both targets

## Test Cases

<!-- Concrete input → expected output. Every `Must`/`Should` AC maps to at least one TC.
     Mirrors the REDIS-01 catalog format (TC-id | AC | Given | When | Then | design ref). -->

| TC | AC | Given (input) | When (action) | Then (expected) | Design |
|----|----|---------------|---------------|-----------------|--------|
| TC-REG-1 | AC-1 | the populated `PlatformVersionRegistry` | `getVersions(LuaPlatform.VALKEY)` | returns exactly `[VersionEntry("7.2","valkey-7.2","redis7"), VersionEntry("8","valkey-8","redis7")]` in that order; `defaultVersion(VALKEY)` = the `"7.2"` entry | §2.1, §2.2 |
| TC-REG-2 | AC-1 | `Target(LuaPlatform.VALKEY, findVersion(VALKEY,"8"))` | `getImplicitLanguageLevel()`; `getLibraryRootPath()`; `getLuacheckStd()` | `LuaLanguageLevel.LUA51`; `"runtime/valkey/valkey-8"`; `"redis7"` | §2.2, §3.1 |
| TC-REG-3 | AC-2 | a `LuaProjectSettings.State` whose serialized `lunar.xml` has `platform="VALKEY" versionLabel="7.2"` | `getState()` XML → `loadState` → `state.getTarget()` | round-trips to `Target(VALKEY, "7.2")`; `getImplicitLanguageLevel() == LUA51` | §3.1 |
| TC-REG-4 | AC-2 | a legacy `lunar.xml` with `platform="REDIS" versionLabel="7+"` (no VALKEY) | deserialize → `getTarget()` | still resolves to `Target(REDIS, "7+")`; adding `VALKEY` to the enum did not break the existing tag; `platforms()` now contains `VALKEY` | §3.1 |
| TC-STUB-1 | AC-3 | a `.lua` file under a project whose target is `Target(VALKEY,"8")`, body `server.call("PING")` | resolve the `call` member reference (`LuaNameReference.multiResolve`) | resolves to the `function server.call(...)` declaration in `runtime/valkey/valkey-8/server.lua` | §2.4, §3.2 |
| TC-STUB-2 | AC-3 | Valkey target, body `local n = SERVER_VERSION_NUM` | resolve the `SERVER_VERSION_NUM` name reference | resolves to the global declared in `runtime/valkey/valkey-8/server_global.lua`; inferred type `number` | §2.4 |
| TC-STUB-3 | AC-3 | Valkey target, body `server.error_reply("x")` | resolve `error_reply` | resolves via the `---@class server : redis` parent to the inherited `redis.error_reply` signature (member present without a duplicate `server.error_reply` definition) | §2.4, §3.2 |
| TC-STUB-4 | AC-4 | Valkey target, body `redis.call("GET", KEYS[1])` | resolve `call`, `KEYS` | `redis.call` resolves to `runtime/valkey/valkey-8/redis.lua` (compat namespace); `KEYS` resolves to the Valkey `global.lua` `KEYS` declaration | §2.3, §2.4 |
| TC-FLV-1 | AC-5 | an `INFO server` reply body containing `valkey_version:8.0.1` and `redis_version:7.2.4`; project target `Target(REDIS,"7+")` | `LuaRedisServerFlavor.detect(infoBody)` then mismatch check | `detect` returns `ServerFlavor(Flavor.VALKEY, "8.0.1")` (a `valkey_version` line wins over `redis_version`); mismatch predicate is `true` (Valkey server vs Redis target) | §2.5, §3.3 |
| TC-FLV-2 | AC-5 | an `INFO server` body with only `redis_version:7.4.0`; project target `Target(REDIS,"7+")` | `detect` then mismatch check | `ServerFlavor(Flavor.REDIS, "7.4.0")`; mismatch predicate `false` (no warning) | §2.5, §3.3 |
| TC-FLV-3 | AC-5 | a Valkey server detected under a Redis target, `LuaRedisFlavorWarning` invoked twice in one session for the same connection id | second invocation | exactly one notification is shown; the second call is suppressed by the per-`(connectionId)` session guard | §2.6, §3.4 |
| TC-INSP-1 | AC-6 | project target `Target(REDIS,"7+")`, file `server.call("PING")` | run `LuaValkeyPortabilityInspection` | one WARNING on the `server` name-ref segment: "`server.*` is a Valkey-only namespace and is not portable to Redis"; a quick fix "Replace 'server' with 'redis'" is offered | §2.7, §3.5 |
| TC-INSP-2 | AC-6 | project target Redis, file `local x = SERVER_NAME` | run inspection | one WARNING on `SERVER_NAME`: "`SERVER_NAME` is a Valkey-only global…"; no quick fix (no 1:1 `redis` equivalent) | §2.7, §3.5 |
| TC-INSP-3 | AC-6 | project target Redis, file `server.call("PING")`, apply the quick fix | `LuaValkeyToRedisQuickFix.applyFix` | the `server` base identifier is replaced by `redis`; result text is `redis.call("PING")`; the file re-parses with no portability warning | §2.8, §3.6 |
| TC-INSP-4 | AC-7 | project target `Target(VALKEY,"8")`, file `server.call("PING")` and `local x = SERVER_NAME` | run inspection | no problems registered (both are legal under the Valkey target) | §2.7, §3.5 |
| TC-INSP-5 | AC-7 | project target `Target(STANDARD,"5.4")`, file `server.call("PING")` | run inspection | no problems registered (inspection only fires when target platform is `REDIS`) | §2.7, §3.5 |
| TC-INSP-6 | AC-6 | project target Redis, file `redis.call("PING")` | run inspection | no problems (the compat namespace is portable) | §2.7, §3.5 |

### AC → TC coverage matrix

| AC | Requirement | Test Case(s) |
|----|-------------|--------------|
| AC-1 | `VALKEY` registry entries (versions, level, luacheck std) | TC-REG-1, TC-REG-2 |
| AC-2 | Platform enumeration surfaces Valkey; migration unaffected | TC-REG-3, TC-REG-4 |
| AC-3 | `server.*` + `SERVER_*` stubs (inherit `redis`, no dup) | TC-STUB-1, TC-STUB-2, TC-STUB-3 |
| AC-4 | `redis.*` / `KEYS` / `ARGV` resolvable under Valkey | TC-STUB-4 |
| AC-5 | Flavor detection + once-per-session mismatch warning | TC-FLV-1, TC-FLV-2, TC-FLV-3 |
| AC-6 | Portability inspection flags `server.*`/`SERVER_*` under Redis + quick fix | TC-INSP-1, TC-INSP-2, TC-INSP-3, TC-INSP-6 |
| AC-7 | Inspection target-aware (silent under Valkey / non-Redis) | TC-INSP-4, TC-INSP-5 |
| AC-8 | Unit coverage of the above | all TC-* above (registry round-trip, stub resolution both namespaces, inspection ±) |
