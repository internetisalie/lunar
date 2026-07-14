---
id: "REDIS-04-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "todo"
parent_id: "REDIS-04"
folders:
  - "[[features/redis/04-language-integration/requirements|requirements]]"
---

# REDIS-04: Risks & Gaps

Feature-level register. DR-02 (spec source) is resolved below. **DR-03 (typing approach) was
re-opened on 2026-07-14** after the Phase-1 ABORT_REPLAN proved the "no engine changes" premise
false — it is re-resolved as bounded, grounded type-engine work (design §3.1a-c, Phase 1a) with
two tracked sub-decisions (DR-03a/DR-03b). See the epic register
([../redis-risks-and-gaps.md](../redis-risks-and-gaps.md)) for RISK-R04, R06, R07, R08 context.

## RESOLVED (re-plan 2026-07-14) — AC-1 typing premise was false; DR-03 re-opened as engine work

**Status: RE-PLANNED.** The blocker below was ground-truthed correct. DR-03 is re-opened and
redefined as the concrete engine work in design §3.1a/§3.1b/§3.1c, and implementation-plan
Phase 1 is split into Phase 1a (engine) + Phase 1b (stubs/wiring). The two engine root causes
are grounded at `file:line` in design §3.1a (single-file inference, `LuaTypesVisitor.kt:25,676-680,
782-787`) and §3.1b (`visitIndexExpr` dotted-only, `LuaTypesVisitor.kt:644-658`). AC-1 remains a
`Must` (handoff Option A taken; Option B — silently downgrading AC-1 — rejected). The original
blocker analysis is retained verbatim below as the audit trail.

---

**(original blocker, verbatim) Status: ABORT_REPLAN — Phase 1 cannot meet its exit criteria; DR-03 must be re-opened.**

Design §3.1 and TC-KEYS-1 assert that, under `Target(REDIS, "7+")`, the *existing* type engine
resolves `getValueType(exprFor("KEYS[1]"))` to `string` "with no engine changes", delegating to
the library-roots + type engine, and that `KEYS`/`ARGV` produce no undeclared-variable warning.
Empirical ground-truthing (temporary probe through the real `LuaTypesSnapshot.forFile` on the
gce-builder, under a real `Target(REDIS, "7+")`) shows this is **false on two independent counts**:

1. **The Redis `global.lua` stub is not on scope for ambient-global *type inference* in the
   light fixture.** Under `Target(REDIS, "7+")`, `KEYS` still raises
   `Undeclared variable 'KEYS'` and `getValueType(KEYS)` / `getValueType(ARGV)` both return
   `undefined` — the stub-declared `---@type string[]` binding is not consumed as an ambient
   type. (Reference *resolution* of `redis.call` DOES work through the same roots — see
   REDIS-03 `ValkeyStubResourceTest` — so this is specifically a **type-inference vs
   reference-resolution** gap for stub-declared *globals*, not a library-root wiring failure.)

2. **The type engine has no array/subscript element-type inference.** Even a directly-annotated
   local sidesteps the scope question and still fails: `---@type string[] local arr = {}` infers
   `arr` as `{ ... } | string[]` (the expected union), but `arr[1]` infers **`undefined`**. Root
   cause: `LuaTypesVisitor.visitIndexExpr` (`.../lang/psi/types/LuaTypesVisitor.kt:644-658`) only
   models **dotted member access** (`o.nameRef != null` → `a.b`); a **bracket subscript**
   (`KEYS[1]`, `arr[1]`) has a null `nameRef`, so the branch is skipped and no element node is
   recorded. There is no `LuaGraphType.Array`-element propagation anywhere in the visitor. So even
   if finding #1 were fixed, `KEYS[1]` could never infer `string` under the current engine.

