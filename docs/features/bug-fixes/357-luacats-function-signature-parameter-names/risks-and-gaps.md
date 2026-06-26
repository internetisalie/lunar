---
id: "BUG-357-risks"
title: "BUG-357 — Cannot reproduce: claimed root cause contradicted by current code"
type: "risk"
parent_id: "BUG-357"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes/357-luacats-function-signature-parameter-names/bug-report|bug-report]]"
---

# BUG-357 — Cannot Reproduce / Abort & Replan

## Disposition

**Not reproducible on `main` (HEAD `a75d9c7f`).** The behavior the report claims is
broken already works correctly. No code fix was applied — per the Abort & Replan
Protocol, no workaround was invented to "force" a non-existent bug.

## Evidence

Three independent checks, all green before any fix:

1. **Parser extracts names correctly.** `TypeParser.parse("fun(name: string, age: number): boolean")`
   yields `LuaParameter` names `name` / `age`, not `"p"`. The `fun(...)` branch in
   `TypeParser.parseDistinctType` reads `arg.argName.text` directly
   (`TypeParser.kt:99-107`) — `argName` is the grammar rule `functionSignatureArgument ::=
   <<ArgName argumentName>> …` (`luacats.bnf:187`), which captures the real identifier.

2. **Graph round-trip preserves names.** For both
   `---@type fun(myParam1: number, myParam2: string)` (plain) and
   `---@type fun(paramA: number, paramB: string) | string` (union), the declared
   variable's `getValueType(...).displayName()` is `fun(myParam1, myParam2)` /
   `fun(paramA, paramB) | string` — **not** `fun(p, p)`. `LuaGraphType.fromLuaType`
   carries `p.name` into `Function.Parameter` (`LuaGraphType.kt:137`) and
   `graphTypeToLuaType` reads it back (`LuaTypes.kt:107-113`). The `"p"` fallback only
   fires for genuinely anonymous parameters (e.g. a bare `function` primitive / inferred
   call-demand signature), which is not this scenario.

3. **Call-site hints render.** `LuaParameterInlayHintsProvider` produces
   `myParam1:` / `myParam2:` (plain) and `paramA:` / `paramB:` (`| string` union) at the
   call site. The `shouldShowHint` suppression (`paramName == "p"`) is never hit because
   the names are real.

## Likely cause of the stale report

Commit `fe5b1000 fix(hints): support parameter inlay hints on union types (BUG-133)`
already implemented union function-type parameter hints. BUG-357 reads as a re-filing of
behavior BUG-133 resolved; the plain `@type fun(...)` case appears never to have been
broken. The report's stated root cause (names "resolve as `p`") does not hold against the
current AST/graph code paths.

## What was delivered instead

No production code changed. Added regression coverage for the two cases that were
previously **untested** (BUG-133's test only covered `| nil`):

- `TestTypeParser.testParseFunctionType` — tightened to assert parameter *names*
  (`name` / `age`), removing a stale "extraction is loose" comment.
- `LuaParameterInlayHintsTest.testPlainFunctionTypeParameterHints` — plain `@type fun(...)`
  call-site hints.
- `LuaParameterInlayHintsTest.testUnionStringFunctionTypeParameterHints` — `| string`
  union call-site hints.

## Recommendation

Close BUG-357 as **cannot-reproduce / already-resolved (by BUG-133)**. Keep the three new
tests as guards. If a real reproduction exists, it must come with a concrete environment
(IDE build, language level, exact file) where `getValueType(...).displayName()` actually
yields `fun(p, p)` for a *named* signature — none was found here.
