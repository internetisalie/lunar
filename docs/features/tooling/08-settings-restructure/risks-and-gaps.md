---
id: "TOOLING-08-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "TOOLING-08"
folders:
  - "[[features/tooling/08-settings-restructure/requirements|requirements]]"
---

# TOOLING-08: Risks & Gaps

## Critical Risks

### Risk 1.1: Explicit target and the synchronizer fight over the target
- **Impact**: if the explicit-target guard is wrong, a runtime re-probe (`TOOL_UPDATED`) reverts the
  user's pinned platform — reintroducing BUG-362 in a subtler form.
- **Likelihood**: medium.
- **Mitigation**: the guard is a single early-return keyed on `state.explicitTarget` (design §2.5);
  covered by TC 4 as a unit test asserting the target survives a `TOOL_UPDATED` event before any UI
  work. Switching back to Auto explicitly calls `ensureSynchronized()` so the runtime reflow is
  immediate and intentional (§3.3).

### Risk 1.2: DSL migration silently changes app-settings behavior
- **Impact**: rewriting `LuaApplicationSettingsPanel` could drop the `dropResolveCaches()` side effect
  in `getData` (`LuaApplicationSettingsPanel.kt:65-73`), breaking type-inference toggling.
- **Likelihood**: low.
- **Mitigation**: preserve the public API and the `getData` side effect verbatim; TC 11 asserts
  `isModified` after a toggle. Migration is layout-only.

### Risk 1.3: `collapsibleGroup` availability / default-collapsed
- **Impact**: if the pinned platform's Kotlin UI DSL lacks `Panel.collapsibleGroup` or defaults to
  expanded, the advanced group won't behave as specified.
- **Likelihood**: low — `collapsibleGroup` is standard in the 2026.1 platform DSL.
- **Mitigation**: DR-01 confirms the API and default state against the pinned SDK before Phase 3.

## Design Gaps

### Gap 2.1: Keep vs remove `setGlobalBinding` (product-owner judgment) — RESOLVED
- **Question**: no panel drives `LuaToolchainRegistry.setGlobalBinding`; either add a UI or remove the
  capability plus its PRD/checklist references.
- **Options / leaning**: (a) add a minimal global-bindings UI; (b) delete the capability.
- **Resolution**: **(a)**. The mutator is honored by the resolver (`ResolutionSource.GLOBAL_BINDING`),
  diagnostics, and the runtime banner, and is referenced by the TOOLING PRD Use Case 2 and the E2E.2
  verification checklist. A one-group UI (design §3.5) is cheaper and lower-risk than unwinding those
  references. Folded into requirement TOOLING-08-05. No open item remains.

### Gap 2.2: Where do platform-server kinds belong once evicted? — RESOLVED
- **Question**: `redis-server`/`valkey-server` leave the general bindings list; do they need a home?
- **Resolution**: **no new home needed for this feature.** They are consumed programmatically by the
  Redis subsystem via `LuaToolResolver.resolve(project, "redis-server")`
  (`redis/connection/LuaRedisServerConnection.kt:50`, `LuaRedisConnectionSettings.kt:90`) and remain
  fully resolvable. Surfacing a server picker on the *Redis Connections* child page is a Redis-epic
  concern, not a settings-restructure concern — noted as future work below, not planned here.

## Technical Debt & Future Work
- **TBD: Server picker on *Redis Connections* page** — an explicit `redis-server`/`valkey-server`
  binding control could live on `LuaRedisConnectionsConfigurable`; deferred to the Redis workstream.
- **TBD: A `Capability.PLATFORM_SERVER` marker** — the classifier keys off `capabilities.isEmpty()`.
  A dedicated capability would be more self-documenting but is a model change touching REDIS-01's
  descriptors; deferred to avoid scope creep. If added later, `LuaToolKindClassifier.tierOf` step 1
  becomes `PLATFORM_SERVER in capabilities`.
- **TBD: Terminology sweep** — the run-config "Interpreter" label and other non-settings wording are a
  separate chore (Out of Scope); this feature only standardizes labels on the pages it touches.

## Roadmap Placement (note for the supervisor — do NOT edit `docs/roadmap.md` here)
TOOLING-08 is a **post-epic** follow-up: the TOOLING epic is `status: done`, and this feature is a
UX/discoverability refinement driven by a 2026-07-16 user-flow review plus BUG-362/BUG-369. It depends
only on shipped work (TOOLING-02, TOOLING-06) and has no downstream blockers. Suggested placement: the
current maintenance wave (Wave 12 / `MAINT`) or a small "settings polish" slot alongside ROCKS-16.
The supervisor should place the roadmap row.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| TOOLING-00-DR-08a | Confirm `com.intellij.ui.dsl.builder.Panel.collapsibleGroup` exists in the pinned SDK and defaults to collapsed | Risk 1.3 | todo |
| TOOLING-00-DR-08b | Confirm `explicitTarget` XML round-trips through `lunar.xml` and defaults `false` for an old file with no tag | §3 edge case | todo |

## Absorbed codebase-review findings (2026-07-17)

The 2026-07 codebase review ([docs/review.md](../../../review.md); remediation verified
2026-07-17) has three open findings in the settings machinery this feature restructures. They are
**in scope here**; do not file/fix them separately:

| Review # | Defect | Where it lands here |
|----|----|----|
| #41 | Settings event bus half-dead: the topic is now *published* (`LuaTargetSynchronizer`, `LuaProjectConfigurable`) but the sole subscriber `LuaSettingsChangeListener` is a lazy `@Service` nothing instantiates — events go nowhere | The explicit-target/bindings rework must make the change-notification chain real (instantiate/register the listener, or replace the mechanism) — arguably Phase 0 |
| #44 | `LuaApplicationSettingsConfigurable` has no `reset()`/`disposeUIResources()`; panel mutates live persisted `LuaInterpreter` objects pre-Apply — Cancel can't undo | DSL migration phase: clone-edit-commit pattern + full Configurable lifecycle |
| #50 | `Target.default()` documented "Standard Lua 5.4" but resolves to the registry's first entry (5.1) | Explicit-target work (BUG-362) must pin the documented default |

## Test Case Gaps
- No test yet asserts the *visual* collapsed state of the advanced group (VNC-only, Phase 6) — this is
  intentional; appearance is a `verify-in-ide` DoD gate, not a unit test.
- Auto-mode read-only version display is asserted only for the `Target.default()` fallback (TC 1); the
  runtime-derived Auto display is covered by the live VNC pass.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
