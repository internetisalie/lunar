---
id: "TYPE-13-DESIGN"
title: "13: Design — carrying a declaration through structural member resolution"
type: "design"
parent_id: "TYPE-13"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-13 Design

## 1. What changes, in one paragraph

`LuaGraphType.Table` already resolves members structurally, including through `setmetatable`'s
`__index` supertype. What it does not do is say **where the member was declared**, because
`tableToLuaType` constructs every structural `LuaTypeMember` without a `sourceElement`. This design
marks each member node at its **mint site** as binding a declaration or a use, recovers the
declaration node from the winning node's `upSet` when a merge dropped it, and populates
`sourceElement` from that — preferring a nominal member's element where one already exists. **No
merge is changed and no type expression is changed**, so a member's `type` is computed from exactly
the node it is computed from today.

## 2. Constraints examined rather than assumed

| Constraint | Assumed or chosen | Verdict |
| :-- | :-- | :-- |
| "Un-annotated receivers need class inference" | assumed, from `REFACT-01-00-DR-03` | **Removed.** DR-03 measured the nominal route only. DR-01 shows the plain-table and global-table shapes resolve structurally with a real declaration node. |
| "The member map must key on a class name" | inherited from the nominal path | **Removed.** `LuaGraphType.Table.getMembers()` is name-free and already merges supertypes. |
| "`LuaTypeMember` needs a new field for the declaration" | assumed | **Removed.** `sourceElement: PsiElement?` exists ([LuaType.kt:26](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaType.kt)) and is what `LuaOverrideLineMarkerProvider` already consumes. |
| "`LuaTypeMember` needs a *provenance discriminator* alongside `sourceElement`" | assumed | **Removed, and it carried no information.** At every site this design specifies, "has a declaration" and `sourceElement != null` are the same predicate: §4.3 and §4.4 set both from the same value, the four nominal sites always pass a real element, and `TypeParser.kt:110` passes neither. No site can set `sourceElement` without a declaration. An enum would have added a field to a `data class` built at every member site and changed `equals`/`hashCode` to encode a fact Kotlin's nullability already encodes. The mint-site mark (§3.2) is the part that carries real information and it stays. |
| "A node's kind can be read back from its PSI class" | assumed | **Removed, and it was wrong.** `function t.m()` mints a `LuaFuncNameProperty`, `t.m = f` a `LuaIndexExpr`, a constructor field a `LuaField`; and a `LuaIndexExpr` is a declaration or a use depending on its position. The mark is a property of the **mint site**, which the engine owns — §3.2. |
| "An index expression on an assignment's left-hand side declares the member it names" | assumed | **Removed — false whenever any step precedes it.** The graph anchors *every* suffix on the bare head of the `var` ([LuaTypesVisitor.kt:1189-1194](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt) says so in its own comment), so both `t.a.m = f` and `t().m = f` mint a member `m` **on `t`**, and marking either a declaration would offer an unrelated statement as the declaration of `t.m`. §3.3 replaces the assumption with property (P) — no navigation step between the head and the index expression — and closes the predicate over the grammar's two step kinds rather than over a list of shapes. |
| "The merge that drops the declaration must be fixed" | assumed | **Removed.** The merges that drop it — `LuaTypesSnapshot.typeOf` and `LuaGraphType.Table.getMembers()` — feed the checker at [LuaTypeGraph.kt:763 and :850](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraph.kt) plus `handleSetMetatable`'s helpers — the widest blast radius available. DR-05 measured the declaration reachable from the winner's `upSet` instead, so neither merge is touched (§4.2). |
| A member has exactly one node | assumed by each merge | **Fixed constraint, and the defect.** A name can have a declaration node and a demand node; the merges keep one. Recovering the other through `upSet` is the response. |
| `LuaUnionType` rebuilds members and drops `sourceElement` | inherited | **Fixed constraint here, partially addressed — and the reachable half is measured, not assumed.** The class has two entry points with different reach. `resolveMember` needs every arm to carry the name, which `---@param p A|B`, `---@return A|B` on a function whose result is assigned, and `---@type A|B` over two declaring `---@class` types all satisfy (each measured HIT at `09bd4b6f`; the last is requirements case 17). `getMembers` unions the arms instead of intersecting them, so it also carries a name only one arm has — including the ordinary `---@class` local's `setName`. §4.4 preserves the field on both. Making `resolveMember` resolve a name only some arms carry stays out of scope (Gap 2.3, which enumerates the measured reach of both entry points). |

## 3. Data model

### 3.1 No new type is introduced

The declaration answer is `LuaTypeMember.sourceElement != null`, and the handle a consumer wants is
`LuaMemberDeclarations.declarationOf(member)` (§4.5), which returns null for exactly the members
whose `sourceElement` is null. There is no new enum and no new field on `LuaTypeMember` — see §2's
premise table for why a member-level discriminator carries no information this design does not
already have.

The one new *type-level* addition is a boolean on the graph node, §3.2, which is where the
information actually exists.

### 3.2 `VariableNode.declaresMember` (new) — the mint-site mark

