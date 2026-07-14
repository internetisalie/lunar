---
id: "REDIS-05"
parent_id: "REDIS"
type: "feature"
status: "done"
folders:
  - "[[features/redis/requirements|requirements]]"
title: "REDIS-05: Redis Functions Workflow"
---

# REDIS-05: Redis Functions Workflow

**Requirement**: First-class support for Redis Functions (Redis 7+/Valkey): shebang-aware
editing, typed `redis.register_function`, a FUNCTION LOAD deploy mode on the run
configuration, and a server functions panel.
**Priority**: Could
**Status**: Done (all phases complete; TC-INT-1 green on redis:8 and valkey/valkey:8)

---

## Overview

Redis 7 replaced ad-hoc `EVAL` scripts with **Functions**: a library file starting with
`#!lua name=<libname>`, registering named callbacks via `redis.register_function`, loaded
once with `FUNCTION LOAD` and invoked with `FCALL <fn> <numkeys> …`. Valkey retains the
same machinery. This feature closes the loop for teams that have moved from EVAL to
Functions.

Function callbacks receive `(keys, args)` as arrays — a different ambient contract from
EVAL's `KEYS`/`ARGV` globals — and library files must not use the `KEYS`/`ARGV` globals at
all.

## Acceptance Criteria

- [x] Files beginning with `#!lua name=<lib>` are recognized as Redis Function libraries
      under Redis 7+/Valkey targets: the shebang line lexes/parses cleanly (no error
      elements) and is rendered distinctly *(Phase 1; TC-SHB-1/2 green)*
- [x] LuaCATS-typed stubs for the Functions API: `redis.register_function` in both the
      positional form `(name, callback)` and the table form
      (`function_name`, `callback`, `flags` — `no-writes`, `allow-oom`,
      `allow-stale`, `no-cluster`, `allow-cross-slot-keys` — and `description`), with
      callback signature `fun(keys: string[], args: string[]): any`
      **(Phase 1; scoped per 2026-07-14 decision — see §3.3 / risks Gap 2.4)**: the stub
      *declares* both overloads and the callback signature, so completion + hover surface
      them and the reference resolves live. The bundled type engine does **not** propagate
      an expected `fun(...)` argument type onto a passed lambda's parameters (ground-truthed:
      direct `---@param` works, expected-type propagation does not), so `keys`/`args` inside
      an un-annotated callback are **not** auto-typed as `string[]`; users annotate the
      callback params themselves. General "expected-type → lambda-param" inference is
      deferred to a TYPE-epic enhancement.
- [x] Inspection: `KEYS`/`ARGV` usage inside a function library file is flagged (they are
      EVAL-only); REDIS-04's ambient globals are suppressed in library files
      *(Phase 2; TC-KEYS-1/2/3 green)*
- [x] REDIS-01 run configuration gains the `FUNCTION LOAD` + `FCALL` execution mode:
      library file, `REPLACE` toggle, function name to call, KEYS/ARGV inputs; loading a
      library without calling is a valid "deploy" run
- [x] REDIS-01's read-only toggle maps to `FCALL_RO` in this mode; a hint suggests
      enabling it when the selected function declares the `no-writes` flag (and the run
      fails with a clear server error surface if a write is attempted)
- [x] `FCALL` mode validates the requested function name against the names registered in
      the library file (best-effort static scan; dynamic names skipped)
- [x] Functions tool-window panel (or run-config gutter view) listing `FUNCTION LIST`
      output for a configured connection: libraries, functions, flags; per-library actions
      Deploy (LOAD REPLACE from the local file) and Delete, with confirmation
      *(Phase 5; TC-PANEL-1/2/3 green; panel UI is human-verification-deferred)*
- [x] Local-vs-server drift indicator: the panel marks a library whose local source hash
      differs from the loaded `library_code` (when the server reports it via
      `FUNCTION LIST WITHCODE`) *(Phase 5; TC-DRIFT-1 green)*
- [x] REDIS-02 debugging note surfaced in the UI: LDB does not support stepping FCALL
      invocations (Redis limitation) — the Debug executor for FCALL mode is disabled with
      an explanatory tooltip
- [x] Integration test against dockerized `redis:8`: load, list, call, replace, delete;
      Valkey parity run *(Phase 6; TC-INT-1 green on redis:8 and valkey/valkey:8)*

