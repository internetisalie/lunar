---
id: "REDIS-05-PLAN"
title: "Implementation Plan"
type: "plan"
status: "todo"
parent_id: "REDIS-05"
folders:
  - "[[features/redis/05-functions-workflow/requirements|requirements]]"
---

# REDIS-05: Implementation Plan

Phases are independently testable and leave the build green. Kotlin lands under
`net.internetisalie.lunar.redis.{functions, functions.ui, run}` and one inspection under
`net.internetisalie.lunar.analysis.redis`. Resource edit under
`src/main/resources/runtime/redis/redis-7/` and a description under
`src/main/resources/inspectionDescriptions/`. Every task names the file it creates/edits and the
design section it realizes. REDIS-05 assumes REDIS-01 (hard) is implemented; the REDIS-02 and
REDIS-04 amendments are called out where they land. `run test`/`run build` gate on the **full**
suite (memory: isolated `--tests` masks synthetic-lambda failures).

## Phases

### Phase 1: Library model + shebang + `register_function` stub (AC-1, AC-2) [Must]
- **Goal**: `#!lua name=<lib>` is detected; `redis.register_function` resolves and types the
  callback; static name/flag scan works.
- **Tasks**:
  - [x] Edit `src/main/resources/runtime/redis/redis-7/redis.lua`: add `redis.register_function`
        with the positional signature + table-form `@overload` and
        `fun(keys: string[], args: string[]): any` callback — realizes design §2.1. **Callback
        auto-typing descoped 2026-07-14** (engine lacks expected-type→lambda inference; risks
        Gap 2.4) — stub declares/resolves/hovers; users annotate callback params.
  - [x] Create `net.internetisalie.lunar.redis.functions.LuaRedisFunctionLibrary`
        (`detect(file)` via the shebang walk §3.2; `registeredNames(file)`/`registeredFlags`
        via `RedisCallSiteMatcher` + arg/table PSI walk §3.7; `CachedValuesManager`-cached) —
        realizes design §2.2, §3.2, §3.7
- **Exit criteria**: TC-SHB-1, TC-SHB-2, TC-SCAN-1 green (real-flow); TC-STUB-1/2 in-fixture
  smoke (no type errors) + array-subscript regression pin — resolution/callback-typing/hover
  are live-only (jar-stub + descoped engine limit, §3.3 / risks Gap 2.4).