[LuaTypeNodes.kt](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeNodes.kt):
add to `interface VariableNode` (line 72):

```kotlin
    /**
     * True when this node was minted to bind a member name that Lua source DECLARES, rather than
     * one a use site demands. Set at the mint site (TYPE-13 design §3.2), never inferred from
     * [TypeNode.element]'s PSI class: `function t.m()` and `t.m = …` mint different classes, and a
     * `LuaIndexExpr` is a declaration or a use depending on its position.
     */
    val declaresMember: Boolean get() = false
```

`internal class VariableElement` (line 119) gains it as a constructor property:

```kotlin
internal class VariableElement(
    override val element: PsiElement,
    private val graph: LuaTypeGraph,
    override val declaresMember: Boolean = false,
) : VariableNode {
```

`LuaTypeGraph.variable` ([LuaTypeGraph.kt:144](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraph.kt))
gains the matching defaulted parameter, so its other call sites compile unchanged:

```kotlin
    fun variable(
        element: PsiElement,
        declaresMember: Boolean = false,
    ): VariableNode {
        val node = VariableElement(element, this, declaresMember)
        _nodes += node
        bumpRevision()
        return node
    }
```

**The mint sites.** A `VariableNode` becomes a member only by being placed as a value in a
`LuaGraphType.Table.localMembers` map. Every site that does so, and what it passes:

| site | Lua form | element class it anchors | `declaresMember` |
| :-- | :-- | :-- | :-- |
| `LuaTypesVisitor.kt:754` (`visitTableConstructor`) | `local t = { m = … }` | `LuaField` | `true` |
| `LuaTypesVisitor.kt:832` (`visitFuncDecl`, `funcNamePropertyList` loop) | `function t.m()` | `LuaFuncNameProperty` | `true` |
| `LuaTypesVisitor.kt:848` (`visitFuncDecl`, `funcNameMethod`) | `function t:m()` | `LuaFuncNameMethod` | `true` |
| `LuaTypesVisitor.kt:927` (`visitFuncCall`) | `o:m()` | `LuaMethodExpr` | `false` (default) |
| `LuaTypesVisitor.kt:1198` (`visitIndexExpr`) | `t.m = …`, a suffix of `t.a.m = …`, **or** a read of `t.m` | `LuaIndexExpr` | `isAssignmentTarget(o)` — §3.3 |
| `LuaGraphType.kt:391` (`memberNodeFor`, the scratch graph built from a nominal `LuaType`) | — | the caller's `anchor` | `false` (default) |

That list is closed and checkable: `grep -rn "localMembers" src/main/kotlin` names every map, and
each map's values come from one of the `graph.variable(...)` calls above. The remaining sites that
*build* a member map — `LuaTypesVisitor.kt:176` (`mergedTableOf`), `LuaTypeNodes.kt:256`
(`mergeTableDemands`) and `LuaGraphType.kt:200` (`Table.getMembers`) — copy nodes minted above and
mint none of their own. `LuaGraphType.kt:286` (`LuaTableLiteralType`) and `:378` (`classTable`) both
route through `memberNodeFor` at `:391`.

**`memberNodeFor`'s node is not inert, and §4.2's walk does not stop at it.** Measured on the
`---@class Builder` fixture at `3e151d4c` (requirements DR-05c): the member node for `setName` is a
`VariableElement` anchored on `LuaFile@0`, and its `upSet` contains
`LuaFuncNameMethodImpl@53` — the real `:setName` declaration node — at depth 1, because
`seedDeclaredMember` wires the declaration into the seeded member. `declaringNodeOf` therefore
returns a genuine declaration for an annotated receiver, and §4.3's nominal-preservation rule, not
an absence of edges, is what keeps the nominal element in place.

### 3.3 `isAssignmentTarget` — the one mint site that is not constant

`LuaTypesVisitor.kt:1198` mints the node for `t.m` in `t.m = f`, in `t.a.m = f`, in `t().m = f`, and
in `print(t.m)`. Two facts decide the predicate:

1. A `LuaVar` is a child of a `LuaVarList` only inside an assignment's left-hand side
   ([LuaAssignmentStatement.getVarList](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaAssignmentStatement.java),
   [LuaVarList.getVarList](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaVarList.java)).
2. The node is anchored on the `var`'s **bare head**: `LuaTypesVisitor.kt:1182` computes the receiver
   as `firstNode(unwrapExpression(varElement.firstChild))`, and the comment at `:1189-1194` states
   that every suffix is anchored there. So the node minted for the `.m` of `t.a.m = f` — and equally
   of `t().m = f` — is a member **of `t`**, while the statement declares a member of something else.

#### The property the predicate must guarantee

> **(P)** The node minted at `:1198` may claim a declaration only when the assignment writes the
> name `o` carries **directly on the node's anchor** — that is, when **no navigation step stands
> between the `var`'s head and `o`**.

(P) is the whole requirement, and it is decidable from the grammar rather than from a list of Lua
shapes. [lua.bnf:292-294](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf):

