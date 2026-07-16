---
id: "TYPE-10-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TYPE-10"
folders:
  - "[[features/type/10-lambda-parameter-inference/requirements|requirements]]"
---

# Technical Design: TYPE-10 — Expected-Type → Lambda-Parameter Inference

## 1. Architecture Overview

### Current State

`LuaTypesVisitor.visitFuncCall` (`LuaTypesVisitor.kt:524`) builds a `callDemand`
(`LuaGraphType.Function`) from the call's argument nodes and adds one edge
`calleeNode → use(o, callDemand)` (`LuaTypesVisitor.kt:607`). Each argument node is wrapped
as a `LuaGraphType.Function.Parameter` around a fresh variable node that receives the arg's
own value (`LuaTypesVisitor.kt:578-587`). **Nothing flows the callee's declared parameter
types back onto a passed lambda's own parameters.** A lambda argument
(`function(k) … end`, a `LuaFuncDef`) has already been visited by `super.visitFuncCall(o)`
(`LuaTypesVisitor.kt:525`) → `visitFuncDef` → `visitFunctionBody`
(`LuaTypesVisitor.kt:766`), which declares each lambda parameter's graph node
(`elementNodes[nameRef] = listOf(paramNode)`, `LuaTypesVisitor.kt:802`) and applies only its
**direct** `---@param` annotations (`injectParamAnnotations`, `LuaTypesVisitor.kt:822`). So
an un-annotated lambda parameter's `write` resolves to `Undefined`.

This was ground-truthed by the REDIS-05 Gap 2.4 probe: `rf(function(k) k[1] end)` with
`rf: fun(cb: fun(k: string[]))` inferred `k` as `Undefined`
(`docs/features/redis/05-functions-workflow/risks-and-gaps.md:54`).

### Prior Art in This Repo
Searched `src/main/kotlin/.../lang/psi/types/` and `.../lang/insight/hint/` for existing
expected-type / callback-param handling:
- **`LuaTypesVisitor.visitFuncCall`** (`LuaTypesVisitor.kt:524`) — the call-unification seam.
  This design **EXTENDS** it with a new private step (§2.1) after the existing arg-node
  construction; it does not replace the demand-edge logic.
- **`LuaParameterInlayHintsProvider.resolveStandardCall` / `resolveMethodCall`**
  (`LuaParameterInlayHintsProvider.kt:117,100`) — already resolves a call's callee
  `LuaFunctionType` (including bundled stubs) via reference resolution + the declaration
  file's snapshot. This design **REUSES that exact resolution pattern** (re-implemented as a
  small visitor-local helper, §2.2 — it cannot call the inlay provider's private methods)
  and does not duplicate the inlay feature itself.
- **`injectParamAnnotations`** (`LuaTypeGraphBridge.kt:102`) — applies a lambda's *direct*
  `---@param`. This design leaves it untouched and uses the resulting node `write` to detect
  "already annotated" (§3.1 step 4).
- **`graphTypeToLuaType` / `getValueType`** (`LuaTypes.kt:77,52`) and
  `LuaGraphType.fromLuaType` (`LuaGraphType.kt:108`) — existing `LuaType ↔ LuaGraphType`
  bridges; reused to convert the expected `LuaFunctionType` param types into graph values.
- No existing component performs expected-type→lambda-param propagation. None found — the
  behavior is genuinely new.

