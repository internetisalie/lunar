---
id: MAINT-02
title: "MAINT-02: Label Refactoring"
type: feature
parent_id: MAINT
status: planned
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-02: Label Refactoring

<!-- SRS — the "what". Behaviour, constraints, acceptance. -->

## Overview

Lua `goto`/label handling is the last name-resolution path still on the pre-MAINT-04
eager model. `LuaLabelReference.multiResolve` walks the **whole file**
(`PsiTreeUtil.findChildrenOfType(containingFile, LuaLabel)`,
`LuaLabelReference.kt:61`), which ignores Lua's block-scoping rules (it would resolve a
`goto` to a label inside an unrelated sibling block or nested function) and re-scans the
entire file on every keypress. This feature replaces that with a lazy, scope-aware
`PsiScopeProcessor` walk (the same idiom MAINT-04 introduced for variables in
`LuaScopeProcessor`/`LuaBlockExt`) and wires `::label::` into the platform's native Rename
refactoring by making `LuaLabelName` a `PsiNameIdentifierOwner` and binding `goto`
references to the rename. Parent epic: [[features/maint/requirements|MAINT]].

## Scope

### In Scope
- Scope-aware, lazy resolution of `goto X` to the nearest in-scope `::X::` declaration via a
  `PsiScopeProcessor`, replacing the eager file-wide walk.
- Correct Lua label visibility: same block, enclosing blocks within the same function,
  forward **and** backward references; **not** across a function boundary nor into a
  sibling/nested block that does not enclose the `goto`.
- `LuaLabelName` implementing `PsiNameIdentifierOwner` (`getNameIdentifier`, plus the
  already-present `getName`/`setName`).
- Native Rename of a label: renaming the `::X::` declaration (or invoking Rename on a
  `goto X`) updates the declaration and every in-scope `goto X`, leaving out-of-scope
  same-named labels untouched.
- Lazy `goto` completion (`LuaLabelReference.getVariants`) offering only visible labels.

### Out of Scope
- Cross-file label resolution. Lua labels are file-local by language definition; no
  stub-index path is added.
