---
id: MAINT-02-PLAN
title: "Implementation Plan"
type: plan
parent_id: MAINT-02
status: planned
folders:
  - "[[features/maint/02-label-refactoring/requirements|requirements]]"
---

# MAINT-02: Implementation Plan

<!-- Sequence the design into shippable, verifiable phases. No parser regeneration is
     required (see design §2.5), so there is no human lexer/parser-gen handoff. -->

## Phases

### Phase 1: `PsiNameIdentifierOwner` for labels [Must]
- **Goal**: `LuaLabelName` becomes a first-class renameable named element.
- **Tasks**:
  - [x] Edit `net.internetisalie.lunar.lang.psi.LuaBaseElements` — change
        `interface LuaNameDeclElement : PsiNamedElement` to `: PsiNameIdentifierOwner`; in
        `LuaNameDeclElementImpl` add `getNameIdentifier()` returning the IDENTIFIER leaf and
        make `getName()` null-safe (drop the `!!`). Keep the existing `setName`. Realizes
        design §2.5.
  - [x] Add the `com.intellij.psi.PsiNameIdentifierOwner` import; remove the now-unused
        `PsiNamedElement` import if no longer referenced.
- **Exit criteria**: project compiles; `LuaLabelName` is assignable to
  `PsiNameIdentifierOwner`; TC 6 (`LuaLabelRenameTest.testNameIdentifierOwner`) passes.

### Phase 2: Lazy scope-aware resolution [Must]
- **Goal**: replace the eager file-wide label walk with a scope-correct `PsiScopeProcessor`.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.LuaLabelScopeProcessor` and
        `LuaLabelCompletionScopeProcessor` (new file `lang/LuaLabelScopeProcessor.kt`) —
        realizes design §2.1, §2.2.
  - [x] Add `LuaBlock.processLabelDeclarations(processor, state)` to
        `lang/psi/LuaBlockExt.kt` — realizes design §2.3.
  - [x] Rewrite `net.internetisalie.lunar.lang.LuaLabelReference`: add the private
        `walkLabelScopes(start, visit)` up-walk (design §3.1) and reimplement
        `multiResolve`/`resolve` to return the matched `LuaLabelName`; delete the old
        `findLabels` (`PsiTreeUtil.findChildrenOfType`) path — realizes design §2.4, §3.1.
  - [x] Update `isReferenceTo` to compare against the resolved `LuaLabelName`
        (design §2.4).
- **Exit criteria**: TC 1–5 pass (`LuaLabelResolutionTest`); `LuaLabelReference` no longer
  references `PsiTreeUtil.findChildrenOfType`.

### Phase 3: Rename binding for references [Must]
- **Goal**: a label rename rewrites every in-scope `goto`.
- **Tasks**:
  - [ ] Override `LuaLabelReference.handleElementRename(newElementName)` to call the
        inherited `setName` on the `LuaLabelRef` (design §2.4, §3.3).
- **Exit criteria**: TC 7–9 pass (`LuaLabelRenameTest`) via real `renameElementAtCaret`;
  out-of-scope same-named labels are untouched.

### Phase 4: Lazy goto completion [Should]
- **Goal**: `goto ` completion offers only visible labels.
- **Tasks**:
  - [ ] Reimplement `LuaLabelReference.getVariants` with
        `LuaLabelCompletionScopeProcessor` + `walkLabelScopes` (design §2.4, §3.2).
- **Exit criteria**: TC 10 passes (`LuaLabelCompletionTest`) via real `completeBasic`.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-02-01 | M | Phase 2 |
| MAINT-02-02 | M | Phase 1 |
| MAINT-02-03 | M | Phase 1 + Phase 3 |
| MAINT-02-04 | S | Phase 4 |

## Verification Tasks
- [ ] `LuaLabelResolutionTest` (`BasePlatformTestCase`, `configureByText` + `<caret>`):
      backward (TC 1), forward (TC 2), enclosing block (TC 3), function-boundary negative
      (TC 4), sibling-block negative (TC 5).
- [ ] `LuaLabelRenameTest`: `getNameIdentifier`/`setName` (TC 6), rename-from-declaration
      (TC 7), rename-from-goto (TC 8), scope-isolated rename across two functions (TC 9) —
      all via `myFixture.renameElementAtCaret(...)`.
- [ ] `LuaLabelCompletionTest`: visible-label completion (TC 10) via `myFixture.completeBasic()`.
- [ ] `gce-builder run "ktlintFormat ktlintCheck"` on the touched files; `gce-builder run test`
      stays green.
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md) if present
      (live Rename + goto completion in GoLand).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: `PsiNameIdentifierOwner` for labels | done | Must |
| Phase 2: Lazy scope-aware resolution | done | Must |
| Phase 3: Rename binding for references | todo | Must |
| Phase 4: Lazy goto completion | todo | Should |