```
var ::= nameRef varSuffix*
    | '(' expr ')' varSuffix+
varSuffix ::= nameAndArgs* indexExpr
```

The steps a `var` applies to its head are therefore exactly the flattened sequence of `nameAndArgs`
(a **call** step) and `indexExpr` (an **index** step) children of its `varSuffixList`. The production
admits no third step kind, so (P) holds **iff** the `var` has exactly one `varSuffix`, that suffix
carries no `nameAndArgs`, and its `indexExpr` is `o`. Those clauses close over the step alphabet:
any `var` the grammar can build is a sequence of call and index steps, and a `var` with a step
before `o` fails one of them.

**Both step kinds must be tested, and counting suffixes tests only one.** A `varSuffix` bundles a
run of call steps with the index step that ends it, so suffix *count* bounds the index steps and says
nothing about the call steps inside the sole suffix: `t().m = f` is **one** `varSuffix` whose
`nameAndArgsList` is `[()]`. The `nameAndArgsList.isEmpty()` clause is not a patch for that Lua
shape — it is the second half of the alphabet, and it covers `t()().m`, `t(1)(2).m` and any other
call-prefixed sole suffix by the same closure.

```kotlin
    private fun isAssignmentTarget(o: LuaIndexExpr): Boolean {
        val enclosingVar = PsiTreeUtil.getParentOfType(o, LuaVar::class.java) ?: return false
        if (enclosingVar.parent !is LuaVarList) return false
        val soleSuffix = enclosingVar.varSuffixList.singleOrNull() ?: return false
        return soleSuffix.nameAndArgsList.isEmpty() && soleSuffix.indexExpr === o
    }
```

placed as a private member of `LuaTypesVisitor`. `LuaVarSuffix.getIndexExpr()` and
`LuaVarSuffix.getNameAndArgsList()` are both `@NotNull`
([LuaVarSuffix.java](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaVarSuffix.java)),
and the `varSuffixList…indexExpr` comparison is already the file's own idiom a few lines above at
`LuaTypesVisitor.kt:1195`.

**Executed**, at `137a2a5a` with §3.2–§4.5 applied as a throwaway prototype and the predicate
toggled between the two forms in one run. Each row resolves `m` (or `a`) on the named receiver and
prints the member node, its `declaresMember`, the resulting `sourceElement`, and `declarationOf`:

| fixture (receiver.member) | with the `nameAndArgs` clause **absent** | with it **present** |
| :-- | :-- | :-- |
| `local t = {}` ; `t().m = function() end` → `t.m` | node `LuaIndexExpr@16`, `declaresMember=true`, src `LuaIndexExpr@16`, `declarationOf=LuaAssignmentStatement@13` | `declaresMember=false`, **src `null`**, `declarationOf null` |
| `local M = setmetatable({}, { __call = function() return {} end })` ; `M().m = function() end` → `M.m` | `declaresMember=true`, src `LuaIndexExpr@69`, `declarationOf=LuaAssignmentStatement@66` | `declaresMember=false`, **src `null`** |
| `function g(p)` ; `  p().m = function() end` ; `  return p.m` ; `end` → `p.m` | `declaresMember=true`, src `LuaIndexExpr@19`, `declarationOf=LuaAssignmentStatement@16` | `declaresMember=false`, **src `null`** |
| `Cfg = {}` ; `Cfg().m = function() end` → `Cfg.m` | `declaresMember=true`, src `LuaIndexExpr@14`, `declarationOf=LuaAssignmentStatement@9` | `declaresMember=false`, **src `null`** |
| `local t = {}` ; `t()().m = function() end` → `t.m` | `declaresMember=true`, src `LuaIndexExpr@18`, `declarationOf=LuaAssignmentStatement@13` | `declaresMember=false`, **src `null`** |
| **control** `local t = {}` ; `t.m = function() end` → `t.m` | `declaresMember=true`, src `LuaIndexExpr@14`, `declarationOf=LuaAssignmentStatement@13` | **unchanged** — `true`, src `LuaIndexExpr@14`, `LuaAssignmentStatement@13` |
| **control** `local t = {}` ; `t.a = {}` ; `t.a.m = function() end` ; `t.a.m()` → `t.a` | `declaresMember=true`, src `LuaIndexExpr@14`, `declarationOf=LuaAssignmentStatement@13` | **unchanged** |
| **control** the same fixture → `t.m` | `declaresMember=false`, src `null` | **unchanged** |
| **control** `local t = {}` ; `t.a = {}` ; `t.a().m = function() end` → `t.m` | `declaresMember=false`, src `null` (two suffixes) | **unchanged** |
| **control** `local t = {}` ; `function t:m() end` ; `t:m()` → `t.m` | node `LuaFuncNameMethod@23`, src `LuaFuncNameMethod@23`, `declarationOf=LuaFuncDecl@13` | **unchanged** |

Only the call-prefixed rows move, and every accepting control is untouched, so the clause narrows
exactly the set (P) names.

