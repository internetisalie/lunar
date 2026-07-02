---
id: "REDIS-03"
parent_id: "REDIS"
type: "feature"
status: "todo"
folders:
  - "[[features/redis/requirements|requirements]]"
title: "REDIS-03: Valkey Runtime Target"
---

# REDIS-03: Valkey Runtime Target

**Requirement**: Valkey as a first-class runtime target: platform registry entries, stdlib
stubs including the `server.*` namespace, flavor detection on connect, and a portability
inspection.
**Priority**: Should
**Status**: Not Implemented

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

- [ ] `PlatformVersionRegistry` gains a `VALKEY` platform with versions `7.2` and `8`,
      implicit language level Lua 5.1, and luacheck std mapping (reuse `redis7`-compatible
      std; document that no dedicated `valkey` std exists in luacheck)
- [ ] Settings UI (contextual platform→version) shows Valkey with its versions; legacy
      settings migration is unaffected
- [ ] Valkey library roots ship `server.*` stubs (call/pcall/error_reply/status_reply/
      sha1hex/log/setresp/breakpoint/debug — full mirror of the `redis.*` surface) and
      `SERVER_NAME`/`SERVER_VERSION`/`SERVER_VERSION_NUM` globals, with quick documentation
- [ ] `redis.*` remains fully resolvable under the Valkey target (compatibility namespace)
- [ ] REDIS-01 connections detect server flavor + version via `HELLO`/`INFO` and warn (once
      per session, non-modal) when the connected flavor mismatches the project target
      platform (e.g. Valkey server, Redis target)
- [ ] New inspection "Valkey-only API under Redis target": flags `server.*` and `SERVER_*`
      usage when the project target platform is Redis; quick fix rewrites `server.<x>` →
      `redis.<x>` where a 1:1 equivalent exists
- [ ] Inspection is target-aware in both directions: no warnings for `server.*` under the
      Valkey target
- [ ] Unit tests: registry round-trip, stub resolution for both namespaces, inspection
      positive/negative cases under both targets
