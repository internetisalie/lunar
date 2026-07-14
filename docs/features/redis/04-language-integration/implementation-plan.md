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

Phases are independently testable and leave the build green. Most Kotlin lands under
`net.internetisalie.lunar.analysis.redis` (inspections/service/matcher/doc) and
`net.internetisalie.lunar.lang.completion` (completion). **Phase 1a additionally edits the
shared type engine** in `net.internetisalie.lunar.lang.psi.types`
(`LuaTypesVisitor`, `LuaTypeGraph`) — a high-blast-radius seam gated by the §3.1c regression
contract. Resource edits under
`src/main/resources/{runtime/redis,commandspec,inspectionDescriptions}`. Every task names the
file it creates/edits and the design section it realizes.

## Phases

<!-- Re-plan 2026-07-14 (DR-03 re-opened, REDIS-04-phase-1 ABORT_REPLAN): the original Phase 1
     assumed "resource-edits only, no engine changes". Ground-truthing proved AC-1 needs real
     type-engine work (stub-global inference + array-subscript element inference). Phase 1 is
     split into 1a (engine, STRONG, high-blast-radius) and 1b (stubs + wiring + TC tests).
     AC-6 (pcall union) and the on-disk stubs/replicate_commands are unaffected and land in 1b. -->

### Phase 1a: Type-engine — stub-global inference + array-subscript element inference (AC-1) [Must]
- **Goal**: the single-file type engine (a) seeds the active target's ambient stub globals
  (`KEYS`/`ARGV` as `string[]`) into inference, and (b) infers `arr[1] → T` for any `T[]`
  receiver — so `getValueType(KEYS[1]) == string`. **High blast radius**: `visitIndexExpr` and
  the single-file inference path are the shared "Serial: type-engine" seam.
- **Grounded change points** (cite from design):
  - [x] **§3.1a** — added `seedAmbientGlobals(file)` to `LuaTypesVisitor`, invoked in the static
        `buildSnapshot` before `file.accept(visitor)`; reads the target's `global.lua` via
        `RuntimeLibraryProvider(project).getLibraryFiles(target)`, injects each top-level bare
        global's `@type` via `LuaTypeGraphBridge.injectTypeAnnotation`, and
        `scope.declare(name, node)`. Comment attachment walks up parents (`ambientCatsComments`)
        because the stub's first statement sits inside a `BLOCK` whose leading `---@type` comment
        is a file-level sibling — realizes design §3.1a
  - [x] **§3.1b** — extended `visitIndexExpr` with a bracket branch
        (`nameRef == null && o.expr != null`) → `seedSubscriptElement`, which reads the receiver's
        resolved `Array(T)` element type (`arrayElementType`, unwrapping a `{ ... } | T[]` union)
        and emits `graph.value(o, T)`. **DR-03a seam decision: seam (b)** — seam (a) is not
        implementable without adding a node-carrying field to the shared `Array` data class (it
        holds a *type*, not a node) or graph side-channel/sentinel state (higher blast radius,
        against the minimal/regression-safe mandate; reviewer flagged seam (a) "under-specified").
        Seam (b) reads a stable pre-fixed-point value because the receiver's `@type`/seed edge is
        wired before `visitIndexExpr` runs (decl precedes use) — realizes design §3.1b
  - [x] **§3.1b `#`-sub-fix** — widened the `#`-operand `use` constraint at `LuaTypesVisitor.kt`
        to admit `LuaGraphType.Array(Any)` so `#array` does not emit a spurious "not assignable"
        — realizes design §3.1b (`#ARGV` note)
