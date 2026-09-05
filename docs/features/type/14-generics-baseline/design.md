---
id: "TYPE-14-DESIGN"
title: "14: Design — one substitution mechanism, applied at four sites"
type: "design"
parent_id: "TYPE-14"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-14 Design

## 1. What changes, in one paragraph

The engine already has the right shape for generics — a `LuaGraphType.Generic` placeholder, a
per-call-site instantiation that mints a fresh `VariableNode` per type parameter, and a lazy value
node (`LazyValueElement`) that can compute a type from another node at read time. What it lacks is
**one substitution mechanism** that works below the top level of a type: the placeholder is only
produced when the generic name is the *whole* type string, instantiation only replaces a parameter
or return whose *entire* type is the placeholder, and a placeholder that leaks to the checker
takes the `else -> false` branch and reports. This design adds that mechanism once — a path-based
projection for binding and a lazy rebuild for substitution (§5.3–§5.6) — and applies it at four
sites: same-file callees, declaration-typed callees, generic classes and parameterized parents.
Constraints ride on the placeholder as a `bound` (§5.7); backtick capture is a binding rule at one
path (§5.11). No merge in the engine is changed and no existing node is ever written into by a
call site that did not own it.

Every claim below about what existing code does is backed by the DR-01 probe output quoted in
[requirements.md](requirements.md) "What the engine does today" and in
[risks-and-gaps.md](risks-and-gaps.md), executed at `ddaac805`.

## 2. Constraints examined rather than assumed

