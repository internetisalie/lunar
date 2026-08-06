---
id: "BUG-406"
title: "LDoc's untyped @param is misread as a LuaCATS typed @param"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-406: LDoc's untyped `@param` is misread as a LuaCATS typed `@param`

LDoc writes `@param <name> <english description>` with **no type slot**. LuaCATS writes
`@param <name> <type> <description>`. The grammar cannot tell them apart, so the first word of the
prose is consumed as the parameter's type.

> **This report corrects the original roadmap entry**, which justified a `Must` priority on two
> claims that measurement does not support. Both are addressed under "What is actually affected".
> The defect is real; its blast radius is a fraction of what was recorded.

## Reproduction

```lua
--- Search an array.
--- @param array Lua table of values to search
--- @param e a value
local function findValue(array, e)
    return array[1] == e
end
```

Probe output (agent-run 2026-08-05, `BasePlatformTestCase` fixture):

```
paramTag name=array argType='Lua'
paramTag name=e     argType='a'
```

## What is actually affected — measured, not assumed

**1. The type engine is NOT affected.** The same probe reports `param array : { ... }`,
`param e : number` — both inferred from usage — and **`errors in graph = 0`**.
`LuaTypeGraphBridge.injectParamAnnotations` (`:122`) does
`resolveTypeWithGenerics(typeName, …) ?: return@forEachIndexed`, and `Lua` / `a` resolve to nothing,
so no constraint is ever injected. The roadmap's claim that this is "a large part of why Penlight's
first baseline records `LuaTypeAssignability=570`" is **false**; that number has another cause.

**2. Penlight is NOT affected**, despite carrying 490 `@param`/`@tparam` lines. Real LDoc — and
Penlight specifically — writes a `---` summary line followed by `--` continuation lines:

```lua
--- convert a SIP pattern into the equivalent Lua string pattern.
-- @param spec a SIP pattern
```

`LuaLexer.getTokenType` only produces a `LUACATS_COMMENT` when **every** line of the merged comment
starts with `---`, so this shape is a plain short comment and yields **no `paramTag` at all**. The
probe confirms it: zero `paramTag` elements for the Penlight shape. The bug needs `---` on every
line — a real style, but not the one the corpus member uses.

**3. What IS affected: the documentation surfaces.** `argType` has nine consumers; the user-visible
ones are `LuaParameterInfoHandler.kt:140`
(`types.addAll(comment.paramTagList.map { it.argType.text })`) and
`LuaCatsDocumentationRenderer`. Parameter info and quick documentation therefore display
`array: Lua` and `e: a`.

**4. Residual constraint injection is small and mostly benign.** Where the first prose word happens
to be a primitive name, the type *does* resolve and *is* injected. Measured across Penlight's 329
bare-`@param` lines, that is ~14 (`table`, `number`, `any`) against 315 that resolve to nothing
(`a` ×110, `value` ×34, `the` ×28, `optional` ×15, …). In practice those 14 read as
`@param n number of items`, where `number` is also the correct type.

## Root cause

`luacats.bnf:143`:

```
paramTag ::= '@param' ((<<ArgName NAME>> <<ArgSymbol ('?')>>?) | <<ArgSymbol ('...')>>) <<ArgType type>> description?
```

`<<ArgType type>>` accepts a bare `NAME`, and every English word is a bare `NAME`. BUG-393 added
`ldocParamTag ::= '@param' description` as a **fallback** (`:153`), tried only after `paramTag`
fails — and `paramTag` does not fail here, it succeeds wrongly.

**Pre-existing, not caused by BUG-393**, but BUG-393 widened its reach: a description containing a
backtick used to error visibly and now parses to a silently wrong type. A visible error became a
silent bad inference, which is the worse failure mode.

## Fix strategy

**Do not** fix this in the grammar. A parse-time heuristic cannot distinguish `value` from a
`@class Value`; the discriminating knowledge is *resolution*, which the parser must not depend on.

**Do not** use a per-file dialect signal either. Measured: only **11 of 26** Penlight files using
bare `@param` also carry `@tparam`/`@treturn`, so a marker-based signal misclassifies most of them.

Fix at the **display layer**, matching what the type engine already does — treat an `@param` type
that resolves to nothing as prose rather than as a type:

- `LuaParameterInfoHandler.kt:140` — render the declared type only when it resolves
  (`LuaTypeManager.resolveType`, a primitive, or a structural form such as `a|b`, `t[]`,
  `table<k,v>`, `fun(…)`); otherwise render the parameter untyped.
- `LuaCatsDocumentationRenderer` — same rule for the `@param` row, folding the unresolved word back
  into the description text so no documentation is lost.

This makes the whole system consistent: `LuaTypeGraphBridge` already ignores unresolvable `@param`
types, and after this the doc surfaces agree with it.

**Deliberately out of scope:** warning on an unresolvable `@param` type (it would fire ~315 times on
Penlight alone and needs its own design), and the ~14 prose-words-that-are-primitives, which are not
distinguishable even with resolution.

## Verification

- Regression test: the all-`---` fixture above renders `array` and `e` untyped in parameter info,
  and their descriptions retain the words `Lua` and `a`.
- A genuine LuaCATS `--- @param x string the name` still renders `x: string`.
- A `@class`-declared type still renders: `--- @param b Builder` where `Builder` is declared.
- Corpus ratchet unmoved — Penlight is not affected, so this must change no baseline.

## Why priority drops from Must to Low

The `Must` rested on the type engine and Penlight claims, both of which measurement refutes. What
remains is cosmetic-but-wrong text on two documentation surfaces, for one LDoc styling variant.

## Outcome (2026-08-06)

Fixed in `LuaCatsDeclaredType`, consulted by the three render sites
(`LuaCatsDocumentationRenderer`'s signature line and Parameters table, and
`LuaParameterInfoHandler`). **Two signals are required to demote**, not one: the word resolves to
nothing *and* the tag carries prose behind it.

Resolution alone was the first attempt and it was wrong — it broke `testSimpleParamTypeIsHyperlinked`
(TC-02c), because `---@param a Player` for a class the index has not seen is indistinguishable from
prose, and demoting it drops a type the author did write. The second signal narrows the rule to what
it is for: *an unresolvable name followed by a sentence is the first word of that sentence.*

The first draft of the regression tests was **vacuous**: two of them passed before the fix, because
`renderTypeText` wraps every type in `<font color=…>` so `contains("(Lua)")` was false either way.
Rewritten to assert on tag-stripped text, exactly the two bug-targeting tests fail before the fix and
pass after; the three control tests (primitive, declared class, structural) pass throughout.

Gates: full suite **2389 / 0** (1 skipped), ktlint clean. The corpus ratchet is not affected and was
not re-run — the sweep exercises inspections and require resolution, neither of which renders
documentation or parameter info.
