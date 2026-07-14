---
id: "REDIS-05-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "todo"
parent_id: "REDIS-05"
folders:
  - "[[features/redis/05-functions-workflow/requirements|requirements]]"
---

# REDIS-05: Risks & Gaps

Feature-level register. The epic-wide items that bind REDIS-05 are **RISK-R05** (LDB cannot
debug FCALL — realized here as the disabled Debug executor, design §3.11), **RISK-R08**
(`KEYS`/`ARGV` ambient mechanics — REDIS-05's library-file suppression is a scoped inspection,
not an engine change), and **RISK-R10** (Docker in CI — the integration test rides REDIS-01's
`redisIntegrationTest` task / DR-04). See the epic register
([../redis-risks-and-gaps.md](../redis-risks-and-gaps.md)); this doc records only what is new or
sharpened at the REDIS-05 design level.

## Critical Risks

### Risk 1.1: KEYS/ARGV-in-library inspection false positives
- **Impact**: WARNING on a legitimate use (e.g. a non-Function file misdetected as a library, or a
  local named `KEYS`) erodes trust.
- **Likelihood**: low.
- **Mitigation**: the inspection is double-gated — Redis-7+/Valkey target **and**
  `LuaRedisFunctionLibrary.isLibrary` true (design §3.4); it skips declaration/member positions
  (mirrors `LuaDeprecatedApiInspection.isDeclaration`, `LuaDeprecatedApiInspection.kt:59`) and
  honours `LuaInspectionSuppression`. It ships at WARNING (never ERROR). TC-KEYS-2/3 pin the
  non-library and off-Redis no-op paths.

### Risk 1.2: Cross-feature amendment ordering (REDIS-01 A1 / REDIS-02 A2)
- **Impact**: REDIS-05 depends on enabling REDIS-01's reserved `FCALL` slot and gating REDIS-02's
  `LuaRedisDebugRunner.canRun`. If those seams drift (renamed enum/options fields), REDIS-05
  breaks.
- **Likelihood**: medium (three features touch `LuaRedisRunConfiguration`).
- **Mitigation**: both amendments are additive and explicitly recorded (design §7 A1/A2); REDIS-01
  risks §"Public Seams" already reserves `LuaRedisExecMode.FCALL` for REDIS-05, and REDIS-02 risks
  already anticipate the FCALL Debug disable. DR-01 confirms the seam signatures at implementation
  start. No REDIS-01/02 **requirement** changes — design-level edits only.

### Risk 1.3: `FUNCTION LIST` reply shape varies (RESP2 vs RESP3, WITHCODE availability)
- **Impact**: a wrong parse yields an empty or wrong panel model.
- **Likelihood**: medium (RESP2 returns an array-of-pairs; RESP3 a map; `library_code` only with
  WITHCODE, and only on servers that support it).
- **Mitigation**: `LuaRedisFunctionListParser` handles both shapes via the `asPairs` coercion
  (design §3.8) and degrades missing fields to empty; `LuaRedisFunctionDrift` returns `UNKNOWN`
  when `library_code` is absent (design §3.10, TC-DRIFT-1). DR-02 validates both protocols against
  a live `redis:8` in the integration test.

## Design Gaps

### Gap 2.4: no expected-type → lambda-parameter inference (AC-2 callback typing) — **descoped 2026-07-14**
Design §3.3's original premise (the engine auto-types an un-annotated callback's `keys`/`args` as
`string[]` from `register_function`'s stub `fun(keys: string[], args: string[])` annotation) was
**ground-truth-false**. An empirical probe proved the bundled type engine propagates parameter
types only from a **direct** `---@param` on the function, not from the **expected** function type of
the argument slot a lambda is passed into (V3 `rf(function(k) k[1] end)` with `rf: fun(cb:
fun(k: string[]))` → `Undefined`).
- **Impact:** `redis.register_function('f', function(keys, args) … end)` does not auto-type
  `keys`/`args`; the user annotates them (`---@param keys string[]`), which works.
- **Decision:** given REDIS-05 is Priority **Could**, descope callback-param auto-typing rather than
  take on high-blast-radius shared-engine work. `register_function` still resolves + hover-documents
  both overloads (the primary editing value). See design §3.3, requirements AC-2 / TC-STUB-1/2.
