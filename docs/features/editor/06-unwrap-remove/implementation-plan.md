---
id: "EDITOR-06-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "EDITOR-06"
folders:
  - "[[features/editor/06-unwrap-remove/requirements|requirements]]"
---

# EDITOR-06: Implementation Plan

Sequence the design into shippable, independently-testable phases. All new code lands
under `src/main/kotlin/net/internetisalie/lunar/lang/insight/unwrap/`; tests under
`src/test/kotlin/net/internetisalie/lunar/lang/insight/`. Build/verify only via
`gce-builder` (not in scope for planning). Design precondition met: algorithms §3.1–§3.4
specified, plugin.xml block stated (§7), all classes named (§2), Open Questions empty.

## Phases

### Phase 1: Descriptor + block-hoist unwrap (`if`/`while`/`for`/`do`/`function`) [Must]
- **Goal**: Ctrl+Shift+Delete offers "Unwrap '<kw>'" for the five constructs and hoists the body. Delivers EDITOR-06-01 and the preview highlight (EDITOR-06-04) for these options.
- **Tasks**:
  - [ ] Create `LuaConstruct` enum (design §2.7) — construct→PSI-type mapping + descriptions.
  - [ ] Create `LuaBlockStructure` object with `primaryBody` (§3.1), `blockParent`, `hasElseOrElseIf`, `ifBranches` (§3.2), `LuaIfBranch` data class (§2.4).
  - [ ] Create `LuaUnwrapper` abstract base + `Context.extractBlockBody` delegating to platform `AbstractContext.extract` (§2.2).
  - [ ] Create `LuaBlockUnwrapper` (§2.3): `isApplicableTo` (construct match; `IF` excludes else/elseif) + `doUnwrap` (extract body, delete construct).
  - [ ] Create `LuaUnwrapDescriptor extends UnwrapDescriptorBase` returning the unwrapper array (§2.1).
  - [ ] Register `<lang.unwrapDescriptor language="Lua" .../>` in `plugin.xml` (§7).
- **Exit criteria**: TC-01 (unwrap if), TC-05 (unwrap while), TC-06 (unwrap numeric for), TC-07 (unwrap do), TC-08 (unwrap function) pass via `UnwrapHandler().invoke` + `myFixture.checkResult`.

### Phase 2: Else/elseif collapse + remove-construct [Should]
- **Goal**: Add the "Remove 'else' branch" and "Remove enclosing block" options. Delivers EDITOR-06-02 and EDITOR-06-03.
- **Tasks**:
  - [ ] Create `LuaElseBranchRemover` (§2.5) implementing §3.3 (rebuild-and-`replace` + reformat, mirroring `LuaInvertIfIntention`); add to descriptor array.
  - [ ] Create `LuaRemoveConstructUnwrapper` (§2.6) with `addElementToExtract(element)` before `delete` for preview (§3.4 note); add to descriptor array.
- **Exit criteria**: TC-02 (collapse else), TC-03 (remove while), TC-09 (three-way if drops only trailing else) pass.

### Phase 3: Preview-range + option-set verification [Should]
- **Goal**: Lock EDITOR-06-04 and the option picker contents with explicit assertions.
- **Tasks**:
  - [ ] Add a test asserting `LuaUnwrapDescriptor().collectUnwrappers(...)` returns the expected `(element, description)` set for a nested `function`+`do`+`if` fixture (mirrors platform `assertOptions`), covering the affected-range/preview path via `collectAffectedElements`.
- **Exit criteria**: TC-04 (preview/option set) passes.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| EDITOR-06-01 Unwrap block | M | Phase 1 |
| EDITOR-06-02 Unwrap else/elseif | S | Phase 2 |
| EDITOR-06-03 Remove construct | S | Phase 2 |
| EDITOR-06-04 Preview highlight | S | Phase 1 (block hoist) + Phase 3 (assertion) |

## Verification Tasks
DoD gate (epic requirements.md): a **real-flow** test drives the `UnwrapHandler` and asserts document text. Pattern (grounded in `intellij-community` `GroovyUnwrapTest`):
```kotlin
class LuaUnwrapTest : BasePlatformTestCase() {
    private fun assertUnwrapped(before: String, after: String, option: Int = 0) {
        myFixture.configureByText("a.lua", before)
        // drive the same handler the action uses; select option via the descriptor
        UnwrapHandler().invoke(project, myFixture.editor, myFixture.file)
        myFixture.checkResult(after)
    }
}
```
- [ ] `LuaUnwrapTest.testUnwrapIf` — covers TC-01.
- [ ] `LuaUnwrapTest.testCollapseElse` — covers TC-02.
- [ ] `LuaUnwrapTest.testRemoveWhile` — covers TC-03.
- [ ] `LuaUnwrapTest.testOptionsAndPreviewRange` — covers TC-04 (assert offered options + `collectAffectedElements` range).
- [ ] `LuaUnwrapTest.testUnwrapWhile/For/Do/Function` — covers TC-05..TC-08.
- [ ] `LuaUnwrapTest.testThreeWayIfDropsElse` — covers TC-09.
- [ ] Run `human-verification-checklists.md` (VNC: Ctrl+Shift+Delete picker + live preview highlight).

> Test note: `UnwrapHandler` shows an interactive popup when multiple options apply. For deterministic single-option tests, place the caret so only one unwrapper is applicable, or invoke `LuaUnwrapDescriptor().collectUnwrappers(...)` and call the chosen `Unwrapper.unwrap(editor, element)` directly inside a `WriteCommandAction` (the platform-test pattern `UnwrapTestCase.assertUnwrapped(code, expected, option)` uses a subclassed handler that auto-selects `options[option]`).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Descriptor + block-hoist unwrap | todo | Must |
| Phase 2: Else collapse + remove construct | todo | Should |
| Phase 3: Preview-range + option-set verification | todo | Should |
