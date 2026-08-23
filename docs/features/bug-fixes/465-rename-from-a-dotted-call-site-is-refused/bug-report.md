---
id: "BUG-465"
title: "Rename from an `M.run()` call site is refused — TargetElementUtil hands back the whole LuaFuncDecl"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-465: rename-from-usage works for every kind except a dotted function

Measured by REFACT-01 **DR-05** (2026-08-23) and recorded as that feature's `risks-and-gaps.md`
**Gap 2.14**. Filed here because it is a shipped user-visible limitation with no requirement row of
its own, and because it is the **second** gap needing the same missing instrument — the first being
Gap 2.9, the numeric-`for` declaration caret.

## Reproduction

```lua
-- a.lua
function M.run() end
M.ru|n()          -- caret here, invoke Rename (Shift+F6)
```

**Expected:** the same rename the declaration caret gets — `REFACT-01-02` makes rename-from-usage
the contract for every other declaration kind, and it works for locals, parameters, `for`
variables, local functions and every bare global form.

**Actual:** the platform refuses with `error.cannot.be.renamed` ("This element cannot be renamed"),
before `LuaRenameProcessor` is consulted at all.

## Mechanism — verified statically and by probe, not inferred

1. `TargetElementUtil.findTargetElement` resolves the reference and returns the whole enclosing
   **`LuaFuncDecl`**, not the `run` identifier leaf.
2. `LuaRenameProcessor.canProcessElement` is `element is LuaNameRef || LuaDeclarationSite.kindOf(element) != null`.
   A `LuaFuncDecl` is neither a `LuaNameRef` nor a classified leaf, so the predicate is false.
3. `RenamePsiElementProcessorBase.forPsiElement` therefore selects no processor and the platform's
   own refusal fires.

DR-05 called `substituteElementToRename` on that same `LuaFuncDecl` directly and it returned the
correct answer — the `run` leaf, `DOTTED_FUNCTION`. **The machinery is right and simply never
runs.**

## Severity: contained, never a half-rename

The failure mode is a refusal, not a silent partial rewrite, which is the outcome REFACT-01 exists
to guarantee. The dotted function still renames correctly from its **declaration** caret
(`LuaRenameTest.testRenameDottedFunctionDeclaration`, TC-09).

## Why it was not fixed inside REFACT-01

Widening `canProcessElement` to admit declaration nodes closes this and simultaneously **reopens**
Gap 2.10: `LuaDeclarationSite.identifierLeafOf(LuaFuncDecl)` is the *last* name segment either way,
so it cannot tell "the user pointed at `run`" from "the user pointed at the receiver `M`" and would
silently redirect the receiver caret to the wrong segment. Closing it properly needs the caret
offset, i.e. a `TargetElementEvaluatorEx2` for Lua.

## Fix sketch

Register a `com.intellij.targetElementEvaluator` for `LuaLanguage` implementing
`TargetElementEvaluatorEx2.getElementByReference` (or `adjustReferenceOrReferencedElement`) so that
a caret inside a `funcName` or on a member access maps to the IDENTIFIER leaf **under the caret**
rather than to the enclosing declaration. That one evaluator is what Gap 2.9 (numeric-`for`
declaration caret) needs as well; fix them together or not at all.

## Verification

A `BasePlatformTestCase` case with the caret on `M.ru<caret>n()`, asserting the rename applies to
declaration and call sites both — today it throws the platform's `cannot be renamed` refusal, which
makes it the reproduction and the regression test in one.
