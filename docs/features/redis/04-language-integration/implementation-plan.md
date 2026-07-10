---
id: "REDIS-04-PLAN"
title: "Implementation Plan"
type: "plan"
status: "todo"
parent_id: "REDIS-04"
folders:
  - "[[features/redis/04-language-integration/requirements|requirements]]"
---

# REDIS-04: Implementation Plan

Phases are independently testable and leave the build green. All Kotlin lands under
`net.internetisalie.lunar.analysis.redis` (inspections/service/matcher/doc) and
`net.internetisalie.lunar.lang.completion` (completion). Resource edits under
`src/main/resources/{runtime/redis,commandspec,inspectionDescriptions}`. Every task names the
file it creates/edits and the design section it realizes.

## Phases

### Phase 1: Stubs & ambient typing (AC-1, AC-6) [Must]
- **Goal**: `KEYS`/`ARGV` type as `string[]` and `redis.pcall` narrows for all registered
  Redis versions; add `redis.replicate_commands`.
- **Tasks**:
  - [ ] Create `src/main/resources/runtime/redis/redis-5/` and `redis-6/` mirroring the
        `redis-7` file set, trimmed per design §3.10 — realizes design §2.1, §3.10
  - [ ] Add `---@return boolean\nfunction redis.replicate_commands() end` to every
        `runtime/redis/*/redis.lua` — realizes design §2.1
  - [ ] Change `redis.pcall` `@return` to `any|{ err: string }` in every
        `runtime/redis/*/redis.lua` — realizes design §2.1, §4.2
- **Exit criteria**: TC-KEYS-1, TC-KEYS-2, TC-KEYS-3, TC-PCALL-1 green.

### Phase 2: Command-spec service + bundled data (AC-2) [Must]
- **Goal**: `RedisCommandSpecService.specFor(target)` returns a cached, parsed spec.
- **Tasks**:
  - [ ] Vendor `src/main/resources/commandspec/{redis-5,redis-6,redis-7}.json` from the
        BSD-Valkey source, reduced to the §4.1 schema (offline vendoring script; not runtime
        code) — realizes design §4.1
  - [ ] Create `RedisCommandSpec`/`RedisCommandInfo` data classes — realizes design §2.2
  - [ ] Create `RedisCommandSpecService` (`@Service(Service.Level.APP)`, Gson, lazy
        `synchronized` memo keyed on resolved resource path; `specFor` → `EMPTY` when absent)
        — realizes design §2.2, §3.2, §4.1
  - [ ] Register `<applicationService>` in `plugin.xml` — realizes design §7
- **Exit criteria**: TC-SPEC-1, TC-SPEC-2 green.

### Phase 3: Call-site matcher (shared seam) [Must]
- **Goal**: single source of truth for `redis.call`/`pcall` (`server.*`) call-shape parsing.
- **Tasks**:
  - [ ] Create `RedisCallSite` data class + `RedisCallSiteMatcher.match(anchor)` walking
        `LuaFuncCall.varOrExp.var.nameRef` (namespace) + `varSuffix.indexExpr.nameRef`
        (member) + `nameAndArgsList[0].args` (command literal via `LuaTerminalExpr.string`,
        arg count via `exprList`) — realizes design §2.10 (grounded on
        `LuaRequireReferenceContributor`, `lua.bnf:270-281`)
- **Exit criteria**: unit test of the matcher on literal, member (`redis.call`), non-literal,
  and non-Redis call shapes (feeds TC-COMP-3/TC-UNK-2).

### Phase 4: Command completion (AC-3) [Should]
- **Goal**: version-valid command names inside the first string arg.
- **Tasks**:
  - [ ] Create `LuaRedisCommandCompletionContributor` (§3.3 sourcing, §3.11 `sinceLe` filter,
        summary tail text) — realizes design §2.3, §3.3, §3.11
  - [ ] Register `<completion.contributor language="Lua" …>` — realizes design §7
- **Exit criteria**: TC-COMP-1..4 green.

### Phase 5: Command inspection + quick fix + determinism (AC-4, AC-9) [Should]
- **Goal**: unknown/arity WARNINGs with did-you-mean fix; Redis 5/6 determinism WARNING.
- **Tasks**:
  - [ ] Create `LuaRedisCommandInspection` (`visitFuncCall`; §3.4 unknown+arity; §3.9
        determinism, version-gated) with `LuaInspectionSuppression` guards — realizes design
        §2.4, §2.9, §3.4, §3.9
  - [ ] Create `LuaRedisRenameCommandQuickFix` (`WriteCommandAction`, `LuaElementFactory`) and
        the §3.5 Levenshtein suggestion helper — realizes design §2.5, §3.5
  - [ ] Add `inspectionDescriptions/LuaRedisCommand.html`; register `<localInspection
        shortName="LuaRedisCommand">` — realizes design §7