- **Deferred enhancement:** general expected-type→lambda-param inference in `LuaTypesVisitor`
  (`visitFuncCall` unification) — benefits all `fun(...)`-typed callback slots plugin-wide
  (`table.sort`, `pcall`, etc.). Belongs in the TYPE epic, gated by the REDIS-04 §3.1c-style
  regression contract. Filed as a follow-up task.

_Otherwise none open._ Every other REDIS-05 design decision is pinned in design.md. Decisions that
could have been left open were resolved:
- **Shebang recognition mechanism** — resolved: reuse the existing `SHEBANG`+`SHORTCOMMENT` lexing
  (design §1, §3.1) + a static regex detector; no grammar change (avoids MAINT-20 generation
  fragility).
- **Function-name validation source** — resolved: edit-time static scan
  (`LuaRedisFunctionLibrary.registeredNames`, design §3.7), not a live `FUNCTION LIST` (no live
  connection at `checkConfiguration` time, matching REDIS-01 §3.7).
- **`no-writes` enforcement** — resolved: server-authoritative (`FCALL_RO`); the client shows the
  server error + an editor hint, never a client-side write block (design §3.5, §9).
- **New config type vs mode-on-existing** — resolved: reuse REDIS-01's `LuaRedisRunConfiguration`
  and its reserved `FCALL` slot (design §9).

## Technical Debt & Future Work
- **TBD: FCALL debugging** — LDB cannot debug Function invocations (epic RISK-R05); REDIS-05 leaves
  the Debug executor disabled for FCALL (design §3.11). Revisit if upstream adds FCALL debugging.
- **TBD: multi-engine / non-Lua Functions** — Redis Functions are currently Lua-only; if a second
  engine appears, `LuaRedisFunctionListParser` reads `engine` but REDIS-05 ignores it.
- **TBD: live-completion of loaded function names in FCALL config** — the run-config function-name
  field validates against the static library scan; completing from the server's live
  `FUNCTION LIST` is future UX (would couple the editor to a connection).
- **TBD: panel auto-refresh** — the panel refreshes on demand; a push/poll refresh on connect is
  future work (epic non-goal: no RESP3 push tooling).

## Public Seams (none downstream)
REDIS-05 is the final epic feature; it exposes no seam other features consume. It **consumes**:
- REDIS-01: `RespClient`, `RespValue`, `LuaRedisServerConnection`/`LuaRedisConnectionSettings`,
  `LuaRedisRunConfiguration`/`Options`/`LuaRedisExecMode.FCALL`, `LuaRedisRunProfileState`,
  `LuaRedisScriptExecutor` command shape, `RespReplyTreeConsole`, `LuaRedisSettingsEditor`.
- REDIS-02: `LuaRedisDebugRunner` (amendment A2).
- REDIS-04: `RedisCallSiteMatcher`/`RedisCallSite`, `RedisCommandSpecService`, `isRedisTarget`,
  `LuaInspectionSuppression`, the stub-declared-global ambient seam.
- REDIS-03: `LuaPlatform.VALKEY` + `---@class server : redis` stub inheritance (soft; Valkey
  parity for `server.register_function` and `FUNCTION *`).

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| REDIS-05-DR-01 | Confirm REDIS-01 seam signatures at impl start: `LuaRedisExecMode.FCALL` present, `LuaRedisRunConfigurationOptions` uses `string()` StoredProperties, `LuaRedisRunProfileState.execute` dispatches per `execMode`; confirm REDIS-02 `LuaRedisDebugRunner.canRun` shape | Risk 1.2 | todo |
| REDIS-05-DR-02 | Against dockerized `redis:8` + `valkey/valkey:8`: capture `FUNCTION LIST` and `FUNCTION LIST WITHCODE` replies in RESP2 and RESP3; confirm the §3.8 parse and §3.10 drift on both flavors | Risk 1.3 | todo |

## Test Case Gaps
- Live `FCALL_RO` write-rejection (TC-RO-1) and the full deploy/list/call/replace/delete loop are
  exercised only under Docker (Phase 6) — no unit coverage for the live server-error surface;
  covered by the integration test + human checklist.
- The Debug-disabled **tooltip text** presentation (TC-DBG-1 covers `canRun == false`) is verified
  by the human checklist §4, not a unit test (platform greys the action).

## See Also
- Epic register: [../redis-risks-and-gaps.md](../redis-risks-and-gaps.md)
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
