---
id: "REDIS-04"
parent_id: "REDIS"
type: "feature"
status: "planned"
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

- [ ] **AC-1** — Under Redis/Valkey targets, `KEYS` and `ARGV` resolve as ambient `string[]`
      globals: no "undeclared variable" warnings, `KEYS[1]` infers `string`, `#ARGV` infers
      `integer`; not present under non-Redis targets. **(Re-plan 2026-07-14, DR-03 re-opened):
      realized by two REQUIRED type-engine changes — feed stub-declared globals into inference
      (design §3.1a) and array-subscript element inference (design §3.1b) — NOT by stub edits
      alone. The prior "existing engine, no changes" premise was ground-truth-false.)**
- [ ] **AC-2** — Bundled command specs per supported target version (Redis 5 / 6 / 7+ —
      Valkey 7.2 / 8 land with REDIS-03; see §Dependencies), loaded lazily and shared via an
      application service
- [ ] **AC-3** — Completion inside the first string argument of `redis.call` / `redis.pcall`
      (and `server.call` / `server.pcall` once the Valkey target exists) offers command names
      valid for the target version, with the command summary as tail text
- [ ] **AC-4** — Inspection "Unknown or wrong-arity Redis command": string-literal first
      arguments are validated against the spec — unknown command (WARNING, with did-you-mean
      rename quick fix) and argument count below the command's minimum arity (WARNING);
      dynamic (non-literal) command names are never flagged
- [ ] **AC-5** — Quick documentation on a command-name string literal shows the spec summary,
      since-version, and arity
- [ ] **AC-6** — `redis.pcall` return type models the error-table shape (`{ err: string }`
      union with the success reply type) so `if reply.err then` narrows correctly
- [ ] **AC-7** — Sandbox inspection: `io.*`, `os.*` (beyond `os.time`/`os.clock` — match the
      actual sandbox allowlist per version), `require`, `dofile`, `loadfile`, and `print`
      usage flagged under Redis/Valkey targets with explanatory text (ships at WARNING per
      RISK-R07; escalates to ERROR after live validation)
- [ ] **AC-8** — The existing Global-creation inspection escalates from WARNING to ERROR under
      Redis/Valkey targets (global writes are runtime errors in the sandbox), suppressible
      per the normal mechanisms
- [ ] **AC-9** — Determinism inspection under Redis 5/6 targets only: a command whose spec
      flags mark it nondeterministic (e.g. `TIME`, `RANDOMKEY`, `SRANDMEMBER`), invoked via
      `redis.call`/`pcall` before any write command in the same script and without a
      preceding `redis.replicate_commands()` call, is flagged WARNING with an explanation
      of verbatim replication; never raised under Redis 7+/Valkey targets; command
      classification comes from the bundled command spec, not a hand-maintained list
- [ ] **AC-10** — Unit tests: ambient typing, completion (per-version filtering),
      arity/unknown-command inspection positive+negative, pcall narrowing, sandbox inspection
      matrix, and no-op behavior under the Standard target

## Dependencies

- **TARGET-04 (done)** provides the per-target stdlib-stub mechanism
  (`runtime/redis/redis-7/`, `LuaLibraryProvider`, `RuntimeLibraryProvider`) and already
  ships `global.lua` (`KEYS`/`ARGV` as `string[]`) plus `redis.lua`. AC-1 is realized by that
  stub base; REDIS-04 extends it (redis-5/redis-6 stub dirs, `redis.pcall` return type).
- **REDIS-03 (Valkey target, not implemented)** owns the `LuaPlatform.VALKEY` enum entry and
  its `PlatformVersionRegistry` rows plus `server.*` stubs. Under REDIS-04's own scope the
  command-spec lookup, completion, and inspections key on `Target`; the Valkey rows (Valkey
  7.2 / 8, `server.call`/`server.pcall`) activate automatically once REDIS-03 registers the
  platform. REDIS-04 does **not** add the Valkey platform.

## Test Cases

<!-- Step 3 catalog. Each Must AC → at least one input→output test. UI-only behavior that a
     light fixture cannot assert (rendered doc popup, completion popup visuals) is covered by
     human-verification-checklists.md and noted in the matrix. -->