- **Exit criteria**: TC-ARITY-1, TC-ARITY-2, TC-UNK-1, TC-UNK-2, TC-DET-1..4 green.

### Phase 6: Sandbox inspection + Global-creation escalation + quick doc (AC-5, AC-7, AC-8) [Should]
- **Goal**: sandbox-API WARNINGs; Redis-target ERROR escalation; command quick doc.
- **Tasks**:
  - [ ] Create `RedisSandboxAllowlist.forTarget(target)` (derive from stub roots +
        `os.lua` members via `RuntimeLibraryProvider`, cached per path segment) — realizes
        design §3.7
  - [ ] Create `LuaRedisSandboxInspection` (`visitNameRef`; declaration skip mirrors
        `LuaDeprecatedApiInspection.isDeclaration`) + `inspectionDescriptions/
        LuaRedisSandbox.html`; register `<localInspection shortName="LuaRedisSandbox">` —
        realizes design §2.7, §3.7, §7
  - [ ] Edit `LuaGlobalCreationInspection`: add `isRedisTarget(project)` and select
        `ProblemHighlightType.ERROR` vs `GENERIC_ERROR_OR_WARNING` at the `registerProblem`
        site — realizes design §2.8, §3.8
  - [ ] Create `RedisCommandDocumentationTargetProvider` +
        `RedisCommandDocumentationTarget` (`DocumentationResult.documentation(...)`); register
        `<platform.backend.documentation.targetProvider>` — realizes design §2.5(doc), §3.6,
        §7
- **Exit criteria**: TC-SBX-1..3, TC-GLOB-1, TC-GLOB-2, TC-DOC-1 green.

### Phase 7: Test suite & polish (AC-10) [Must]
- **Goal**: all TC-* automated on `BasePlatformTestCase`; no-op behavior off Redis proven.
- **Tasks**:
  - [ ] Add test classes under `src/test/kotlin/net/internetisalie/lunar/analysis/redis/` and
        `.../lang/completion/` covering every TC (target set via
        `settings.setTargetAndNotify(Target(LuaPlatform.REDIS, VersionEntry(...)))`, the idiom
        from `LibraryLoadingAfterTargetChangeTest.kt:96`) — covers all TC-*
  - [ ] Run `human-verification-checklists.md`
- **Exit criteria**: full `test` suite green (not isolated `--tests`, per memory note on
  synthetic-lambda masking); `run build` (checkStatus/koverVerify) green.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| AC-1 `KEYS`/`ARGV` typing | S | Phase 1 |
| AC-2 command-spec service | S | Phase 2 |
| AC-3 command completion | S | Phase 3, Phase 4 |
| AC-4 unknown/arity inspection + fix | S | Phase 3, Phase 5 |
| AC-5 command quick doc | S | Phase 6 |
| AC-6 `pcall` union narrowing | S | Phase 1 |
| AC-7 sandbox inspection | S | Phase 6 |
| AC-8 global-creation escalation | S | Phase 6 |
| AC-9 determinism inspection | S | Phase 5 |
| AC-10 unit tests | S | Phase 7 |

## Verification Tasks
- [ ] Ambient typing tests — covers TC-KEYS-1..3, TC-PCALL-1
- [ ] Command-spec service tests — covers TC-SPEC-1, TC-SPEC-2
- [ ] Completion tests (per-version filter, non-literal, off-Redis) — covers TC-COMP-1..4
- [ ] Command inspection + quick fix tests — covers TC-ARITY-1/2, TC-UNK-1/2
- [ ] Determinism tests — covers TC-DET-1..4
- [ ] Sandbox + global-escalation tests — covers TC-SBX-1..3, TC-GLOB-1/2
- [ ] Quick-doc test — covers TC-DOC-1
- [ ] Run `human-verification-checklists.md` (completion popup, doc popup, live ERROR gutter)

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Stubs & ambient typing | todo | Must |
| Phase 2: Command-spec service + data | todo | Must |
| Phase 3: Call-site matcher | todo | Must |
| Phase 4: Command completion | todo | Should |
| Phase 5: Command inspection + fix + determinism | todo | Should |
| Phase 6: Sandbox + escalation + quick doc | todo | Should |
| Phase 7: Test suite & polish | todo | Must |
