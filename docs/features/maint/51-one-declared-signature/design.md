---
id: "MAINT-51-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-51"
folders:
  - "[[features/maint/51-one-declared-signature/requirements|requirements]]"
---

# Technical Design: MAINT-51 — One Declared Signature

All paths are under `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/`. Line numbers are at
`dccb8b18`, before [[MAINT-52]]. MAINT-52 lands first and moves some of them, so find each site by
the symbol named beside it. Every "after MAINT-52" statement below is the code of MAINT-52's design §2.

## 1. Current state

| Rule | Site | Copy |
|:--|:--|:--|
| `?` demands `T\|nil` | `LuaGraphType.declaredParamNodeFor`, `LuaGraphType.kt:434-435` | materialized signature (stub-backed method, `fun(...)` annotation) |
| | `LuaTypesVisitor.demandTypeOf`, `LuaTypesVisitor.kt:1096-1105` | per-call copy for a declaration-typed callee |
| | `LuaTypeGraphBridge.demandTypeFor`, `LuaTypeGraphBridge.kt:155-164`, called at `:149` | in-file function; the only copy that leaves a `Generic` alone |
| per-call copy | `LuaTypesVisitor.demandDeclaredParams` `:1050-1061`, `detachedDemand` `:1064-1074`, `detachedFunction` `:1080-1084`, `detachedParamNode` `:1087-1094`, `carriesGraphNodes` `:1120-1126`, `positionsAreUnambiguous` `:1155-1158` | [[BUG-19]], [[BUG-425]]; [[TYPE-14]] design §5.8 plans a second one |
| a `nil` arm is not evidence | `LuaTypeGraph.checkCompatibility`, `informative` at `LuaTypeGraph.kt:553`, the "must not ENABLE" return at `:572-577` | absent from `isCompatible`'s union branch, `:751-752` |
| only a certain `nil` is a defect | `checkCompatibility`'s `Nil` branch, `:683-700` | reached only after the union-demand branch `:597-645`, which reports any `nil` |

After MAINT-52 the three `?` copies build the same `Union.create(listOf(T, Nil))`, except that
`demandTypeFor` skips a `Generic`. They are still three functions.

DR-03 measured what the two `nil` rows cost: a nested `nil` arm and an uncertain `nil` are both
reported against a union demand, and both are silent against a single type.

## 2. Changes

### 2.1 `LuaTypeAlgebra` gains two rules

Add after MAINT-52's `isOptional`:

```kotlin
/**
 * The demand a declared parameter places on its argument. A `?` parameter accepts `nil` whether the
 * argument is omitted or written (BUG-552), so its demand is `T|nil`. A generic `T` is left alone:
 * binding it is the instantiation's job (TYPE-05).
 */
fun parameterDemand(
    declared: LuaGraphType,
    isOptional: Boolean,
): LuaGraphType =
    if (isOptional && declared !is LuaGraphType.Generic) {
        LuaGraphType.Union.create(listOf(declared, LuaGraphType.Nil))
    } else {
        declared
    }

/**
 * The arms of a union value that are evidence about it: all but `nil`. A `nil` arm is optionality
 * (BUG-416), so it never decides whether the value fits a demand.
 */
fun informativeArms(union: LuaGraphType.Union): List<LuaGraphType> = union.types.filter { it != LuaGraphType.Nil }
```

The `Generic` clause is `demandTypeFor`'s, kept and extended to the other two sites. Its only
measured effect is the wording of an error that is wrong either way (DR-02, C12x).

### 2.2 The materialized and in-file sites call `parameterDemand`

`LuaGraphType.declaredParamNodeFor` (after MAINT-52, `val acceptsNil = param.isOptional` and the
`val demand = …` line below it) becomes:

```kotlin
val demand = LuaTypeAlgebra.parameterDemand(graphType, param.isOptional)
```

In its KDoc, replace "An optional parameter also accepts `nil` (BUG-552)." with "Its demand is
[LuaTypeAlgebra.parameterDemand], as at a call site ([LuaSignatureCopier.parameterUse])."

`LuaTypeGraphBridge`: delete `demandTypeFor` with its KDoc (`:154-164`). At `:149` write:

```kotlin
val demand = LuaTypeAlgebra.parameterDemand(graphType, LuaCatsDeclarations.isOptionalParam(paramTag))
val useNode = graph.use(context, demand, declaredDemand = true)
```

### 2.3 `LuaSignatureCopier` — new

`net.internetisalie.lunar.lang.psi.types.LuaSignatureCopier`, file `LuaSignatureCopier.kt`. One instance
per `LuaTypesVisitor`, created beside its graph. Threading: inside the snapshot build, which already
holds a read action.

