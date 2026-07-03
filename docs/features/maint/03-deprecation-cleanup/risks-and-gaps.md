---
id: "MAINT-03-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-03"
folders:
  - "[[features/maint/03-deprecation-cleanup/requirements|requirements]]"
---

# MAINT-03: Risks & Gaps

Behavior-preserving cleanup; the risks are (a) picking a *semantically different* replacement, and
(b) a few Group-IV replacements not yet verified against the 261 SDK. The DR tasks below gate the
dependent phases (Phase 0).

## Risks

### R-1: Deleting the `platform` field breaks settings deserialization
- **Impact**: `LuaProjectSettings.State.platform` anchors backward-compat loading of pre-`target`
  `.idea/lunar` settings (`migrateFromLegacySettings`). A naive "delete the deprecated field" would
  silently drop old users' platform selection on upgrade.
- **Mitigation**: MAINT-03-08 **keeps** the field (with its `@Property` persistence) and only removes
  *external* callers, confining the migration read/write to one `@Suppress("DEPRECATION")` helper.

### R-2: `runReadAction` → wrong replacement changes threading semantics
- **Impact**: `ReadAction.nonBlocking` is async/cancellable; swapping the 14 blocking sites to it
  would change behaviour (and risks the CLAUDE.md EDT/read-action invariants).
- **Mitigation**: MAINT-03-07 mandates `runReadActionBlocking` (the non-cancellable blocking twin).
  DR-01 verifies its exact import.

### R-3: A Group-IV API has no clean 261 replacement
- **Impact**: forcing a rewrite could change behaviour or not compile.
- **Mitigation**: where DR-02/03 find no equivalent, `@Suppress("DEPRECATION")` the site with a
  one-line rationale (MAINT-03-09) rather than force-migrate — deprecation-warning reduction is the
  goal, not blind API churn.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| MAINT-03-00-DR-00 | Verify `FileChooserDescriptorFactory.singleFileOrDir()`/`singleDir()`/`singleFile()` exist at 2026.1.3 (else pin the ctor fallback). | MAINT-03-02/03 | todo |
| MAINT-03-00-DR-01 | Verify the exact `runReadActionBlocking` import (`com.intellij.openapi.application.runReadActionBlocking`) exists and is non-deprecated at 261. | MAINT-03-07, R-2 | todo |
| MAINT-03-00-DR-02 | Determine + verify replacements for `TailType.SPACE`, `StubBasedPsiElementBase.getElementType()`, and the `LuaChunkCompletion.kt:17` `ProgressIndicatorUtils` cluster. | MAINT-03-09, R-3 | todo |
| MAINT-03-00-DR-03 | Determine the exact deprecated API + replacement (or `@Suppress` rationale) for `LuaDocSearchEverywhereContributor.kt:111,112` and `LuaCovReportHighlight.kt:24,27` (`createTextAttributesKey`). | MAINT-03-09, R-3 | todo |
| MAINT-03-00-DR-04 | Confirm the single-arg `TemplateContextType(name)` ctor and that the `templateContextType` EP registration in `plugin.xml` supplies the `"LUA"` contextId (so dropping the id arg is safe). | MAINT-03-09 | todo |

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md) §11
- Plan: [implementation-plan.md](implementation-plan.md) (Phase 0 executes these DR tasks)