The acceptance criteria above are referenced by position below as **AC-1** (shebang
recognition) … **AC-10** (integration + Valkey parity), top to bottom.

## Test Cases

Concrete input → expected output for every acceptance criterion. Each TC seeds the automated
test named in [implementation-plan.md](implementation-plan.md) "Verification Tasks"; the
design section that specifies the behavior is cited in the last column. Every AC maps to at
least one TC (see the AC → TC coverage matrix after the table). AC-1, AC-4, AC-6, AC-9 (marked
`*` below) also carry a human-verification scenario for their editor/UX surface
([human-verification-checklists.md](human-verification-checklists.md)).

| # | AC | Given (input) | When (action) | Then (expected) | Design |
|---|-----|---------------|---------------|-----------------|--------|
| TC-SHB-1 | AC-1* | a `.lua` file whose first line is `#!lua name=mylib` followed by `redis.register_function('f', function(keys, args) return 1 end)` | parse the file; walk the PSI tree for `PsiErrorElement` | zero error elements; the first leaf is a `SHEBANG` (`#!`) followed by a `SHORTCOMMENT` leaf `lua name=mylib`; the shebang range highlights with `LuaHighlight.COMMENT` (distinct from code) | §3.1, §2.1 |
| TC-SHB-2 | AC-1 | files `#!lua name=lib1`, `#!lua  name=lib_2`, `#! lua name=x`, `-- not a shebang`, and a file with no shebang | `LuaRedisFunctionLibrary.detect(file)` on each | `"lib1"`, `"lib_2"`, `"x"` (leading/interior whitespace tolerated per §3.2 regex); `null` for the comment-only and shebang-less files | §3.2 |
| TC-STUB-1 | AC-2 | Redis 7+ target; `#!lua name=lib` file with `redis.register_function('f', function(keys, args) return keys[1] end)` | run the type engine on the file; separately type a `---@type string[]` local subscript | file produces **no type errors**; the `string[]`-subscript path infers `string` (regression pin, `ArraySubscriptTypeTest`). **Live-only (jar-stub + engine limits, §3.3):** `register_function` reference resolution (no unresolved ref) and callback-param auto-typing are **not** fixture-assertable — resolution rides the jar-stub `FileBasedIndex` path (same constraint as REDIS-04 `RedisAmbientTypingTest`) and callback-param typing needs expected-type→lambda inference the engine lacks (descoped, risks Gap 2.4); both verified via human-verification §1 | §2.1, §3.3 |
| TC-STUB-2 | AC-2 | Redis 7+ target; a `register_function{ function_name='f', callback=function(keys, args) end, flags={'no-writes'}, description='d' }` table-form call | run the type engine on the file | table-form call produces **no type errors** (the `@overload` parses and the call type-checks). Overload-field resolution + hover fidelity are live-only (human-verification §1), same jar-stub limit as TC-STUB-1 | §2.1, §3.3 |
| TC-KEYS-1 | AC-3* | Redis 7+ target; `#!lua name=lib` file whose callback body reads `KEYS[1]` and `ARGV[1]` | run `LuaRedisFunctionKeysInspection` (`visitNameRef`) | one WARNING on `KEYS` and one on `ARGV` ("`KEYS`/`ARGV` are not available in a Redis Function library; use the callback's `keys`/`args` parameters"); suppressible via `LuaInspectionSuppression` | §3.4 |
| TC-KEYS-2 | AC-3 | a plain (non-library, no shebang) Redis 7+ EVAL script reading `KEYS[1]` | run the inspection | no WARNING (the file is not a library — `LuaRedisFunctionLibrary.detect` returns `null`); REDIS-04 typing still resolves `KEYS` | §3.4 |
| TC-KEYS-3 | AC-3 | a `#!lua name=lib` file under a **non-Redis** (`STANDARD 5.4`) target reading `KEYS[1]` | run the inspection | no WARNING (target guard: inspection inert off Redis/Valkey) | §3.4 |
| TC-MODE-1 | AC-4 | a `LuaRedisRunConfiguration` in the new `FCALL` mode with `scriptPath="lib.lua"`, `connectionId="u1"`, `functionName="f"`, `replaceOnLoad=true`, `keys=["k"]`, `argv=["v"]` | write options → read back (round-trip); then blank `functionName` and call `checkConfiguration()` with `execMode=FCALL` | all fields persist (`functionName`/`replaceOnLoad` survive their `string()` StoredProperty bridge); FCALL is **accepted** (no longer rejected as in REDIS-01); with a blank `functionName` **and** deploy-only unchecked, `checkConfiguration` throws `RuntimeConfigurationException("Function name is not defined")`; with deploy-only checked, no throw | §2.4, §3.6 |
| TC-DEPLOY-1 | AC-4 | FCALL mode, `deployOnly=true` (load without calling), library body `#!lua name=lib\nredis.register_function('f', …)` | `LuaRedisFunctionExecutor.execute(client, config, body)` | one `FUNCTION LOAD [REPLACE] <body>` command is sent; **no** `FCALL` follows; the returned library name (`+lib`) is shown as the reply | §3.5 |
| TC-CALL-1 | AC-4 | FCALL mode, `deployOnly=false`, `functionName="f"`, `replaceOnLoad=true`, `keys=["k1"]`, `argv=["a1"]` | `LuaRedisFunctionExecutor.execute(...)` | sequence = `FUNCTION LOAD REPLACE <body>` then `FCALL f 1 k1 a1` (numkeys = `keys.size`, keys then argv); reply tree shows the `FCALL` result | §3.5 |
| TC-RO-1 | AC-5 | FCALL mode with `readOnly=true`, `functionName="f"` | `LuaRedisFunctionExecutor.execute(...)` | the invocation command is `FCALL_RO f <numkeys> …` (not `FCALL`); a write inside `f` returns a `RespValue.Error` (`ERR ... Write commands are not allowed`) surfaced via `console.showError` (server-enforced; not blocked client-side) | §3.5 |
| TC-RO-2 | AC-5* | FCALL mode, selected `functionName="f"`, static scan finds `f` registered with `flags={'no-writes'}`, `readOnly=false` | open the settings editor / `checkConfiguration()` | a non-error hint is shown ("`f` declares `no-writes`; consider enabling read-only (`FCALL_RO`)"); it is a hint, not a validation error (run still allowed) | §3.6, §3.7 |
| TC-VALID-1 | AC-6 | FCALL mode, `functionName="ghost"`, library statically registering only `f` and `g` | `checkConfiguration()` | throws `RuntimeConfigurationException("Function 'ghost' is not registered in lib.lua (registered: f, g)")` | §3.6, §3.7 |
| TC-VALID-2 | AC-6 | FCALL mode, `functionName="f"`, library registers `f`; and a second library whose only registration uses a **dynamic** (non-literal) name `redis.register_function(fnName, cb)` | `checkConfiguration()` on each | first: no error (`f` is registered); second: no error even though `f`'s literal is absent — a library containing any dynamic registration skips name validation entirely (best-effort; dynamic names not statically known) | §3.7 |
| TC-SCAN-1 | AC-6 | library body registering `a` (positional literal), `b` (table-form `function_name='b'`), and `c` via a dynamic name | `LuaRedisFunctionLibrary.registeredNames(file)` | returns `RegisteredNames(names={"a","b"}, hasDynamic=true)` (literal positional + table-form names collected; `hasDynamic` set because of `c`) | §3.7 |
| TC-PANEL-1 | AC-7 | a `FUNCTION LIST` reply `*1 %2 library_name $mylib functions *1 %1 name $f flags *1 $no-writes` (RESP3-shaped) for a configured connection | `LuaRedisFunctionListParser.parse(reply)` | one `RedisLibraryEntry(name="mylib", functions=[RedisFunctionEntry(name="f", flags={"no-writes"})])` | §3.8 |
| TC-PANEL-2 | AC-7 | the panel model above; user triggers Delete on `mylib` and confirms | `LuaRedisFunctionsController.delete("mylib")` | a `FUNCTION DELETE mylib` command is sent over the connection's `RespClient`; on `+OK` the library row is removed from the model | §3.9 |
| TC-PANEL-3 | AC-7 | the panel; user triggers Deploy on a library backed by local file `lib.lua` | `LuaRedisFunctionsController.deploy(entry, "lib.lua")` | a `FUNCTION LOAD REPLACE <fileBody>` command is sent; on success the list is refreshed via `FUNCTION LIST` | §3.9 |
| TC-DRIFT-1 | AC-8 | a `FUNCTION LIST WITHCODE` reply whose `library_code` for `mylib` = `#!lua name=mylib\nX` and a local file with identical bytes; and a second case where the local file differs | `LuaRedisFunctionDrift.compare(serverCode, localBody)` on each | first → `IN_SYNC` (sha1 of normalized bodies equal); second → `DRIFTED`; when the server omits `library_code` (no `WITHCODE`) → `UNKNOWN` (no drift glyph) | §3.10 |
| TC-DBG-1 | AC-9* | a `LuaRedisRunConfiguration` in `FCALL` mode | `LuaRedisDebugRunner.canRun(DefaultDebugExecutor.EXECUTOR_ID, config)` | returns `false` (Debug executor disabled for FCALL); for the same config in `EVAL` mode `canRun` returns `true` (REDIS-02 behavior preserved) | §3.11, §7 (REDIS-02 amendment) |
| TC-INT-1 | AC-10 | dockerized `redis:8` **and** `valkey/valkey:8` | `RedisFunctionsIntegrationTest`: deploy `lib.lua` (LOAD), `FUNCTION LIST`, `FCALL f`, edit + LOAD REPLACE, `FUNCTION DELETE` | each step succeeds on both flavors; the list reflects load/replace/delete; `FCALL` returns the expected reply on both Redis and Valkey | impl-plan Phase 6 |

