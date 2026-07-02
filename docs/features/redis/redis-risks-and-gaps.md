---
id: "REDIS-RISKS"
parent_id: "REDIS"
type: "risk"
folders:
  - "[[features/redis/requirements|requirements]]"
title: "Design Gaps & De-risking"
---

# REDIS Risk Register

**Last Updated**: 2026-07-02
**Status**: Pre-design (requirements written; design not started). All risks **Open**
unless noted; the DR spikes below gate design sign-off.

---

## Risk Summary

| Risk ID | Title | Probability | Impact | Mitigation | Status |
|---------|-------|-------------|--------|-----------|--------|
| RISK-R01 | LDB protocol is implementation-defined, not specified | High | High | DR-01 handshake spike; integration tests as compatibility contract | Open |
| RISK-R02 | Managed Redis services block `SCRIPT DEBUG` | High | Medium | Capability probe + clear messaging; "launch local" path | Open |
| RISK-R03 | Sync-mode debugging can freeze a shared server | Medium | Critical | Fork default; confirmation gate on non-local connections | Open (design-time) |
| RISK-R04 | Command-spec bundling has licensing exposure (Redis ≥7.4/8) | Medium | High | DR-02: source from BSD Valkey repo or runtime `COMMAND DOCS` | Open |
| RISK-R05 | LDB functional limitations surprise users | High | Low | Disabled actions with tooltips; documented in user guide | Open (design-time) |
| RISK-R06 | Redis/Valkey script-API drift over time | Medium | Medium | Shared stub base + overlays; dual-flavor canary tests | Open |
| RISK-R07 | Sandbox-allowlist inaccuracy → ERROR-level false positives | Medium | High | Derive allowlist from stub roots; ship at WARNING first | Open |
| RISK-R08 | `KEYS`/`ARGV` ambient injection interacts badly with caching/inspections | Medium | Medium | DR-03: prefer stub-declared globals over engine injection | Open |
| RISK-R09 | New debug adapter inherits legacy debugger defect patterns | Medium | High | Greenfield client; review.md checklist as DoD; avoid `LuaProcessUtil` | Open (process) |
| RISK-R10 | Docker-based integration tests destabilize the CI gate | Medium | Medium | DR-04: builder capability check; separately-tagged test task | Open |
| RISK-R11 | RESP client scope creep (TLS/ACL/RESP3/cluster) | Medium | Medium | Hard scope in REDIS-01 + epic non-goals | Mitigated (scoped) |
| RISK-R12 | Run-configuration producer collision on `.lua` files | Low | Low | Producer gated on Redis/Valkey target; explicit ordering | Mitigated (design) |

---

## Risk Details

### RISK-R01: LDB protocol is implementation-defined

**Description**: The LDB wire behavior (`SCRIPT DEBUG` + debug commands over RESP) has no
written specification; it is defined by the `redis-cli --ldb` client implementation.
Reply framing of debug-mode output, session-end signaling, and error surfaces may differ
between Redis and Valkey, and between versions, in ways documentation won't reveal.