### Phase 2: KEYS/ARGV-in-library inspection (AC-3) [Must]
- **Goal**: `KEYS`/`ARGV` inside a library file WARN under Redis 7+/Valkey; inert elsewhere.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.analysis.redis.LuaRedisFunctionKeysInspection`
        (`visitNameRef`; target-7+ + `isLibrary` guard; declaration skip mirrors
        `LuaDeprecatedApiInspection.isDeclaration`; `LuaInspectionSuppression` guard) — realizes
        design §2.3, §3.4
  - [x] Add `inspectionDescriptions/LuaRedisFunctionKeys.html`; register `<localInspection
        shortName="LuaRedisFunctionKeys" level="WARNING">` in `plugin.xml` — realizes design §7
- **Exit criteria**: TC-KEYS-1, TC-KEYS-2, TC-KEYS-3 green.

### Phase 3: FCALL run-config mode + executor (AC-4, AC-5, AC-6) [Must]
- **Goal**: the FCALL deploy/call mode on `LuaRedisRunConfiguration`, validated and executed.
- **Tasks** (REDIS-01 amendment A1):
  - [x] Edit `LuaRedisRunConfigurationOptions`: add `functionName`/`replaceOnLoad`/`deployOnly`
        `string()` StoredProperties; edit `LuaRedisRunConfiguration` with the bridged getters —
        realizes design §2.4
  - [x] Create `net.internetisalie.lunar.redis.run.LuaRedisFunctionExecutor`
        (`FUNCTION LOAD [REPLACE]` + `FCALL`/`FCALL_RO`; deploy-only early return; numkeys/keys/argv
        marshalling) — realizes design §2.5, §3.5
  - [x] Edit `LuaRedisRunProfileState`: dispatch to `LuaRedisFunctionExecutor` when
        `execMode == FCALL` — realizes design §2.6
  - [x] Edit `LuaRedisRunConfiguration.checkConfiguration`: remove the FCALL rejection; add the
        FCALL branch (function-name presence, static name validation, dynamic-skip) — realizes
        design §3.6, §3.7
  - [x] Edit `LuaRedisSettingsEditor` (REDIS-01): FCALL section (function name field, REPLACE +
        deploy-only checkboxes) + the no-writes hint label (§3.6 step 4) — realizes design §2.4,
        §3.6
- **Exit criteria**: TC-MODE-1, TC-DEPLOY-1, TC-CALL-1, TC-RO-1, TC-RO-2, TC-VALID-1,
  TC-VALID-2 green.

### Phase 4: Debug executor disable for FCALL (AC-9) [Must]
- **Goal**: the Debug action is greyed for FCALL configs with an explanatory tooltip.
- **Tasks** (REDIS-02 amendment A2):
  - [x] Edit `net.internetisalie.lunar.redis.debug.LuaRedisDebugRunner.canRun`: add
        `&& runProfile.execMode != LuaRedisExecMode.FCALL`; supply the tooltip text — realizes
        design §3.11, §7 A2
- **Exit criteria**: TC-DBG-1 green (EVAL still runnable under Debug; FCALL not).
- **Dependency note**: requires REDIS-02's `LuaRedisDebugRunner` to exist. If REDIS-02 is not yet
  implemented, this phase is a no-op stub deferred until REDIS-02 lands (tracked in
  risks-and-gaps Gap 2.2); REDIS-05's other phases do not depend on it.

### Phase 5: Functions panel — list/parse/deploy/delete/drift (AC-7, AC-8) [Should]
- **Goal**: a "Redis Functions" tool window listing libraries with flags + drift, Deploy/Delete.
- **Tasks**:
  - [ ] Create `LuaRedisFunctionListParser` (`RespValue` → `RedisLibraryEntry`/`RedisFunctionEntry`;
        RESP2 array-of-pairs + RESP3 map) — realizes design §2.7, §3.8
  - [ ] Create `LuaRedisFunctionDrift` (`compare(serverCode, localBody)` → sha1 normalized) —
        realizes design §2.8, §3.10
  - [ ] Create `LuaRedisFunctionsController` (`list`/`delete`/`deploy` over `RespClient`, suspend,
        per-call open/dispose) — realizes design §2.9, §3.9
  - [ ] Create `LuaRedisFunctionsPanel` + `LuaRedisFunctionsToolWindowFactory` (connection
        selector, tree with flag/drift glyphs, Deploy/Delete with `Messages.showYesNoDialog`;
        pooled coroutine → EDT publish; `DependencyTreePanel` pattern) — realizes design §2.10, §3.9
  - [ ] Register `<toolWindow id="Redis Functions" …>` in `plugin.xml` — realizes design §7
- **Exit criteria**: TC-PANEL-1, TC-PANEL-2, TC-PANEL-3, TC-DRIFT-1 green.

### Phase 6: Integration test & polish (AC-10) [Must]
- **Goal**: end-to-end load/list/call/replace/delete on dockerized `redis:8` + Valkey parity.
- **Tasks**:
  - [ ] Add `RedisFunctionsIntegrationTest` under the `redisIntegrationTest` task
        (REDIS-01 DR-04 / RISK-R10): deploy a `#!lua name=lib` library, `FUNCTION LIST`, `FCALL`,
        edit + `LOAD REPLACE`, `FUNCTION DELETE`; run against `redis:8` and `valkey/valkey:8` —
        covers TC-INT-1
  - [ ] Run `human-verification-checklists.md`
- **Exit criteria**: full `test` suite green (not isolated `--tests`); `run build`
  (checkStatus/koverVerify/lintDocs) green.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| AC-1 shebang recognition | C | Phase 1 |
| AC-2 `register_function` stubs | C | Phase 1 |
| AC-3 KEYS/ARGV-in-library inspection | C | Phase 2 |
| AC-4 FCALL run-config mode | C | Phase 3 |
| AC-5 `FCALL_RO` + no-writes hint | C | Phase 3 |
| AC-6 FCALL name validation | C | Phase 1 (scan), Phase 3 (validation) |
| AC-7 functions panel | C | Phase 5 |
| AC-8 drift indicator | C | Phase 5 |
| AC-9 Debug disabled for FCALL | C | Phase 4 |
| AC-10 integration + parity | C | Phase 6 |

## Verification Tasks
- [ ] Library-model tests (shebang detect variants, registered-name scan) — covers TC-SHB-1/2,
      TC-STUB-1/2, TC-SCAN-1
- [ ] KEYS/ARGV inspection tests (library vs non-library vs off-Redis) — covers TC-KEYS-1..3
- [ ] FCALL executor + checkConfiguration tests (deploy-only, call, RO, validation, dynamic-skip)
      — covers TC-MODE-1, TC-DEPLOY-1, TC-CALL-1, TC-RO-1/2, TC-VALID-1/2
- [ ] Debug-runner gate test (FCALL vs EVAL) — covers TC-DBG-1
- [ ] Panel parser/drift/controller tests — covers TC-PANEL-1..3, TC-DRIFT-1
- [ ] Docker integration test (redis:8 + valkey/valkey:8) — covers TC-INT-1
- [ ] Run `human-verification-checklists.md` (shebang render, register_function completion/typing,
      FCALL deploy round-trip, panel list/deploy/delete, Debug-disabled tooltip)

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Library model + shebang + register_function stub | todo | Must |
| Phase 2: KEYS/ARGV-in-library inspection | todo | Must |
| Phase 3: FCALL run-config mode + executor | todo | Must |
| Phase 4: Debug executor disable for FCALL | todo | Must |
| Phase 5: Functions panel | todo | Should |
| Phase 6: Integration test & polish | todo | Must |
