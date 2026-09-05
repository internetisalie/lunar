---
id: "TYPE-14"
title: "14: Generics — the LuaLS closed baseline, plus a constraint's bound inside the body"
type: "feature"
status: "planned"
priority: "high"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-14: Generics — the LuaLS closed baseline, plus a constraint's bound inside the body

## Overview

[[TYPE-05]] shipped let-polymorphism for exactly one shape: a same-file function whose `@param` and
`@return` tags name a `@generic` parameter **bare** (`---@param v T`, `---@return T`). Every other
shape a LuaCATS author writes — `T[]`, `T|nil`, `fun(a: T)`, `table<K, V>`, a generic function in
another file, a generic method, a generic class, a constraint — either degrades silently or, as
the DR-01 probe below measured, **emits false diagnostics on legal code**. This feature brings the
engine to the set of behaviours LuaLS already has (its closed generics issues), and adds one item
from its open list: a constrained parameter's bound is usable inside the function body (the
survey's Q1). It folds in [[BUG-420]], whose fix falls out of parameterized-name resolution.

Executed evidence for the current state is the DR-01 probe (`Type14Dr01GenericsReachProbeTest`,
run on the builder at `ddaac805`, not committed). The lines are quoted verbatim in the tables
below and in [risks-and-gaps.md](risks-and-gaps.md) "DR-01 result".

## Scope

### In Scope

- **Nested generic positions.** A `@generic` name is recognised anywhere inside a type expression
  on the same comment — array element, union arm, callback parameter or return, type argument —
  not only when it is the whole expression.
- **Recursive substitution at a call site**, over the same positions, in both parameters (binding)
  and returns (substitution).
- **Generic methods** (`obj:m(...)`, `Class:m(...)`, `Class.fn(...)`).
- **Cross-file generic callees** — a free global, a required module's member, or a class method
  declared in another file, reached through the declaration-typed path — instantiate per call
  site. Function stubs carry the `@generic` names so the stub and AST paths agree.
- **Generic classes** — `---@class Box<T>` declares a type parameter; `Box<string>` resolves to an
  instantiation whose fields and method signatures have `T` substituted; a parameterized parent
  (`---@class Kid : Base<string>`) contributes inherited members (closes [[BUG-420]]).
- **Grammar**: a type argument is a full type (`table<string, Widget|nil>`, `Box<string[]>`,
  `table<string, table<string, number>>`), which today parses **silently as prose**.
- **Constraints** (`---@generic T : Base`): the argument is checked against the bound at the call
  site, and inside the body a parameter typed `T` resolves `Base`'s members (survey Q1).
- **Backtick capture** (`` ---@param cls `T` ``): a string-literal argument names the class bound to
  `T`.
- **The safety net**: a symbolic generic that reaches a compatibility check — because it was not
  instantiated, or could not be bound — never produces a diagnostic.

### Out of Scope

Each of these is a LuaLS **open** issue with no settled upstream design, recorded in
[risks-and-gaps.md](risks-and-gaps.md) "Future work" so it is not rediscovered:

- Scoped `---@type T` inside a generic function's body (survey Q2).
- Method-level type parameters on `fun<K>(...)` class fields (Q3) — needs an annotation-syntax
  extension.