| Constraint | Assumed or chosen | Verdict |
| :-- | :-- | :-- |
| "A generic can only be recognised when it is the whole type string" | inherited from TYPE-05 (`isSimpleTypeName`, the `TODO` at [LuaTypeGraphBridge.kt:43](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraphBridge.kt)) | **Removed.** `TypeParser` recurses through every type shape already; it needs the set of names, not a new parser. §4.1. |
| "Type arguments are bare names" (`typeParam ::= NAME`, [luacats.bnf:201-203](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/parser/luacats.bnf)) | inherited from the original grammar | **Removed — and it was silently wrong.** DR-01 H: `table<string, Widget\|nil>` yields `errorElements=0` and `TypeParser = table`; the `<…>` tail is consumed as `description`. The lexer already emits every symbol a nested type needs in `TAG_TYPE`, `TAG_CLASS`, `TAG_PARAM`, `TAG_RETURN` and `TAG_FIELD` (`TYPES` macro, [luacats.flex:74](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/lexer/luacats.flex)), so only the grammar rule changes. §6. |
| "REFACT-08's `LuaCatsTypeParam` contract must not move" | chosen | **Kept, by construction.** The and-predicate in §6 keeps a bare-name argument on exactly the PSI shape it has today; DR-02 executed the regenerated parser against `LuaCatsTypeDeclarationsTest` and `LuaCatsTypeReferenceTest` (risks-and-gaps "DR-02 result"). |
| "A declaration-typed callee raises no call demand and nothing writes into its seed" (BUG-397, BUG-425) | inherited | **Fixed constraint, and the design honours it.** The declared path's instantiation is a full copy with fresh nodes (§5.8); nothing routes through `checkFunctionCompatibility` against the seed. Requirements case 11 pins it. |
| "`@overload` never reaches the engine, so positional binding is unsafe when arity does not match" (BUG-425's `positionsAreUnambiguous`) | inherited | **Fixed constraint.** Binding on the declared path is gated by the same predicate; an ambiguous call still seeds substituted returns with whatever bound (nothing), which resolves to `Undefined` and raises nothing. |
| "The class-name index must key on the tag's raw text" | inherited (`classTag?.argType?.text`, [LuaLocalVarStubElementType.kt:32](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/stubs/impl/LuaLocalVarStubElementType.kt)) | **Removed.** DR-01 E: keys are `[Box<T>, …]` and `resolveType("Box")` misses. The key becomes the head name; the parameters go to a stub field. §5.10. |
| "Stripping `<…>` at extraction is enough for BUG-420" (the bug report's cheap option) | assumed by BUG-420 | **Removed.** Stripping discards the arguments a substituted parent needs. Resolving the parameterized name instead (§5.9) closes BUG-420 *and* delivers inheritance with substitution; the stub shape is unchanged. |
| "A nested binding needs a new graph-type kind that references a node" (a `Var(node)` type) | considered | **Removed.** `LazyValueElement` already expresses "a type computed from other nodes at read time" and threads the BUG-390 cycle guard. Binding is a lazy projection *into* the type-parameter node; substitution is a lazy value *out of* it. No new `LuaGraphType` subclass, so every exhaustive `when` in the engine is untouched. §5.4, §5.6. |
| "Binding must be eager (read the argument's `write` at visit time)" | considered | **Removed.** Eager binding reproduces the TYPE-10 intra-traversal ordering hazard (a lambda argument's parameter is seeded after the call is visited). Lazy projection is what TYPE-10 §3.4 already does for subscripts. |
| "`LuaTypeReference` can name any type string" | inherited (`LuaTypeReference(pType, decl)` in `funcTypeFromStub`) | **Fixed constraint with a measured edge.** `resolveType` resolves a *name*; a structured string (`string[]`, `Box<string>`) is not a name and resolves to nothing (DR-01 E: `resolveType("Box<string>") = null`). §5.9 gives `doResolveType` one structured branch — parameterized names — and §4.8 routes generic-bearing strings through `TypeParser`. Structured non-generic field types stay as they are (risks-and-gaps "Adjacent gap"). |
| "Kotlin `data class` equality on `LuaGraphType.Generic` is by name" | inherited | **Kept, deliberately.** Two functions' `T`s are equal *types*; they are told apart by the *node* each call site mints, never by the placeholder. Adding an owner field to `Generic` would make every `Union.create` canonicalisation and every `compatMemo` key depend on it for no consumer. |
| "The corpus ratchet is opt-in" | inherited | **Fixed constraint, applied.** Phases 1, 2 and 5 run `-PwithCorpus`. At Phases 1 and 2 the expected delta is only *removed* diagnostics; at Phase 5 a generic class that newly resolves may add real ones, each attributed to its class (TYPE-14-17). |
| "Re-keying the class-name indexes for the engine is free for their other consumers" | assumed | **Removed, by measurement.** The re-matching consumers in §5.10's table fetch files by the key and then compare the tag's raw text against that key. DR-03 measured the pre-change state: `processElements("Box<T>")` finds the class and `processElements("Box")` does not, so a key change without a matcher change loses the class from Go to Class. They move together. |
| "`self` belongs outside the generic-name map for every consumer" | assumed | **Removed.** The engine's map excludes it (§5.1) because `funcTypeFromStub` substitutes it; the quick-doc predicate `LuaCatsDeclaredType.isType` includes it on purpose (`.toSet() + "self"`, [LuaCatsDeclaredType.kt:71](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/doc/LuaCatsDeclaredType.kt)) so `---@param p self …` is a type, not prose. §4.2 keeps that addition at that call site. |
| "REFACT-08's holder predicates must not change" | chosen | **Kept, and costed.** Adapting `isDeclarationSlotHolder` to a new parent chain is a two-line edit plus re-proving REFACT-08's mutations T/U/V; the `private` rule costs nothing and leaves that proof intact (§11). |

## 3. Data model

### 3.1 `LuaGenericType` gains a bound and a capture flag

[LuaComplexTypes.kt:77](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaComplexTypes.kt):

```kotlin
class LuaGenericType(
    override val name: String,
    /** The `: Base` of `---@generic T : Base`, resolved lazily; null when unconstrained. */
    val bound: LuaType? = null,
    /** True for the `` `T` `` spelling — the argument's string literal names the class bound. */
    val capture: Boolean = false,
) : LuaType
```

- `resolveMember(name)` / `getMembers()` delegate to `bound` when non-null, else empty (as today).
- `isAssignableTo(other)`: `ANY` → true; `bound != null` → `bound.isAssignableTo(other)`; else
  **true**. The last arm is TYPE-14-02 at Layer 1: a symbolic parameter is not a value that can
  fail an assignment. (Today it returns `this == other`, which is where
  `LuaTypeAssignabilityInspection` would report against a leaked `T`.)
- `equals`/`hashCode` stay reference-based as today; the graph layer compares by `name` (§3.2).

### 3.2 `LuaGraphType.Generic` gains the same two fields

[LuaGraphType.kt:95](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaGraphType.kt):

```kotlin
data class Generic(
    val name: kotlin.String,
    val bound: LuaGraphType? = null,
    val capture: kotlin.Boolean = false,
) : LuaGraphType()
```

`data class` equality now includes `bound` and `capture`; that is correct — `T : Base` and an
unconstrained `T` are different demands. `displayName()` stays `name`. `getMembers()` returns
`bound?.getMembers() ?: emptyMap()` (today `emptyMap()`), which is what lets a member demand on a
constrained parameter resolve (§5.7). `fromLuaType(LuaGenericType)` maps `bound` through
`fromLuaType` and copies `capture`.

### 3.3 `LuaFunctionType.typeParameters` is populated

[LuaStructuredTypes.kt:99](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaStructuredTypes.kt)
already declares `val typeParameters: List<LuaGenericType> = emptyList()` with zero producers and
zero consumers. This feature makes `funcTypeFromStub` (§4.8) the producer; the consumers are
`LuaGenericInstantiation.containsGeneric` (which does not need it — it scans the structure) and the
parity test (TYPE-14-06), which renders it. It is populated for completeness of the Layer-1 model
and so quick-doc can render `<T : Base>` from a resolved type rather than from the comment.

### 3.4 `LuaClassType` gains arguments and a head name, and its unused `typeParameters` gets a producer

[LuaStructuredTypes.kt:3](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaStructuredTypes.kt):

```kotlin
class LuaClassType(
    override val name: String,                 // "Box" for the declaration, "Box<string>" for an instantiation
    val superTypes: List<LuaType> = emptyList(),
    val localMembers: Map<String, LuaTypeMember> = emptyMap(),
    val typeParameters: List<LuaGenericType> = emptyList(),   // ["T"] on the declaration; empty on an instantiation
    val typeArguments: List<LuaType> = emptyList(),           // [] on the declaration; [string] on an instantiation
) : LuaType {
    /** `name` before the first `<` — what the indexes are keyed on. */
    val headName: String get() = name.substringBefore('<').trim()
}
```

`isAssignableToInternal` gains one clause before the inheritance walk: `other is LuaClassType &&
this.headName == other.headName && (other.typeArguments.isEmpty() || argumentsMatch(this, other))`
where `argumentsMatch` compares `typeArguments` pairwise by `isAssignableTo`. So `Box<string>` ≤
`Box` and `Box<string>` ≤ `Box<string>`, and `Box<string>` ≰ `Box<number>`.

### 3.5 Stub fields, and the version bumps they force

| Stub | New field | Source at index time | Read by |
| :-- | :-- | :-- | :-- |
| `LuaFuncStub`, `LuaLocalFuncStub` ([LuaFuncStub.kt:7-9](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/stubs/LuaFuncStub.kt)) | `luacatsGenericParams: List<Pair<String, String?>>` — name and bound text, in declaration order | `LuaCatsDeclarations.genericParameters(comment)` (§4.2) | `funcTypeFromStub` (§4.8) |
| `LuaLocalVarStub` ([LuaLocalVarStub.kt:7-13](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/stubs/LuaLocalVarStub.kt)) | `luacatsTypeParameters: List<String>`; and `luacatsClassName` now holds the **head** name | `LuaCatsDeclarations.classTypeParameterNames(tag)` / `classHeadName(tag)` (§4.2) | `hostedParts` → `materializeClass` (§4.8); `indexStub` sinks the head name |

Serialization follows the existing idiom (`writeInt(size)` then `writeName` per entry, a `null`
bound written as `writeName(null)`), appended **after** the current fields in both `serialize` and
`deserialize`. `LuaFileElementType.getStubVersion()`
([LuaFileElementType.kt:20](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaFileElementType.kt))
goes from `4` to `5`. Every file-based index whose keys change for a parameterized head is
bumped by one for the same reason — §9's table is the list (`LuaCatsTypeNameIndex` and
`LuaReceiverMemberIndex`, per §5.10); `LuaReceiverMemberIndex`'s own KDoc records that a key
change without a bump leaves already-indexed files answering under the old keys.

### 3.6 `LuaParameterizedType` is unchanged in shape

[LuaComplexTypes.kt:95](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaComplexTypes.kt)
stays `(baseType, arguments)`. Its `resolveMember`/`getMembers` today delegate to the raw base; they
now delegate to `LuaGenericSubstitution.instantiate(base, arguments)` when the resolved base is a
`LuaClassType` with non-empty `typeParameters`, else to the base as today (a `table<K, V>` keeps
its current no-member behaviour). The instantiation is memoized in a `lazy` field on the
`LuaParameterizedType` instance, the idiom `LuaTypeReference.memoizedAnswer` uses, so repeated
member lookups do not re-substitute. It is the Layer-1 spelling of `Box<string>` *before*
resolution; after resolution the engine holds the instantiated `LuaClassType`.

## 4. Components

### 4.1 `net.internetisalie.lunar.lang.psi.types.TypeParser` — generic-aware parsing (extended)

```kotlin
object TypeParser {
    /** [typeParameters]: name → bound (null = unconstrained). Empty = today's behaviour. */
    fun parse(typeString: String, context: PsiElement, typeParameters: Map<String, LuaType?> = emptyMap()): LuaType
}
```

Every private `parse*` gains the `typeParameters` argument and threads it. Two branches change and
one is added in `parseDistinctType`
([TypeParser.kt:70-134](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/TypeParser.kt)):

- `namedType`: **before** `createTypeReference`, `if (name in typeParameters) return
  LuaGenericType(name, typeParameters[name])`.
- `parameterizedName`: the base still goes through `createTypeReference(genericType.text)`; the
  arguments are the node's `children` in document order filtered to `LuaCatsType` (parsed with
  `parseType`) and `LuaCatsTypeParam` (the same name rule as `namedType` — generic first, then
  reference). Order matters and the two generated lists lose it (§6).
- `parameterName` (new branch; `parameterName ::= CODE` has no branch today and falls to
  `UNKNOWN`, DR-01 G): strip the backticks; if the inner name is in `typeParameters`, return
  `LuaGenericType(name, bound, capture = true)`; otherwise `UNKNOWN` as today.

**Prior art**: this *extends* `TypeParser`; nothing else parses type strings.

### 4.2 `net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations` — three readers added

```kotlin
data class GenericParameter(val name: String, val boundText: String?)
/** Every `@generic` parameter on [comment], in declaration order, with its `: Bound` text. */
fun genericParameters(comment: LuaCatsComment): List<GenericParameter>
/** The class name the indexes key on: the `genericType` text of a parameterized head, else the trimmed `argType` text. */
fun classHeadName(tag: LuaCatsClassTag): String
/** The head's own `<T, U>` names, in order; empty for a plain head. */
fun classTypeParameterNames(tag: LuaCatsClassTag): List<String>
```

Grounding: `LuaCatsGenericTypeParam` exposes `argName` / `argSymbol` / `argType`
([gen/LuaCatsGenericTypeParam.java](../../../../src/main/gen/net/internetisalie/lunar/luacats/lang/psi/LuaCatsGenericTypeParam.java));
`LuaCatsClassTag.argType` holds either a `LuaCatsParameterizedName` (with `genericType` and the
argument list) or a bare `NAME` ([gen/LuaCatsClassTag.java](../../../../src/main/gen/net/internetisalie/lunar/luacats/lang/psi/LuaCatsClassTag.java),
`typeName ::= parameterizedName | NAME` at luacats.bnf:94). `classHeadName` is the engine-side
twin of `LuaCatsDocumentationRenderer.simpleParentName`
([LuaCatsDocumentationRenderer.kt:629](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/doc/LuaCatsDocumentationRenderer.kt)),
which reads the PSI instead of splitting text; the renderer keeps its own (it takes a string).

**Prior art**: `LuaTypeGraphBridge` and `LuaCatsDeclaredType.genericNames` each hold a private copy
of "collect the `@generic` names" ([LuaTypeGraphBridge.kt:74-79](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraphBridge.kt),
[LuaCatsDeclaredType.kt:67-71](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/doc/LuaCatsDeclaredType.kt)).
Both are **replaced** by `genericParameters(...).map { it.name }` — MAINT-34's rule that a tag's
*extraction* lives in one place; `LuaCatsDocumentationRenderer.kt:262` still walks the tag itself,
but to render it, not to collect a name set — with one difference preserved: `LuaCatsDeclaredType.genericNames` ends
`.toSet() + "self"` and keeps that `+ "self"` after the replacement. It is a doc-side predicate
("is this `@param` text a type or the first word of prose?") for which `self` is a type; the
engine's map never contains `self` because `funcTypeFromStub` substitutes the receiver class for it.

### 4.3 `net.internetisalie.lunar.lang.psi.types.LuaGenericSubstitution` — Layer-1 substitution (new)

```kotlin
object LuaGenericSubstitution {
    /** [type] with every LuaGenericType whose name is in [bindings] replaced, at every depth. */
    fun substitute(type: LuaType, bindings: Map<String, LuaType>, visited: MutableMap<LuaType, LuaType> = mutableMapOf()): LuaType
    /** `Box<string>` from `Box` and `[string]` — §5.9. */
    fun instantiate(base: LuaClassType, arguments: List<LuaType>): LuaClassType
}
```

Runs inside `LuaTypeManagerImpl`'s cached resolution (read action, pooled). Collaborators:
`LuaClassType`, `LuaArrayType`, `LuaUnionType`, `LuaFunctionType`, `LuaParameterizedType`,
`LuaTableLiteralType`, `LuaAliasType`, `LuaTypeReference` (resolved through `.resolved` — which
replays its source frame, TYPE-11).

### 4.4 `net.internetisalie.lunar.lang.psi.types.LuaGenericInstantiation` — graph-level binding and substitution (new)

Replaces `LuaTypeGraph.instantiateGeneric` / `doInstantiateGeneric`
([LuaTypeGraph.kt:212-256](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraph.kt)),
which handle the top-level case only ("For Phase 5, we only handle direct Generic parameters").
The cache keyed on `(template, element)` moves with it.

```kotlin
internal class LuaGenericInstantiation(private val graph: LuaTypeGraph) {
    /** True when [type] contains a Generic at any depth (§5.3). */
    fun containsGeneric(type: LuaGraphType, visited: MutableSet<LuaGraphType> = mutableSetOf()): Boolean
    /** A fresh copy of [template] for [call]: new parameter and return nodes, generics bound through
     *  lazy projections and substituted through lazy values (§5.5). Memoized per (template, call). */
    fun instantiate(template: LuaGraphType.Function, call: PsiElement): Instantiated
    /** The declared-path application: bind from [argNodes], flow returns into [resultNodes] (§5.8). */
    fun apply(instantiated: Instantiated, argNodes: List<TypeNode>, argExprs: List<PsiElement>, resultNodes: List<VariableNode>)

    class Instantiated(
        val function: LuaGraphType.Function,          // fresh nodes throughout
        val bindings: Map<String, VariableNode>,        // type-parameter name → the node a call site binds
        val captures: Map<Int, String>,                 // parameter index → type-parameter name, for `` `T` `` positions at path ε
    )
}
```

One instance per `LuaTypesVisitor` (it owns the graph). Threading: inside the snapshot build.

### 4.5 `LuaTypeGraphBridge` — passes the names, nothing else

`resolveTypeWithGenerics` keeps today's two-way split **whenever the string mentions no type
parameter**, and takes the generic-aware parser only when it does:

```kotlin
private fun resolveTypeWithGenerics(typeName: String, context: PsiElement, typeParameters: Map<String, LuaType?>): LuaType? {
    if (typeParameters.isEmpty() || tokensOf(typeName).none { it in typeParameters }) {
        return if (isSimpleTypeName(typeName)) LuaTypeManager.getInstance(context.project).resolveType(typeName, context)
        else TypeParser.parse(typeName, context)          // exactly today's two arms, nullability included
    }
    return TypeParser.parse(typeName, context, typeParameters)
}
```

`tokensOf` is §4.8's splitter, shared. The distinction matters because `resolveType` returns
**null** for an unresolvable simple name and every inject method skips the tag on null
(`?: return` at [LuaTypeGraphBridge.kt:81](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraphBridge.kt),
`?: return@forEachIndexed` at `:129` and `:177`),
while `TypeParser.parse` never returns null — DR-03 measured `resolveType("Widget") = null` against
`TypeParser.parse("Widget") = LuaTypeReference(Widget)` for an undeclared name. Routing every simple
name on a `@generic` comment through the parser would inject an `Undefined` value where today
nothing is injected (BUG-406's prose-parameter shape) and pay a dummy-file parse per tag. A string
that does mention a parameter always contains a real type, so injecting its parse is the intent.
The three inject methods build `typeParameters` once via `LuaCatsDeclarations.genericParameters(cats)`,
resolving each bound text through the same parser (a bound is itself a type; a bound naming
another parameter of the same comment is left unresolved — §8). The "For generics, we must inject
both a source and a sink" branches at
[LuaTypeGraphBridge.kt:130-140 and :178-188](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraphBridge.kt)
have identical arms today and collapse to one statement.

### 4.6 `LuaTypesVisitor.visitFuncCall` — the two call paths

[LuaTypesVisitor.kt:864-979](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt):

1. **Declared path** (`calleeNodeRef in declarationTypedNodes`, line 913): after `declaredFunctionOf`,
   `if (instantiation.containsGeneric(declared))` → `apply(instantiate(declared, o), argNodesOf(argExprs), argExprs, callResultNodes)`
   and `return`; else the existing `seedDeclaredReturns` + `demandDeclaredParams`.
2. **Same-file path**: the generic branch at lines 957-970 drops its `calleeNode is ValueNode`
   guard. The callee's function type is read as `functionTypeOf(calleeNode)`: `(calleeNode as?
   ValueNode)?.write as? Function` **or**, for a `VariableNode` (the member node a `:`/`.` callee
   is rebound to at lines 921-931), the first `Function` arm of `calleeNode.write` (a union when
   the member has both a placeholder and a declaration flowing in — DR-01 D's `fun(v) | fun(v)`).
   `isGeneric` becomes `instantiation.containsGeneric(funcTemplate)`. The wiring
   `addEdge(value(o, instantiated.function), use(o, callDemand))` is unchanged.
3. **Ordering with TYPE-10** (§5.12): `propagateExpectedLambdaParams` moves after the callee node
   is resolved and receives the call's `Instantiated` (nullable) so a callback slot typed `fun(a: T)`
   can be seeded from `bindings["T"]`.

### 4.7 `LuaTypeGraph` — two compatibility arms and one nominal clause

- `checkCompatibility` ([LuaTypeGraph.kt:465](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraph.kt)),
  immediately after the `Any`/`Undefined`/equality returns: the `Generic` arm of §5.7.
- `isCompatible` (line 657), after the same three returns and **before** the memo lookup: the
  boolean half of §5.7. A `Generic` result is not memoized when it has a bound (it depends on the
  bound's structure, which may embed nodes) and is `true` without memo when it has none.
- `isNominallyCompatible` (line ~880): `value.className == use.className` becomes
  `sameClass(value.className, use.className)` — equal strings, **or** equal head names where the
  `use` has no `<`. The same rule as §3.4's Layer-1 clause.

### 4.8 `LuaTypeManagerImpl` — parameterized resolution, head keys, and generic-aware stub reads

- `doResolveType(name)` ([LuaTypeManagerImpl.kt:318](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeManagerImpl.kt)):
  new first branch — `if ('<' in name) return resolveParameterized(name, project)` (§5.9).
- `catsClassTags` (line 435): the filter `it.argType?.text?.trim() == name` becomes
  `LuaCatsDeclarations.classHeadName(it) == name`.
- `hostedParts` / `unhostedParts`: `DeclaredParts` gains `typeParameterNames: List<String>` from the
  stub field or from `classTypeParameterNames(tag)`; `materializeClass` and
  `materializeUnhostedClass` pass `typeParameters = names.map { LuaGenericType(it) }` to the
  `LuaClassType` constructor, and build every `@field` type with §4.8's `typeOrReference`.
- `funcTypeFromStub(className, decl)` (line 588) gains `classTypeParameters: List<String>` (the
  receiver class's, passed down from `collectMethodMembers`) and reads the method's own
  `luacatsGenericParams` (stub) or `genericParameters(cats)` (AST). Parameter and return types are
  built by:

  ```kotlin
  private fun typeOrReference(text: String, context: PsiElement, typeParameters: Map<String, LuaType?>): LuaType =
      if (typeParameters.isEmpty() || tokensOf(text).none { it in typeParameters }) LuaTypeReference(text, context)
      else TypeParser.parse(text, context, typeParameters)
  ```

  where `tokensOf` splits on `[^A-Za-z0-9_.]+`. The fast path keeps today's lazy reference for the
  overwhelmingly common non-generic case; the parser runs only when a parameter name actually
  occurs. `LuaFunctionType.typeParameters` is set to the method's own parameters (not the class's).
- `resolveGlobal` is unchanged: it reads the declaring file's snapshot
  ([LuaTypeManagerImpl.kt:253-262](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeManagerImpl.kt)),
  whose function node already carries `Generic` writes once §4.5 lands — DR-01 C measured
  `resolveGlobal(ident) = LuaFunctionType fun(v: T): T`, so the cross-file signature reaches the
  caller today; only the call site's handling (§5.8) is missing.

### 4.9 Stub element types and the file-based index

- `LuaFuncStubElementType.createStub` / `LuaLocalFuncStubElementType.createStub`
  ([LuaFuncStubElementType.kt](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/stubs/impl/LuaFuncStubElementType.kt)):
  add `luacatsGenericParams = catsComment?.let { LuaCatsDeclarations.genericParameters(it) }.orEmpty().map { it.name to it.boundText }`.
- `LuaLocalVarStubElementType.createStub` (line ~32): `val className = classTag?.let { LuaCatsDeclarations.classHeadName(it) }`
  and `luacatsTypeParameters = classTag?.let { classTypeParameterNames(it) }.orEmpty()`.
- `LuaCatsTypeNameIndex.Indexer.map` ([LuaCatsTypeNameIndex.kt:85-88](../../../../src/main/kotlin/net/internetisalie/lunar/lang/indexing/LuaCatsTypeNameIndex.kt)):
  the class-tag key becomes `LuaCatsDeclarations.classHeadName(tag)`.
- **The consumers that re-match the tag text against the key** (§5.10's second group; `catsClassTags`
  is covered in §4.8) — they fetch files by the key and then compare `argType.text.trim()` to the
  name, so they must compare the head instead (DR-03): `LuaCatsTypeNavigation.processElements`
  ([LuaCatsTypeNavigation.kt:48](../../../../src/main/kotlin/net/internetisalie/lunar/lang/navigation/LuaCatsTypeNavigation.kt),
  `if (id.text.trim() != name) continue` → `if (LuaCatsDeclarations.classHeadName(tag) != name) continue`;
  the `LuaNavigationItem` keeps `id` as its element and `name` as its display name),
  `LuaDocumentationTargetProvider` ([LuaDocumentationTargetProvider.kt:291](../../../../src/main/kotlin/net/internetisalie/lunar/lang/doc/LuaDocumentationTargetProvider.kt),
  `if (id.text.trim() == name) return tag` → head), and
  `LuaCatsDocumentationRenderer.resolveBareClassComment` ([LuaCatsDocumentationRenderer.kt:674](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/doc/LuaCatsDocumentationRenderer.kt),
  `if (tag.argType.text.trim() != className) continue` → head). The alias branches beside each are
  untouched. `LuaCatsTypeDeclarations.declarationLeaves` reads the same index but filters through
  `classDeclarationLeaf`, which is null for a parameterized head by REFACT-08-17's design; it is
  left alone (requirements "Out of Scope"). The AST readers of the raw text (§5.10's table)
  change with these.

### 4.10 The grammar — §6.

### 4.11 `LuaGraphType.fromLuaType` — two arms change

[LuaGraphType.kt:303-309](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaGraphType.kt):

- `is LuaGenericType -> Generic(type.name, type.bound?.let { fromLuaType(it, graph, visited) }, type.capture)`.
- `is LuaParameterizedType ->` resolve: `val instantiated = (type.baseType.let { if (it is LuaTypeReference) it.resolved else it } as? LuaClassType)?.takeIf { it.typeParameters.isNotEmpty() }?.let { LuaGenericSubstitution.instantiate(it, type.arguments) }`;
  `instantiated?.let { classTable(it, graph, visited) } ?: Table(type.name)`. The fallback is
  today's behaviour, kept for `table<K, V>` and for a base that is not a generic class.

## 5. Algorithms

### 5.1 Which names are generic for a tag

- **Input**: a `LuaCatsComment`, and optionally the receiver class's `typeParameters`.
- **Output**: `Map<String, LuaType?>` name → bound.
- **Steps**:
  1. `genericParameters(comment)` in declaration order; a later duplicate name overwrites an earlier one.
  2. For each, the bound is `TypeParser.parse(boundText, context, mapSoFar)` when `boundText` is
     non-null — so `---@generic K, V : K` sees `K`; a forward reference (`T : U` before `U` is
     declared) resolves `U` nominally and, failing that, to `UNKNOWN`, which `fromLuaType` maps to
     `Undefined` and §5.7 treats as no bound.
  3. When the comment sits on a class method (a `LuaFuncDecl` whose receiver resolves to a class
     with `typeParameters`), or is a class comment itself, the class's parameters are added
     **first**, so a method-level `T` shadows a class-level `T`.
- `self` is never in the map.

### 5.2 Generic-aware parse

`TypeParser.parse(text, context, typeParameters)` — the rule at every `NAME` position (a
`namedType`, a bare `typeParam` argument, the head of a `parameterizedName` is **excluded**):
generic name first, then builtin, then `createTypeReference`. Exclusion of the head means
`T<string>` is not supported (LuaLS does not support it either).

### 5.3 `containsGeneric` and `projectionsOf`

- **Path steps** (a sealed set): `Elem`, `Arm(i)`, `Param(i)`, `Return(i)`, `Arg(i)`, `Member(name)`.
- `containsGeneric(type)`: depth-first over `Array.elementType`, `Union.types`, `Function.params[i].node.write`,
  `Function.returns[i].write`, `Table.localMembers[name].write` and `Table.superTypes`, with a
  `visited` set keyed on the `LuaGraphType` instance (member nodes can cycle: `t.self = t`). True
  on the first `Generic`.
- `projectionsOf(type): Map<String, List<Path>>` — the same walk, recording the path to every
  `Generic` by name. A `Union` records `Arm(i)` **only** for arms that are themselves `Generic` or
  contain one; a nil arm is never a path.
- Depth is bounded by the same `MAX_DISTRIBUTION_DEPTH` the checker uses; beyond it the walk stops
  (no binding), never throws.

### 5.4 Projection — reading a bound type out of an argument's type, lazily

`projectAt(type: LuaGraphType, path: List<Step>, visited): LuaGraphType`:

1. Empty path → `type`.
2. `Elem`: `type` is `Array` → recurse into `elementType`; `Union` → the first `Array` arm's element
   (the `arrayElementType` rule at [LuaTypesVisitor.kt:1314](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt),
   which is `private` today and becomes `internal` so `LuaGenericInstantiation` can call it —
   the same visibility change §5.8 makes for `positionsAreUnambiguous` and `carriesGraphNodes`);
   else `Undefined`.
3. `Arm(i)`: a `Union` value bound to a union-typed parameter binds the generic to **the whole
   non-nil value**: `T|nil` against `string` gives `T := string`; against `string|nil` gives
   `T := string`. Rule: strip `Nil` arms from the value, then if one arm remains it is the binding,
   else the union of the remaining arms. (`Arm(i)` never indexes into the value by position — a
   union's arms are unordered after canonicalisation.)
4. `Param(i)` / `Return(i)`: `type` is `Function` → `params[i].node.writeWith(visited)` /
   `returns[i].writeWith(visited)`; `Union` → first `Function` arm; else `Undefined`.
5. `Arg(i)`: `type` is a `Table` whose `className` parses as `Head<a0, …>` → the *i*-th argument
   parsed by `TypeParser` against the call's context (a nominal instantiation carries its arguments
   only in its name; this is the one place the design reads a type back out of a name, and it is
   the same text `resolveParameterized` accepts); else `Undefined`.
6. `Member(name)`: `type.getMembers()[name]?.writeWith(visited) ?: Undefined`.

Every recursion passes `visited` (BUG-390); the compute is installed as a `LazyValueElement`
whose `compute` is `{ visited -> projectAt(source.writeWith(visited), path, visited) }`.

### 5.5 Instantiation of a template for one call site (same-file callee)

- **Input**: `template: LuaGraphType.Function` (the callee's write), `call: LuaFuncCall`.
- **Output**: `Instantiated` (§4.4).
- **Steps**:
  1. `bindings = names(template).associateWith { graph.variable(call) }` — one fresh node per
     distinct generic name across params and returns. If a name's `Generic` has a `bound`, add
     `graph.addEdge(bindings[name], graph.use(call, bound, declaredDemand = true))` (TYPE-14-11).
  2. For each parameter `p` at index `i` with type `w = p.node.write`:
     - `w is Generic` (path ε): the instantiated parameter node **is** `bindings[w.name]`
       (today's behaviour, unchanged); if `w.capture`, record `captures[i] = w.name`.
     - `containsGeneric(w)`: mint `p' = graph.variable(call)`; for each `(name, paths)` in
       `projectionsOf(w)` and each `path`, `graph.addEdge(graph.lazyValue(call) { v -> projectAt(p'.writeWith(v), path, v) }, bindings[name])`.
       The instantiated parameter is `Parameter(p', p.name, p.isOptional, p.isVararg)`. No demand
       is attached to `p'` (the structural shape is checked by the caller's existing wiring against
       `p`'s own declared use — see step 4).
     - else: the parameter is reused as is (today's behaviour).
  3. For each return `r` at index `j` with `w = r.write`:
     - `w is Generic`: the instantiated return is `bindings[w.name]` (unchanged).
     - `containsGeneric(w)`: mint `r' = graph.variable(call)` and
       `graph.addEdge(graph.lazyValue(call) { v -> substitute(w, bindings, v) }, r')` (§5.6).
     - else: reuse `r`.
  4. Return `Function(params', returns', template.declaredSignature)`. The caller wires it exactly
     as today: `addEdge(value(call, function), use(call, callDemand))`, and
     `checkFunctionCompatibility` flows each argument node into `p'` / `bindings[…]` and each `r'`
     into the call's result node.
- **Memo**: `(template, call)` → `Instantiated`, as today's `instantiationCache`.
- **Why the template's own parameter node is not fed**: a generic function's body is inferred once
  against its symbolic types; feeding call-site types into it is what TYPE-05 avoided and what
  would make `first(xs)` and `first(ys)` contaminate each other. Unchanged from today.

### 5.6 Lazy substitution of a return type

`substitute(type, bindings, visited): LuaGraphType`:

- `Generic` → `bindings[name]?.writeWith(visited) ?: type` (unbound stays symbolic; §5.7 keeps it silent).
- `Array` → `Array(substitute(elementType))`.
- `Union` → `Union.create(types.map { substitute(it) })`.
- `Function` → a `Function` whose parameter and return **nodes are fresh** variables each fed by a
  lazy value of the substituted node write (a callback returned from a generic function is a new
  function value per call — TYPE-14's reading of LuaLS Q8).
- `Table` with a `className` of the form `Head<…>` whose arguments mention a bound name (§5.4 step 5
  in reverse): compute the instantiated name `Head<arg0', …>` where each `argᵢ'` is
  `substitute(argᵢ).displayName()`; resolve it through `LuaTypeManager.resolveType(name, call)`
  and `fromLuaType` it **once per distinct name per lazy node** (a `HashMap<String, LuaGraphType>`
  captured by the lambda), so repeated reads do not re-mint member nodes and bump the revision
  (the BUG-473 hazard). A name that does not resolve (an argument whose display is `{ ... }` or a
  union) falls back to `Table(name)` — memberless, as today, and recorded in §8.
- `Table` without such a name, and every other kind → returned as is.

### 5.7 Compatibility with a symbolic generic

In both `checkCompatibility` (may emit; wires member edges) and `isCompatible` (boolean):

| value | use | rule |
| :-- | :-- | :-- |
| `Generic(bound = null)` | anything | compatible; no edges, no report |
| anything | `Generic(bound = null)` | compatible; no edges, no report |
| `Generic(bound = B)` | `U` | recurse as `(B, U)` — the parameter is used *as* its bound (TYPE-14-12: `Table{k}` demand on `p : T : Base` wires `Base.k` into the access node) |
| `V` | `Generic(bound = B)` | recurse as `(V, B)` — the argument must satisfy the bound (TYPE-14-11) |
| `Generic(a)` | `Generic(b)` | compatible when `a == b`; otherwise the bound rules above, in the order listed |

`checkCompatibility`'s arm sits after the `Any`/`Undefined`/equality returns and **before** the
union distribution, so `Generic | nil` as a value still distributes arm-wise. `isCompatible`'s arm
sits before the memo lookup and returns without storing when either side is a bounded `Generic`.

### 5.8 Instantiation for a declaration-typed callee

- **Input**: `declared: LuaGraphType.Function` from `declaredFunctionOf` (the seed's member nodes —
  shared by every call in the file), `argNodes`, `argExprs`, `callResultNodes`.
- **Steps**:
  1. `inst = instantiate(declared, call)` as §5.5 — but step 2's "else: reuse" and step 3's "else:
     reuse" become **copy**: a non-generic parameter becomes a fresh `p''` carrying
     `graph.use(call, p.node.read, declaredDemand = true)` only when `!carriesGraphNodes(p.node.read)`
     (the BUG-425 rule, [LuaTypesVisitor.kt:1043](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt);
     it and `positionsAreUnambiguous` at `:1078` are `private` today and become `internal`, called
     from `LuaGenericInstantiation` rather than copied); a non-generic return becomes a fresh `r''`
     fed by `graph.value(call, r.write, declaredOrigin = true)`.
     So the instantiated function shares **no node** with the seed.
  2. `apply`: if `positionsAreUnambiguous(declared, argExprs.size)`, for each `i`:
     `graph.addEdge(argNodes[i], inst.function.params[i].node)`; and for each capture position
     `(i, name)` in `inst.captures`, §5.11. If positions are ambiguous, no parameter edge is added
     (bindings stay unbound, returns resolve to `Undefined`).
  3. For each `j`: `graph.addEdge(inst.function.returns[j], callResultNodes[j])`.
  4. No edge to the seed, no `use(callDemand)` on it, no arity check — the contract
     `testDeclarationTypedCalleeIsNotArityChecked` pins.

### 5.9 Resolving a parameterized name, and instantiating a class

`resolveParameterized(name, project)`:

1. Split at the first `<`: `head = name.substringBefore('<').trim()`, `argsText = name.substringAfter('<').removeSuffix(">")`.
2. Split `argsText` on commas **at depth 0** (tracking `<`/`>`, `(`/`)`, `{`/`}`, `[`/`]`
   nesting) — the parser has already validated the shape (§6), this is only re-splitting text the
   grammar produced. Trim each.
3. `base = resolveType(head)` (the ordinary door, cached). Not a `LuaClassType`, or
   `typeParameters` empty → return `base` (a `table<K, V>` resolves to the `table` primitive as
   today; a non-generic class with stray arguments resolves to itself).
4. `arguments = argsTextList.map { TypeParser.parse(it, context, emptyMap()) }` — a caller's own
   generic names inside the arguments (`Box<T>` in a generic signature) are handled *before* this
   door by §4.8's `typeOrReference`, which parses with the names and produces a
   `LuaParameterizedType` holding `LuaGenericType` arguments; that never reaches this door by name.
5. Return `LuaGenericSubstitution.instantiate(base, arguments)`.

`instantiate(base, arguments)`:

1. `bindings = base.typeParameters.mapIndexed { i, p -> p.name to (arguments.getOrNull(i)?.takeUnless { it is LuaTypeReference && it.resolved == LuaPrimitiveType.UNKNOWN } ?: p) }.toMap()`
   — a missing argument, or one that is an unresolved reference (e.g. the `T` of a module's
   exported `Box<T>` parsed with no parameter names), leaves that parameter symbolic
   (TYPE-14-09's last sentence); an extra argument is ignored. Reading `resolved` forces the
   reference and replays its TYPE-11 source frame. Reached from `resolveParameterized` that replay
   lands in `resolveType`'s open frame, so the argument's sources become the instantiation's;
   reached from §3.6/§4.11 with no frame open it is a no-op (`LuaTypeSourceRecorder` at depth 0).
2. `members = base.localMembers.mapValues { m -> m.copy(type = substitute(m.type, bindings)) }`
   — `LuaTypeMember` is a data class; `sourceElement`, `visibility`, `description` are preserved
   (TYPE-13's declaration provenance survives).
3. `superTypes = base.superTypes.map { substitute(it, bindings) }`.
4. `LuaClassType(name = "${base.headName}<${arguments.joinToString(", ") { it.name }}>", superTypes, members, typeParameters = emptyList(), typeArguments = arguments)`.

`substitute(type, bindings, visited)` at Layer 1:

- `LuaGenericType` → `bindings[name] ?: type`.
- `LuaTypeReference` → `substitute(type.resolved)` when the reference's `name` mentions a bound
  name as a token (`tokensOf`, §4.8), else the reference untouched (keeps laziness for the common
  case).
- `LuaArrayType` → `LuaArrayType(substitute(elementType))`; `LuaUnionType` → union of substituted
  arms; `LuaFunctionType` → params and return substituted, `declaredSignature` and
  `typeParameters` copied; `LuaParameterizedType` → arguments substituted, base untouched;
  `LuaTableLiteralType` → members substituted; `LuaAliasType` → target substituted.
- `LuaClassType` with `typeArguments` → arguments substituted and re-instantiated through
  `instantiate(resolveType(headName), …)`; a `LuaClassType` without arguments → itself.
- `visited` maps an input to its output for cycle safety (`t.self = t` at Layer 1).

**Inheritance with substitution** (TYPE-14-10): `materializeClass` already builds
`superTypes` as `LuaTypeReference("PBase<string>", decl)`; `LuaTypeReference.resolved` calls
`resolveType("PBase<string>")`, which now takes the parameterized branch. Nothing else is needed
for BUG-420 — `getMembersInternal` walks supertypes and finds the instantiated `PBase<string>`
with `inherited: string`.

### 5.10 Head naming at index time

`classHeadName(tag)`: `PsiTreeUtil.getChildOfType(tag.argType, LuaCatsParameterizedName::class.java)?.genericType?.text ?: tag.argType.text.trim()`.
`classTypeParameterNames(tag)`: the parameterized name's `typeParamList` mapped to `.text` —
the accessor that §6 measured **unchanged** after regeneration. A nested `LuaCatsType` argument in
a **head** (`---@class Box<string[]>`) is a user error LuaLS also rejects; it lands in the sibling
`typeList` and is ignored rather than reported.

Every site that produces, sinks, re-matches or reads a class name was enumerated at `ddaac805`
by `grep -rn 'luacatsClassName\|LuaClassNameIndex\.\|LuaCatsTypeNameIndex\.\|classTagList.*argType\|classTag.*argType.*text' src/main/kotlin`
**plus** a manual read of the producers that pattern cannot reach (a tag's `argType` read through
a local — found by `grep -rn 'argType' src/main/kotlin` filtered to `LuaCatsClassTag` receivers,
listed in the last group). They fall into five groups:

| Group | Sites | Change |
| :-- | :-- | :-- |
| **Producers** of the name | `LuaLocalVarStubElementType.createStub` (`:32`, the stub field and through it the `LuaClassNameIndex` sink at `:131`); `LuaCatsTypeNameIndex.Indexer` (`:85-88`) | head name (§4.9) |
| **Re-matchers** — fetch by key, then compare the tag's raw text | `LuaTypeManagerImpl.catsClassTags` (`:446`); `LuaCatsTypeNavigation.processElements` (`:48`); `LuaDocumentationTargetProvider` (`:291`); `LuaCatsDocumentationRenderer.resolveBareClassComment` (`:674`) | compare `classHeadName(tag)` (§4.8, §4.9) |
| **AST readers of the raw text** — with a stub arm (`LuaHierarchyUtil`, `LuaImportNameResolver`) or without one (the bridge) | `LuaHierarchyUtil.className` ([LuaHierarchyUtil.kt:23-30](../../../../src/main/kotlin/net/internetisalie/lunar/lang/hierarchy/LuaHierarchyUtil.kt)); `LuaImportNameResolver.classNameOf` ([LuaImportNameResolver.kt:50-57](../../../../src/main/kotlin/net/internetisalie/lunar/lang/completion/LuaImportNameResolver.kt)); `LuaTypeGraphBridge.injectTypeAnnotation`'s `classTag?.argType?.text` ([LuaTypeGraphBridge.kt:71](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraphBridge.kt)) | the AST arm reads `LuaCatsDeclarations.classHeadName(tag)`, so a `---@class Box<T>` answers `Box` whether stubbed or in-edit. Without this the hierarchy browser and import resolution would answer `Box` for a stubbed file and `Box<T>` for the file being edited, and the bridge would inject the type `Box<T>` for the class's own local. |
| **Key-only readers** — query the index by a name they already hold and never look at the tag text | `LuaHierarchyUtil` (`:41`, `:56`), `LuaNameReference` (`:136`), `LuaDocumentationLinkHandler` (`:29`), `LuaDocumentationTargetProvider` (`:202`), `GlobalSymbolRankingService` (`:72`, `:209`), `LuaTypeManagerImpl.doResolveType` (`:323`), `LuaCatsTypeDeclarations.declarationLeaves` (`:179`), `LuaCatsDocumentationRenderer.resolveClassComment` (`:651`), `LuaCatsTypeNavigation.processNames` (`:32`, `processAllKeys`) | none in code; they now see `Box` where they saw `Box<T>`. `declarationLeaves` additionally filters through `classDeclarationLeaf`, which is null for a parameterized head by REFACT-08-17's design, and is left alone (rename stays out of scope). `LuaDocumentationLinkHandler` is fed by the renderer's `buildTypeLink` href, which is built from the raw tag text (`:118`) and resolves for a class tag in neither state today (it needs a `LuaCatsDocumentationTarget`, and a class tag is wrapped in `LuaCatsTypeDocumentationTarget` at `LuaDocumentationTargetProvider.kt:82`) — unchanged by this feature. |
| **Producers the grep cannot reach** — a tag's `argType` read through a local variable | `LuaReceiverMemberIndex.indexClassFields` ([LuaReceiverMemberIndex.kt:474-480](../../../../src/main/kotlin/net/internetisalie/lunar/lang/indexing/LuaReceiverMemberIndex.kt)), which keys a class's `@field` members on the raw tag text; `LuaFileElementType.extractExportedType` ([LuaFileElementType.kt:73-78](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaFileElementType.kt)), which records a `return Box` module's exported type as the raw `Box<T>` into `LuaFileStub.exportedTypeString`; `LuaDocumentationTargetProvider.kt:78` (display name); `LuaCatsDocumentationRenderer.kt:118` (the href above) | `indexClassFields` keys on `classHeadName(tag)`, so `membersIn("Box")` (which `collectMethodMembers` already queries by the class name, §4.8) and its other door `globalMembership` (`LuaCompletionContributor.kt:182`) see the class's fields under the same key the engine uses; its version is bumped with the others (§9). The KDoc of `LuaCatsTypeDeclarations.classDeclarationLeaf` (`LuaCatsTypeDeclarations.kt:57-62`) explains its exclusion of a parameterized head by "the index keys the tag under the whole text" — that sentence becomes false and is rewritten to cite REFACT-08-04's head exclusion instead; the exclusion itself stays. The exporter is left alone: its string reaches `TypeParser.parse` at `LuaTypeManagerImpl.kt:278`, which after §4.1 yields `LuaParameterizedType(Box, [ref T])`, and §5.9 step 1 leaves a parameter symbolic when its argument is unresolved — so `require`-ing a generic-class module yields the raw class with its members carrying symbolic `T`. **That is a change**: today the base `Box` of that parameterized type does not resolve at all (DR-01 E: the key is `Box<T>`), so the module's type has no members. It is the improvement TYPE-14-08 exists for, and it is why TYPE-14-17's corpus gate at Phase 5 admits added diagnostics that a newly-resolving class accounts for. The two display sites keep the raw text on purpose. |

Consumers that keep the raw text on purpose: `LuaCatsDocumentationRenderer`'s signature
rendering (`:226`, renders what was written) and its inherited-field walk's `visited` seed
(`:690`, which is compared against parent names already stripped by `simpleParentName` — a
parameterized head there can only affect a class that lists itself as its own parent).

**Why the re-matchers must move with the key — measured (DR-03, unmodified engine, `ddaac805`):**

```
DR-03 processNames keys containing Box/Plain = [LunarFixtureBox, Box, Box<T>, Plain]
DR-03 processElements("Box") = 0 item(s) []
DR-03 processElements("Box<T>") = 1 item(s) [Box<T>]
DR-03 processElements("Plain") = 1 item(s) [Plain]
DR-03 undeclared name: resolveType(Widget)=null | TypeParser.parse(Widget)=LuaTypeReference(Widget) -> fromLuaType=undefined
DR-03 declared name control: resolveType(Plain)=LuaClassType(Plain)
```

The key today is the raw text, so the raw-text match succeeds and Go to Class finds `Box<T>`.
After the key becomes `Box`, the same raw-text match against `Box` fails; without §4.9's change
the class would be found under neither name. Requirements case 27 pins the post-change state and
names leaving the matcher as its mutation.

### 5.11 Backtick capture

At `apply` (§5.8 step 2) and, for the same-file path, in `visitFuncCall` right after
instantiation: for each `(i, name)` in `inst.captures`, `argExprs.getOrNull(i)` unwrapped is a
`LuaTerminalExpr` with `string != null` → `literal = text.trim('"', '\'')` (the two quote forms
`visitTerminalExpr` already recognises; a long-bracket string is not captured) →
`resolved = LuaTypeManager.resolveType(literal, call)` → if non-null,
`graph.addEdge(graph.value(argExpr, fromLuaType(resolved, graph), declaredOrigin = true), inst.bindings[name])`.
The argument itself still flows into the parameter node (it is a `string`), which raises nothing
because the parameter's write is a capture `Generic` (§5.7). Not a literal, or unresolvable → no
edge; `T` stays unbound.

### 5.12 Callback-slot binding (TYPE-14-16, Could)

`seedLambdaParams(lambda, expected)` ([LuaTypesVisitor.kt:1117](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt))
gains `bindings: Map<String, VariableNode>?`. When `expectedParam.type is LuaGenericType` and
`bindings?.get(name)` exists, the seed edge is `graph.addEdge(bindings[name], paramNode)` instead
of a value node built from the symbolic type. For this to see a binding from a *sibling* argument,
the call's `Instantiated` must exist before the lambda is seeded, so in `visitFuncCall` the order
becomes: resolve callee → build `Instantiated` if generic → `propagateExpectedLambdaParams(o,
argExprs, calleeUnwrapped, instantiated?.bindings)` → wire. Binding from `t: T[]` into `T` is a
lazy projection (§5.4), so it does not matter that `t`'s node is read after the lambda is seeded.
The one shape this cannot serve is a lambda whose parameter is the **only** source of `T` — that
is inference *from* an un-annotated lambda, which is LuaLS's own open half of #1883 and stays out.

## 6. The grammar, and what regeneration changes

[luacats.bnf:201-203](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/parser/luacats.bnf):

```bnf
parameterizedName    ::= genericType '<' typeArgument {',' typeArgument }* '>'
private typeArgument ::= (typeParam &(',' | '>')) | type
genericType          ::= NAME
typeParam            ::= NAME
```

- `&(',' | '>')` is Grammar-Kit's and-predicate: it consumes nothing and succeeds only when the
  next token is `,` or `>`. So a bare name that is immediately followed by the list separator or
  the closer stays a `LuaCatsTypeParam` — the shape `LuaCatsTypeDeclarations.isDeclarationSlotHolder`
  and `LuaCatsTypeReferenceContributor` are registered on — and anything else (`string[]`,
  `Widget|nil`, `fun(a: T): T`, `table<…>`) is a nested `LuaCatsType`.
- **`private` is load-bearing.** A public `typeArgument` rule generates a `LuaCatsTypeArgument`
  node between `LuaCatsTypeParam` and `LuaCatsParameterizedName`, and `isDeclarationSlotHolder`
  tests `holder.parent is LuaCatsParameterizedName`
  ([LuaCatsTypeDeclarations.kt:107](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/psi/LuaCatsTypeDeclarations.kt)).
  DR-02 run a measured that form turning the tests listed in risks-and-gaps "DR-02 result" red;
  run b measured the private form leaving every one green with both test files unmodified.
- **Generated delta** (measured, run b): `LuaCatsParameterizedName.getTypeParamList()` is
  **unchanged** and `getTypeList(): List<LuaCatsType>` is added; no new PSI class. Because a name
  argument and a nested argument land in different lists, the one consumer that needs argument
  **order** — `TypeParser.kt:86` (§4.1) — walks `parameterizedName.children` in document order and
  keeps `LuaCatsTypeParam` and `LuaCatsType` children. The test consumers at
  `LuaCatsTypeDeclarationsTest.kt:200`/`:218`/`:233` and `LuaCatsTypeReferenceTest.kt:97` read
  `typeParamList` and stay as they are. `LuaCatsDocumentationRenderer.kt:262` reads
  `genericTypeParamList` (the `@generic` tag), not this rule — unaffected.
- **Regeneration** is `.claude/skills/generate-parser/scripts/generate.sh` (headless, MAINT-20);
  commit `src/main/gen` with the `.bnf`. The script compiles Kotlin **before** regenerating, so the
  consumer edit in `TypeParser` must compile against both the old and the new accessors — the
  children walk does; a direct `typeArgumentList`/`typeList` reference does not (measured: run b's
  first regen aborted at that step).

## 7. Data flow

### Example 1 — `first(xs)` with `---@param xs T[] ---@return T` (same file)

1. `visitFunctionBody` injects `xs`'s write `Array(Generic(T))` and the return's write `Generic(T)`
   (§4.5 passes `{T → null}` to the parser).
2. `visitFuncCall` finds the callee's `Function` write; `containsGeneric` is true; `instantiate`
   mints `bindings = {T → n_T}`, a fresh `p'` for `xs`, and adds
   `lazy{ projectAt(p'.write, [Elem]) } → n_T`; the return is `n_T` itself.
3. `checkFunctionCompatibility` wires `argNode(xs) → p'` and `n_T → callResult[0]`.
4. Reading `a`'s type: `callResult[0].write` → `n_T.write` → the lazy value → `p'.write` is
   `{ ... } | string[]` → `Elem` → `string`. Requirements case 5.

### Example 2 — `ident("x")` against `lib.lua`'s `@generic` global

1. `freeGlobalSeed("ident")` seeds `fun(v: Generic(T)): Generic(T)` (DR-01 C shows the signature
   already arrives).
2. The declared branch: `containsGeneric` true → `instantiate` copies the signature with a fresh
   `n_T` as both the parameter and the return; `apply` adds `argNode("x") → n_T` (positions are
   unambiguous: one argument, one parameter) and `n_T → callResult[0]`.
3. `s : string`; the seed's own parameter node is never written (case 11). The second call mints
   its own `n_T`, so `n : number`.

### Example 3 — `---@type Box<string> local b; local v = b.value`

1. Index time: `LuaLocalVarStub(luacatsClassName = "Box", luacatsTypeParameters = ["T"], luacatsFields = {value → "T"})`.
2. `injectTypeAnnotation` parses `Box<string>` → `LuaParameterizedType(ref(Box), [string])` →
   `fromLuaType` → `instantiate(Box, [string])` → `LuaClassType("Box<string>", members = {value: string})`
   → `classTable` mints `value`'s node with write `String`.
3. `visitIndexExpr` on `b.value` demands `Table{value}` of `b`; `checkTableCompatibility` wires
   the class table's `value` node into the access node → `v : string`. Requirements case 16.

## 8. Edge cases

| Case | Handling |
| :-- | :-- |
| A generic name shadows a project class of the same name | The parser checks the name map before nominal resolution (§5.2). Case 2. |
| `T` used in a comment that declares no `@generic` | Not in the map → nominal → unresolved → `Undefined`, as today. |
| Bound referencing a later parameter (`T : U`, `U` declared after) | Resolves nominally, fails, `UNKNOWN` → treated as unconstrained. Documented, not reported. |
| A union parameter `T\|nil` bound from a `nil` argument | Strip `Nil` arms → nothing left → `Undefined`; `T` unbound. |
| Arity mismatch on a declared generic callee | `positionsAreUnambiguous` false → no binding; returns resolve `Undefined`; no diagnostic. |
| A generic method called through a union receiver (`{ ... } \| Box`) | The member node's write is a union; the first `Function` arm is the template (§4.6 step 2). |
| `Box<T>` returned where `T` binds to `{ ... }` (an anonymous table) | The instantiated name `Box<{ ... }>` does not resolve; fallback `Table("Box<{ ... }>")` — memberless, symbolic display. Recorded in risks-and-gaps "Known limitation". |
| `Box<string>` assigned to `Box` | Head-name clause (§3.4, §4.7): compatible. `Box<number>` to `Box<string>`: not. |
| Recursive generic class (`---@class Node<T> ---@field next Node<T>`) | `substitute`'s `visited` map returns the in-construction instantiation for the cycle-back reference; `classTable` does the same at graph level via its `visited`. |
| Two `@generic` tags on one comment | `genericParameters` concatenates them in order. |
| A `@generic` tag with no parameters (`---@generic` alone) | Empty list; nothing changes. |
| A `` `T` `` argument that is a variable, not a literal | No capture edge; `T` unbound; the return is `Undefined`. Case 21's `u`. |
| Class-level `T` and method-level `T` on the same method | Method-level wins (§5.1 step 3). |
| Stub built by an older plugin | `getStubVersion` 5 forces a rebuild. |

## 9. Integration points

No `plugin.xml` change: no new extension is registered; every component is reached through the
existing `LuaTypeManager` service, the existing stub element types, and the existing
`LuaCatsTypeNameIndex` and `LuaReceiverMemberIndex` (both `FileBasedIndexExtension`s already registered).

| Touch point | Change |
| :-- | :-- |
| `LuaFileElementType.getStubVersion()` | `4` → `5` |
| `LuaCatsTypeNameIndex.getVersion()` | +1 |
| `LuaReceiverMemberIndex.getVersion()` | +1 (its class-field key changes, §5.10) |
| `luacats.bnf` + `src/main/gen/**/LuaCats*` | §6, regenerated and committed |
| `LuaCatsStubAstParityTest` | + generic function and generic class fixtures (TYPE-14-06) |
| Corpus baselines (`test -PwithCorpus`) | re-baselined at Phases 1, 2 and 5; removed diagnostics only, except Phase 5's attributed additions (TYPE-14-17) |
| `CHANGELOG.md` | one entry under the next milestone, via the `changelog` skill |

## 10. Requirement coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TYPE-14-01 | M | §4.1, §4.2, §4.5, §5.1, §5.2 |
| TYPE-14-02 | M | §3.1 (`isAssignableTo`), §3.2, §4.7, §5.7 |
| TYPE-14-03 | M | §4.4, §5.3, §5.4, §5.5, §5.6 |
| TYPE-14-04 | M | §4.6 step 2 |
| TYPE-14-05 | M | §4.6 step 1, §5.8 |
| TYPE-14-06 | M | §3.3, §3.5, §4.8 (`funcTypeFromStub`), §4.9 |
| TYPE-14-07 | M | §6 |
| TYPE-14-08 | M | §3.4, §3.5, §4.8, §4.9 (index key and its re-matching consumers), §5.10 |
| TYPE-14-09 | M | §3.4, §3.6, §4.3, §4.8, §4.11, §5.9 |
| TYPE-14-10 | M | §5.9 (last paragraph) |
| TYPE-14-11 | S | §3.1, §3.2, §5.5 step 1, §5.7 |
| TYPE-14-12 | S | §3.2 (`getMembers`), §5.7 |
| TYPE-14-13 | S | §4.1 (`parameterName`), §5.11 |
| TYPE-14-14 | S | §5.6 (`Table` arm), §4.11 |
| TYPE-14-15 | S | §3.4 (`name`), §5.6, `displayName` unchanged |
| TYPE-14-16 | C | §5.12 |
| TYPE-14-17 | M | §9 (gates), §2 (corpus row) |

## 11. Alternatives considered

- **A `Var(node)` graph-type kind** for nested bindings. Rejected (§2): a new `LuaGraphType`
  subclass touches every exhaustive `when` in the engine and the checker's memo keys, while
  `LazyValueElement` already gives "a type computed from nodes at read time" with the cycle guard
  threaded.
- **Eager unification at the call site** (compute bindings from `argNode.write` when the call is
  visited). Rejected: the TYPE-10 ordering hazard, measured there and fixed with laziness.
- **Strip `<…>` for BUG-420 only.** Rejected: it closes the symptom and discards the information
  TYPE-14-10 needs; the parameterized-resolution branch closes both.
- **Store the bound on the binding node as a `use` only, not on the placeholder.** Rejected: the
  body-side rule (TYPE-14-12) needs the bound *where the parameter's write is read*, which is the
  placeholder itself; the call-site demand is added as well (§5.5 step 1).
- **Change `typeParam ::= type` outright.** Rejected: it moves the NAME leaf two levels deeper
  and breaks REFACT-08's holder predicates and reference registration; the predicate form keeps
  the bare case byte-identical (DR-02).
- **A public `typeArgument` rule with a `LuaCatsTypeArgument` node.** Rejected by measurement
  (DR-02 run a): one extra PSI level breaks the same predicates; the private rule inlines away.
  Adapting the predicates instead was costed: `isDeclarationSlotHolder`'s second clause would test
  `parent is LuaCatsTypeArgument && grandparent is LuaCatsParameterizedName`, and REFACT-08's
  mutation-proved tests T/U/V would need re-proving against the new chain. Two lines of code plus a
  re-proof, to buy a PSI node nothing consumes. The private rule is free.
- **Re-key the index and leave its re-matching consumers alone.** Rejected by measurement (DR-03):
  a class would be reachable under neither its raw nor its head name.
- **Keep `resolveType` name-only and make `LuaTypeReference` parse.** Rejected: the reference is the
  hot lazy path for every `@field`; parsing every one costs a dummy-file parse per member. The
  token fast path (§4.8) parses only strings that mention a parameter.

## 12. Open Questions

_None — feature has cleared the planning bar._