**Probe evidence** (real engine, `Target(REDIS, "7+")`):
`KEYS[1]=undefined  #ARGV=number  ARGV=undefined  KEYS=undefined  redisUndeclared=[Undeclared
variable 'KEYS']  arr(@type string[])={ ... } | string[]  arr[1]=undefined`.
Note `#ARGV=number` passes TC-KEYS-2 but **for the wrong reason** — the `#` operator is modeled
as always-`number` regardless of operand type, not because `ARGV` resolved to `string[]`.

**What DOES hold (AC-6 is fine):** `redis.pcall("GET", …)` infers `{ ... }` whose members include
`err`, so the union-return stub edit (`---@return any|{ err: string }`) is correctly consumed —
TC-PCALL-1 passes on real engine resolution. AC-6's Phase-1 resource edits are sound.

**Why this is a replan, not a workaround:** the fix is out of Phase 1's "resource-edits-only"
scope and contradicts design §3.1's "no new engine code" premise. Making TC-KEYS-1 green requires
EITHER (a) new type-engine work — `visitIndexExpr` (or `visitVarSuffix`) must propagate
`LuaGraphType.Array.elementType` for numeric/bracket subscripts, AND ambient stub-declared globals
must feed the inference graph (not only reference resolution) — OR (b) a redesign of AC-1's
verification contract (e.g. assert reference-resolution / no-undeclared instead of `getValueType ==
string`, and relocate the `string[]`-index inference to a new engine task). Both are DR-03
re-open decisions for the planner. Asserting `undefined` (or deleting TC-KEYS-1) to force a green
Phase 1 would be a rogue workaround that silently drops a Must acceptance criterion — explicitly
disallowed by the Abort & Replan protocol.

**On-disk artifacts left in place for the replan** (uncommitted, branch
`claude/modest-newton-5d8280`): the redis-5/6 stub dirs, the `replicate_commands` + `pcall`-union
edits (redis-5/6/7 + valkey-7.2/8 kept in sync), and `RedisAmbientTypingTest.kt` as authored
(its TC-KEYS-1 asserts `string` and currently FAILS against the real engine — left failing, not
doctored, so the replan sees the true state). No commit was made.

## Resolved Epic Spikes (folded into design)

- **DR-02 (spec source) — RESOLVED**: bundle per-version JSON **generated from the
  BSD-3-Clause Valkey repository** `src/commands/*.json`, reduced to the minimal schema
  (design §4.1). Redis-repo `commands.json` (RSALv2/SSPLv1 ≥7.4, AGPLv3 in 8) is **not**
  redistributed. Loading is behind `RedisCommandSpecService` so the source can be swapped
  (e.g. to runtime `COMMAND DOCS`) without touching consumers.
- **DR-03 (`KEYS`/`ARGV` typing) — RE-OPENED then RE-RESOLVED (2026-07-14)**: the "stub-declared
  globals, consumed for free by the existing engine, no engine changes" resolution was
  ground-truth-**false** (see the blocker above). Re-resolved as **required, grounded engine
  work**: (1) design §3.1a — seed the target's stub globals into inference via
  `RuntimeLibraryProvider` + `LuaTypeGraphBridge` (root cause: single-file engine,
  `LuaTypesVisitor.kt:25,676-680,782-787`); (2) design §3.1b — array-subscript element inference
  in `visitIndexExpr` (root cause: dotted-only, `LuaTypesVisitor.kt:644-658`); (3) design §3.1c —
  a regression contract over the shared type-engine seam. Realized in implementation-plan
  Phase 1a. Two bounded sub-decisions carried as DR-03a/DR-03b below.

## Critical Risks

### Risk 1.1: Sandbox-allowlist false positives (inherits epic RISK-R07)
- **Impact**: ERROR-level false positives destroy trust; users disable the inspection.
- **Likelihood**: medium.
- **Mitigation**: derive the allowlist from the shipped stub roots (design §3.7 — single
  source of truth), and **ship at WARNING** (`level="WARNING"` in the `<localInspection>`;
  the requirement AC-7 was amended accordingly). Escalation to ERROR is deferred to a later
  release after live validation (DR-01 below).

