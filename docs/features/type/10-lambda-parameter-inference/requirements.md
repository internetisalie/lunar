---
id: "TYPE-10"
title: "10: Expected-Type → Lambda-Parameter Inference"
type: "feature"
status: "planned"
priority: "low"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-10: Expected-Type → Lambda-Parameter Inference

## Overview

When a lambda (`function(...) … end`) is passed as an argument to a function whose
corresponding parameter is declared as a callback function type (`fun(a: T1, b: T2)`),
Lunar's type engine currently leaves the lambda's own parameters `Undefined` unless the
user hand-annotates them with a direct `---@param`. This feature propagates the **expected
argument type** onto the passed lambda's parameters, so `redis.register_function('f',
function(keys, args) … end)` types `keys`/`args` as `string[]` and `table.sort(t,
function(a, b) … end)` types `a`/`b` from the comparator's declared parameter types — with
no user annotation. It extends the TYPE epic's inference engine
([`TYPE-01`](../01-basic-type-inference/requirements.md)) and re-enables the callback
typing that [`REDIS-05`](../../redis/05-functions-workflow/requirements.md) descoped (Gap 2.4).

## Scope

### In Scope
- In `LuaTypesVisitor.visitFuncCall`, when an argument expression is a lambda (`LuaFuncDef`)
  and the callee's matching parameter is declared as a function type
  (`fun(p1: T1, …)`), bind each of the lambda's parameter symbols to the expected `Ti`.
- Resolve the callee's declared parameter function type from **both** sources:
  a LuaCATS `---@param cb fun(...)` on a locally-declared callee, and a bundled external
  stub (`redis.register_function`, `table.sort`) reached via reference resolution.
- Precedence: a direct `---@param` on the lambda parameter **wins** over the expected type;
  the propagation only fills in parameters the lambda did not annotate itself.
- Preserve the existing `TYPE-04` union/`TYPE-05` generic behavior for callback parameter
  types (an expected `fun(x: A | B)` seeds `A | B`; a generic `fun(x: T)` seeds whatever the
  call-site instantiation produced, or `any` if unresolved).
- A REDIS-04 §3.1c-style regression contract over the shared type-engine seam and its
  consumers (this is a high-blast-radius change to inference for **every** call with a
  lambda argument).

### Out of Scope
- Inferring lambda parameters from an **untyped** parameter slot (e.g. `pcall(f, …)` whose
  `f` has no `fun(...)` annotation) — the lambda's parameters stay `Undefined`/`any`. See
  [Behavior Rules](#behavior-rules) (negative case N3).
- Inferring the lambda's **return** type from the expected function type's declared return
  (only parameters are propagated in this feature). Deferred — see
  [risks-and-gaps.md](risks-and-gaps.md) Gap 2.1.
- Second-and-later segments of a method chain `a:m1(fn):m2(fn)` (the engine models only
  `nameAndArgsList.firstOrNull()`; per `.agents/AGENTS.md`). Deferred — Gap 2.2.
- Narrowing `table.sort`'s comparator `a`/`b` to the **array element type** of the sorted
  list. The bundled stub declares `comp: fun(a: any, b: any): boolean`, so the propagated
  type is `any`, not the element type. Element-type narrowing would require flowing the
  first argument's `Array(T)` into the callback's generic — deferred, Gap 2.3.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TYPE-10-01 | **Expected-type propagation to lambda params** | M | When a lambda arg is passed to a parameter whose declared type is `fun(p1: T1, …)`, each lambda parameter with no direct `---@param` infers `Ti`. |
| TYPE-10-02 | **Stub-provided callback types** | M | The expected function type is resolved from bundled external stubs (`redis.register_function`, `table.sort`) via reference resolution, not only from LuaCATS on a local callee. |
| TYPE-10-03 | **Direct `---@param` precedence** | M | A lambda parameter carrying its own `---@param` keeps that declared type; the expected-type seed does not override it. |
| TYPE-10-04 | **No-op on untyped / non-lambda slots** | M | An argument slot that is not a lambda, or a lambda whose matching parameter has no `fun(...)` type, is unchanged from the current baseline (no spurious narrowing). |
| TYPE-10-05 | **User-visible surfacing** | S | The propagated type is observable through a user-facing surface (inlay hint / completion) on the lambda parameter, not only via `getValueType`. |
| TYPE-10-06 | **Regression contract** | M | The full `.../lang/types/*` suite and the enumerated engine consumers stay green (no inference regressions) on a full-suite run. |

## Detailed Specifications

### TYPE-10-01: Expected-type propagation to lambda params
For a call `callee(arg0, arg1, …)` where `argK` is a `LuaFuncDef` and the callee's declared
type is `LuaFunctionType` with `params[K]` of type `LuaFunctionType` (`fun(p0: T0, p1: T1,
…)`):
- For each `LuaFuncDef` parameter `pi` (by AST position `i` in
  `funcDef.parList.nameList.nameRefList`), if the expected callback type has a `params[i]`,
  and `pi` has no direct `---@param`, add a value edge carrying `Ti` (converted to a
  `LuaGraphType`) into `pi`'s parameter graph node.
- Positional matching only; there is no name-based matching between the expected callback's
  parameter names and the lambda's parameter names. Extra lambda parameters (beyond the
  expected type's arity) are left untouched. Extra expected parameters (beyond the lambda's
  arity) are ignored.
- The self/`:`-call receiver adjustment already performed by the visitor for method
  parameters is **not** applied to the callback's parameters — a `fun(...)` type is a plain
  function type, not a method.

### TYPE-10-02: Stub-provided callback types
The callee's declared function type is resolved by mirroring the proven resolution used by
`LuaParameterInlayHintsProvider.resolveStandardCall` /
`resolveMethodCall` (`LuaParameterInlayHintsProvider.kt:117,100`):
1. Reference-resolve the callee (`LuaNameRef` / dotted `LuaVar` last suffix `nameRef`) to its
   declaration.
2. From the declaration's containing file, obtain `LuaTypesSnapshot.forFile(declFile)` and
   `graphTypeToLuaType(getValueType(decl))`, then `extractFunctionType(...)` to get the
   callee `LuaFunctionType`.
3. Read `params[K].type`; if it is (or contains, via a union) a `LuaFunctionType`, that is the
   expected callback type for argument `K`.

This reaches bundled stubs because `redis.register_function` / `table.sort` resolve to a
`LuaFuncDecl` in the runtime `.lua` stub file, whose `---@param callback fun(...)` /
`---@param comp fun(...)` annotation is on that declaration.

### TYPE-10-03: Direct `---@param` precedence
"Has a direct `---@param`" is decided at graph-construction time: after
`injectParamAnnotations` runs in `visitFunctionBody`, a lambda parameter that carried its
own `---@param` already has a non-`Undefined` `write` on its node from the injected value
edge. The propagation MUST check the lambda parameter node's `write` and skip parameters
whose `write != LuaGraphType.Undefined` (i.e. already annotated), so the seed never
overrides an explicit annotation.

### TYPE-10-04: No-op on untyped / non-lambda slots
- If `argK` is not a `LuaFuncDef`, do nothing for that slot.
- If the callee cannot be resolved to a `LuaFunctionType`, do nothing (baseline).
- If `params[K].type` is not (and does not contain) a `LuaFunctionType`, do nothing —
  the lambda's parameters stay `Undefined`.

## Behavior Rules
- **Positional precedence**: direct `---@param` on the lambda > expected callback param type
  > (nothing → `Undefined`).
- **Union callback types**: if `params[K].type` is `T1 | fun(...) | T2`, the first
  `LuaFunctionType` member is used (matches `extractFunctionType`'s existing behavior).
- **Negative case N1 (non-lambda arg)**: `table.sort(t, myComp)` where `myComp` is a name —
  no lambda, no propagation; `myComp`'s own type is unchanged.
- **Negative case N2 (annotated lambda)**: `table.sort(t, ---@param a number\nfunction(a, b)
  end)` — `a` keeps `number`; only `b` may receive the expected type.
- **Negative case N3 (untyped slot)**: `pcall(function(x) end)` — `pcall`'s `f` parameter has
  no `fun(...)` annotation, so `x` stays `Undefined`.
- **Determinism / no fixpoint dependency**: the expected type is resolved via reference
  resolution + the *declaration file's* snapshot, not via the current file's in-flight graph
  fixpoint, so seeding is order-independent within the current file (mirrors REDIS-04
  §3.1b seam-(b) stability).

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TYPE-10-01, TYPE-10-02 | `redis.register_function('f', function(keys, args) return keys[1] end)` under a Redis target | `getValueType(exprFor("keys"))` | `string[]` (`keys` typed from `callback fun(keys: string[], args: string[])`) |
| 2 | TYPE-10-01, TYPE-10-02 | same as #1 | `getValueType(exprFor("keys[1]"))` | `string` (re-enables REDIS-05 TC-STUB-1). This exercises the lazy-subscript path (design §3.4): the `keys[1]` subscript is visited *before* the `keys` param is seeded, so it must resolve via the deferred `arrayElementType(keys.node.write)` read, not an eager one. |
| 3 | TYPE-10-01, TYPE-10-02 | `table.sort(t, function(a, b) return a end)` (any target) | `getValueType(exprFor("a"))` | `any` (comparator declared `fun(a: any, b: any)`) — proves stub-driven propagation without asserting element narrowing |
| 4 | TYPE-10-01 | `---@param cb fun(x: string)`\n`local function run(cb) end`\n`run(function(x) return x end)` | `getValueType(exprFor("x"))` | `string` (LuaCATS-annotated local callee) |
| 5 | TYPE-10-03 | `run(---@param x number\nfunction(x) return x end)` with `run`'s `cb` = `fun(x: string)` | `getValueType(exprFor("x"))` | `number` (direct `---@param` wins) |
| 6 | TYPE-10-04 | `local function run(cb) end`\n`run(function(x) return x end)` (`cb` untyped) | `getValueType(exprFor("x"))` | `Undefined` (untyped slot — regression invariant) |
| 7 | TYPE-10-04 | `table.sort(t, myComp)` where `local myComp = function(a,b) return a<b end` | `getValueType(exprFor("myComp"))` unchanged vs baseline | non-lambda arg unaffected (regression invariant) |
| 8 | TYPE-10-04 | `local function run(cb) end`\n`run(42)` | `getValueType(exprFor("42"))` unchanged; no exception | non-lambda, non-function slot unaffected (regression invariant) |
| 9 | TYPE-10-05 | `redis.register_function('f', function(keys, args) keys: end)` | completion on `keys.`/`keys[1]:` OR inlay/type hint on `keys` | offers `string[]`/string members — user-visible surface |
| 10 | TYPE-10-06 | full `.../lang/types/*` suite + consumers | `run test` (full suite, not isolated `--tests`) | 0 failures — no inference regression |

## Acceptance Criteria
- [ ] TYPE-10-01: a lambda passed into a `fun(...)`-typed slot infers its parameters from the expected type (TC 1, 4).
- [ ] TYPE-10-02: stub-provided callback types propagate (TC 1, 3).
- [ ] TYPE-10-03: a direct `---@param` on the lambda is not overridden (TC 5).
- [ ] TYPE-10-04: non-lambda / untyped / non-function slots are unchanged from baseline (TC 6, 7, 8).
- [ ] TYPE-10-05: the propagated type is observable on a user-facing surface (TC 9).
- [ ] TYPE-10-06: the full type-engine suite and enumerated consumers stay green on a full-suite run (TC 10).

## Non-Functional Requirements
- **Threading**: all work occurs inside the existing `LuaTypesVisitor` traversal, which runs
  under a read action via `LuaTypesSnapshot.forFile`; no new EDT or write-action work. The
  reference resolution added in §3.2 is a read-action PSI operation, valid in this context.
- **Caching**: no new cache. The result rides the existing `LuaTypes` snapshot cached by
  `CachedValuesManager` (`LuaTypes.forFile`); the cross-file declaration snapshot is itself
  `forFile`-cached. No hard references to `Project`/`Editor`/`PsiFile` are retained.
- **Complexity**: propagation is O(args × lambda-params) per call; reference resolution is
  bounded by one `resolve()` per lambda-bearing call. Callee-type resolution is memoized per
  call via a local `val` (resolved once, reused across the call's lambda args).
- **Contract**: ≤30 logic lines/function, ≤3 args including private helpers, no `!!`,
  immutability-first, static-singleton element types unchanged.

## Dependencies
- [`TYPE-01`](../01-basic-type-inference/requirements.md) — the inference engine (`LuaTypesVisitor`, `LuaTypeGraph`). **Done.**
- Bundled runtime stubs (`src/main/resources/runtime/…/table.lua`, `redis/*/redis.lua`) — existing.
- Unblocks [`REDIS-05`](../../redis/05-functions-workflow/requirements.md) AC-2 / TC-STUB-1 (Gap 2.4).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklists: [human-verification-checklists.md](human-verification-checklists.md)