```kotlin
/**
 * Per-call copies of a declared signature's parameters (BUG-19, BUG-425).
 *
 * A declared callee's signature lives in a memoized seed that every call in the file reads. A
 * structural check wires edges into the demand's own nodes, so checking an argument against the
 * seed's node would let one call rewrite the signature for the rest. Each argument therefore meets a
 * fresh demand, built here, and nowhere else.
 */
internal class LuaSignatureCopier(
    private val graph: LuaTypeGraph,
) {
    /**
     * A fresh `declaredDemand` use for [param] at [argument], demanding the parameter's declared
     * demand ([LuaTypeAlgebra.parameterDemand]). Null when that demand holds a table, which is not
     * copied (MAINT-51 design §8).
     *
     * Wire the argument's node straight into it. A variable between the two would merge the
     * argument's reaching definitions into one write and check their union, where BUG-416 checks
     * each definition alone.
     */
    fun parameterUse(
        param: LuaGraphType.Function.Parameter,
        argument: PsiElement,
    ): UseNode? {
        val demand = detachedDemand(LuaTypeAlgebra.parameterDemand(param.node.read, param.isOptional)) ?: return null
        return graph.use(argument, demand, declaredDemand = true)
    }

    private fun detachedDemand(type: LuaGraphType): LuaGraphType?
    private fun detachedFunction(declared: LuaGraphType.Function): LuaGraphType.Function
    private fun detachedParamNode(shared: VariableNode): VariableNode

    companion object {
        fun carriesGraphNodes(type: LuaGraphType): Boolean
        fun positionsAreUnambiguous(declared: LuaGraphType.Function, count: Int): Boolean
    }
}
```

The functions after `parameterUse` move from `LuaTypesVisitor` (`:1063-1094`, `:1107-1126`,
`:1128-1158`) with their bodies and KDocs unchanged, except:
- `detachedParamNode` and `detachedFunction` read `graph` from the class.
- `carriesGraphNodes` and `positionsAreUnambiguous` lose `private`, so [[TYPE-14]] can call them.
- In `carriesGraphNodes`' KDoc, "[detachedDemand] copies function types instead (BUG-19)" stays as written.

`parameterUse` builds the one use node per checked argument that `demandDeclaredParams` builds today.

### 2.4 `LuaTypesVisitor`

- After `private val graph = LuaTypeGraph()` (`:23`), add `private val signatureCopier = LuaSignatureCopier(graph)`.
- Delete `demandTypeOf` (`:1096-1105`), and the functions §2.3 moves.
- `demandDeclaredParams` (`:1050-1061`) keeps its KDoc and becomes:

```kotlin
private fun demandDeclaredParams(
    declared: LuaGraphType.Function,
    argExprs: List<PsiElement>,
) {
    if (!LuaSignatureCopier.positionsAreUnambiguous(declared, argExprs.size)) return
    argExprs.forEachIndexed { index, argExpr ->
        val param = declared.params.getOrNull(index) ?: return@forEachIndexed
        val argNode = firstNode(unwrapExpression(argExpr)) ?: return@forEachIndexed
        val paramUse = signatureCopier.parameterUse(param, argExpr) ?: return@forEachIndexed
        graph.addEdge(argNode, paramUse)
    }
}
```

`takeUnless { it.isVararg }` goes: `positionsAreUnambiguous` has already returned for any vararg
signature.

### 2.5 One `nil` rule in `LuaTypeGraph`

**The rule.** A union value's `nil` arms are dropped before it is compared, in both checkers. A bare
`nil` value against a demand that does not admit `nil` is reported only when it is certain (the
BUG-416 rule), and is checked before the union-demand branch, so a union demand gets the same rule as
a single type. "Admits `nil`" is `isCompatible(LuaGraphType.Nil, demand)`, so there is no third
predicate.

1. `isCompatible`, the union-value arm (`:751-752`) becomes:

   ```kotlin
   value is LuaGraphType.Union ->
       LuaGraphType.Any in value.types ||
           LuaTypeAlgebra.informativeArms(value).all { isCompatible(it, use, ctx.deeper()) }
   ```

   Replace the comment above it with: "A gradual union (Any arm, BUG-397) is compatible with
   everything. A nil arm is optionality, not evidence (BUG-416), exactly as in checkCompatibility."

2. `checkCompatibility`, union-value branch: `val informative = valueType.types.filter { it != LuaGraphType.Nil }`
   (`:553`) becomes `val informative = LuaTypeAlgebra.informativeArms(valueType)`. The "must not
   ENABLE" condition (`:574`) `!isCompatible(valueType, useType, CompatContext())` becomes
   `!isCompatible(LuaGraphType.Nil, useType, CompatContext())`. Append to its comment: "Asked of the
   nil arm alone because isCompatible now forgives it too; a demand that admits nil is the one that
   propagated before."