**Probability**: High (some divergence from expectations is near-certain)
**Impact**: High (REDIS-02 is a Must feature and half the epic's headline value)

**Mitigation**:
- **DR-01 (gates REDIS-02 design)**: spike a minimal RESP client that performs
  `SCRIPT DEBUG YES` → `EVAL` → `break`/`step`/`print`/`abort` against dockerized
  `redis:8` **and** `valkey/valkey:8`, in both fork and sync modes; document observed
  framing and session-end behavior in the REDIS-02 design doc.
- Treat the redis-cli source as the reference implementation during design.
- Integration tests on both flavors are the permanent compatibility contract (epic
  constraint); run them per-version when new server majors appear.

---

### RISK-R02: Managed services block `SCRIPT DEBUG`

**Description**: Hosted offerings (AWS ElastiCache/MemoryDB, Azure Cache, GCP Memorystore,
Redis Cloud) restrict admin/debug commands; `SCRIPT DEBUG` is typically unavailable. A
large user segment cannot use REDIS-02 against their real infrastructure.

**Probability**: High (this is the documented posture of managed offerings)
**Impact**: Medium (debugging still works locally; expectation management problem)

**Mitigation**:
- Probe capability at debug-session start: a rejected `SCRIPT DEBUG` produces a specific,
  actionable message ("this server does not permit script debugging — debug against a
  local server instead") with a one-click switch to a "launch local" connection.
- REDIS-01's Docker/local-binary provisioning is the designed escape hatch — debugging
  locally against production-shaped data is the recommended workflow in the user guide.
- Do not market REDIS-02 as "debug in production".

---

### RISK-R03: Sync-mode debugging freezes the server

**Description**: `SCRIPT DEBUG SYNC` blocks the entire server event loop while paused and
commits writes. A developer pausing on a breakpoint against a shared/staging server causes
an outage-shaped incident; this is the epic's only genuinely dangerous affordance.

**Probability**: Medium (someone will try it)
**Impact**: Critical (external harm, not just IDE misbehavior)

**Mitigation** (already encoded in REDIS-02 acceptance criteria):
- Fork mode is the default; sync is an explicit opt-in checkbox with warning text.
- Sync against a connection that is not session-local ("launch local") requires a
  per-session confirmation dialog.
- Session banner states the mode's consequences (writes committed / server blocked).
- Design option to evaluate: a hard setting `redis.debug.allowSyncOnRemote=false` by
  default, making remote sync impossible without an explicit settings change.

---

### RISK-R04: Command-spec licensing (REDIS-04)

**Description**: REDIS-04 bundles per-version command metadata. Redis changed licensing
(RSALv2/SSPLv1 at 7.4; AGPLv3 added in 8) — redistributing `commands.json` content and
documentation text extracted from the Redis repo inside a plugin needs a license review.
Valkey is BSD-3-Clause.

**Probability**: Medium (bundling verbatim Redis-repo content is the risky variant)
**Impact**: High (legal/compliance; potential rework of a shipped feature)

**Mitigation**:
- **DR-02 (gates REDIS-04 design)**: choose the metadata source. Preferred order:
  (a) generate from the BSD-licensed Valkey repository (command surface is a superset of
  the shared baseline and covers Redis ≤7.2 commands); (b) fetch live via `COMMAND DOCS`
  from the connected server at runtime, cached per connection (no bundling at all);
  (c) bundle Redis-repo content only after an explicit license determination.
- Keep spec loading behind an application service so the source can be swapped without
  touching consumers.

---

### RISK-R05: LDB functional limitations

**Description**: LDB has no step-out; cannot debug `FCALL` (Functions) invocations; forked
sessions are timed out by the server after inactivity; `redis.breakpoint()` is inert
outside debug mode; output of `print` for deep/cyclic tables is truncated by `maxlen`.

**Probability**: High (all are certain; the risk is user surprise, not occurrence)
**Impact**: Low (cosmetic/expectation issues if handled; support-load if not)

**Mitigation** (mostly already in REDIS-02/05 acceptance criteria):
- Disabled Step Out and disabled FCALL-mode Debug executor, each with explanatory
  tooltips; session-timeout surfaced as a clean "session ended by server" message.
- Map LDB `maxlen` to a debugger setting; document truncation in the variables view.
- User-guide section "What the Redis debugger can and cannot do".

---

### RISK-R06: Redis/Valkey drift

**Description**: Valkey is script-compatible with Redis 7.2 today and already exhibits
stricter Lua-engine behavior in edge cases; Redis 8+ adds features Valkey won't mirror
(and vice versa — Valkey 8 `server.*`, `SERVER_*`). Stubs, command specs, and sandbox
allowlists risk silent divergence maintained in two places.

**Probability**: Medium (drift is certain long-term; near-term surface is small)
**Impact**: Medium (wrong completion/inspection results for one flavor)

**Mitigation**:
- Single shared stub base + thin per-flavor overlays (REDIS-03 acceptance criterion);
  never fork the shared API definitions.
- Version/flavor gates keyed on `SERVER_VERSION_NUM`/`INFO` where behavior differs.
- Dual-flavor integration tests (epic constraint) act as the drift canary; add a
  checklist item to the release process: re-run the canary against new server majors.

---

### RISK-R07: Sandbox-allowlist inaccuracy (REDIS-04)

**Description**: REDIS-04 flags sandbox-unavailable APIs at ERROR level. The precise
allowlist (`os` subset, `cjson`/`cmsgpack`/`bit`/`struct` availability) varies by server
version and flavor. A wrong allowlist produces ERROR-level false positives — the most
trust-destroying failure mode an inspection has.

**Probability**: Medium
**Impact**: High (users disable the inspection and the feature's value evaporates)

**Mitigation**:
- Derive the allowlist from the shipped per-target stdlib stub roots (single source of
  truth — if the stub exists for the target, it is allowed) instead of a second
  hand-maintained list.
- Validate the generated allowlist against live servers in the integration suite
  (attempt each API under each dockerized flavor/version).
- Ship the sandbox inspection at WARNING for one release; escalate to ERROR only after
  live validation (amend the REDIS-04 criterion accordingly at design time).
- The Redis 5/6 determinism inspection shares this exposure: its nondeterministic-command
  classification must come from the DR-02 command-spec flags (not a hand list), and it
  ships WARNING-level by design.

---

### RISK-R08: `KEYS`/`ARGV` ambient-injection mechanics (REDIS-04)

**Description**: Engine-level scope injection must not leak into non-Redis files, must be
suppressed in Function-library files (REDIS-05), and must invalidate cleanly when the
project target changes. The type-engine snapshot cache has known invalidation weaknesses
([docs/review.md](../../review.md): `FileUserData` staleness) — target-change invalidation
is exactly the hazardous path.

**Probability**: Medium
**Impact**: Medium (stale typing/inspection results after switching targets)

**Mitigation**:
- **DR-03 (gates REDIS-04 design)**: evaluate declaring `KEYS`/`ARGV` as globals **in the
  Redis/Valkey stdlib stubs** instead of engine injection — the library-root mechanism
  already invalidates correctly on target change (TARGET-04) and requires zero new engine
  surface. Engine injection only if stub-based typing proves insufficient (e.g. for the
  REDIS-05 per-file suppression, which can instead be a scoped inspection exemption).
- If injection is used: tie it to the same modification tracker the target settings
  publish, and add an explicit target-switch invalidation test.

---

### RISK-R09: Inheriting legacy debugger defect patterns

**Description**: The existing MobDebug adapter has a documented defect cluster
([docs/review.md](../../review.md) P1 #4–#7, #13, #16–#18: byte/char framing confusion,
EDT-blocking waits, unsynchronized cross-thread state, silent error swallowing, `!!` in
payload parsing). Copy-paste or shared use of `LuaProcessUtil`/`LuaDebugConnection`
idioms would replicate them into REDIS-01/02.

**Probability**: Medium (path of least resistance without an explicit guard)
**Impact**: High (same hang/desync classes in a brand-new feature)

**Mitigation** (process controls, already partially encoded as epic constraints):
- Greenfield RESP client: raw `InputStream` byte framing, explicit UTF-8 decode, no
  shared code with `LuaDebugConnection`; do not use `LuaProcessUtil.capture` for server
  processes (use `GeneralCommandLine`/`OSProcessHandler` with cancellation).
- REDIS-02 acceptance criterion "engineering-contract compliance verified in review" is
  the DoD gate; reviewer uses the review.md debugger findings as the checklist.
- Preferably land the MobDebug hardening fixes (review.md execution-order item 2) before
  or alongside REDIS-02 so the codebase has one good pattern to point to.

---

### RISK-R10: Docker in CI (gce-builder)

**Description**: The epic's integration tests require Docker on the gce-builder VM. The
existing `:integrationTest` task is already environment-fragile, and the build gate runs
it; adding container-dependent tests could destabilize the green suite or block builds
when Docker/images are unavailable (spot-VM churn, image pulls).

**Probability**: Medium
**Impact**: Medium (CI flakiness; masked regressions if tests get skipped silently)

**Mitigation**:
- **DR-04**: verify Docker availability/provisioning on the gce-builder image (bootstrap
  installs docker + pre-pulls `redis:8`/`valkey/valkey:8`).
- Put Redis integration tests in a separately-tagged Gradle task (`redisIntegrationTest`)
  excluded from the default `build`/`test` gates; run it explicitly in the epic's
  verification workflow and on a scheduled CI job.
- Tests must **fail loudly** (not skip) when Docker is expected but absent, with a clear
  environment message — silent skips read as green.

---

### RISK-R11: RESP client scope creep — *mitigated by scoping*

**Description**: A "small" RESP client attracts TLS, ACL users, RESP3 push, Sentinel,
cluster redirects, SSH tunnels. Each is real-world useful and each doubles the surface.

**Mitigation**: REDIS-01 scope is fixed: single-node, RESP2 with `HELLO` fallback
negotiation, TLS via standard JSSE, username/password AUTH. Cluster key-slot validation,
Sentinel, SSH tunnels, and RESP3 push are named epic non-goals; revisit only on demand
signal after ship.

---

### RISK-R12: Run-config producer collision — *mitigated by design*

**Description**: `.lua` files already produce the standard Lua run configuration (and
test/rocks producers exist). A Redis producer could shadow or duplicate them.

**Mitigation**: The REDIS-01 producer activates only when the project target platform is
Redis/Valkey (acceptance criterion), never replaces the standard producer (both offered;
standard remains first for non-Redis targets), and reuses the established producer-ordering
pattern from the test/rocks configs.

---

## Open Gaps (acknowledged, not scheduled)

- **Cluster support**: no key-slot validation for `EVAL` KEYS on cluster deployments; no
  `MOVED`/`ASK` handling. Single-node only (epic non-goal).
- **Redis Stack / module commands** (RedisJSON, RediSearch, …): command-spec coverage is
  core-server only; module command completion is future work on top of DR-02's chosen
  source (runtime `COMMAND DOCS` would cover modules for free — a point in its favor).
- **Sentinel / SSH-tunnel connections**: not designed; users can tunnel externally.
- **Valkey 8 non-Lua engines** (e.g. future scripting engines): out of scope; Lua only.
- **luacheck `--std` for Valkey**: no dedicated std exists upstream; Valkey targets reuse
  the Redis std mapping (documented in REDIS-03).
- **Debugging FCALL**: server-side limitation (RISK-R05); revisit if upstream adds it.

---

## De-risking Spikes (gate design sign-off)

| ID | Spike | Gates | Exit criterion |
|----|-------|-------|----------------|
| DR-01 | LDB handshake spike against dockerized Redis 8 + Valkey 8 (fork + sync) | REDIS-02 design | Observed framing/session-end behavior documented in design doc |
| DR-02 | Command-metadata source decision (Valkey BSD repo vs runtime `COMMAND DOCS` vs licensed bundle) | REDIS-04 design | Source chosen with license rationale recorded |
| DR-03 | `KEYS`/`ARGV` typing approach: stub-declared globals vs engine scope injection | REDIS-04 design | Approach chosen; target-switch invalidation test defined |
| DR-04 | Docker capability on gce-builder (install, image pre-pull, task isolation) | Epic verification plan | `redisIntegrationTest` runs green on the builder VM |

---

## Escalation Path

1. Document escalations as a new row (mark "IN_REVIEW").
2. Flag for discussion before the affected feature's design sign-off.
3. Update mitigation; re-assess probability/impact.

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Tech Lead | — | — | ⏳ Pending |
| QA Lead | — | — | ⏳ Pending |
| Product Manager | — | — | ⏳ Pending |