### AC → TC coverage matrix

| AC | Requirement | Test Case(s) |
|----|-------------|--------------|
| AC-1 | Shebang recognition (clean lex, distinct render) | TC-SHB-1, TC-SHB-2 (render UX: human checklist §1) |
| AC-2 | `register_function` typed stubs (positional + table, callback signature) | TC-STUB-1, TC-STUB-2 |
| AC-3 | `KEYS`/`ARGV` flagged in library files; REDIS-04 ambient suppressed | TC-KEYS-1, TC-KEYS-2, TC-KEYS-3 |
| AC-4 | FCALL run-config mode (deploy-only valid, fields persisted) | TC-MODE-1, TC-DEPLOY-1, TC-CALL-1 |
| AC-5 | `FCALL_RO` mapping + `no-writes` hint + write error surface | TC-RO-1, TC-RO-2 |
| AC-6 | FCALL function-name validation (static scan, dynamic skipped) | TC-VALID-1, TC-VALID-2, TC-SCAN-1 |
| AC-7 | Functions panel (`FUNCTION LIST`, Deploy/Delete with confirm) | TC-PANEL-1, TC-PANEL-2, TC-PANEL-3 |
| AC-8 | Local-vs-server drift indicator | TC-DRIFT-1 |
| AC-9 | Debug executor disabled for FCALL (tooltip) | TC-DBG-1 (tooltip UX: human checklist §4) |
| AC-10 | Integration test (load/list/call/replace/delete) + Valkey parity | TC-INT-1 |