| # | AC | Given (input) | When (action) | Then (expected) | Design |
| :--- | :--- | :--- | :--- | :--- | :--- |
| TC-KEYS-1 | AC-1 | project target `Target(REDIS,"7+")`; `local x = KEYS[1]` | `LuaTypesSnapshot.forFile(file).getValueType(exprFor("KEYS[1]"))` | resolves to `string` — requires §3.1a (seed `KEYS` as `Array(String)` from stub) AND §3.1b (subscript element); AND `getValueType(exprFor("KEYS"))` == `string[]` (Array); no undeclared-variable highlight on `KEYS`/`ARGV` | §2.1, §3.1a, §3.1b |
| TC-KEYS-2 | AC-1 | project target `Target(REDIS,"7+")`; `local n = #ARGV` | infer type of `#ARGV`, AND infer type of `ARGV` operand | `#ARGV` → `number`; **AND** `getValueType(exprFor("ARGV"))` == `string[]` (proves it is not the always-`number` false-pass — §3.1a); **AND** `doHighlighting()` reports NO "not assignable" error on `#ARGV` (proves the `#`-operand-over-Array sub-fix, §3.1b) | §2.1, §3.1a, §3.1b |
| TC-KEYS-3 | AC-1 | project target `Target(STANDARD,"5.4")`; `local x = KEYS[1]` | `myFixture.doHighlighting()` + `getValueType(exprFor("KEYS[1]"))` | `KEYS` flagged undeclared AND `getValueType(KEYS[1])` == `Undefined` (`seedAmbientGlobals` seeds nothing off Redis) — structural no-leak | §2.1, §3.1a, §6 |
| TC-IDX-1 | AC-1 | any target; `---@type string[]`\n`local arr = {}`\n`local x = arr[1]` (no Redis needed — isolates §3.1b) | `getValueType(exprFor("arr[1]"))` | `string` (array-subscript element inference; unit test of §3.1b independent of stub seeding) | §3.1b |
| TC-IDX-2 | AC-1 | any target; `local t = {}`\n`local y = t[1]` (non-array receiver) | `getValueType(exprFor("t[1]"))` | `Undefined` — **regression**: non-array bracket access is unchanged (§3.1c inv. 2) | §3.1b, §3.1c |
| TC-IDX-3 | AC-1 | any target; `local a = {}`\n`local m = a.b` (dotted, not bracket) | `getValueType`/`doHighlighting` on `a.b` unchanged vs baseline | dotted member access identical to pre-fix — **regression** (§3.1c inv. 1); asserted via existing `TableTypeTest`/`QualifiedMemberResolutionTest` staying green | §3.1b, §3.1c |
| TC-SEED-1 | AC-1 | `Target(REDIS,"7+")`; empty script referencing `KEYS` | `getValueType(exprFor("KEYS"))` | `string[]` (Array) — isolates §3.1a global-seeding independent of subscript | §3.1a |
| TC-SPEC-1 | AC-2 | bundled resource `commandspec/redis-7.json` | `RedisCommandSpecService.getInstance().specFor(Target(REDIS,"7+"))` twice | returns a non-empty `RedisCommandSpec` with `GET` present (`arity=2`); second call returns the **same** cached instance | §2.2, §4.1 |
| TC-SPEC-2 | AC-2 | target with no bundled spec (e.g. `Target(TARANTOOL,"2.10")`) | `specFor(target)` | returns `RedisCommandSpec.EMPTY` (no exception); consumers no-op | §2.2, §3.2 |
| TC-COMP-1 | AC-3 | `Target(REDIS,"7+")`; `redis.call("<caret>")` | `myFixture.completeBasic()`; collect lookup strings | includes `GET`, `SET`, `HSET`; each carries the spec summary as tail text; excludes commands whose `since` is newer than the target | §2.3, §3.3 |
| TC-COMP-2 | AC-3 | `Target(REDIS,"5")`; `redis.call("<caret>")` | `completeBasic()` | offers Redis-5 command surface; a `since:"7.0.0"` command (e.g. `SINTERCARD`) is **absent** | §2.3, §3.3 |
| TC-COMP-3 | AC-3 | `Target(REDIS,"7+")`; `redis.call(cmd)` where `cmd` is a name-ref (non-literal) | `completeBasic()` | no command-name completions injected (contributor bails on non-literal first arg) | §2.3, §3.3 |
| TC-COMP-4 | AC-3 | `Target(STANDARD,"5.4")`; `redis.call("<caret>")` | `completeBasic()` | no Redis command names offered (contributor no-ops off Redis targets) | §2.3, §6 |
| TC-ARITY-1 | AC-4 | `Target(REDIS,"7+")`; `redis.call("GET")` (`GET` arity 2, needs 1 key arg) | highlights filtered on the arity message | one WARNING "Redis command 'GET' expects at least 1 argument(s), found 0" on the arg list | §2.4, §3.4 |
| TC-ARITY-2 | AC-4 | `Target(REDIS,"7+")`; `redis.call("GET", KEYS[1])` | highlighting | no arity/unknown warning (satisfies minimum arity) | §2.4, §3.4 |
| TC-UNK-1 | AC-4 | `Target(REDIS,"7+")`; `redis.call("Gte")` | highlighting + intentions on the string literal | WARNING "Unknown Redis command 'GTE'"; a `LuaRedisRenameCommandQuickFix` offering `GET` (Levenshtein ≤ 2) is present | §2.4, §3.4, §3.5 |
| TC-UNK-2 | AC-4 | `Target(REDIS,"7+")`; `redis.pcall(commandName)` (non-literal) | highlighting | no unknown-command WARNING (dynamic names never flagged) | §2.4, §3.4 |
| TC-DOC-1 | AC-5 | `Target(REDIS,"7+")`; caret on the `"GET"` literal in `redis.call("GET")` | `RedisCommandDocumentationTarget.computeDocumentation()` | HTML contains the summary, `Since 1.0.0`, and `Arity 2` | §2.5, §3.6 |
| TC-PCALL-1 | AC-6 | `Target(REDIS,"7+")`; `local reply = redis.pcall("GET", KEYS[1]); if reply.err then end` | infer type of `reply.err` inside the `if` | `string` (the `{ err: string }` arm is reachable); `reply` has member `err` — proves the union stub | §2.6, §4.2 |
| TC-SBX-1 | AC-7 | `Target(REDIS,"7+")`; `print("x")` / `require("m")` / `io.read()` / `dofile("f")` / `os.getenv("X")` | highlighting filtered on the sandbox message | each flagged (WARNING per RISK-R07) "'io'/'os'/'print'/... is not available in the Redis script sandbox" on the offending ref | §2.7, §3.7 |
| TC-SBX-2 | AC-7 | `Target(REDIS,"7+")`; `os.time()` / `os.clock()` / `redis.sha1hex("x")` / `cjson.encode({})` | highlighting | no sandbox warning (all in the allowlist derived from the stub roots) | §2.7, §3.7 |
| TC-SBX-3 | AC-7 | `Target(STANDARD,"5.4")`; `io.read()` | highlighting | no sandbox warning (inspection no-ops off Redis targets) | §2.7, §6 |
| TC-GLOB-1 | AC-8 | `Target(REDIS,"7+")`; `myGlobal = 1` | highlighting; read the `HighlightInfo.severity` for the "Global creation" problem | severity `ERROR` (escalated); off a Redis target the same input stays `WARNING` | §2.8, §3.8 |
| TC-GLOB-2 | AC-8 | `Target(REDIS,"7+")`; `myGlobal = 1` with a `-- luacheck: ignore` suppression range covering the line | highlighting | no problem (escalation still honors `LuaInspectionSuppression`) | §2.8 |
| TC-DET-1 | AC-9 | `Target(REDIS,"6")`; `local t = redis.call("TIME"); redis.call("SET", KEYS[1], t[1])` | highlighting filtered on the determinism message | WARNING on the `"TIME"` literal ("nondeterministic command before a write under verbatim replication") | §2.9, §3.9 |
| TC-DET-2 | AC-9 | `Target(REDIS,"6")`; `redis.replicate_commands(); local t = redis.call("TIME"); redis.call("SET", KEYS[1], t[1])` | highlighting | no determinism warning (guard call precedes) | §2.9, §3.9 |
| TC-DET-3 | AC-9 | `Target(REDIS,"7+")`; `local t = redis.call("TIME"); redis.call("SET", KEYS[1], t[1])` | highlighting | no determinism warning (version-gated off for 7+) | §2.9, §3.9 |
| TC-DET-4 | AC-9 | `Target(REDIS,"6")`; `local t = redis.call("TIME"); return t` (no write) | highlighting | no determinism warning (no write follows the nondeterministic call) | §2.9, §3.9 |