- **Regression contract (§3.1c) — required, not optional**:
  - [x] Dotted member access unchanged (`nameRef != null` branch untouched).
  - [x] Non-array bracket / non-Redis global typing unchanged (TC-IDX-2 / TC-KEYS-3 structural).
  - [x] The FULL `.../lang/types/*` suite stays green on a full-suite run (not isolated
        `--tests`, per the synthetic-lambda masking note): `TestLuaTypeEnginePhase1`,
        `TestFlowSensitiveType`, `CrossFileInferenceTest`, `UnionAndGenericTest`,
        `PrimitiveTypeCompatibilityTest`, `LuaUnionDistributionTest`, `TableTypeTest`,
        `QualifiedMemberResolutionTest`, `ReceiverAwareMemberResolutionTest`,
        `MemberFieldIndexTest`, `FunctionSignatureMatchingTest`, `LuaMethodMembersTest`,
        `MultiReturnValueTest`.
  - [x] Downstream consumers unregressed (build gate covers their suites):
        `LuaTypeAssignabilityInspection`, `LuaReturnTypeMismatchInspection`,
        `LuaSuspiciousConcatenationInspection`, `LuaInferredTypeAnnotator`, and the three
        inlay-hint providers.
  - [x] **DR-03b** — the `LuaTypes.forFile` snapshot cache key now folds the active `Target`
        hash with the document-text hash, so a target switch invalidates it automatically;
        `LuaTypesVisitor.getTypes` delegates to the same entry point so the two consumers share
        one KEY/hash scheme (no thrash). Verified by `StubGlobalSeedTypeTest` target-switch test.
- **Tests (real-flow + unit)**: TC-SEED-1 (§3.1a), TC-IDX-1 (§3.1b), TC-IDX-2 + TC-IDX-3
  (§3.1c regression) — added under `src/test/kotlin/net/internetisalie/lunar/lang/types/`
  (`ArraySubscriptTypeTest`, `StubGlobalSeedTypeTest`; using `LuaTypesSnapshot.forFile`).
- **Exit criteria**: TC-SEED-1, TC-IDX-1, TC-IDX-2, TC-IDX-3 green AND the full type-engine
  suite (above) green AND `run build` (checkStatus/koverVerify/integrationTest) green.

### Phase 1b: Redis stub resources + ambient-typing wiring (AC-1 tests, AC-6) [Must]
- **Goal**: the bundled Redis stubs + `pcall` union consumed by the Phase-1a engine; `KEYS[1]`
  and `redis.pcall` narrowing prove out end-to-end under a real Redis target. **The stub work
  below is ALREADY ON DISK (uncommitted) from the aborted Phase 1 and verified correct by the
  handoff — preserve it; do NOT redo.**
- **Tasks**:
  - [x] `src/main/resources/runtime/redis/redis-5/` and `redis-6/` mirror the
        `redis-7` file set, trimmed per design §3.10 (redis-5 drops `setresp`+`acl_check_cmd`;
        redis-6 drops `acl_check_cmd`; `replicate_commands` in all three) — realizes §2.1, §3.10
  - [x] `---@return boolean\nfunction redis.replicate_commands() end` added to
        every `runtime/redis/*/redis.lua` — realizes design §2.1
  - [x] `redis.pcall` `@return` changed to `any|{ err: string }` in every
        `runtime/redis/*/redis.lua` (+ valkey-7.2/8 kept byte-in-sync — the valkey `redis.*`
        namespace is the redis-7-compat mirror; see §Valkey-consistency below) — realizes §2.1, §4.2
  - [x] Verified + completed the on-disk `RedisAmbientTypingTest.kt`: TC-KEYS-1 (type inference)
        PASSES post-1a; added TC-KEYS-2 (`#ARGV`→number), TC-KEYS-3 (off-Redis undeclared),
        TC-PCALL-1 (pcall `{err:string}` union member), a **redis-5** coverage test, and a
        **chained-subscript** (`KEYS[1][2]`) bounded-behavior regression. Real Redis target via
        `setTargetAndNotify(Target(REDIS, …))` + `PlatformLibraryIndex.reload()`
        (ValkeyStubResourceTest idiom).
  - **NOTE (resolution sub-assertion split)**: requirements TC-KEYS-1 also names "no
    undeclared-variable highlight on KEYS/ARGV". That rides the *reference-resolution*
    (`multiResolve` → platform library-bindings `FileBasedIndex`) path, not the type-inference
    path. Empirically confirmed in a light fixture: the stub `global.lua` is served from the
    plugin jar (`jar:` URL) and is **not** FileBasedIndex-indexed, so bare-global `KEYS` never
    resolves and the warning fires regardless of target (a project-file `KEYS = {}` copy also
    fails to resolve — bare-global assignments are not stub-indexed). This is a
    fixture/packaging limitation, **not** a REDIS-04 resolution gap; a real IDE indexes library
    roots. The live "no undeclared on KEYS/ARGV" behavior is covered by
    human-verification-checklists Scenario 4.1. Tracked as risks-and-gaps §Gap 2.3.