**The parenthesised head is out of reach on both sides of the change, measured.** For
`local t = {}` ; `(t).m = function() end`, `varElement.firstChild` is the `(` token, `firstNode`
returns null, and `:1197`'s `if (receiverNode == null) return` mints no member node at all — the
probe prints `node=none resolveMember=MISS` under both predicates. (P) is satisfied there anyway:
`.m` is the sole step on the parenthesised head.

The index-step half of (P), measured at `3e151d4c` on
`local t = {}` ; `t.a = {}` ; `t.a.m = function() end` ; `t.a.m()`:

| index expression | text | left-hand side | single `varSuffix` | `isAssignmentTarget` |
| :-- | :-- | :-- | :-- | :-- |
| `LuaIndexExprImpl@14` | `.a` (in `t.a = {}`) | yes | yes | **true** |
| `LuaIndexExprImpl@23` | `.a` (in `t.a.m = …`) | yes | no | false |
| `LuaIndexExprImpl@25` | `.m` (in `t.a.m = …`) | yes | no | false |
| `LuaIndexExprImpl@46` | `.a` (in `t.a.m()`) | no | — | false |
| `LuaIndexExprImpl@48` | `.m` (in `t.a.m()`) | no | — | false |

and on the step-free control `local t = {}` ; `t.m = function() end` ; `t.m()`, the `.m` at
offset 14 is **true** and resolves to `LuaAssignmentStatementImpl@13`. Both `.__index` targets in
the `setmetatable` fixtures are single suffixes carrying no call step and stay **true**.

### 3.4 `LuaTypeMember` is unchanged

[LuaType.kt:21-27](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaType.kt)
keeps its current shape. What changes is which sites populate `sourceElement`:

| site | passes a declaration today | change |
| :-- | :-- | :-- |
| `LuaTypeManagerImpl.kt:357` (`materializeClass`, `@field`/implicit members) | `sourceElement = member.tag ?: decl` | none |
| `LuaTypeManagerImpl.kt:413` (`materializeUnhostedClass`) | `sourceElement = member.tag ?: tag` | none |
| `LuaTypeManagerImpl.kt:561` (inside `addMethodsOf`, which `collectMethodMembers` at `:513` calls) | `sourceElement = decl` (a `LuaFuncDecl`) | none |
| `LuaImplicitFields.kt:143` | `sourceElement = luaVar` | none |
| `LuaTypes.kt:178`, `:185` (`tableToLuaType`) | nothing | rewritten by §4.3 |
| `TypeParser.kt:110` (members of a parsed `table<…>` / inline table annotation) | nothing — there is no per-member PSI | unchanged; stays null |
| `LuaComplexTypes.kt:16`, `:27` (`LuaUnionType`) | nothing — members are rebuilt | rewritten by §4.4 |

Every nominal site above needs no edit at all, which is the practical consequence of §2's decision to
drop the enum: this feature touches the nominal route's *ordering* (§4.3), never its construction.

### 3.5 The declaration handle

New file
`src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaMemberDeclarations.kt`, holding
`object LuaMemberDeclarations` with the functions specified in §4.2 and §4.5. It is a top-level
object in the same package as `LuaTypes` and `LuaTypeManagerImpl`. **Its only production caller
inside this feature is `LuaTypesSnapshot.putGraphMember` (§4.3), on the structural route** — no
`LuaTypeManagerImpl` site calls it or is edited at all, and §3.4's table records that every nominal
`sourceElement` site is unchanged. `declarationOf` (§4.5) is exercised directly by this feature's
Phase 2 tests and is `public` for `REFACT-09`, the first caller outside this package.

## 4. Algorithm

### 4.1 Where the declaration mark comes from

Nowhere but §3.2. There is no function that classifies a node after the fact.

### 4.2 `declaringNodeOf` — recovering a node a merge dropped

```kotlin
    /**
     * The nearest node bound to this member that was minted at a declaration site, or null.
     *
     * Reads [VariableNode.upSet] edges only — never `write`, `read` or `declaredDemand`, each of
     * which opens a resolution walk root and would be charged against BUG-473's budget.
     */
    internal fun declaringNodeOf(node: VariableNode): VariableNode? {
        val visited = mutableSetOf<TypeNode>()
        var frontier = listOf<TypeNode>(node)
        while (frontier.isNotEmpty()) {
            val next = mutableListOf<TypeNode>()
            for (candidate in frontier) {
                if (visited.size >= MAX_VISITED) return null
                if (!visited.add(candidate)) continue
                if (candidate is VariableNode) {
                    if (candidate.declaresMember) return candidate
                    candidate.upSet.forEach { next += it }
                }
            }
            frontier = next
        }
        return null
    }

    private const val MAX_VISITED = 64
```

`internal` is deliberate and sufficient: the Gradle Kotlin test compilation is associated with `main`,
so the test source set sees it — `LuaTypeGraphRootResolutionBudgetTest` already calls
`LuaTypesSnapshot.rootResolutionCount`, which is `internal` for the same reason. `declarationOf`
(§4.5) is the public surface `REFACT-09` consumes.

Every property below must survive a reimplementation:

1. **Breadth-first, starting at `node` itself.** In several of the shapes DR-05a measured the winning
   node *is* the declaration; a walk that skipped the start node would miss every one of them.
2. **`visited` is identity-based, and it is load-bearing.** `ValueElement`, `LazyValueElement`,
   `UseElement` and `VariableElement` are plain classes with no `equals` override, so
   `mutableSetOf<TypeNode>()` is identity semantics. Measured at `3e151d4c`: an `upSet` walk over the
   `A.__index = B ; B.__index = A ; function B:m() end` fixture's winning node, run **without** the
   de-duplication guard, re-encountered the same 5 distinct nodes until a 5 000-step probe cap
   stopped it; the annotated `Builder` fixture behaved the same over 7 distinct nodes. The guard is
   what makes those calls return.
3. **`MAX_VISITED = 64` is a cost bound, not a correctness one, and exhausting it refuses.** Measured
   sizes of `visited` at return, at `3e151d4c`:

   | fixture | `visited` at return |
   | :-- | :-- |
   | `local t = {}` ; `function t:m() end` ; `t:m()` | 1 |
   | `Obj = {}` ; `function Obj:m() end` ; `Obj:m()` | 1 |
   | `local t = {}` ; `t.m = function() end` | 1 |
   | `setmetatable` OO | 2 |
   | `A.__index = B` ; `B.__index = A` | 2 |
   | `---@class Builder` (the seeded member node) | 3 |
   | every shape that reports no declaration | 1 |

   No measured shape approaches the bound. It exists so an unmeasured graph cannot make the walk
   expensive, and a hand-built chain longer than the bound is what tests it (requirements case 12).
4. **Only `upSet` is followed.** `downSet` leads to consumers of the member, not to its definition.

### 4.3 `tableToLuaType`

[LuaTypes.kt:174-187](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypes.kt).
Both branches currently end with the same `forEach`; both are replaced by a call to one new private
member of `LuaTypesSnapshot`, placed beside `tableToLuaType` — it must live there because it calls
the private `graphTypeToLuaType(type, visited)` overload — so the branches cannot drift:

```kotlin
    private fun putGraphMember(
        members: MutableMap<String, LuaTypeMember>,
        entry: Map.Entry<String, VariableNode>,
        visited: MutableMap<LuaGraphType, LuaType>,
    ) {
        val (name, node) = entry
        val graphType = graphTypeToLuaType(node.write, visited) // unchanged: same node, same expression
        // TYPE-13-06: in the className branch a nominal member is already present and its element is
        // authoritative — annotations beat inference, the rule visitIndexExpr states at :1183. The
        // graph member still wins on TYPE, which is the pre-existing "graph members win" rule.
        val existing = members[name]
        members[name] =
            if (existing?.sourceElement != null) {
                existing.copy(type = graphType)
            } else {
                LuaTypeMember(
                    name,
                    graphType,
                    sourceElement = LuaMemberDeclarations.declaringNodeOf(node)?.element,
                )
            }
    }
```

Call site in both branches: `type.getMembers().forEach { putGraphMember(members, it, visited) }`.
In the `className == null` branch `members` is empty when the loop starts, so `existing` is always
null there and the guard is inert — the same code is correct in both places.

**The guard is unconditional on the nominal side, and it has to be.** A guard of the form *"only
when the graph route found nothing"* does not fire on the `---@class Builder` fixture at all,
because `declaringNodeOf` returns `LuaFuncNameMethodImpl@53` there (requirements DR-05c). Executed
at `3e151d4c` with the guard as written above:

```
TC8  ARM LuaClassType 'Builder' -> HIT src=LuaFuncDeclImpl@37  declarationOf=LuaFuncDeclImpl@37
TC8b ARM LuaClassType 'Box'     -> member 'lid'  src=LuaCatsFieldTagImpl@17
                                   member 'open' src=LuaFuncDeclImpl@50
```

and with the guard deleted:

```
TC8  ARM LuaClassType 'Builder' -> HIT src=LuaFuncNameMethodImpl@53
TC8b ARM LuaClassType 'Box'     -> member 'lid'  src=null
                                   member 'open' src=LuaFuncNameMethodImpl@62
```

So the guard covers a nominal element replaced by a **coarser** one (`LuaFuncDecl` →
`LuaFuncNameMethod`, which `LuaOverrideLineMarkerProvider.kt:82` tests by class) as well as a
nominal element replaced by **nothing** (`@field`, whose graph member has no declaring node).

**Which element is normative.** For a member built here, the **raw `sourceElement`** is the
normative answer and is what a test asserts; `declarationOf` is a derivation over it. They agree
on the annotated fixture — `declarationOf(LuaFuncDecl@37)` falls to `else -> anchor` and returns
`@37` — but they are not interchangeable in general: on the plain-table fixture `sourceElement` is
`LuaFuncNameMethod@23` and `declarationOf` is `LuaFuncDecl@13`. `LuaOverrideLineMarkerProvider.kt:82`
(`sourceElement !is LuaFuncDecl`) is an existing reader that depends on `sourceElement`'s class, so
the field's value is a contract, not an implementation detail.