## Dependencies

- **REDIS-01** (hard): consumes `RespClient` (§2.3), `LuaRedisServerConnection` /
  `LuaRedisConnectionSettings` (§2.4/§2.5), `LuaRedisRunConfiguration` /
  `LuaRedisRunConfigurationOptions` / `LuaRedisExecMode.FCALL` (§2.8),
  `LuaRedisScriptExecutor` command-selection shape (§3.8), and `RespReplyTreeConsole`
  (§2.6). REDIS-05 **enables** the reserved `FCALL` enum slot and adds two small options
  fields — see design §7 amendment A1.
- **REDIS-04** (soft): consumes `RedisCallSiteMatcher` / `RedisCallSite`,
  `RedisCommandSpecService`, `isRedisTarget`, `LuaInspectionSuppression`, and the
  stub-declared-global ambient seam (design §2 / §7 "Reusable seam for REDIS-05").
- **REDIS-02** (cross-feature amendment): `LuaRedisDebugRunner.canRun` must additionally
  gate `execMode != FCALL` once REDIS-05 removes REDIS-01's FCALL rejection — design §7
  amendment A2.
- **REDIS-03** (soft parity): when `LuaPlatform.VALKEY` exists, `server.register_function`
  mirrors `redis.register_function` via the `---@class server : redis` stub inheritance —
  design §2.1 note.