- **Depends on**: Phase 1a (TC-KEYS-1/2/3 cannot pass without the engine work).
- **Exit criteria**: TC-KEYS-1 (type), TC-KEYS-2, TC-KEYS-3, TC-PCALL-1, redis-5 coverage,
  chained-subscript green.
- **Valkey-consistency decision (for reviewer)**: `runtime/valkey/valkey-7.2/redis.lua` and
  `valkey-8/redis.lua` are byte-identical redis-7-compat mirrors (copied by REDIS-03). The 1b
  `pcall` union + `replicate_commands` edits were applied to them too, keeping `redis.pcall`
  narrowing (AC-6) and the future determinism guard working under Valkey targets. This is
  deliberate (the Valkey `redis.*` namespace is the redis-compat surface); the three files stay
  in sync.

### Phase 2: Command-spec service + bundled data (AC-2) [Must] — done
- **Goal**: `RedisCommandSpecService.specFor(target)` returns a cached, parsed spec.
- **Tasks**:
  - [x] Vendor `src/main/resources/commandspec/{redis-5,redis-6,redis-7}.json` from the
        BSD-3-Clause Valkey source (`valkey-io/valkey`, `src/commands/*.json`, pinned tag
        `8.0.0`), reduced to the §4.1 schema (offline vendoring script
        `scripts/vendor_redis_commandspec.py`; not runtime code). NO Redis-repo
        `commands.json` (RSALv2/SSPLv1/AGPLv3) data used — realizes design §4.1
  - [x] Create `RedisCommandSpec`/`RedisCommandInfo` data classes — realizes design §2.2
  - [x] Create `RedisCommandSpecService` (`@Service(Service.Level.APP)`, Gson, lazy
        `synchronized` memo keyed on resolved resource path; `specFor` → `EMPTY` when absent)
        — realizes design §2.2, §3.2, §4.1
  - [x] Register `<applicationService>` in `plugin.xml` — realizes design §7
- **Exit criteria**: TC-SPEC-1, TC-SPEC-2 green. **Met** (full suite 1917 tests / 0 failed).

### Phase 3: Call-site matcher (shared seam) [Must] — done
- **Goal**: single source of truth for `redis.call`/`pcall` (`server.*`) call-shape parsing.
- **Tasks**:
  - [x] Create `RedisCallSite` data class + `RedisCallSiteMatcher.match(anchor)` walking
        `LuaFuncCall.varOrExp.var.nameRef` (namespace) + `varSuffix.indexExpr.nameRef`
        (member) + `nameAndArgsList[0].args` (command literal via `LuaTerminalExpr.string`,
        arg count via `exprList`) — realizes design §2.10 (grounded on
        `LuaRequireReferenceContributor.kt:24,29`, `lua.bnf:270-281`, and
        `LuaTypesVisitor.extractModuleName` `LuaTypesVisitor.kt:66-73`). The `member` field is
        carried **verbatim** (not restricted to `call`/`pcall`) so REDIS-05 reuses the seam for
        `register_function` unchanged; downstream consumers filter on `member`.
- **Exit criteria**: unit test of the matcher on literal, member (`redis.pcall`), namespace
  (`server.call`/`server.pcall`), non-literal (commandName null), and non-Redis call shapes
  (feeds TC-COMP-3/TC-UNK-2). **Met** — `RedisCallSiteMatcherTest` (11 tests) green; full suite
  1928 tests / 0 failed.

### Phase 4: Command completion (AC-3) [Should]
- **Goal**: version-valid command names inside the first string arg.
- **Tasks**:
  - [x] Create `LuaRedisCommandCompletionContributor` (§3.3 sourcing, §3.11 `sinceLe` filter,
        summary tail text) — realizes design §2.3, §3.3, §3.11
  - [x] Register `<completion.contributor language="Lua" …>` — realizes design §7
- **Exit criteria**: TC-COMP-1..4 green.