- Validating `goto`/label legality (jumping into a local's scope, duplicate visible labels).
  That is a diagnostic concern, deferred to a future INSP item.
- Changing `LuaLabelReferenceContributor`, `LuaFindUsagesProvider`, or
  `LuaRefactoringSupportProvider` registrations (all already present in `plugin.xml`).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-02-01 | **Lazy scope-aware label resolution** | M | `goto X` resolves to the nearest enclosing in-scope `::X::` `LuaLabelName` using a `PsiScopeProcessor` block-walk that stops at the first match and at the enclosing function boundary; no whole-file traversal. |
| MAINT-02-02 | **`LuaLabelName` is `PsiNameIdentifierOwner`** | M | `LuaLabelName` reports its IDENTIFIER leaf as the name identifier and supports `setName`, so the platform treats `::X::` as a first-class renameable named element. |
| MAINT-02-03 | **Native label rename** | M | The platform `RenameProcessor` renames a label declaration and all in-scope `goto` references together; same-named labels outside the renamed label's scope are not affected. |
| MAINT-02-04 | **Lazy goto completion** | S | Completing after `goto ` offers exactly the labels visible at that position, collected lazily via a completion scope processor. |

## Detailed Specifications

### MAINT-02-01: Lazy scope-aware label resolution
Lua 5.4 §3.3.4 label visibility: a label is visible in the entire block where it is
defined and in blocks nested inside it, **except inside nested function bodies**; a `goto`
may target a label in its own block or any enclosing block of the same function, regardless
of textual order (forward jumps are legal). Therefore resolution must:
- Start at the `LuaLabelRef` and walk **up** the PSI ancestor chain.
- At each `LuaBlock` ancestor, scan that block's `statementList` for a `LuaLabel` whose
  `labelName.identifier.text` equals the reference name (no textual-order gating — forward
  references resolve).
- Stop and succeed on the first match (nearest enclosing block wins).
- Stop and fail (unresolved) upon reaching a function PSI node (`LuaFuncDef`,
  `LuaFuncDecl`, `LuaLocalFuncDecl`, `LuaGlobalFuncDecl`) or the `LuaFile` root without a
  match — labels never resolve across a function boundary.
- The resolve target is the `LuaLabelName` (a `PsiNamedElement`/`PsiNameIdentifierOwner`),
  not the bare IDENTIFIER leaf the current code returns.

### MAINT-02-02: `LuaLabelName` is `PsiNameIdentifierOwner`
`LuaLabelName` already extends `LuaNameDeclElement : PsiNamedElement` and inherits a working
`getName`/`setName` from `LuaNameDeclElementImpl` (`LuaBaseElements.kt:48-63`). The only
missing member of `PsiNameIdentifierOwner` is `getNameIdentifier(): PsiElement?`, which must
return the IDENTIFIER leaf (`labelName ::= IDENTIFIER`).

### MAINT-02-03: Native label rename
With MAINT-02-02 in place, `RenameProcessor` renames the `LuaLabelName` via its `setName` and
locates references through `ReferencesSearch` (default named-element searcher; labels are
explicitly excluded from `LuaNameReferenceSearcher`, see `LuaNameReferenceSearcher.kt:36-38`).
Each found `LuaLabelReference` must rewrite its `goto` identifier — `LuaLabelReference` must
override `handleElementRename` (no `ElementManipulator` is registered for `LuaLabelRef`).
Scope isolation follows from MAINT-02-01: `isReferenceTo` only returns true for references
whose `resolve()` is the renamed `LuaLabelName`, so out-of-scope same-named gotos are skipped.

### MAINT-02-04: Lazy goto completion
`LuaLabelReference.getVariants` collects visible labels with a completion-mode scope
processor using the same up-walk as MAINT-02-01 (without the stop-on-first-match), returning
one `LookupElement` per distinct visible label name.

## Behavior Rules
- **Nearest scope wins**: an inner-block `::X::` shadows an enclosing `::X::` for a `goto X`
  in the inner block.
- **Function boundary is hard**: a `goto` inside a nested function never sees an outer
  function's (or file-level) labels, and vice versa.
- **Sibling blocks are invisible**: a label in a `do … end` that does not enclose the `goto`
  is not a candidate.
- **Order-independent**: forward and backward `goto` both resolve (unlike local variables).
- **Unresolved is empty**: when no in-scope label matches, `multiResolve` returns an empty
  array (reference shows as unresolved), never a cross-scope false positive.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-02-01 | `::done::\nprint(1)\ngoto don<caret>e` | `findReferenceAt(caret).resolve()` | Returns a `LuaLabelName` with `identifier.text == "done"` whose parent is the `::done::` `LuaLabel`. |
| 2 | MAINT-02-01 | `goto don<caret>e\n::done::` (forward jump) | resolve the reference | Resolves to the `::done::` `LuaLabelName` (forward reference is legal). |
| 3 | MAINT-02-01 | `::top::\ndo\n  goto to<caret>p\nend` | resolve the reference | Resolves to the outer `::top::` (enclosing-block visibility). |
| 4 | MAINT-02-01 | `::outer::\nlocal f = function() goto out<caret>er end` | `multiResolve(false)` | Returns an empty array — the label is across a function boundary and not visible. |
| 5 | MAINT-02-01 | `do ::inner:: end\ngoto inn<caret>er` | `multiResolve(false)` | Empty array — `::inner::` is in a sibling block that does not enclose the `goto`. |
| 6 | MAINT-02-02 | `::lbl::` | cast the `LuaLabelName` and call `getNameIdentifier()`, then `setName("renamed")` | `getNameIdentifier().text == "lbl"`; after `setName`, the declaration text is `::renamed::` and `getName() == "renamed"`. |
| 7 | MAINT-02-03 | `::myLabel::\ngoto myLabel` with caret on the `::myLabel::` identifier | `myFixture.renameElementAtCaret("newLabel")` | File becomes `::newLabel::\ngoto newLabel`. |
| 8 | MAINT-02-03 | `::myLabel::\ngoto my<caret>Label` (caret on the goto) | `renameElementAtCaret("newLabel")` | File becomes `::newLabel::\ngoto newLabel` (rename from the reference site). |
| 9 | MAINT-02-03 | `function a() ::L:: goto L end\nfunction b() ::L:: goto L end` with caret on `a`'s `::L::` | `renameElementAtCaret("L2")` | Only function `a` becomes `::L2:: goto L2`; function `b` is unchanged. |
| 10 | MAINT-02-04 | `::alpha::\n::beta::\ngoto <caret>` | `myFixture.completeBasic()` and read lookup strings | Lookup contains `alpha` and `beta`. |

## Acceptance Criteria
- [ ] MAINT-02-01: TC 1–5 pass; `LuaLabelReference` no longer calls
      `PsiTreeUtil.findChildrenOfType(containingFile, LuaLabel)`.
- [ ] MAINT-02-02: TC 6 passes; `LuaLabelName` is assignable to `PsiNameIdentifierOwner`.
- [ ] MAINT-02-03: TC 7–9 pass via real `renameElementAtCaret` (no engine-only stub).
- [ ] MAINT-02-04: TC 10 passes via real `completeBasic`.
- [ ] Full unit suite remains green (`gce-builder run test`).

## Non-Functional Requirements
- **Threading**: resolution and completion run under the platform read action that already
  wraps reference resolution; no new threading. Rename mutations occur inside the platform's
  `WriteCommandAction` (driven by `RenameProcessor`); `setName`/`handleElementRename` only
  mutate PSI and must not be wrapped in a second write action.
- **Performance**: resolution is O(enclosing scope depth × statements-per-block), not
  O(file size); the eager full-file scan is removed.
- **Memory**: no hard references to `Project`/`Editor`/`PsiFile` retained; processors hold
  only the search `name` and a result `PsiElement` for the duration of one resolve.
- **Contract**: ≤30 logic lines/function, ≤3 args, `val`-first, no `!!`, no wildcard imports
  (`docs/engineering-contract.md`).

## Dependencies
- [[features/maint/04-refactor-symbol-resolution/requirements|MAINT-04]] — establishes the
  `PsiScopeProcessor`/`processDeclarations` idiom this feature mirrors (done).
- REFACT-05 `LuaNamesValidator` (done) — already validates new rename input; reused as-is.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
