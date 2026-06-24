---
id: "SCHEMA-01-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "planned"
parent_id: "SCHEMA-01"
folders:
  - "[[features/schema/01-engine/requirements|requirements]]"
---

# SCHEMA-01: Risks & Gaps

## Critical Risks

### Risk 1.1: Platform API surface is large and partly internal
- **Impact**: `JsonLikePsiWalker` (~20 methods) and the adapter interfaces must be implemented
  correctly; some engine entry points (`JsonSchemaComplianceChecker`, a base inspection) may be marked
  `@ApiStatus.Internal` and shift between releases.
- **Likelihood**: medium.
- **Mitigation**: YAML is a maintained in-tree implementation to copy method-for-method
  (`plugins/yaml/backend/src/schema/*`). DR-02 confirms which entry points are reachable from a
  third-party plugin on GoLand 2026.1; pin behaviour with the engine tests (TC #1-#8). If an entry
  point is closed, fall back to the public `JsonSchemaComplianceInspection` registration pattern.

### Risk 1.2: Enabling the engine on Lua files costs lookups on every `.lua`
- **Impact**: `LuaJsonSchemaEnabler.isEnabledForFile` calling `JsonSchemaService.getSchemaFilesForFile`
  per Lua file could add overhead to ordinary `.lua` editing.
- **Likelihood**: low–medium.
- **Mitigation**: the service result is cached; the enabler returns false fast when no provider maps
  the file. Fallback: a name-pattern fast-path (providers contribute claimed name globs to a light
  `LuaSchemaFileRegistry`, checked before the service). TC #6 guards "no effect on plain `.lua`".

### Risk 1.3: New bundled-plugin dependency
- **Impact**: depending on `com.intellij.modules.json` adds a load-time requirement; a mis-set
  `platformBundledPlugins` breaks the build/sandbox.
- **Likelihood**: low — JSON is bundled in every target IDE (GoLand/IDEA/PyCharm/CLion).
- **Mitigation**: verified present in the GoLand 2026.1.3 build (`plugins/json/lib/json.jar`,
  `<id>com.intellij.modules.json</id>`). Phase 1 exit gates on a clean `./gradlew build`.

## Design Gaps

### Gap 2.1: Mixed / computed-key tables
- **Question**: how are `{ 1, 2, x = 3 }` (mixed) and `[expr] = v` (computed key) modelled?
- **Decision**: a table with any keyed field is an **object**; positional entries are ignored for
  property checks; computed/non-string keys are skipped as properties (design §3.4, Behavior Rule 4).
- **Resolved by**: decision recorded — **deliberate** (bounded v1). Real rockspec/luacheckrc/busted
  configs use bareword or string keys, so the loss is immaterial.

### Gap 2.2: A file with both top-level globals and `return {…}`
- **Question**: which is the schema root?
- **Decision**: shape B (the returned table) wins; top-level assignments are then treated as
  locals/side declarations, not properties (design §3.1).
- **Resolved by**: decision recorded — **deliberate**. Providers target one shape per file type
  (rockspec/luacheckrc = A, busted = B), so the ambiguity is not exercised in practice.

### Gap 2.3: Whether completion/docs need a Lua-side contributor
- **Question**: do the engine's completion contributor and documentation provider fire on a Lua file
  purely via the registered walker, or are they gated to JSON/YAML languages (like the inspection is)?
- **Decision**: assume engine-supplied first; if DR-03 shows they are language-gated, add a thin
  `language="Lua"` delegating completion contributor / doc provider (small, same walker).
- **Resolved by**: DR-03 (de-risking); not a blocker — fallback is specified.

## Technical Debt & Future Work
- **TBD**: schema-driven *syntax colouring* of keys (separate annotator; out of scope).
- **TBD**: `LuaSchemaFileRegistry` name-pattern fast-path if Risk 1.2 materialises.
- **TBD**: surfacing "Go to schema" / schema-version switch UI (engine provides it for JSON/YAML;
  confirm it follows the walker for Lua).

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| SCHEMA-01-00-DR-01 | Build a pointer oracle: for the harness schema, assert `LuaJsonLikePsiWalker.findPosition` produces the same `JsonPointerPosition` as the equivalent JSON/YAML for shape A and B. | Risk 1.1, design §3.2 | todo |
| SCHEMA-01-00-DR-02 | From a third-party-plugin test on GoLand 2026.1.3, confirm the reachable validation entry point (`JsonSchemaComplianceChecker` ctor + `annotate`, or a `JsonSchemaBasedInspectionBase` subclass) and that `JsonLikePsiWalker.getWalker` returns the Lua walker for a Lua element. | Risk 1.1, design §2.5 | todo |
| SCHEMA-01-00-DR-03 | Verify the engine's completion contributor + documentation provider fire on a Lua data file via the registered walker (no Lua-side contributor); if not, scope the thin delegating contributor. | Gap 2.3, design §2.5 | todo |
| SCHEMA-01-00-DR-04 | Confirm the bundled-schema → `VirtualFile` helper available to providers (`JsonSchemaProviderFactory.getResourceFile(...)` or equivalent) and its exact signature on this platform. | design §2.6 | todo |

## Cross-Feature Sequencing
SCHEMA-02..04 (providers) **depend on** SCHEMA-01's base `LuaSchemaFileProvider`/factory and the
registered walker/enabler. Until SCHEMA-01 lands, providers cannot be wired; SCHEMA-01's own tests use
a TEST-ONLY provider so the engine is verifiable independently.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