### Target State
`visitFuncCall`, after building `argNodes` and before adding the demand edge, calls a new
private method `propagateExpectedLambdaParams(o, argExprs)`. For each argument that is a
`LuaFuncDef` whose callee-declared parameter is a `LuaFunctionType`, it seeds each
un-annotated lambda parameter's graph node with a value carrying the expected parameter's
graph type. All other flow is unchanged.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor` (extended)
- **Responsibility**: host the new propagation step inside the existing call visit.
- **Threading**: same as the enclosing visitor — read action via
  `LuaTypesSnapshot.forFile`. No new threading.
- **Collaborators**: `LuaExpectedCallbackResolver` (§2.2), `LuaTypeGraph` (`value`,
  `addEdge`), `LuaGraphType.fromLuaType` (`LuaGraphType.kt:108`).
- **Key API** (new private members; all ≤3 args, ≤30 logic lines):
  ```kotlin
  // called from visitFuncCall(o) after argNodes are built, before graph.addEdge(calleeNode, …).
  // `argExprs` and `calleeUnwrapped` are the values already in scope at LuaTypesVisitor.kt:571,528.
  // (Bundle the two non-o inputs into a small context data class if a ≤3-arg cap requires it —
  //  o + one context object stays within budget.)
  private fun propagateExpectedLambdaParams(o: LuaFuncCall, argExprs: List<LuaExpr>, calleeUnwrapped: PsiElement?)

  // seeds one lambda arg's params from its expected LuaFunctionType (arity-matched, positional)
  private fun seedLambdaParams(lambda: LuaFuncDef, expected: LuaFunctionType)

  // reads the lambda param node's current write to enforce direct-@param precedence (§3.1 step 4)
  private fun isAlreadyAnnotated(paramNode: VariableNode): Boolean
  ```
- **Notes**: `argExprs` is the *unwrapped* list already computed in `visitFuncCall`
  (`LuaTypesVisitor.kt:578` maps over it). `propagateExpectedLambdaParams` re-reads
  `unwrapExpression(argExpr)` per slot to detect a `LuaFuncDef` (a lambda may be wrapped in a
  `LuaTerminalExpr`/paren — reuse the existing `unwrapExpression`, `LuaTypesVisitor.kt:528`).

### 2.2 `net.internetisalie.lunar.lang.psi.types.LuaExpectedCallbackResolver`
- **Responsibility**: resolve, for a given call, the callee's declared `LuaFunctionType` and
  return `params[K].type` as a `LuaFunctionType` when that slot is a callback type.
- **Threading**: read action (PSI reference resolution + `LuaTypesSnapshot.forFile`).
- **Collaborators**: `LuaTypesSnapshot.forFile` (`LuaTypes.kt:140`), `graphTypeToLuaType` +
  `getValueType` (`LuaTypes.kt:77,52`), `LuaNameRef.reference` / `LuaVar` last-suffix
  `indexExpr.nameRef.reference` (mirrors `LuaParameterInlayHintsProvider.getDeclaration`,
  `LuaParameterInlayHintsProvider.kt:169`).
- **Key API**:
  ```kotlin
  // `calleeUnwrapped` is the already-unwrapped callee the visitor computed at
  // LuaTypesVisitor.kt:528 (so the resolver never re-implements unwrapExpression, which is
  // private on the visitor). `call` supplies nameAndArgsList + containingFile.
  internal class LuaExpectedCallbackResolver(
      private val call: LuaFuncCall,
      private val calleeUnwrapped: PsiElement?,
  ) {
      // the callee's declared function type, or null (memoized in one val by the caller)
      fun resolveCalleeType(): LuaFunctionType?
      // expected callback type for positional arg index K, or null if that slot isn't fun(...)
      fun expectedCallbackAt(index: Int, calleeType: LuaFunctionType, selfOffset: Int): LuaFunctionType?
  }
  ```
- **Rationale for a separate class**: keeps `LuaTypesVisitor` under its method/complexity
  budget and isolates raw reference-resolution from the graph-orchestration logic
  (engineering contract §3, "Parser/PSI orchestration symmetry"). It carries only the
  `LuaFuncCall` (no `Project`/`Editor`/`PsiFile` field), satisfying the memory rule.

## 3. Algorithms

### 3.1 Expected-type propagation (per call)
- **Input → Output**: `(call: LuaFuncCall, argExprs: List<LuaExpr>)` → side effect: value
  edges added into un-annotated lambda parameter nodes. No return.
- **Steps**:
  1. Build `resolver = LuaExpectedCallbackResolver(call, calleeUnwrapped)` (reusing the
     visitor's `calleeUnwrapped` from `LuaTypesVisitor.kt:528`); `calleeType =
     resolver.resolveCalleeType()`. If `calleeType == null`, return (no-op — TYPE-10-04).
     `selfOffset = if (call.nameAndArgsList.first().methodExpr != null) 1 else 0`.
  2. For each `(index, argExpr)` in `argExprs`:
     a. `lambda = unwrapExpression(argExpr) as? LuaFuncDef` — if null, skip (non-lambda slot).
     b. `expected = resolver.expectedCallbackAt(index, calleeType, selfOffset)` — if null,
        skip (slot not a `fun(...)` type).
     c. Call `seedLambdaParams(lambda, expected)`.
  3. `seedLambdaParams(lambda, expected)`:
     - `lambdaParams = lambda.parList?.nameList?.nameRefList ?: emptyList()`.
     - For each `(i, nameRef)` in `lambdaParams`:
       - `expectedParam = expected.params.getOrNull(i)` — if null, stop (lambda arity >
         expected arity; leave the rest untouched).
       - `paramNode = elementNodes[nameRef]?.firstOrNull() as? VariableNode` — if null, skip.
       - If `isAlreadyAnnotated(paramNode)`, skip (TYPE-10-03 precedence).
       - `graphType = LuaGraphType.fromLuaType(expectedParam.type, graph)`
         (`LuaGraphType.kt:108`).
       - `graph.addEdge(graph.value(nameRef, graphType), paramNode)` — this is a
         `ValueNode → VariableElement` edge, handled by `propagateDownward`
         (`LuaTypeGraph.kt:97`), identical in shape to how `injectParamAnnotations` injects a
         direct `---@param` (`LuaTypeGraphBridge.kt:131`).
  4. `isAlreadyAnnotated(paramNode)`: `paramNode.write != LuaGraphType.Undefined`. A direct
     `---@param` has already added a value edge (`LuaTypeGraphBridge.kt:131`) making the
     `write` non-`Undefined`; an un-annotated lambda parameter's `write` is `Undefined` at
     this point (its only inbound flow — the body's uses — are `downSet`/read constraints, not
     writes). This is the precedence gate.
- **Rules / edge handling**:
  - Positional, arity-clamped both ways (steps 2 & 3). No name matching.
  - Union expected slot `T | fun(...) | U`: `expectedCallbackAt` returns the first
    `LuaFunctionType` member (see §3.2 step 4), matching `extractFunctionType`
    (`LuaParameterInlayHintsProvider.kt:147`).
  - Cycle safety: `fromLuaType` already carries a `visited` map (`LuaGraphType.kt:111`); no
    new recursion is introduced.
- **Complexity / bounds**: O(args × lambda-params) edges per call; one `resolveCalleeType()`
  (memoized in the caller's local `val`, invoked once per call) — one `resolve()` +
  one cross-file snapshot lookup (itself `forFile`-cached).

### 3.2 Callee-type resolution (`LuaExpectedCallbackResolver.resolveCalleeType`)
- **Input → Output**: `LuaFuncCall` → `LuaFunctionType?`.
- **Steps** (mirrors `LuaParameterInlayHintsProvider.resolveStandardCall`/`resolveMethodCall`
  `LuaParameterInlayHintsProvider.kt:117,100`):
  1. `callee = calleeUnwrapped` (the visitor's already-unwrapped callee, passed into the
     constructor — the resolver does not re-unwrap).
  2. `nameAndArgs = call.nameAndArgsList.firstOrNull()`; if null → null.
  3. **Method call** (`nameAndArgs.methodExpr != null`, a `:`/`.`-method): resolve
     `methodExpr.nameRef.reference?.resolve()`; `funcDecl = resolved?.parent?.parent as?
     LuaFuncDecl`; `declFile = funcDecl.containingFile as? LuaFile`;
     `extractFunctionType(LuaTypesSnapshot.forFile(declFile).let { it.graphTypeToLuaType(
     it.getValueType(funcDecl)) })`.
  4. **Standard call**: `calleeType = extractFunctionType(types.graphTypeToLuaType(
     types.getValueType(callee)))` where `types = LuaTypesSnapshot.forFile(call.containingFile)`;
     if null, fall back to reference resolution:
     `resolved = (callee as? LuaNameRef)?.reference?.resolve()`, then
     `decl = resolved?.parent as? LuaLocalFuncDecl ?: resolved as? LuaFuncDecl ?:
     resolved?.parent?.parent as? LuaFuncDecl`, and for a dotted callee
     (`LuaVar`) use the last `varSuffixList` `indexExpr.nameRef.reference?.resolve()` (mirrors
     `LuaParameterInlayHintsProvider.getDeclaration`, `LuaParameterInlayHintsProvider.kt:169`).
     Then `extractFunctionType(LuaTypesSnapshot.forFile(declFile).graphTypeToLuaType(
     getValueType(decl)))`.
- **`extractFunctionType(type: LuaType): LuaFunctionType?`** — copy of the inlay provider's
  helper (`LuaParameterInlayHintsProvider.kt:147`): return `type` if `LuaFunctionType`;
  if `LuaUnionType`, return the first member that extracts to a `LuaFunctionType`; else null.
- **`expectedCallbackAt(index, calleeType, selfOffset)`**:
  `param = calleeType.params.getOrNull(index + selfOffset)`; return
  `extractFunctionType(param.type)` (handles `fun(...)` directly and a `T | fun(...)` union
  slot). `selfOffset` is 1 for a `:`-method call (the callee's `params[0]` is `self`, but the
  explicit `argExprs` are indexed from 0) and 0 otherwise — see §6, method-call self offset.

### 3.3 No new external parsing
The callback types come from LuaCATS `fun(...)` annotations already parsed by
`TypeParser.parseDistinctType` (`TypeParser.kt:98-112`) into `LuaFunctionType`. No CLI/text
parsing is introduced.

## 4. External Data & Parsing
None. This feature consumes only in-repo `LuaType`/`LuaGraphType` structures and PSI. The
bundled stub `.lua` files (`src/main/resources/runtime/…`) are ordinary Lua parsed by the
existing lexer/parser; TYPE-10 reads their already-resolved `LuaFunctionType`, not raw text.

## 5. Data Flow

### Example 1: `redis.register_function('f', function(keys, args) return keys[1] end)`
1. `super.visitFuncCall` visits the lambda → `visitFunctionBody` declares `keys`/`args`
   nodes; neither has a direct `---@param`, so both `write == Undefined`.
2. `propagateExpectedLambdaParams`: `resolveCalleeType()` reference-resolves
   `register_function` (dotted `redis.register_function`) to the stub `LuaFuncDecl` in
   `runtime/redis/redis-7/redis.lua:76`, reads its `LuaFunctionType` → `params = [name:
   string, callback: fun(keys: string[], args: string[]): any]`.
3. Argument index 1 (`function(keys,args)…`) is a `LuaFuncDef`; `expectedCallbackAt(1, …)` →
   `fun(keys: string[], args: string[])`.
4. `seedLambdaParams` adds `value(keys, Array(String)) → keys.node` and
   `value(args, Array(String)) → args.node`.
5. `getValueType(keys)` → `Array(String)` → `graphTypeToLuaType` → `string[]`;
   `keys[1]` subscript (existing REDIS-04 §3.1b `seedSubscriptElement`,
   `LuaTypesVisitor.kt:674`) → `string`. Re-enables REDIS-05 TC-STUB-1.

### Example 2: `run(---@param x number\nfunction(x) return x end)` with `run: fun(cb: fun(x: string))`
1. Lambda's `visitFunctionBody` applies the direct `---@param x number` →
   `x.node.write == Number`.
2. `seedLambdaParams`: `expectedCallbackAt(0,…)` → `fun(x: string)`; but
   `isAlreadyAnnotated(x.node)` is true (`write == Number ≠ Undefined`) → skip.
3. `getValueType(x)` → `Number`. Direct `---@param` wins (TYPE-10-03).

### Example 3: `pcall(function(v) end)` (untyped slot — negative)
1. `resolveCalleeType()` resolves `pcall` to its stub `function pcall(f, ...) end`
   (`runtime/standard/lua-5.4/builtin.lua:122`) — `f` has **no** `fun(...)` annotation, so
   `params[0].type` is `any`.
2. `expectedCallbackAt(0,…)` → `extractFunctionType(any)` = null → skip.
3. `getValueType(v)` → `Undefined`. No spurious narrowing (TYPE-10-04, N3).

## 6. Edge Cases
- **Method call `obj:each(function(x) end)`**: `nameAndArgs.methodExpr != null`. The callee
  `LuaFunctionType`'s `params[0]` is `self`; the explicit `argExprs` are indexed from 0, so
  when the call is a method call, `expectedCallbackAt` must add +1 to align. The resolver
  knows this from `call.nameAndArgsList.first().methodExpr != null` — pass a
  `selfOffset: Int` (0 or 1) into `expectedCallbackAt` (still ≤3 args). This mirrors the
  inlay provider's `isColonCall` drop-`self` handling (`LuaParameterInlayHintsProvider.kt:60`).
- **Vararg expected slot** (`fun(...: T)`): the expected `LuaFunctionType`'s vararg parameter
  is positional in `params`; only lambda parameters at that index or earlier are seeded
  (no fan-out of one vararg across multiple lambda params). Acceptable for this feature.
- **Overloaded callee** (`@overload` on `register_function`): resolution uses the primary
  declaration's `LuaFunctionType` (the `@param`-declared one), consistent with the inlay
  provider. The `@overload` table form is out of scope (Gap 2.2).
- **Lambda with a self/`:` — impossible**: a `LuaFuncDef` (anonymous `function(...)`) has no
  method receiver, so no `self` binding applies (requirements TYPE-10-01).
- **Unresolvable callee** (dynamic/local reassigned): `resolveCalleeType()` returns null →
  no-op, baseline preserved.
- **Empty arg list / string-form / table-constructor args**: `argExprs` only contains
  `LuaFuncDef`s when a lambda is literally present; string/table args unwrap to non-`LuaFuncDef`
  and are skipped.

## 7. Integration Points
No `plugin.xml` change. `LuaTypesVisitor` is not a registered extension itself — it is invoked
by `LuaTypesSnapshot.forFile` which is consumed by already-registered providers/inspections.
No new index, service, settings key, or extension point.

```xml
<!-- plugin.xml: NO CHANGE. TYPE-10 is internal to the type-inference engine invoked by
     existing, already-registered consumers (inlay hints, annotator, completion, inspections). -->