3. `checkCompatibility`: delete the `Nil` branch at `:683-700`, comment included, and insert
   immediately before `if (useType is LuaGraphType.Union) {` (`:597`):

   ```kotlin
   // BUG-416: only a CERTAIN nil is a defect — a literal operand (`nil .. s`) or a variable whose
   // sole reaching definition is nil. Checked before the union-demand branch so that a union demand
   // forgives an uncertain nil exactly as a single type does (MAINT-51).
   if (valueType == LuaGraphType.Nil && !isCompatible(LuaGraphType.Nil, useType, CompatContext())) {
       if (certain) {
           reportIncompatible(
               useElement,
               valueElement,
               "nil value is not assignable to ${useType.displayName()}",
               declaredDemand,
               valueType.displayName(),
               unknownProvenance,
           )
       }
       return
   }
   ```

   The deleted branch is unreachable after the insertion: every `Nil` value whose demand does not
   admit `nil` returns here, and a demand that admits it is either `Nil` (returned by `valueType ==
   useType`) or a union that the union-demand branch accepts.

A certain `nil` against a union demand changes wording, from *nil is not assignable to union
number | string* to *nil value is not assignable to number | string*, and is no longer also anchored
at the in-file declaration (DR-02, TC-12). That is the single-type wording, which is the point.

Step 1 also changes what the type goldens display; the rows are in `risks-and-gaps.md`. The corpus
findings do not move (DR-07).

`checkCompatibility` is over the contract's 30-line cap at `dccb8b18`. §2.5 moves its `nil` block and
does not lengthen it.

## 3. Threading and contract

No `plugin.xml` change, extension point or service. `LuaSignatureCopier` is a plain class owned by the
visitor and holds the visitor's graph, which lives no longer than the snapshot build. No function
gains a fourth argument; `parameterUse` has 2 logic lines.

## 4. Tests

New `src/test/kotlin/net/internetisalie/lunar/lang/types/LuaDeclaredSignatureTest.kt`:
`@RunWith(JUnit4::class) class LuaDeclaredSignatureTest : IndexedBasePlatformTestCase()`, one `@Test`
per TC in `requirements.md` from TC-01 to TC-16.

```kotlin
private val defs =
    """
    ---@param a string
    ---@param s? string
    function opt2(a, s) end
    ---@param fn fun()
    function take(fn) end
    ---@param n number
    function takeNumber(n) end
    ---@param v string|number
    function takeSN(v) end
    ---@param xs string[]|number
    function takeArrOrN(xs) end
    ---@class C
    local C = {}
    ---@param a string
    ---@param b? string
    function C:m(a, b) end
    """.trimIndent()

private fun errors(caller: String): List<String> {
    myFixture.addFileToProject("defs.lua", defs)
    myFixture.enableInspections(LuaTypeAssignabilityInspection())
    myFixture.configureByText("t.lua", caller)
    myFixture.doHighlighting()
    return LuaTypesSnapshot
        .forFile(myFixture.file as LuaFile)
        .getErrors()
        .map { "${it.severity}:${it.message}@${it.element.text.take(20).replace("\n", " ")}" }
        .distinct()
        .sorted()
}
```

Each test is `assertEquals(<expected list>, errors(<input>))`, with the input and expected list of its
TC. TC-03's expected list is `[]`. TC-06, TC-08 and TC-09 list their entries in sorted order.

## 5. What TYPE-14 calls

[[TYPE-14]] design §5.8 step 1 copies a non-generic parameter as "a fresh `p''` carrying
`graph.use(call, p.node.read, declaredDemand = true)` only when `!carriesGraphNodes(p.node.read)`",
and step 2 wires each argument into `p''`. Built that way it would:
- drop the `?` rule, which is mutation M1 and turns TC-01 red;
- skip every `fun()` demand, the pre-BUG-19 behaviour [[BUG-19]] §2 measured;
- put a variable between the argument and its demand, which DR-05 measured as two corpus false
  positives.

MAINT-51-05 replaces that clause in TYPE-14's documents:

- `docs/features/type/14-generics-baseline/design.md` §5.8 step 1: replace from "a non-generic
  parameter becomes" to "rather than copied);" with "a non-generic parameter becomes a fresh, untyped
  `p'' = graph.variable(call)`, because step 2 checks its argument against [[MAINT-51]]'s
  `LuaSignatureCopier.parameterUse` (which applies the `?` rule and copies function demands) and
  never wires it into `p''`;".
- The same §5.8, step 2: replace "if `positionsAreUnambiguous(declared, argExprs.size)`, for each `i`:
  `graph.addEdge(argNodes[i], inst.function.params[i].node)`;" with "if
  `LuaSignatureCopier.positionsAreUnambiguous(declared, argExprs.size)`, for each `i`: when
  `containsGeneric(declared.params[i].node.read)`, `graph.addEdge(argNodes[i], inst.function.params[i].node)`;
  otherwise `copier.parameterUse(declared.params[i], argExprs[i])?.let { graph.addEdge(argNodes[i], it) }`;".