**This changes no type.** The `type` argument is the identical expression it is at `3e151d4c`, over
the identical node, so `TYPE-13-10` holds by construction. One deliberate widening comes with
`existing.copy(type = graphType)`: the previous line rebuilt the member with the two-argument
constructor and so discarded the nominal member's `visibility` and `description` as well as its
`sourceElement`. `copy` preserves all three. That is strictly more information on a member whose
type is unchanged, and no reader of either field is on this route (§5.1).

### 4.4 `LuaUnionType` must stop discarding `sourceElement`

[LuaComplexTypes.kt:8-29](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaComplexTypes.kt)
builds fresh two-argument `LuaTypeMember`s, so a member resolved through a union loses
`sourceElement` today.

**The shapes that reach this code** divide by entry point; Gap 2.3 enumerates them with the
measurement. `resolveMember` keeps the all-arms rule, so it is reached by a union every arm of which
carries the name: `---@param p A|B`, `---@return A|B` on a function whose result is assigned, and
`---@type A|B` over two `---@class` types that each declare the member. `getMembers` unions the arms
rather than intersecting them, so it is additionally reached by unions only one arm of which carries
the name — `---@type A|string`, a two-branch `if` assigning two annotated class locals, `---@type
A|B` where only `A` declares, and the ordinary `---@class` local, whose `getMembers()["setName"]` is
PRESENT. Both halves of this section therefore have production shapes behind them.

Taking the `---@type A|B` shape as the worked path: `TypeParser.parseUnionType`
([TypeParser.kt:53](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/TypeParser.kt))
turns `A|B` into a `LuaUnionType`, `LuaGraphType.fromLuaType`
([LuaGraphType.kt:294-301](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaGraphType.kt))
turns that into a `Union` of two `Table(className = …)` arms, and `graphTypeToLuaType`
([LuaTypes.kt:147-150](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypes.kt))
converts it back to a `LuaUnionType` of two `LuaClassType`s — **every** arm of which carries the
name, which is what satisfies the all-arms rule. Requirements case 17 is that fixture, executed on
both halves of this section.

The `---@class` local's union does **not** reach `resolveMember`: its bare `Table(className = null)`
arm carries no member, so `resolveMember` misses every name on it (Gap 2.3). It does reach
`getMembers`, which is the entry point that unions the arms.

**A production consumer already calls the method this section changes.**
`LuaParameterInlayHintsProvider.resolveMemberFromType`
([LuaParameterInlayHintsProvider.kt:155-168](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/hint/LuaParameterInlayHintsProvider.kt))
branches on `type is LuaUnionType` and calls `type.resolveMember(methodName)` before falling back to
the arms, and it is fed by `graphTypeToLuaType(getValueType(callee))` at `:118-121` where `callee` is
`LuaTypeInlayHintProvider.unwrapExpression(element.varOrExp)` — which walks single-child chains down
to the `LuaNameRef`, the same handle case 17 measures the union on. So this section is not a
test-only path.

`resolveMember` (`:8-17`) keeps its existing all-arms null rule and gains field preservation:

```kotlin
    override fun resolveMember(name: String): LuaTypeMember? {
        val members = types.mapNotNull { it.resolveMember(name) }
        if (members.size != types.size) return null
        return LuaTypeMember(
            name,
            LuaUnionType(members.map { it.type }.toSet()),
            sourceElement = members.firstNotNullOfOrNull { it.sourceElement },
        )
    }
```

`getMembers` (`:19-29`) needs its accumulator restructured, because the current one holds no
members — it is `MutableMap<String, MutableSet<LuaType>>`, which cannot carry a `sourceElement`
forward. Replace it with a second map keyed the same way:

```kotlin
    override fun getMembers(): Map<String, LuaTypeMember> {
        val typesByName = linkedMapOf<String, MutableSet<LuaType>>()
        val declarationByName = linkedMapOf<String, PsiElement>()
        for (type in types) {
            for ((name, member) in type.getMembers()) {
                typesByName.getOrPut(name) { mutableSetOf() }.add(member.type)
                member.sourceElement?.let { declarationByName.putIfAbsent(name, it) }
            }
        }
        return typesByName.mapValues { (name, typeSet) ->
            LuaTypeMember(
                name,
                if (typeSet.size == 1) typeSet.first() else LuaUnionType(typeSet),
                sourceElement = declarationByName[name],
            )
        }
    }
```

`LuaComplexTypes.kt` currently declares no imports; this adds `import com.intellij.psi.PsiElement`.
`putIfAbsent` fixes the arm order: the **first** arm that carries an element wins, matching
`resolveMember`'s `firstNotNullOfOrNull`. `linkedMapOf` keeps `types`' iteration order so "first" is
well defined; the existing `mutableMapOf` is already a `LinkedHashMap`, so this is the same order the
method has today.