```

New files (no registration needed):
- `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaExpectedCallbackResolver.kt`
- Edits to `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TYPE-10-01 | M | §2.1, §3.1 |
| TYPE-10-02 | M | §2.2, §3.2 |
| TYPE-10-03 | M | §3.1 step 4 (`isAlreadyAnnotated`) |
| TYPE-10-04 | M | §3.1 steps 1–2 (skip guards), §3.2 (null → no-op), §6 |
| TYPE-10-05 | S | §5 Example 1 (inlay/completion surfacing via existing consumers) |
| TYPE-10-06 | M | §1 (extend not replace), regression contract in risks-and-gaps §"Regression contract" |

## 9. Alternatives Considered
- **Pure in-graph propagation** (read `calleeNode.write` as a `LuaGraphType.Function` and wire
  its param nodes to the lambda's): rejected. For bundled cross-file stubs
  (`register_function`, `table.sort`) the current file's graph does **not** hold the callee's
  function type (the graph models only the current file; stubs resolve via
  reference/`StubIndex`). The Gap 2.4 probe used a *local* `rf` and still got `Undefined`,
  confirming even the local-graph path does not currently expose the expected type at seed
  time. The reference-resolution path (mirroring the inlay provider) is the proven way to
  reach both local and stub callees.
- **Name-based lambda↔callback parameter matching**: rejected — Lua callback signatures are
  positional; the lambda author chooses arbitrary names (`keys`/`k`). Positional matching is
  simpler and matches user expectation.
- **Also propagate the return type**: deferred to keep blast radius minimal (Gap 2.1).

## 10. Open Questions

_None — feature has cleared the planning bar._