- The same design, §5.4 step 2: replace "the same visibility change §5.8 makes for
  `positionsAreUnambiguous` and `carriesGraphNodes`" with "as [[MAINT-51]] did for
  `LuaSignatureCopier.positionsAreUnambiguous`".
- The same design, §4.4: `internal class LuaGenericInstantiation(private val graph: LuaTypeGraph)`
  becomes `internal class LuaGenericInstantiation(private val graph: LuaTypeGraph, private val copier: LuaSignatureCopier)`,
  and "One instance per `LuaTypesVisitor` (it owns the graph)." becomes "One instance per
  `LuaTypesVisitor`, built from its graph and its `LuaSignatureCopier`."
- `implementation-plan.md` (TYPE-14) "`LuaTypesVisitor` holds one `LuaGenericInstantiation(graph)`."
  becomes "`LuaTypesVisitor` holds one `LuaGenericInstantiation(graph, signatureCopier)`."
- `implementation-plan.md` Phase 2 task 1: replace "Change `arrayElementType`, `positionsAreUnambiguous`
  and `carriesGraphNodes` in `LuaTypesVisitor` from `private` to `internal` and call them" with
  "Change `arrayElementType` in `LuaTypesVisitor` from `private` to `internal`, and call it and
  `LuaSignatureCopier.positionsAreUnambiguous`". Task 2: replace "reusing `positionsAreUnambiguous`
  and `carriesGraphNodes`" with "reusing `LuaSignatureCopier`".
- The other mentions of `positionsAreUnambiguous` in TYPE-14's documents name the rule, not where the
  function lives, and stay.

A non-generic argument then meets the demand `demandDeclaredParams` gives it (§2.4), through the same
call. TYPE-14's returns are outside this feature (requirements, Out of Scope).

## 6. Mutations

Each is applied to the code after §2. Each ran as the prototype variant of the same name (DR-07).

| ID | Mutation |
|:--|:--|
| M1 | `parameterDemand` returns `declared` always |
| M2 | in `demandDeclaredParams`, `graph.addEdge(argNode, param.node)` (the seed's own node) in place of the `parameterUse` edge |
| M6 | `detachedDemand` returns its argument unchanged |
| M7 | `demandDeclaredParams` wires `argNode` into a fresh `graph.variable(argExpr)` and that variable into `parameterUse`'s result |
| N1 | `isCompatible`'s union arm iterates `value.types` instead of `informativeArms(value)` |
| N2 | the §2.5 step 3 block is removed and the old `Nil` branch restored after the union-demand branch |
| N4 | `informativeArms` returns `emptyList()` |
| N5 | in the §2.5 step 3 block, `if (certain)` becomes `if (false)` |

## 7. Requirement coverage

| Requirement | Priority | Implemented by |
|:--|:--|:--|
| MAINT-51-01 | M | §2.1, §2.2, §2.3 `parameterUse` |
| MAINT-51-02 | M | §2.1 `informativeArms`, §2.5 steps 1-2 |
| MAINT-51-03 | M | §2.5 step 3 |
| MAINT-51-04 | M | §2.3, §2.4 |
| MAINT-51-05 | M | §5 |
| MAINT-51-06 | S | implementation plan, Verification |
| MAINT-51-07 | W | §8 |

## 8. Alternatives considered

- **Copy table demands too.** Prototyped as variant T (DR-06): each member of the class gets a fresh
  node carrying its declared type. Its time cost is within run-to-run noise on the corpus, but it adds
  untriaged findings (DR-06). Most read as an array
  against a `table` demand ([[BUG-481]]). [[MAINT-54]] measures what the checker
  silently skips, which is the evidence this decision needs.
- **A fresh variable per argument, carrying the use** (the shape TYPE-14 §5.8 planned, variant U).
  It merges the argument's reaching definitions into one write and checks their union, which put two
  false positives on the corpus (DR-05). Mutation M7 restores it.
- **Let a `nil` arm enable structural propagation** (`checkCompatibility`'s "must not ENABLE" left
  calling the forgiving `isCompatible`, variant N3). Identical on every probe case (DR-03) and
  unmeasured on the corpus. Rejected because it changes which member edges exist, which is not what
  this feature is for.
- **A dedicated "admits nil" predicate.** `LuaTypeAlgebra.isOptional` answers a field question and is
  MAINT-52's; `isCompatible(Nil, demand)` already answers this one without a third copy.
- **Keep `demandTypeOf` and have it call `parameterDemand`.** It would survive as a one-line wrapper
  with a second name for the same rule.

## 9. Open Questions

None.