### Risk 1.2: Arity semantics differ per command/version
- **Impact**: wrong arity WARNING (Redis arity counts the command token; negative = variadic).
- **Likelihood**: medium.
- **Mitigation**: the arity rule (design §3.4) uses the Redis convention verbatim from the
  bundled spec (`minArgs = |arity|`), never a hand rule, and only flags **below-minimum**
  (never above), so variadic commands (negative arity) never false-positive on extra args.

### Risk 1.3: Determinism rule over/under-fires
- **Impact**: a false WARNING on a script that is actually correct, or a missed case.
- **Likelihood**: low (WARNING-level, Redis 5/6 only, off by default for the majority 7+
  install base).
- **Mitigation**: the rule is deliberately conservative (design §3.9): guard = "any
  `redis.replicate_commands()` at a lower offset"; flags only when a `write`-flagged call
  follows in document order. Classification comes from the spec `flags`, not a hand list.

## Design Gaps

### Gap 2.1: Per-version stub member matrix (redis-5 / redis-6)
- **Question**: exactly which `redis.*` members and `os.*`/library entries differ between
  Redis 5, 6, and 7 stubs.
- **Options / leaning**: safe default is the union (design §3.10) — an over-listed stub only
  over-permits completion/allowlist, and the sandbox ships at WARNING, so the blast radius is
  bounded. A precise matrix is a nice-to-have, not a blocker.
- **Resolved by**: DR-02b below (fold the matrix into §3.10 once measured; not required for
  `planned`).

### Gap 2.2: Valkey rows depend on REDIS-03
- **Question**: `server.*` completion/inspection and Valkey spec files cannot exist until
  REDIS-03 registers `LuaPlatform.VALKEY` and its `PlatformVersionRegistry` rows.
- **Options / leaning**: REDIS-04 keys every consumer on `Target`/`pathSegment` (design §3.2,
  §3.3) so Valkey activates automatically when REDIS-03 lands; REDIS-04 ships Redis 5/6/7+
  only and does **not** add the Valkey platform. No rescope.
- **Resolved by**: tracked as a cross-feature dependency (requirements §Dependencies); no DR.

### Gap 2.3: `KEYS`/`ARGV` "no undeclared-variable" sub-assertion is not unit-testable in a light fixture
- **Question**: requirements TC-KEYS-1 asserts both `KEYS[1]` infers `string` **and** "no
  undeclared-variable highlight on `KEYS`/`ARGV`". The first rides the type-inference path
  (Phase 1a, unit-tested green). The second rides a **different** path: reference resolution
  (`LuaUndeclaredNames.isUnresolvedNonGlobal` → `multiResolve` → the platform library-bindings
  `FileBasedIndex` over the stub `global.lua`).
- **Finding (empirical, Phase 1b)**: in a `BasePlatformTestCase` the bundled stub `global.lua`
  is served from **inside the plugin jar** (`jar:…lunar-…​.jar!/runtime/redis/redis-7/global.lua`)
  and is therefore never `FileBasedIndex`-indexed, so bare-global `KEYS` resolves to nothing and
  the "Undeclared variable 'KEYS'" warning fires regardless of the target. A control probe with a
  project-file `KEYS = {}` copy (indexable, `file:` scheme) **also** failed to resolve, because
  bare-global *assignments* are not registered in `LuaGlobalDeclarationIndex` (only
  `function x.y` `LuaFuncDecl`s are) and the platform-bindings path only fires for *platform*
  package files. So the sub-assertion genuinely cannot pass in a light fixture.
- **Verdict**: this is a **fixture/packaging limitation, not a REDIS-04 resolution gap** — a real
  IDE indexes library roots, so `KEYS`/`ARGV` resolve and no warning fires. No code change is
  warranted for Phase 1b.
- **Coverage**: the live "no undeclared on `KEYS`/`ARGV`" behavior is verified by
  human-verification-checklists **Scenario 4.1**. `RedisAmbientTypingTest` carries a code comment
  pointing here.
