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

Feature-level register. The epic spikes DR-02 (spec source) and DR-03 (typing approach) that
gated this design are **resolved** below; the residual items are bounded, non-blocking DR
tasks. See the epic register ([../redis-risks-and-gaps.md](../redis-risks-and-gaps.md)) for
RISK-R04, R06, R07, R08 context.

## Resolved Epic Spikes (folded into design)

- **DR-02 (spec source) — RESOLVED**: bundle per-version JSON **generated from the
  BSD-3-Clause Valkey repository** `src/commands/*.json`, reduced to the minimal schema
  (design §4.1). Redis-repo `commands.json` (RSALv2/SSPLv1 ≥7.4, AGPLv3 in 8) is **not**
  redistributed. Loading is behind `RedisCommandSpecService` so the source can be swapped
  (e.g. to runtime `COMMAND DOCS`) without touching consumers.
- **DR-03 (`KEYS`/`ARGV` typing) — RESOLVED**: stub-declared globals (already shipped in
  `runtime/redis/redis-7/global.lua`), not engine scope injection. Target-change invalidation
  is handled by the existing library-root mechanism (TARGET-04); TC-KEYS-3 is the
  target-switch/no-leak test.

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
| REDIS-00-DR-03 | Vendoring script: reduce Valkey `src/commands/*.json` → §4.1 schema, filtered per version; commit the generated `commandspec/*.json` + the script | design §4.1 | todo |

## Test Case Gaps
- Live-server validation of the sandbox allowlist (DR-01) is an integration concern, not a
  unit TC; it gates the WARNING→ERROR change, not the initial ship. All unit-testable
  behavior is covered by requirements.md TC-KEYS-* … TC-DET-*.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Epic risks: [../redis-risks-and-gaps.md](../redis-risks-and-gaps.md)