### AC → TC coverage matrix

| AC | Requirement | Test Case(s) |
| :--- | :--- | :--- |
| AC-1 | `KEYS`/`ARGV` ambient `string[]` typing (engine: §3.1a seed globals, §3.1b subscript); absent off Redis; regression-guarded | TC-KEYS-1, TC-KEYS-2, TC-KEYS-3, TC-SEED-1 (§3.1a), TC-IDX-1 (§3.1b), TC-IDX-2 + TC-IDX-3 (§3.1c regression) |
| AC-2 | Per-version bundled command spec via app service (lazy, cached) | TC-SPEC-1, TC-SPEC-2 |
| AC-3 | Command-name completion inside `redis.call`/`pcall` first arg | TC-COMP-1, TC-COMP-2, TC-COMP-3, TC-COMP-4 (popup visuals: checklist §1) |
| AC-4 | Unknown / wrong-arity inspection + did-you-mean quick fix | TC-ARITY-1, TC-ARITY-2, TC-UNK-1, TC-UNK-2 |
| AC-5 | Quick documentation on command literal | TC-DOC-1 (rendered popup: checklist §2) |
| AC-6 | `redis.pcall` `{ err: string }` union narrowing | TC-PCALL-1 |
| AC-7 | Sandbox-API inspection (allowlist from stub roots) | TC-SBX-1, TC-SBX-2, TC-SBX-3 |
| AC-8 | Global-creation escalation to ERROR under Redis; suppressible | TC-GLOB-1, TC-GLOB-2 |
| AC-9 | Determinism inspection (Redis 5/6 only) | TC-DET-1, TC-DET-2, TC-DET-3, TC-DET-4 |
| AC-10 | Unit-test suite across all strands | all TC-* above |