- **Follow-up (tracked, non-blocking)**: if a future need arises to unit-test the resolution
  path, either (a) unpack the runtime stubs to a `file:`-scheme root the fixture can index, or
  (b) add a targeted integration test under `integrationTest` (real-IDE indexing). Neither gates
  the Phase-1b ship.

## Technical Debt & Future Work
- **TBD: runtime `COMMAND DOCS` augmentation** — bundled spec is core-server only; module
  commands (RedisJSON, RediSearch) and live server drift are future work on top of the
  swappable `RedisCommandSpecService` seam (epic open gap).
- **TBD: sandbox ERROR escalation** — deferred until live validation (Risk 1.1 / DR-01).
- **TBD: cluster key-slot awareness for `KEYS`** — epic non-goal.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| REDIS-00-DR-01 | Validate the derived sandbox allowlist against dockerized `redis:8` (attempt each blocked/allowed API in an `EVAL`) before any ERROR escalation | Risk 1.1 | todo |
| REDIS-00-DR-02b | Diff redis-5/6/7 `redis.*` + `os.*` surfaces; fold precise matrix into design §3.10 (union default is acceptable meanwhile) | Gap 2.1 | todo |
| REDIS-00-DR-02c (was DR-03) | Vendoring script: reduce Valkey `src/commands/*.json` → §4.1 schema, filtered per version; commit the generated `commandspec/*.json` + the script. **DONE (Phase 2)** — `scripts/vendor_redis_commandspec.py` fetches BSD-3-Clause `valkey-io/valkey`@`8.0.0` `src/commands/*.json` (verified `COPYING` = BSD 3-Clause; NO Redis-repo data), reduces to §4.1, filters per version → committed `commandspec/{redis-5,redis-6,redis-7}.json` (200/223/242 commands). Container subcommands (e.g. `SLOWLOG GET`) are skipped so they don't clobber top-level commands. The historical `nondeterministic` flag (dropped from modern Valkey JSON) is re-added for a documented static set of `random`-flagged commands, per script header. | design §4.1 | done |
| REDIS-04-DR-03 | **Engine work for AC-1** (Phase 1a): implement §3.1a stub-global seeding + §3.1b array-subscript inference + §3.1c regression contract | AC-1 / design §3.1a-c | done |
| REDIS-04-DR-03a | Array-element propagation seam (§3.1b-graph). **RESOLVED: seam (b)** — seam (a) is not implementable without adding a node-carrying field to the shared `Array` data class (it holds a *type*, not a node) or graph side-channel/sentinel state; seam (b) reads the receiver's resolved `Array(T)` value in `seedSubscriptElement`, which is stable pre-fixed-point because the receiver's `@type`/seed edge is wired before `visitIndexExpr` runs (decl precedes use). Covers TC-IDX-1 and (via seeding) TC-KEYS-1. | design §3.1b | done |
| REDIS-04-DR-03b | Target-switch invalidation of the type-snapshot cache. **RESOLVED: cache key made target-aware** — `LuaTypesSnapshot.forFile` folds `Target.hashCode()` with the document-text hash; `PlatformLibraryIndex.reload()` / `DaemonCodeAnalyzer.restart` do NOT clear plain `putUserData`, so text-only keying WOULD have served a stale seeded snapshot (confirmed real gap). `LuaTypesVisitor.getTypes` delegates to the same entry point so both consumers share one KEY/hash scheme. Verified by `StubGlobalSeedTypeTest` target-switch test. | design §3.1a | done |

## Test Case Gaps
- Live-server validation of the sandbox allowlist (DR-01) is an integration concern, not a
  unit TC; it gates the WARNING→ERROR change, not the initial ship. All unit-testable
  behavior is covered by requirements.md TC-KEYS-* … TC-DET-*.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Epic risks: [../redis-risks-and-gaps.md](../redis-risks-and-gaps.md)
