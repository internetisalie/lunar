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

### R-1: Deleting the `platform` field breaks settings deserialization — MOOT (no user base)
- **Original concern**: the field anchored backward-compat loading of pre-`target` `.idea/lunar`
  settings, so deleting it could drop old users' platform selection on upgrade.
- **Resolution (2026-07-03)**: **there is no installed user base**, so no legacy settings exist. The
  migration is ditched — MAINT-03-08 **deletes** the field outright and reworks
  `migrateFromLegacySettings()` into a plain default-target builder. No `@Suppress` shim, no
  persistence-compat handling. This risk no longer applies.

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
| MAINT-03-00-DR-00 | Verify `FileChooserDescriptorFactory.singleFileOrDir()`/`singleDir()`/`singleFile()` exist at 2026.1.3 (else pin the ctor fallback). | MAINT-03-02/03 | **resolved** — all three static methods present in `intellij.platform.ide.core.jar` (`javap FileChooserDescriptorFactory`). Terse methods used, no ctor fallback. |
| MAINT-03-00-DR-01 | Verify the exact `runReadActionBlocking` import (`com.intellij.openapi.application.runReadActionBlocking`) exists and is non-deprecated at 261. | MAINT-03-07, R-2 | **resolved** — `ActionsKt.runReadActionBlocking` present + non-deprecated in `intellij.platform.core.jar`. Import `com.intellij.openapi.application.runReadActionBlocking`. |
| MAINT-03-00-DR-02 | Determine + verify replacements for `TailType.SPACE`, `StubBasedPsiElementBase.getElementType()`, and the `LuaChunkCompletion.kt:17` `ProgressIndicatorUtils` cluster. | MAINT-03-09, R-3 | **resolved** — `TailType.SPACE` → `TailTypes.spaceType()` (present, `intellij.platform.analysis.jar`). `StubBasedPsiElementBase.getElementType()` deprecated → call non-deprecated `getElementTypeImpl()` and cast (returns the same singleton `IStubElementType`). `LuaChunkCompletion.kt:17` is only a `runReadAction`→`runReadActionBlocking` swap (no `ProgressIndicatorUtils` there — that cluster is in `LuaDocSearchEverywhereContributor`, see DR-03). |
| MAINT-03-00-DR-03 | Determine the exact deprecated API + replacement (or `@Suppress` rationale) for `LuaDocSearchEverywhereContributor.kt:111,112` and `LuaCovReportHighlight.kt:24,27` (`createTextAttributesKey`). | MAINT-03-09, R-3 | **resolved** — `LuaDocSearchEverywhereContributor:111,112` = `ProgressIndicatorUtils.yieldToPendingWriteActions()` / `runInReadActionWithWriteActionPriority(task, indicator)`; **all overloads are `@Deprecated`** and the only modern path is coroutine `readAction`/`smartReadAction` (a threading-semantics change, out of scope) → **`@Suppress("DEPRECATION")` + rationale** (R-3). `createTextAttributesKey(String, TextAttributes)` is `@Deprecated` with **no** non-deprecated overload taking raw `TextAttributes` (only `(String)` and `(String, TextAttributesKey)`); sites deliberately pin colors for non-inheriting themes → **`@Suppress("DEPRECATION")` + rationale** (R-3). |
| MAINT-03-00-DR-04 | Confirm the single-arg `TemplateContextType(name)` ctor and that the `templateContextType` EP registration in `plugin.xml` supplies the `"LUA"` contextId (so dropping the id arg is safe). | MAINT-03-09 | **resolved — dropping the id arg is NOT safe without a plugin.xml change.** The single-arg `TemplateContextType(String)` ctor sets only `myPresentableName`, leaving `myContextId` **null**. The `<liveTemplateContext>` registration in `plugin.xml:282` has **no `contextId` attribute**, so `LiveTemplateContextBean.getContextId()` falls back to the instance's `myContextId` and **asserts it is non-null** (throws `AssertionError` otherwise). The two-arg `TemplateContextType(String, String)` ctor (which sets the id) IS `@Deprecated`, but the sanctioned migration (single-arg ctor + `contextId="LUA"` on the EP) requires editing `plugin.xml` — **out of scope** (design §7, invariant #11) and risks the context-id contract. Per **R-3**, this site is kept on the current two-arg ctor with **`@Suppress("DEPRECATION")` + rationale** rather than force-migrated. |

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md) §11
- Plan: [implementation-plan.md](implementation-plan.md) (Phase 0 executes these DR tasks)
