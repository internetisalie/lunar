---
id: "BUG-469"
title: "Shift+F6 on a numeric-`for` variable reaches no rename handler at all"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-469: a numeric-`for` control variable cannot be renamed

Found 2026-08-25 by [[REFACT-07]]'s `REFACT-07-00-05` (DR-05) data-context probe. Not caused by
REFACT-07; a shipped defect the probe surfaced while measuring something else.

## Reproduction

```lua
for i = 1, 10 do
    print(i)
end
```

Place the caret on `i` in the `for` header and press <kbd>Shift+F6</kbd>.

## Expected

The rename dialog opens and renaming `i` updates the control variable and its uses in the loop
body, as it does for any other Lua local.

## Actual

Nothing happens. No dialog, no error, no balloon.

**Confirmed end-to-end 2026-08-29**, which the scope note below said had never been done. Driven
through the editor's own data context with nothing injected, the `RenameElement` action's `update`
left the presentation **disabled** — so the keystroke reached no handler and produced no refusal
either. The prediction in this section was right, and `REFACT-01`'s `risks-and-gaps.md` Gap 2.9,
which recorded the symptom as *"reports 'cannot rename'"*, was wrong; it has been corrected.

## Measured

DR-05 probes **f / f2 / f3**, read from a real editor data context
(`DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)` →
`PsiElementRenameHandler.getElement(context)`) with nothing injected:

- `supplied` is **null**
- `RenameHandlerRegistry.getRenameHandlers(context)` returns an **empty list** — not the default
  `PsiElementRenameHandler`, which every other non-label caret falls back to

Every other declaration and usage caret measured in the same run returned a non-null element and a
handler. Evidence: `docs/features/refactoring/07-inplace-rename/dr-05-evidence/measured-rows.txt`.

## Scope note

The probe measured the **data context only**; the end-to-end Shift+F6 flow was not driven. The
symptom is inferred from the empty handler list — a handler list with no entries cannot rename —
but the user-visible behaviour has not itself been observed. Confirm before fixing.

**Discharged.** It was driven before any code changed, on `for <caret>i = 1, 10 do print(i) end`:
`TargetElementUtil.findTargetElement` **null**, `PsiElementRenameHandler.getElement` **null**,
`RenameHandlerRegistry.getRenameHandlers` **empty**, `RenameElement` presentation **disabled**, and
`myFixture.renameElementAtCaret` throwing *"element not found in file … findTargetElement=null"*.
The usage caret `print(i<caret>)` was driven as a control in the same run and renamed correctly.

## Root cause

`numericForStatement ::= FOR IDENTIFIER '=' …` (`lua.bnf:152`) hangs the control variable's
IDENTIFIER leaf directly off the statement, making it the one Lua declaration with no `LuaNameRef`
wrapper. `TargetElementUtilBase.doFindTargetElement` therefore fails both of its branches: the leaf
carries no `PsiReference` for `REFERENCED_ELEMENT_ACCEPTED`, and `LuaNumericForStatement` is a plain
`ASTWrapperPsiElement`, so `getNamedElement`'s `PsiNamedElement`-ancestor fallback finds nothing
either. With no target, `CommonDataKeys.PSI_ELEMENT` is null and every rename handler's availability
predicate declines.

## Fix

`LuaTargetElementEvaluator.getNamedElement` returns the leaf itself for
`LuaDeclarationKind.NUMERIC_FOR_VARIABLE`, and null for everything else. The evaluator is the
`TargetElementEvaluatorEx2` shipped in `f4b49c47` for [[BUG-472]]; this bug predates it, and the
report's guess that closing it would need *"a `LuaDeclarationSite` classification or a reference at
that position"* was superseded by that component's arrival — no new registration was needed.

The leaf is the element `LuaNameReference` already resolves to from a **usage** caret, so the
declaration caret and the usage caret now target the same element. Returning null for every other
kind is load-bearing: it is what lets `TargetElementUtilBase.getNamedElement` continue to its
`PsiNamedElement`-parent branch, which is where BUG-472's declaration carets get their `LuaNameRef`.
Widening the guard to every declaration kind reddens BUG-472's own regression test and two REFACT-07
in-place cases — executed as a mutant, not argued.

Shift+F6 now opens the rename dialog at this caret. No **in-place** template is offered, which is
`REFACT-07-14` and is unchanged: `LuaInplaceRenameHandler.declaringNameRefOf` ends at
`leaf.parent as? LuaNameRef` and there is none.

## Why it is separate from REFACT-07

[[REFACT-07]] delivers in-place rename for declarations the data context already supplies. This
caret supplies nothing, so no in-place work reaches it — the gap is upstream, in what the platform
resolves at a numeric-`for` control variable, and is equally a defect for the ordinary dialog path
that [[REFACT-01]] ships. Fixing it likely means a `LuaDeclarationSite` classification or a
reference at that position, not a rename change.
