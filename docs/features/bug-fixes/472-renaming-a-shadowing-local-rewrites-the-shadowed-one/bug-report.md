---
id: "BUG-472"
title: "Renaming a `local` that shadows an earlier same-file `local` renames the WRONG declaration and silently changes what the program does"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-472: the dialog renames the shadowed declaration, not the one under the caret

Found 2026-08-26 while driving [[REFACT-07]]'s Gap 2.21 end-to-end. **Not caused by REFACT-07** — it
is on the **dialog path**, which that feature does not touch, and it reproduces with the in-place
handler absent. **But note the reachability caveat under Reproduction**: with REFACT-07 shipped, the registry no
longer routes this caret to the dialog, so the corruption is not reachable by Shift+F6 on current
`main`.

## Reproduction

```lua
local config = 1
local config = 2
print(config)
```

Two `local`s with the **same name** — the second shadows the first for the rest of the scope, so
`print(config)` resolves to line 2 and the program prints `2`.

Put the caret on **line 2's** `config` and rename it to `renamed`.

**Reachability — read this before reproducing.** On current `main`, with [[REFACT-07]] shipped, the
registry hands that caret to `LuaInplaceRenameHandler`, which fails loudly and changes nothing (see
"What REFACT-07 does" below). **Shift+F6 on current `main` does not reach the corruption.** The
corruption is on the **dialog** path, measured by driving it directly, and it is what this caret got
before REFACT-07 and what it still gets anywhere the dialog path is reached for this shape. The
underlying resolution defect is unchanged either way and affects everything keyed on it.

## Expected

Line 2 is renamed and `print(config)` follows it, because that usage resolves to line 2:

```lua
local config = 1
local renamed = 2
print(renamed)
```

The program still prints `2`.

## Actual

**Line 1 is renamed instead**, and `print(config)` — which resolves to line 2 — is rewritten to
match line 1:

```lua
local renamed = 1
local config = 2
print(renamed)
```

The rename reports success and the file stays valid Lua. **The program printed `2` before and
prints `1` after.** Nothing warns the user.

## Why this is `high`

This is [[BUG-457]]'s class of outcome — code silently broken by a refactoring that reports success —
with one difference that makes it worse: BUG-457 left the file *unparseable*, so it announced itself.
This leaves the file valid and only changes what it computes. The failure is invisible until
something downstream produces a wrong answer.

## Root cause

The declaring statement is excluded from its own scope (`LuaBlockExt.kt:32-36`), so `scopeCrawlUp`
resolves line 2's own name to the **earlier** declaration, and
`TargetElementUtilBase.java:235-239` prefers that resolve over the caret's element. Everything
downstream then operates on line 1.

## Relationship to the other reports in this family

| | Shadowed thing | Outcome | Report |
| :--- | :--- | :--- | :--- |
| `config = 1` ⏎ `local con\|fig = 2` | a **global** | dialog **refuses** (`Cannot perform refactoring.`), document unchanged | [[BUG-470]] |
| `local renamed = 1` ⏎ `local con\|fig = 2` | a **local** | **wrong declaration renamed, program semantics changed** | **this report** |

Same root cause, opposite severity. [[BUG-470]] is a refusal; this is a wrong rewrite. Fixing the
resolution fixes both, and a fix that only restores the *refusal* would leave this one intact — which
is why it is filed separately rather than folded in.

## What REFACT-07 does on the same fixture, and why it must NOT be "fixed" first

With the in-place handler present, `MemberInplaceRenamer` collects the refs of the element it was
handed (line 1's declaration), `getSelectedInEditorElement` finds none holding the caret at `(23,29)`,
and control reaches `LOG.error` + `return null` — after which
`InplaceRefactoring.java:363` (`:362` shipped) dereferences that null unguarded:
`NullPointerException: Cannot invoke "PsiElement.getTextRange()" because "selectedElement" is null`.

So the in-place path **fails loudly and touches nothing**, where the dialog silently corrupts. On this
fixture in-place has the *smaller* blast radius.

**Do not add a guard to `LuaInplaceRenameHandler.declaringNameRefOf` to make it decline this caret.**
Declining routes the caret back to the dialog — i.e. into the corruption above. The NPE is ugly and
should not ship indefinitely, but it is currently protective, and it stops being the right question
once the resolution is fixed. Fix the resolution first.

Note also that the obvious guard shape is unavailable: a **usage** caret legitimately supplies a
declaration leaf that is *not* at the caret (REFACT-07's TC-04, a core case), so "the supplied leaf
must contain the caret" cannot be applied universally. What separates the two is whether the caret
falls inside the supplied element **or one of its collected references** — the predicate
`getSelectedInEditorElement` already computes, and today feeds into that unguarded dereference.

## Evidence

Driven on the shipped tree with a real editor data context, nothing injected; the dialog control and
a non-shadowing control were both run. Recorded in
`docs/features/refactoring/07-inplace-rename/risks-and-gaps.md`, Gap 2.21.
