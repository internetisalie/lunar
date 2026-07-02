---
id: "REDIS-04"
parent_id: "REDIS"
type: "feature"
status: "todo"
folders:
  - "[[features/redis/requirements|requirements]]"
title: "REDIS-04: Language-Engine Integration"
---

# REDIS-04: Language-Engine Integration

**Requirement**: Redis-aware language-engine support: `KEYS`/`ARGV` typing, command-name
completion and arity validation for `redis.call`/`redis.pcall` from a bundled command
specification, and script-sandbox inspections.
**Priority**: Should
**Status**: Not Implemented

---

## Overview

Pure language-engine work, independent of the connection stack — likely the most-used part
of the epic in day-to-day editing. Three strands:

1. **Ambient globals**: under a Redis/Valkey target, scripts receive `KEYS` and `ARGV` as
   `string[]`. The type engine's scope-injection mechanism (used by TYPE-08 narrowing)
   provides these as file-scope ambient bindings so member access, iteration, and
   assignability checks work without annotations.
2. **Command-spec intelligence**: Redis publishes a machine-readable command specification
   (`commands.json` per version). Bundling a per-target-version spec enables completion of
   command names inside `redis.call("…")` / `redis.pcall("…")` (and `server.*` under
   Valkey), arity validation, and per-command quick documentation (summary, since-version,
   key positions).
3. **Sandbox inspections**: the script sandbox rejects global writes at runtime
   ("Script attempted to create global variable"), and omits `io`, `os` (except a subset),
   `require`, `dofile`, `loadfile`, and `print`. Editing-time inspections surface these
   before the script ever runs.
4. **Determinism inspection (Redis 5/6 targets only)**: under verbatim script replication
   (the default before Redis 7), calling a nondeterministic command before a write is a
   replication-correctness bug unless `redis.replicate_commands()` was called first.
   Redis 7+/Valkey always use effects replication, so the inspection is version-gated off
   there — exactly the per-version gating the target model provides.

## Acceptance Criteria

- [ ] Under Redis/Valkey targets, `KEYS` and `ARGV` resolve as ambient `string[]` globals:
      no "undeclared variable" warnings, `KEYS[1]` infers `string`, `#ARGV` infers
      `integer`; not present under non-Redis targets
- [ ] Bundled command specs per supported target version (Redis 5 / 6 / 7+ / Valkey 7.2 / 8),
      loaded lazily and shared via an application service
- [ ] Completion inside the first string argument of `redis.call` / `redis.pcall` /
      `server.call` / `server.pcall` offers command names valid for the target version,
      with the command summary as tail text
- [ ] Inspection "Unknown or wrong-arity Redis command": string-literal first arguments are
      validated against the spec — unknown command (WARNING, with did-you-mean rename quick
      fix) and argument count below the command's minimum arity (WARNING); dynamic
      (non-literal) command names are never flagged
- [ ] Quick documentation on a command-name string literal shows the spec summary,
      since-version, and arity
- [ ] `redis.pcall` return type models the error-table shape (`{ err: string }` union with
      the success reply type) so `if reply.err then` narrows correctly
- [ ] Sandbox inspection: `io.*`, `os.*` (beyond `os.time`/`os.clock` — match the actual
      sandbox allowlist per version), `require`, `dofile`, `loadfile`, and `print` usage
      flagged as ERROR under Redis/Valkey targets with explanatory text
- [ ] The existing Global-creation inspection escalates from WARNING to ERROR under
      Redis/Valkey targets (global writes are runtime errors in the sandbox), suppressible
      per the normal mechanisms
- [ ] Determinism inspection under Redis 5/6 targets only: a command whose spec flags mark
      it nondeterministic (e.g. `TIME`, `RANDOMKEY`, `SRANDMEMBER`), invoked via
      `redis.call`/`pcall` before any write command in the same script and without a
      preceding `redis.replicate_commands()` call, is flagged WARNING with an explanation
      of verbatim replication; never raised under Redis 7+/Valkey targets; command
      classification comes from the bundled command spec, not a hand-maintained list
- [ ] Unit tests: ambient typing, completion (per-version filtering), arity/unknown-command
      inspection positive+negative, pcall narrowing, sandbox inspection matrix, and
      no-op behavior under the Standard target
