---
id: "ROCKS-13-PLAN"
title: "Implementation Plan"
type: "plan"
status: "planned"
parent_id: "ROCKS-13"
folders:
  - "[[features/rocks/13-rockspec-editor-support/requirements|requirements]]"
---

# ROCKS-13: Implementation Plan

Sequence design.md into verifiable phases. All new code lands in the new package
`net.internetisalie.lunar.rocks.editor`. Highlighting is already delivered by the existing file-type
registration ([plugin.xml:55-60](../../../../src/main/resources/META-INF/plugin.xml)); ROCKS-13 adds
only code insight.

## Phases

### Phase 1: Schema model, loader & file guard [Must]
- **Goal**: the bundled JSON schema is parsed into an immutable `RockspecSchema`, version-selected, and
  `.rockspec` files are reliably identified.
- **Tasks**:
  - [ ] Create `RockspecSchema` / `SchemaNode` / `SchemaKind` (design §2.1).
  - [ ] Create `RockspecSchemaLoader` with the bounded JSON-schema interpreter (design §4.1), `v30`/`v31`
    `by lazy`, and `schemaFor(file)` (design §3.6).
  - [ ] Create `RockspecFileSupport` with `isRockspec` and `topLevelAssignments` (design §2.3).
- **Exit criteria**: `RockspecSchemaLoaderTest` (pure) asserts root `closed`; **per-version** required
  set — v3.0 `{package,version,source,build}`, v3.1 `{package,source,version}` with `build ∉ required`
  (design §3.2); `description` closed (7 keys), `build` open, v3.1 has top-level `test`.

### Phase 2: Validation inspection [Must]
- **Goal**: unknown-key, missing-required, and value-kind diagnostics on `.rockspec` files only.
- **Tasks**:
  - [ ] Create `RockspecSchemaInspection : LocalInspectionTool` (design §2.4) with the §3.1/§3.2/§3.3
    checks and the single-emission required-key scan.
  - [ ] Register `<localInspection language="Lua" shortName="RockspecSchema" groupName="LuaRocks" …>`
    (design §7).
- **Exit criteria**: TC #1-#7 pass in `RockspecSchemaInspectionTest` (incl. the `.lua` negative and the
  `build`-open and non-literal-RHS negatives).

### Phase 3: Version selection coverage [Must / Should]
- **Goal**: `rockspec_format` switches the active schema.
- **Tasks**:
  - [ ] Verify `schemaFor` is wired into the inspection (it is, via §2.4) and add the v3.1-only-key tests.
- **Exit criteria**: TC #10-#11 pass.

### Phase 4: Quick fix [Should]
- **Goal**: insert missing required-field stubs.
- **Tasks**:
  - [ ] Create `RockspecAddRequiredFieldsQuickFix : LocalQuickFix` (design §2.7, §3.5); attach it to the
    missing-required problem in Phase 2.
- **Exit criteria**: TC #12 passes (stubs inserted; re-run clean).

### Phase 5: Completion [Should]
- **Goal**: schema-key completion at top level and in known nested tables.
- **Tasks**:
  - [ ] Create `RockspecCompletionContributor : CompletionContributor` (design §2.5, §3.4); register
    `<completion.contributor language="Lua" …>` (design §7).
- **Exit criteria**: TC #8-#9 pass in `RockspecCompletionTest`.

### Phase 6: Hover documentation [Should]
- **Goal**: Quick-Doc on a schema key shows its `description`.
- **Tasks**:
  - [ ] Create `RockspecDocumentationTargetProvider` + `RockspecKeyDocumentationTarget` (design §2.6);
    register `<platform.backend.documentation.targetProvider …>` (design §7).
- **Exit criteria**: manual Quick-Doc check (human-verification-checklists.md) shows the key description.

### Phase 7: Verify in IDE [Should]
- **Goal**: prove the feature live (DoD real-flow gate).
- **Tasks**:
  - [ ] Run human-verification-checklists.md in the containerized GoLand (verify-in-ide skill): unknown
    key squiggle, missing-required + quick fix, completion popup, Quick-Doc.
- **Exit criteria**: all checklist scenarios pass; no markers leak onto a plain `.lua` file.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| ROCKS-13-01 File guard | M | Phase 1 |
| ROCKS-13-02 Schema model & loader | M | Phase 1 |
| ROCKS-13-03 Unknown-key validation | M | Phase 2 |
| ROCKS-13-04 Missing-required validation | M | Phase 2 |
| ROCKS-13-05 Value-kind validation | M | Phase 2 |
| ROCKS-13-09 Version selection | S | Phase 3 |
| ROCKS-13-08 Quick fix | S | Phase 4 |
| ROCKS-13-06 Key completion | S | Phase 5 |
| ROCKS-13-07 Hover documentation | S | Phase 6 |

## Verification Tasks
- [ ] `RockspecSchemaLoaderTest` (pure) — model shape, required set, closed/open flags, v3.1 `test`.
- [ ] `RockspecSchemaInspectionTest` (`BasePlatformTestCase`, `.rockspec` fixtures) — TC #1-#7, #10-#12.
- [ ] `RockspecCompletionTest` (`BasePlatformTestCase`) — TC #8-#9.
- [ ] `./gradlew test`, `ktlintFormat`, `ktlintCheck` (match existing style; no mass reformat).
- [ ] verify-in-ide pass (Phase 7).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Schema model, loader & file guard | todo | Must |
| Phase 2: Validation inspection | todo | Must |
| Phase 3: Version selection coverage | todo | Must |
| Phase 4: Quick fix | todo | Should |
| Phase 5: Completion | todo | Should |
| Phase 6: Hover documentation | todo | Should |
| Phase 7: Verify in IDE | todo | Should |