- Vararg generics (`---@param ... T`, `---@return T ...`) (Q4).
- The `Object.\`T\`` prefixed backtick form (Q5's advanced usage), `` `T`[] `` keyed tables (Q7),
  indexed generic types `T[key]` (Q9).
- Compact display of a recursively-expanded generic class (LuaLS #1856's display half).
- **Renaming a parameterized class head.** `LuaCatsTypeDeclarations.classDeclarationLeaf`
  ([LuaCatsTypeDeclarations.kt:63](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/psi/LuaCatsTypeDeclarations.kt))
  returns null for `---@class Box<T>` by design (REFACT-08-17), so rename does not reach a generic
  class's head today and still will not. **Go to Class by head name is in scope** (TYPE-14-08):
  the index consumers that re-match the tag text move to the head with the index key, because
  DR-03 measured that re-keying without moving them loses the class from navigation entirely.
- The pre-existing table-constructor-versus-array defect the probe exposed (`---@type string[]`
  `local xs = {}` reports `{ ... } is not assignable to string[]`) — it is not a generics defect;
  see risks-and-gaps "Adjacent defect".

## What the engine does today, measured

Each row is one line of the DR-01 report. "False error" means a diagnostic on code LuaLS accepts.

| Shape | Fixture (abridged) | Measured | Verdict |
| :-- | :-- | :-- | :-- |
| bare `T` (control) | `---@param v T ---@return T`, `ident("x")` | `s graph=string`, `n graph=number`, `errors none` | works |
| `T[]` parameter | `---@param xs T[] ---@return T`, `first(xs)` with `xs: string[]` | `a graph=undefined` | silent loss |
| `T[]` return | `---@param v T ---@return T[]`, `wrap(1)` | `w graph=undefined[] \| { ... }` | silent loss |
| `T\|nil` parameter | `---@param v T\|nil ---@return T`, `must("q")` | `m graph=T \| nil \| string`; `nil \| string is not assignable to T`; `string is not assignable to nil` | **symbolic `T` leaks; false errors** |
| cross-file global | `lib.lua` declares `function ident(v)` with `@generic`; `ident("x")` | `s graph=T`; `"x": string is not assignable to T`; `1: number is not assignable to T` | **false error on every call** |
| generic method | `function Box:echo(v)`, `b:echo("x")` on `---@type Box` | `r graph=undefined`; `:echo: fun(v) \| fun(v) is not assignable to fun(p)`; `v: number is not assignable to T` | **false errors** |
| generic class | `---@class Box<T> ---@field value T`, `---@type Box<string> local b`, `b.value` | `b graph=Box<string>`, `v graph=undefined`, `b.value <no node>`; `resolveType(Box<string>)=null`; `LuaClassNameIndex` keys `[Box<T>, …]` | keyed on the raw head text; no members |
| constraint in body | `---@generic T : Base ---@param p T`, `p.k` | `m graph=undefined`; `.k: T is not assignable to { ... }` | **false error**, no members |
| constraint at call | `f(1)` with `T : Base` | `bad graph=number`, no diagnostic on the call | unchecked |
| backtick | `` ---@param cls `T` ``, `new("Widget")` | `w graph=T \| { ... }`; `TypeParser(\`T\`) = unknown` | symbolic `T` leaks |
| nested type argument | `---@type table<string, Widget\|nil>` | `errorElements=0`, `TypeParser = table` (bare) | **parses silently as prose** |
| nested type argument | `---@type Box<string[]>` | `errorElements=0`, `TypeParser = LuaTypeReference(Box)` | **parses silently as prose** |
| filter shape (#1883) | `---@param fn fun(a: T) ---@param t T[] ---@return T[]` | `r graph=undefined[] \| { ... } \| string[]`, `lambda a graph=undefined`, seven errors | **false errors** |
| parameterized parent | `---@class Kid : PBase<string>` | `Kid members=[own]`, `PBase members=[inherited]` | BUG-420 reproduced |

The mechanism behind every "false error" row is one line: `LuaTypeGraph.isCompatible` has no arm
for `LuaGraphType.Generic`, so a symbolic parameter that reaches a check takes the `else -> false`
branch ([LuaTypeGraph.kt:717](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraph.kt)).
The mechanism behind every "silent loss" row is also one line: `resolveTypeWithGenerics` recognises
a generic name only when it is the **whole** type string and otherwise hands the string to
`TypeParser` without the names ([LuaTypeGraphBridge.kt:39-44](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraphBridge.kt)).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TYPE-14-01 | **Generic names parse in every position** | M | A name declared by a `@generic` tag on the same comment (or, for a class method, by the enclosing class's `<…>` head) parses to a generic type node wherever it occurs inside a `@param`, `@return`, `@field` or `@type` expression: as an array element, a union arm, a `fun(...)` parameter or return, or a type argument. A same-named class elsewhere in the project does not win over the parameter. `self` is not a generic name for the engine; the quick-doc predicate that already treats `self` as a type keeps doing so (design §4.2). |
| TYPE-14-02 | **A symbolic generic never produces a diagnostic** | M | A `LuaGraphType.Generic` that reaches `checkCompatibility` or `isCompatible` as either operand is compatible with everything, in both directions, unless it carries a bound (TYPE-14-11). Every "false error" row above goes quiet without anything else changing. |
| TYPE-14-03 | **Substitution is recursive at the call site** | M | For a same-file generic callee, a parameter position binds its generic names through the parameter's structure (`T[]` binds `T` to the argument's element type, `fun(a: T)` to the callback's first parameter, `table<K, V>` to the argument's key/value types) and a return position is rebuilt with the bindings substituted at the same depth. Each call site binds independently. |
| TYPE-14-04 | **Generic methods instantiate** | M | `obj:m(...)`, `Class:m(...)` and `Class.fn(...)` where `m`/`fn` is declared with `@generic` in the same file instantiate exactly as a free function does. |
| TYPE-14-05 | **Cross-file generic callees instantiate per call** | M | A declaration-typed callee (a free global, a required module's member, a class method reached through the declared route) whose declared signature contains a generic instantiates per call site with fresh nodes, so no call site writes into the memoized seed and BUG-397's "no call demand on a declared callee" contract is preserved. |
| TYPE-14-06 | **Type parameters survive the stub, and both paths agree** | M | `LuaFuncStub` and `LuaLocalFuncStub` carry the comment's `@generic` names and bounds; `LuaLocalVarStub` carries a class head's type parameters. `funcTypeFromStub` and `materializeClass` produce the same `LuaFunctionType.typeParameters` / `LuaClassType.typeParameters` from the stub as from the AST. `LuaCatsStubAstParityTest` gains a generic fixture. |
| TYPE-14-07 | **A type argument is a full type** | M | `parameterizedName` accepts any `type` as an argument. `table<string, Widget\|nil>`, `Box<string[]>`, `table<string, table<string, number>>` and `fun(a: T): T` inside `<…>` parse to the structure they name. A bare-name argument still surfaces as `LuaCatsTypeParam` so REFACT-08's reference and rename contracts are unchanged. |
| TYPE-14-08 | **A generic class declares its parameters and is keyed by its head** | M | `---@class Box<T, U>` records `["T", "U"]` on the stub and on `LuaClassType.typeParameters`; `LuaClassNameIndex` and `LuaCatsTypeNameIndex` key the class as `Box`, and `LuaTypeManager.resolveType("Box")` finds it. Every consumer that fetches files by that index key and then re-matches the tag's text (design §5.10 lists them) matches the head too, so Go to Class lists `Box` and lands on the tag. The `T` in `---@field value T` on that class parses as a generic (TYPE-14-01), not as an unresolved class. |
| TYPE-14-09 | **A parameterized name resolves to an instantiation** | M | `LuaTypeManager.resolveType("Box<string>")` returns a `LuaClassType` named `Box<string>` whose members are `Box`'s with `T` replaced by `string` at every depth, whose supertypes are `Box`'s with the same substitution, and which is nominally assignable to `Box`. `---@type Box<string> local b` gives `b.value : string`, with completion and inlay hints reading the substituted type. A wrong argument count substitutes the provided arguments positionally and leaves the rest symbolic. |
| TYPE-14-10 | **A parameterized parent inherits with substitution** | M | `---@class Kid : Base<string>` resolves `Kid`'s members to include `Base`'s, with `Base`'s `T` replaced by `string`. This closes [[BUG-420]] without stripping the arguments from the stub or from `superTypes`. |
| TYPE-14-11 | **A constraint is checked at the call site** | S | For `---@generic T : Base`, the argument bound to `T` is checked against `Base` as a declared demand: `f(1)` reports `number is not assignable to Base`; `f(base)` with `base: Base` does not. |
| TYPE-14-12 | **A constraint's bound is usable in the body** | S | Inside the function, a parameter typed `T` where `T : Base` resolves `Base`'s members: `p.k` infers `string` for `---@field k string`, completion on `p.` offers `k`, and no diagnostic is raised on the access. |
| TYPE-14-13 | **Backtick capture binds a named class** | S | For `` ---@param cls `T` ``, a call whose argument at that position is a string literal binds `T` to the class (or primitive) that literal names; the return `T` infers that class. A non-literal argument leaves `T` unbound and raises nothing. |
| TYPE-14-14 | **A generic return of a parameterized class instantiates the class** | S | `---@return Box<T>` on a generic function returns `Box<string>` at a call site that binds `T` to `string`, with members substituted, so `local b = make("x")` then `b.value` infers `string`. |
| TYPE-14-15 | **Instantiations display by their instantiated name** | S | Inlay hints and `displayName()` show `Box<string>` for an instantiation and `string` for a substituted member, never `T` or `undefined` where a binding exists. |
| TYPE-14-16 | **A callback slot binds from a sibling argument** | C | For `---@param fn fun(a: T) ---@param t T[]`, a lambda passed as `fn` has its `a` inferred from `t`'s element type when `t` is passed first or second. The LuaLS #1883 shape; the ordering caveat is design §5.12. |
| TYPE-14-17 | **No regression** | M | The full unit suite (`test --rerun --no-build-cache`), the corpus ratchet (`test -PwithCorpus`) and `LuaAnnotatedClassDiagnosticsTest` pass unchanged except for (a) diagnostics this feature removes (the false-error rows above) and (b) at Phase 5 only, diagnostics added on a corpus member because a generic class that previously did not resolve now does — each such addition is listed in the re-baseline commit with the class that accounts for it. An addition with no such class fails the gate. |

## Detailed Specifications

### TYPE-14-01: Generic names parse in every position

The set of generic names in scope for a tag is the union of:

1. every `LuaCatsGenericTypeParam.argName.text` on the same `LuaCatsComment`, and
2. for a `@field` on a class comment, or a `@param`/`@return` on a method whose receiver is a
   generic class, that class's head parameters (`---@class Box<T>` contributes `T`).

A name in the set produces `LuaGenericType(name, bound)` at every depth `TypeParser` recurses to.
It is checked **before** nominal resolution, so a project class also called `T` does not capture
it. `self` is not in the engine's map (it is substituted by `funcTypeFromStub` already); the
quick-doc side adds it to its own set, as it does today (design §4.2).

### TYPE-14-02: A symbolic generic never produces a diagnostic

The only two entry points are `checkCompatibility` (edge-time, may emit) and `isCompatible`
(boolean, memoized). Both get a `Generic` arm before the union arms. Unbound: return / true.
Bound: delegate to the bound (design §5.7). This is the one change that goes in first and alone,
because it removes every false error in the table above at once and is trivially revertible.

### TYPE-14-03: Substitution is recursive at the call site

A **path** into a type is a sequence of steps: `Elem` (array element), `Arm(i)` (union arm),
`Param(i)` / `Return(i)` (function), `Arg(i)` (type argument), `Member(name)` (table). For each
generic name the design computes the set of paths at which it occurs in each parameter type and in
each return type. Binding follows a parameter's paths **into the argument's type** (a lazy
projection); substitution rebuilds a return type with each path's generic replaced by the bound
node's resolved type (a lazy value). Both use the engine's existing `LazyValueElement` so that an
argument seeded after the call is visited is still observed (the TYPE-10 §3.4 property).

### TYPE-14-05: Cross-file generic callees instantiate per call

The declaration-typed path (`visitFuncCall`, `calleeNodeRef in declarationTypedNodes`) today does
two things for a declared callee: `seedDeclaredReturns` and `demandDeclaredParams`, both with
fresh per-call nodes and never an edge into the shared seed's own nodes. The generic path keeps
that contract: it builds a fresh instantiation (every parameter and return node new), binds
generics through the *argument* nodes, and flows the substituted returns into the call's result
nodes. It does **not** route through `checkFunctionCompatibility`, which would wire call-site
argument nodes into the seed's parameter nodes and arity-check against a primary signature that
`@overload` may contradict (the reason `testDeclarationTypedCalleeIsNotArityChecked` exists).

### TYPE-14-07: A type argument is a full type

```
parameterizedName    ::= genericType '<' typeArgument {',' typeArgument }* '>'
private typeArgument ::= (typeParam &(',' | '>')) | type
typeParam            ::= NAME
```

The and-predicate keeps a bare-name argument as `LuaCatsTypeParam`, and the `private` rule keeps
it a direct child of `LuaCatsParameterizedName`, exactly where it is today — `table<K, V>` and the
head parameters of `---@class Box<T>` keep their PSI shape, so
`LuaCatsTypeDeclarations.isDeclarationSlotHolder`, `shadowedTypeParameterNames`,
`LuaCatsTypeReferenceContributor` and `LuaCatsTypeRenameProcessor` are untouched (DR-02 ran their
suites unmodified against the regenerated parser). Anything that is not a bare name followed by
`,` or `>` becomes a nested `LuaCatsType` child. `getTypeParamList()` is unchanged and
`getTypeList()` is added; the one consumer that needs argument order is listed in design §6.

### TYPE-14-08 / 09 / 10: Generic classes

The class's **head name** is `LuaCatsDeclarations.classHeadName(tag)`: the `genericType` text when
the head is a `parameterizedName`, else the trimmed `argType` text. Every place that today reads
`classTag.argType.text` as a class name reads the head name instead (design §5.10 lists them). The
head's `typeParamList` becomes `LuaClassType.typeParameters`.

A **parameterized name** `Box<string>` is resolved by `doResolveType` through a new branch: split
into head `Box` and argument types (parsed by `TypeParser` with the caller's generic names), resolve
the head to a `LuaClassType`, and return `LuaGenericSubstitution.instantiate(base, args)` — a new
`LuaClassType` named `Box<string>` with `typeArguments = [string]`, members and supertypes
substituted. `LuaTypeReference("Base<string>")` therefore resolves, which is the whole of BUG-420.

### TYPE-14-11 / 12: Constraints

`LuaGenericType.bound: LuaType?` and `LuaGraphType.Generic.bound: LuaGraphType?` carry the
`: Base` of `---@generic T : Base`. At the call site the fresh node bound to `T` receives a
declared demand `use(callSite, bound, declaredDemand = true)`. In the body, a parameter whose write
is `Generic(T, bound)` satisfies a member demand through the bound: `checkCompatibility(Generic,
Table{k})` recurses as `checkCompatibility(bound, Table{k})`, which wires `Base`'s `k` node into the
access node exactly as a `---@param p Base` would.

### TYPE-14-13: Backtick capture

`parameterName ::= CODE` already parses `` `T` ``. `TypeParser` maps it to
`LuaGenericType(name = T, capture = true)`. At a call site, for each parameter whose type is a
capture generic at path ε, if the argument expression at that position is a `LuaTerminalExpr`
with a string literal, the literal's unquoted text is resolved by `LuaTypeManager.resolveType`
(primitives first, as today) and the result flows into `T`'s node as a declared-origin value.
Otherwise `T` stays unbound.

## Behavior Rules

- **Scope of a generic name** is one `LuaCatsComment`, plus the class head for a class's own
  fields and methods. Two functions each declaring `T` never share a node.
- **Binding is positional** and happens only when `positionsAreUnambiguous(declared, args.size)`
  holds for a declared callee (the BUG-425 rule). For a same-file callee the existing
  `checkFunctionCompatibility` positional wiring applies.
- **An unbound generic** resolves to `Undefined` at every consumer, displays as `undefined` (or
  `undefined[]`, and so on), and raises no diagnostic. It is never `any` and never `nil`.
- **A bound wins over a binding for checking, never for inference**: `T : Base` bound to a `Kid`
  argument reports nothing (Kid ≤ Base) and infers `Kid`.
- **Substitution never touches the template.** The declaring function's own parameter and return
  nodes keep their `Generic` writes, so its body is inferred once, against the symbolic types.
- **A raw generic class name** (`Box` with no arguments) behaves exactly as today: its members keep
  their `T` types, which now parse as generics and therefore raise nothing.

## Test Cases

Every fixture is a light `IndexedBasePlatformTestCase` file unless it says cross-file, in which
case the declaring file is added with `myFixture.addFileToProject` (the stub path) and the using
file with `configureByText` (the AST path). "Type of `x`" means
`LuaTypesSnapshot.forFile(file).getValueType(nameRef).displayName()`. The **mutation** column
names the edit that turns the case red, and each is reachable from the fixture named.

| # | Requirement | Given (input) | When (action) | Then (expected) | Mutation |
|---|-------------|---------------|---------------|-----------------|----------|
| 1 | 14-01 | `---@generic T` `---@param xs T[]` `---@return T` `local function first(xs) end` | parse the `@param` type through `LuaTypeGraphBridge` | the parameter node's write is `Array(Generic("T"))` | drop the `genericNames` argument from the `TypeParser.parse` call in `resolveTypeWithGenerics` → write is `Array(Undefined)` |
| 2 | 14-01 | same comment, plus `---@class T` `local T = {}` elsewhere in the file | parse | still `Array(Generic("T"))` — the parameter shadows the class | move the generic-name check after nominal resolution in `TypeParser.parseDistinctType` → `Array(Table("T"))` |
| 3 | 14-02 | the DR-01 "cross-file global" fixture (`lib.lua` + `ident("x")`) with **only** the safety net applied | `getErrors()` | empty | remove the `Generic` arm from `checkCompatibility` → `string is not assignable to T` returns |
| 4 | 14-02 | `---@generic T` `---@param v T\|nil` `---@return T` `local function must(v) return v end` `local m = must("q")` | `getErrors()` | no error whose message contains ` T` | remove the `Generic` arm from `isCompatible` → `nil \| string is not assignable to T` |
| 5 | 14-03 | `---@type string[] local xs` + `first(xs)` from case 1, `local a = first(xs)` | type of `a` | `string` | in `LuaGenericInstantiation.projectionsOf`, stop descending into `Array` → `undefined` |
| 6 | 14-03 | `---@generic T ---@param v T ---@return T[]` `local w = wrap(1)` | type of `w` | `number[]` | in `substitute`, return `type` unchanged for `Array` → `undefined[]` |
| 7 | 14-03 | `---@generic K, V ---@param t table<K, V> ---@return V` with `---@type table<string, number> local t`, `local v = get(t)` | type of `v` | `number` | skip `Arg(i)` paths in `projectionsOf` → `undefined` |
| 8 | 14-03 | `ident("x")` and `ident(1)` in one file (the TYPE-05 control) | types of both | `string`, `number` | share one binding map across call sites (drop the `element` from the instantiation-cache key) → the second reads `number \| string` |
| 9 | 14-04 | DR-01 fixture D (Appendix A; `function Box:echo(v)` with `@generic`, `b:echo("x")`, `Box:echo(1)`) | types of `r`, `q`; `getErrors()` | `string`, `number`; empty | restore the `calleeNode is ValueNode` guard in `visitFuncCall` before the generic branch → `r` is `undefined` |
| 10 | 14-05 | cross-file: `lib.lua` = `---@generic T ---@param v T ---@return T function ident(v) return v end`; `main.lua` = `local s = ident("x")` `local n = ident(1)` | types; errors | `string`, `number`; none | in the declared branch of `visitFuncCall`, skip instantiation when `declared` has generics and fall through to `seedDeclaredReturns` → both `T`… after 14-02, `undefined` |
| 11 | 14-05 | case 10 plus a second call `ident(true)` | `getValueType` of the seed node for `ident` (`freeGlobalSeed`) before and after | the seed's parameter node write is unchanged (`Generic("T")`) — no call wrote into it | wire the instantiated params through `checkFunctionCompatibility` against the seed → the seed's param write becomes a union of the arguments |
| 12 | 14-06 | cross-file: `lib.lua` from case 10 | `LuaTypeManager.resolveGlobal("ident")` as `LuaFunctionType` | `typeParameters == [T]`; `params[0].type is LuaGenericType`; `returnType is LuaGenericType` | drop `luacatsGenericParams` from `LuaFuncStubImpl` serialization → `typeParameters` empty on the stub path while the AST path (configureByText) still fills it — the parity test's `assertEquals` on the rendered signature goes red |
| 13 | 14-07 | `---@type table<string, Widget\|nil>` `local q` | PSI of the comment | a `LuaCatsParameterizedName` whose children are a `LuaCatsTypeParam` (`string`) then a `LuaCatsType` whose union has two arms; no `PsiErrorElement`; no `LuaCatsDescription` | revert the grammar rule → the `@type` tag's `argType` is `table` and the rest is a `LuaCatsDescription` |
| 14 | 14-07 | `---@type table<K, V>` and `---@class Box<T>` | PSI | both arguments are `LuaCatsTypeParam` children of the `LuaCatsParameterizedName` (unchanged shape); `LuaCatsTypeDeclarationsTest` and `LuaCatsTypeReferenceTest` pass unmodified | delete the word `private` from the rule → a `LuaCatsTypeArgument` node appears between them and every test that risks-and-gaps "DR-02 result, run a" lists as red goes red again (measured); dropping the predicate instead turns every bare argument into a `LuaCatsType` and fails the same tests |
| 15 | 14-08 | `---@class Box<T>` `---@field value T` `local Box = {}`, once via `addFileToProject` (stub path) and once via `configureByText` (AST path) | `resolveType("Box")`; `LuaClassNameIndex` keys; `typeParameters`; `LuaHierarchyUtil.className(decl)` on both paths | a `LuaClassType`; the key set does not contain `Box<T>` (a bare `Box` key can pre-exist from library roots — DR-01 E — so its presence is not asserted); `[T]`; `Box` on both paths; `LuaReceiverMemberIndex.membersIn("Box", …)` contains a member named `value` | read `classTag.argType.text` instead of `classHeadName` in `LuaLocalVarStubElementType.createStub` → key `Box<T>`, `resolveType("Box")` null; leave `LuaHierarchyUtil.className`'s AST fallback on `argType.text` → the AST path answers `Box<T>` while the stub path answers `Box`; leave `indexClassFields` on `argType.text` → `membersIn("Box", …)` has no `value` |
| 16 | 14-09 | case 15 plus `---@type Box<string> local b` `local v = b.value` | type of `v`; `resolveType("Box<string>")` | `string`; a `LuaClassType` named `Box<string>` with member `value: string` | in `LuaGenericSubstitution.instantiate`, return the base's members unsubstituted → `v` is `undefined` (a symbolic `T`) |
| 17 | 14-09 | `---@type Box<string> local b` `---@type Box local raw` `raw = b` | `getErrors()` | none — `Box<string>` is assignable to `Box` | drop the head-name comparison from `isNominallyCompatible` → `Box<string> is not assignable to Box` |
| 18 | 14-10 | `---@class PBase<T> ---@field inherited T` `---@class Kid : PBase<string> ---@field own number` | `resolveType("Kid").getMembers()` and `resolveMember("inherited").type.name` | keys `[inherited, own]`; `string` | remove the parameterized branch from `doResolveType` → keys `[own]` (BUG-420's exact measurement) |
| 19 | 14-11 | `---@class Base ---@field k string` `---@generic T : Base ---@param p T` `local function f(p) end` `f(1)` `---@type Base local base` `f(base)` | `getErrors()` | exactly one error, on `1`, message contains `number` and `Base` | drop the `use(…, bound, declaredDemand = true)` edge on the fresh binding node → no error |
| 20 | 14-12 | case 19's function with body `local m = p.k` | type of `m`; errors on `p.k` | `string`; none | in `checkCompatibility`'s `Generic` arm, return without recursing into `bound` → `undefined` |
| 21 | 14-13 | `---@class Widget ---@field w number` `` ---@generic T ---@param cls `T` ---@return T `` `local function new(cls) end` `local w = new("Widget")` `local u = new(name)` | types of `w`, `u` | `Widget`, `undefined` | in the capture branch, bind from the argument's *type* (`String`) instead of its literal text → `w` is `string` |
| 22 | 14-14 | case 15's class plus `---@generic T ---@param v T ---@return Box<T>` `local function make(v) end` `local b = make("x")` `local val = b.value` | types of `b`, `val` | `Box<string>`, `string` | in `substitute`, treat `Table(className)` as a leaf → `Box<T>` with `val` `undefined` |
| 23 | 14-15 | case 22 | `LuaTypeInlayHintProvider` on `b` and `val` | `: Box<string>`, `: string` | same as 22 |
| 24 | 14-16 | DR-01 fixture I (Appendix A; `filter(function(a) end, xs)` with `xs: string[]`) | type of lambda `a`; type of `r` | `string`; `string[]` | run `propagateExpectedLambdaParams` before the instantiation map is built → `a` is `undefined` |
| 25 | 14-17 | the corpus baseline | `test -PwithCorpus` at each phase that re-baselines | ratchet green; at Phases 1 and 2 the per-file delta lists only removed diagnostics whose message contains a generic name; at Phase 5 every added diagnostic is attributed to a generic class that newly resolves | — untestable by mutation; the ratchet is the gate |
| 26 | 14-06 | `LuaCatsStubAstParityTest` new fixture: a `---@generic T` function and a `---@class Box<T>` class, rendered from the stub and from the AST | compare renderings | identical, including `typeParameters` | any of mutations 12 or 15 |
| 27 | 14-08 | `---@class Box<T>` `local Box = {}` (the DR-03 fixture) | `LuaCatsTypeNavigation.processNames` and `processElements("Box")` | the key set does not contain `Box<T>`; `processElements("Box")` yields exactly one item and its element is this fixture's tag `argType` | re-key the index but leave `LuaCatsTypeNavigation.kt:48`'s `id.text.trim() != name` as is → `processElements("Box")` yields no item, and `"Box<T>"` yields none either (DR-03 measured the pre-change state: the raw key finds it, the head does not). The key-set assertion is deliberately negative: DR-03 shows a bare `Box` key already present from library roots, so "contains `Box`" would not be falsifiable |

## Appendix A — DR-01 fixture sources

The probe was not committed; its fixtures are reproduced here verbatim so every case that names
one can be rebuilt. Each is one `configureByText` file. Cross-file fixture C adds `lib.lua` with
`addFileToProject` first.

**A (control)**
```lua
---@generic T
---@param v T
---@return T
local function ident(v) return v end
local s = ident("x")
local n = ident(1)
```

**B (nested positions)**
```lua
---@generic T
---@param xs T[]
---@return T
local function first(xs) return xs[1] end
---@generic T
---@param v T
---@return T[]
local function wrap(v) return { v } end
---@generic T
---@param v T|nil
---@return T
local function must(v) return v end
---@type string[]
local xs = {}
local a = first(xs)
local w = wrap(1)
local m = must("q")
```

**C (cross-file)** — `lib.lua`:
```lua
---@generic T
---@param v T
---@return T
function ident(v) return v end
```
`c.lua`:
```lua
local s = ident("x")
local n = ident(1)
```

**D (generic method)**
```lua
---@class Box
local Box = {}
---@generic T
---@param v T
---@return T
function Box:echo(v) return v end
---@type Box
local b
local r = b:echo("x")
local q = Box:echo(1)
```

**E (generic class)**
```lua
---@class Box<T>
---@field value T
local Box = {}
---@type Box<string>
local b
local v = b.value
```

**F (constraint)**
```lua
---@class Base
---@field k string
local Base = {}
---@generic T : Base
---@param p T
---@return T
local function f(p)
  local m = p.k
  return p
end
local bad = f(1)
---@type Base
local base
local good = f(base)
```

**G (backtick capture)**
```lua
---@class Widget
---@field w number
local Widget = {}
---@generic T
---@param cls `T`
---@return T
local function new(cls) return {} end
local w = new("Widget")
```

**H (grammar)** — one file per shape: `---@type <shape>` followed by `local q`, for the shapes
listed in the DR-01 report.

**I (filter shape)**
```lua
---@generic T
---@param fn fun(a: T)
---@param t T[]
---@return T[]
local function filter(fn, t) return t end
---@type string[]
local xs = {}
local r = filter(function(a) end, xs)
```

**J (parameterized parent)**
```lua
---@class PBase
---@field inherited string
local PBase = {}
---@class Kid : PBase<string>
---@field own number
local Kid = {}
```

## Acceptance Criteria

- [ ] Every fixture in "What the engine does today" reports zero false errors (TYPE-14-02) and the
      expected type where one is listed in the test cases.
- [ ] `LuaCatsStubAstParityTest` covers a generic function and a generic class (TYPE-14-06).
- [ ] `src/main/gen` is regenerated from the grammar in TYPE-14-07 and committed; the regen over the
      committed sources is a no-op.
- [ ] BUG-420's probe fixture resolves `inherited` (TYPE-14-10) and BUG-420 is closed by this feature.
- [ ] Full suite, corpus ratchet and `LuaAnnotatedClassDiagnosticsTest` green (TYPE-14-17).
- [ ] The human checklists in [human-verification-checklists.md](human-verification-checklists.md)
      pass in GoLand for the generic-class completion scenario.

## Non-Functional Requirements

- **No new root-resolution walks per call site beyond the fresh nodes the instantiation mints.**
  `LuaTypeGraphRootResolutionBudgetTest` stays green; the BUG-473 hazard is that a lazy value which
  mints nodes during a resolution bumps the revision mid-walk — every lazy compute in this design
  memoizes anything it materializes (design §5.6, §5.9).
- **Every serialized shape or key that changes bumps its version once** — the version bumps §9 lists (`LuaFileElementType.getStubVersion`, `LuaCatsTypeNameIndex.getVersion`, `LuaReceiverMemberIndex.getVersion`) — so existing stubs and indexes rebuild rather than answer under old keys.
- **Threading**: every new code path runs inside the existing snapshot build (read action) or
  inside `LuaTypeManagerImpl`'s cached resolution; nothing touches the EDT.

## Dependencies

- [[TYPE-05]] (the same-file bare-`T` baseline this extends), [[TYPE-10]] (lazy value nodes and
  callback-slot seeding), [[TYPE-13]] (member declaration provenance, which the substituted class
  must preserve), [[MAINT-34]] (the shared `LuaCatsDeclarations` extractor both paths read),
  [[BUG-397]]/[[BUG-425]] (the declared-callee contracts TYPE-14-05 must keep).
- Closes [[BUG-420]].
- Needs a parser regeneration (`.claude/skills/generate-parser/scripts/generate.sh`, local-only).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklists: [human-verification-checklists.md](human-verification-checklists.md)
- The LuaLS survey this scopes against: [../05-generics-support/design-luals-generics-super-issue.md](../05-generics-support/design-luals-generics-super-issue.md)