**What this does not fix, measured:** for `---@class Builder ; local Builder = {} ; local b = Builder`,
the receiver's type is `LuaUnionType({ } | Builder)` and `resolveMember("setName")` returns **null**,
because the bare `LuaTableLiteralType` arm does not carry `setName` and the rule above requires every
arm to resolve. That is unchanged by this feature, and it is why the `---@class`-local test cases in
`requirements.md` go through `LuaTypeManagerImpl.resolveType` or through the union's `LuaClassType`
arm rather than through the union. Gap 2.3 owns it.

### 4.5 `declarationOf` — from the anchor to the declaring statement

```kotlin
    /**
     * The PSI a consumer should treat as this member's declaration, or null when it has none.
     * Requires a read action: this is a PSI parent walk.
     */
    fun declarationOf(member: LuaTypeMember): PsiElement? {
        val anchor = member.sourceElement ?: return null
        return when (anchor) {
            is LuaFuncNameMethod, is LuaFuncNameProperty ->
                PsiTreeUtil.getParentOfType(anchor, LuaFuncDecl::class.java, true)
            is LuaIndexExpr ->
                PsiTreeUtil.getParentOfType(anchor, LuaAssignmentStatement::class.java, true)
            else -> anchor
        }
    }
```

The `when` is over the element classes §3.2's declaration mint sites anchor, plus the nominal sites'
elements, which fall to `else`. Measured at `3e151d4c`:

| anchor | measured ancestry | returned |
| :-- | :-- | :-- |
| `LuaFuncNameMethod` | `LuaFuncNameMethod → LuaFuncName → LuaFuncDecl → LuaBlock → LuaFile` | the `LuaFuncDecl` |
| `LuaFuncNameProperty` | `LuaFuncNameProperty → LuaFuncName → LuaFuncDecl → LuaBlock → LuaFile` | the `LuaFuncDecl` |
| `LuaIndexExpr` (sole suffix of an assignment target) | `LuaIndexExpr → LuaVarSuffix → LuaVar → LuaVarList → LuaAssignmentStatement` | the `LuaAssignmentStatement` |
| `LuaField` | `LuaField → LuaFieldList → LuaTableConstructor → LuaExprList → LuaLocalVarDecl` | the `LuaField` itself — the name-bearing element |
| `LuaFuncDecl` (nominal, from `LuaTypeManagerImpl.kt:561`) | — | itself |
| `LuaCatsFieldTag` (nominal, from `LuaTypeManagerImpl.kt:357`) | — | itself |

The branches are ordered by anchor class, not tried in sequence against the ancestor chain. Trying
`LuaFuncDecl` first and falling back would be wrong: `t.m = f` written **inside** a function body has
an enclosing `LuaFuncDecl`, and a sequential walk would return that unrelated function.

`else` returning `anchor` is why an unlisted shape degrades to "the member's own declaring element"
and never to a wrong element or to a silent absence. The `LuaIndexExpr` branch can still return null
— measured on `print(t.m)`, where the anchor has no `LuaAssignmentStatement` ancestor — and null is
the refusing direction.

## 5. Registration

**No `plugin.xml` change.** This is engine-internal: no new class is an extension, and every class
touched is reached through the existing `LuaTypes` / `LuaType` interfaces. The one type-engine entry
already in `src/main/resources/META-INF/plugin.xml` is the project service at lines 569-570
(`serviceInterface=…LuaTypeManager`, `serviceImplementation=…LuaTypeManagerImpl`); this feature
changes neither that class's construction sites nor its registration.

### 5.1 Existing consumers of `sourceElement`, and why none moves

`sourceElement` goes from `null` to non-null for structural members, so every reader of it is blast
radius. `grep -rn "\.sourceElement" src/main/kotlin src/test/kotlin` at `137a2a5a` returns hits of
two kinds. The hits at `LuaCatsDeclarations.kt:27`, `LuaOverrideLineMarkerProvider.kt:88`,
`MemberEnumerationGoldenTest.kt:28` and `:117`, and `LuaCatsDeclarationsTest.kt:78` are **KDoc or
line comments that only mention the field** and read nothing at runtime. Every remaining hit is a
reader in the table below — the test source set included, because a golden file and a probe's regex
assertions are readers too:

| reader | route it resolves through | effect |
| :-- | :-- | :-- |
| `LuaOverrideLineMarkerProvider.kt:71` (`setTargets`) | `findSuperMembers` → `LuaTypeManager.resolveType` — nominal only, never `tableToLuaType` | none: those members' `sourceElement` is unchanged |
| `LuaOverrideLineMarkerProvider.kt:82` (`isAbstractMember`, `sourceElement !is LuaFuncDecl`) | same nominal route | none, same reason — and §4.3's guard is what keeps it that way on a class type built from the graph |
| `LuaDocumentationTargetProvider.kt:134` (`sourceElement as? LuaCatsFieldTag`) | `LuaTypeManager.resolveType` → `LuaClassType.resolveMember` — nominal only | none. Separately, §4.3's guard now carries a `LuaCatsFieldTag` **into** a graph-converted class type where the field was previously blanked (measured: `arm['lid'].src` `null` → `LuaCatsFieldTagImpl@17`), which is more information for any future reader on that route, never a different target |
| `MemberEnumerationGoldenTest.kt:133` (COMP-09 golden records the source PSI class and file) | `LuaOverrideLineMarkerProvider.findSuperMembers` — nominal only | none: the golden was regenerated-free in the prototype run |
| `LuaCatsStubAstParityTest.kt:443-449` (`sourceElement is LuaLocalVarDecl` / `is LuaCatsFieldTag`) | `LuaTypeManagerImpl.resolveType` — nominal only | none |
| `Type13Dr01StructuralReachProbe.kt:52` and `:79` (this feature's own DR-01 probe prints the class and offset into a report its `assertTrue` regexes then match) | line 52 is the **structural** route — `types.graphTypeToLuaType(...).resolveMember(...)`, i.e. exactly the route §4.3 changes; line 79 is the nominal route | **printed value changes and the assertions still hold.** **Every** regex in that probe matches on `resolveMember=HIT` or `MEMBER NODE element=…`; none matches on `sourceElement=`, which is the only field this feature moves on that line. Stated as a property rather than a count deliberately: Phases 1-2 add assertions beside these, and a number would go stale against the very tripwire the plan's Definition of Done calls safety-critical. Measured: the probe passes unmodified in the full-suite run below |

Measured rather than argued: the prototype of §3.2–§4.5, with §3.3's predicate in the form specified
above, ran the full unit suite on the builder at `137a2a5a`
(`test --rerun --no-build-cache`) — **2 891 tests over 463 classes, 0 failures, 0 errors** — with
every file above unmodified.

`LuaTypeMember` is a `data class`, and structural members' `sourceElement` moving from null to
non-null changes `equals`/`hashCode`. Measured at `3e151d4c`: `grep -rn "LuaTypeMember" src/main/kotlin
src/test/kotlin` finds no site that compares members by value — every use constructs, destructures or
reads a field. `copy` is used only by §4.3 itself.

## 6. Threading

`graphTypeToLuaType` already runs under the caller's read action and is reached from
`LuaTypesSnapshot.forFile`, which BUG-473 DR-7 established runs off the EDT on the daemon path.
`declaringNodeOf` reads graph node edges and touches no PSI, so it adds no requirement.
`declarationOf` is a `PsiTreeUtil` parent walk and needs a read action; it performs no I/O and takes
no new lock. `REFACT-09` is the first caller from a refactoring thread and owns establishing that
path's context.

Nothing here retains a `Project`, `Editor`, `PsiFile` or `VirtualFile`: `LuaTypeMember.sourceElement`
is a `PsiElement` held for the lifetime of a snapshot, exactly as the field is used today by
`LuaTypeManagerImpl` and `LuaOverrideLineMarkerProvider`.

## 7. Requirement → design map

| Requirement | Section |
| :-- | :-- |
| `TYPE-13-01` | §4.3 |
| `TYPE-13-02` | §3.1 (no discriminator field), §3.2 (the mint-site mark), §3.3 (the one non-constant site) |
| `TYPE-13-03` | §4.5 |
| `TYPE-13-04` | §3.2 (`declaresMember` defaults false), §3.3 (a mis-anchored suffix is refused), §4.2 (returns null when the walk finds nothing), §4.5 (returns null on a null anchor) |
| `TYPE-13-05` | §4.2 — the `upSet` walk recovers the supertype's declaration node the merges dropped |
| `TYPE-13-06` | §4.3 (the nominal-preservation rule and each measured half of it), §4.4 |
| `TYPE-13-07` | §4.2 properties 2 and 3 |
| `TYPE-13-08` | §4.2 — a required module's members do not reach the receiver's node, so the walk returns null and §4.3 leaves `sourceElement` null |
| `TYPE-13-09` | routing only; the nominal route already carries the declaration (DR-01 fixture E) |
| `TYPE-13-10` | §4.3 — the `type` expression is unchanged; verification gate, not a code path |
| `TYPE-13-11` | §4.2 — `upSet` only, node budget of 64; verification gate |

## 8. What this design does not do

- It does not mint a class name for an un-annotated receiver, and nothing here needs one.
- It does not change `LuaTypesSnapshot.typeOf`'s write/read merge or
  `LuaGraphType.Table.getMembers()`'s supertype merge, and so does not touch the checker at
  `LuaTypeGraph.kt:763`/`:850` or `handleSetMetatable`
  ([LuaTypesVisitor.kt:117](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt)).
- It does not change where a suffix of a chain is anchored. `t.a.m = f` still mints a member `m` on
  `t`; §3.3 only stops that member from claiming to be a declaration (Gap 2.8).
- It does not widen `upSet` reach to the factory-returned, `self`-receiver and nested-constructor
  shapes, which report no declaration (Gap 2.7).
- It does not touch the member-demand minting at `LuaTypesVisitor.kt:927` beyond leaving its
  `declaresMember` at the default. Removing the redundant demands is [[TYPE-12]].
- It does not make a union resolve a name only some arms carry (Gap 2.3).
- It does not decide `REFACT-09`'s completeness predicate. That is `TYPE-13-00-DR-02`.

## Open Questions

None.