### Phase 5: Command inspection + quick fix + determinism (AC-4, AC-9) [Should]
- **Goal**: unknown/arity WARNINGs with did-you-mean fix; Redis 5/6 determinism WARNING.
- **Tasks**:
  - [x] Create `LuaRedisCommandInspection` (`visitFuncCall`; §3.4 unknown+arity; §3.9
        determinism, version-gated) with `LuaInspectionSuppression` guards — realizes design
        §2.4, §2.9, §3.4, §3.9
  - [x] Create `LuaRedisRenameCommandQuickFix` (`WriteCommandAction`, `LuaElementFactory`) and
        the §3.5 Levenshtein suggestion helper — realizes design §2.5, §3.5
  - [x] Add `inspectionDescriptions/LuaRedisCommand.html`; register `<localInspection
        shortName="LuaRedisCommand">` — realizes design §7
- **Exit criteria**: TC-ARITY-1, TC-ARITY-2, TC-UNK-1, TC-UNK-2, TC-DET-1..4 green.

### Phase 6: Sandbox inspection + Global-creation escalation + quick doc (AC-5, AC-7, AC-8) [Should]
- **Goal**: sandbox-API WARNINGs; Redis-target ERROR escalation; command quick doc.
- **Tasks**:
  - [x] Create `RedisSandboxAllowlist.forTarget(target)` (derive from stub roots +
        `os.lua` members via `RuntimeLibraryProvider`, cached per path segment) — realizes
        design §3.7
  - [x] Create `LuaRedisSandboxInspection` (`visitNameRef`; declaration skip mirrors
        `LuaDeprecatedApiInspection.isDeclaration`) + `inspectionDescriptions/
        LuaRedisSandbox.html`; register `<localInspection shortName="LuaRedisSandbox">` —
        realizes design §2.7, §3.7, §7
  - [x] Edit `LuaGlobalCreationInspection`: add `isRedisTarget(project)` and select
        `ProblemHighlightType.ERROR` vs `GENERIC_ERROR_OR_WARNING` at the `registerProblem`
        site — realizes design §2.8, §3.8
  - [x] Create `RedisCommandDocumentationTargetProvider` +
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
| AC-1 `KEYS`/`ARGV` typing | S | Phase 1a (engine) + Phase 1b (tests) |
| AC-2 command-spec service | S | Phase 2 |
| AC-3 command completion | S | Phase 3, Phase 4 |
| AC-4 unknown/arity inspection + fix | S | Phase 3, Phase 5 |
| AC-5 command quick doc | S | Phase 6 |
| AC-6 `pcall` union narrowing | S | Phase 1b |
| AC-7 sandbox inspection | S | Phase 6 |
| AC-8 global-creation escalation | S | Phase 6 |
| AC-9 determinism inspection | S | Phase 5 |
| AC-10 unit tests | S | Phase 7 |

## Verification Tasks
- [x] Type-engine unit tests — covers TC-SEED-1 (§3.1a), TC-IDX-1 (§3.1b), TC-IDX-2/3 (§3.1c
      regression) + full `.../lang/types/*` suite green
- [x] Ambient typing tests — covers TC-KEYS-1 (type), TC-KEYS-2, TC-KEYS-3, TC-PCALL-1,
      redis-5 coverage, chained-subscript regression (TC-KEYS-1 resolution sub-assertion →
      human-verification Scenario 4.1, see risks §Gap 2.3)
- [x] Command-spec service tests — covers TC-SPEC-1, TC-SPEC-2
- [ ] Completion tests (per-version filter, non-literal, off-Redis) — covers TC-COMP-1..4
- [ ] Command inspection + quick fix tests — covers TC-ARITY-1/2, TC-UNK-1/2
- [ ] Determinism tests — covers TC-DET-1..4
- [ ] Sandbox + global-escalation tests — covers TC-SBX-1..3, TC-GLOB-1/2
- [ ] Quick-doc test — covers TC-DOC-1
- [ ] Run `human-verification-checklists.md` (completion popup, doc popup, live ERROR gutter)

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1a: Type-engine (stub-global + array-subscript inference) | done | Must |
| Phase 1b: Redis stub resources + ambient-typing wiring | done | Must |
| Phase 2: Command-spec service + data | done | Must |
| Phase 3: Call-site matcher | done | Must |
| Phase 4: Command completion | todo | Should |
| Phase 5: Command inspection + fix + determinism | todo | Should |
| Phase 6: Sandbox + escalation + quick doc | todo | Should |
| Phase 7: Test suite & polish | todo | Must |
