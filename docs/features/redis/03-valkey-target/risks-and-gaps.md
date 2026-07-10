---
id: "REDIS-03-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "todo"
parent_id: "REDIS-03"
folders:
  - "[[features/redis/03-valkey-target/requirements|requirements]]"
---

# REDIS-03: Risks & Gaps

Feature-level register. Epic-wide risks (RISK-R06 drift, RISK-R11/R12) live in
`../redis-risks-and-gaps.md`; this file tracks REDIS-03-specific gaps and the de-risking actions
that gate `planned`.

## Critical Risks

### Risk 1.1: REDIS-01 flavor seam not yet centralized
- **Impact**: If REDIS-01 ships its inline §4.3 `valkey_version` heuristic and REDIS-03 also
  ships `LuaRedisServerFlavor`, the flavor logic exists in two places (RISK-R06 drift, in the small).
- **Likelihood**: medium (both features touch the same `INFO server` parse; ordering is unfixed).
- **Mitigation**: `LuaRedisServerFlavor.detect` is the single source of truth (design §2.5, §7.3).
  DR-01 makes the REDIS-01 call site consume it. If REDIS-03 lands first, REDIS-01 wires it at
  its connect site; if REDIS-01 lands first, DR-01 edits the one call site. The two REDIS-03
  classes are independently unit-testable, so REDIS-03 is not blocked on REDIS-01.

### Risk 1.2: Base-stub drift between `runtime/redis/redis-7` and the Valkey `redis.lua` copies
- **Impact**: The Valkey dirs byte-copy the Redis-7 base; a later edit to the Redis base is not
  auto-propagated → the compat namespace silently diverges (RISK-R06 at the file level).
- **Likelihood**: medium (long-term certainty; near-term surface is small — the base is stable).
- **Mitigation**: DR-02 adds a repo check + release-checklist item that diffs the copied base files
  against `runtime/redis/redis-7/` and fails/flags on divergence. Only `server.lua` /
  `server_global.lua` are hand-authored and expected to differ.

## Design Gaps

### Gap 2.1: `server`/`SERVER_*` false positives from shadowing locals
- **Question**: The inspection flags by text (`name == "server"` dotted base; `SERVER_*` name refs)
  without resolving. A user local named `server` used as a dotted base, or a `local SERVER_NAME`,
  is flagged.
- **Options / leaning**: (a) accept it — any `server.<x>` dotted access under a Redis target *is*
  non-portable regardless of origin; the rule stays cheap and I/O-free (design §3.5, §6, §9 leaning
  = accept); (b) resolve the reference and suppress when it binds to a local — heavier, on the
  resolve path. **Leaning (a)**, documented as accepted behaviour.
- **Resolved by**: DR-03 (decide + record) before `planned`. Current design assumes (a).

### Gap 2.2: AC-2 "Settings UI shows Valkey" has no live platform-picker surface in current source
- **Question**: requirements AC-2 says the "contextual platform→version" UI shows Valkey. The
  TARGET-03 design named a `LuaProjectSettingsPanel` that enumerates `LuaPlatform.entries`, but no
  such combo exists in current `src/main` (grep `ComboBox<LuaPlatform>` / `LuaPlatform.entries` in
  `src/main` → 0 hits; the target is derived from the active environment via
  `LuaTargetSynchronizer.resolveTarget(info.platform, …)`, `toolchain/resolve/LuaTargetSynchronizer.kt:83`).
- **Options / leaning**: Scope AC-2 to what is grounded and testable today: adding `VALKEY` to the
  enum + registry makes it surface in **every** platform enumeration (`LuaPlatform.entries`,
  `PlatformVersionRegistry.platforms()`, and any env whose `LuaRuntimeInfo.platform == VALKEY`),
  and legacy migration is unaffected (TC-REG-3/4). The *env-detection* that makes an environment
  report `LuaPlatform.VALKEY` (so the sync path selects it) is **out of REDIS-03 scope** and is
  the natural home of the "how does a user pick Valkey" question — it belongs to the toolchain/env
  layer, not this feature.
- **Resolved by**: DR-04 — confirm the target-surface interpretation with the toolchain owner and,
  if a manual picker is expected, file it as a separate toolchain task; do not rescope REDIS-03 to
  build a picker. AC-2's test coverage (TC-REG-3/4) verifies the registry/enumeration/migration
  contract, which is the part REDIS-03 owns.

## Technical Debt & Future Work
- **TBD: explicit `server.*` member docs** — the `---@class server : redis` inheritance form gives
  resolution + hover via the parent; if per-member Valkey-specific doc text is later wanted, copy
  the `function server.<x>(...)` blocks (design §9). Deferred until a docs-fidelity need appears.
- **TBD: `SERVER_VERSION_NUM` value modelling** — stubs type it `number`; no per-version constant
  value is modelled (matches how Redis `REDIS_VERSION_NUM` is stubbed as a plain `number` field).
- **TBD: luacheck `valkey` std** — none exists upstream; Valkey reuses `redis7` (epic Open Gaps).
  Revisit if luacheck adds one.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-01 | Confirm the REDIS-01 connect-site wiring for `LuaRedisServerFlavor.detect` + `warnOnceIfMismatch`; amend REDIS-01 design §4.3 note. | Risk 1.1 | todo |
| DR-02 | Add a base-stub parity check (Valkey copies vs `runtime/redis/redis-7`) + release-checklist item. | Risk 1.2 | todo |
| DR-03 | Decide Gap 2.1 (accept text-based false positives vs resolve-and-suppress); record in design §3.5. | Gap 2.1 | todo |
| DR-04 | Confirm AC-2 scope with the toolchain owner (registry/enumeration contract; env-detection out of scope). | Gap 2.2 | todo |

## Test Case Gaps
- Live integration: a real `valkey/valkey:8` container returning `INFO server` with both
  `redis_version` and `valkey_version` — covered structurally by TC-FLV-1 (unit, on the parsed
  body) and by the epic's dual-flavor integration suite (RISK-R06 canary); no new integration test
  is added by REDIS-03 (it reuses the epic's `redisIntegrationTest`, DR-04 in the epic register).

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Epic risks: [../redis-risks-and-gaps.md](../redis-risks-and-gaps.md)
