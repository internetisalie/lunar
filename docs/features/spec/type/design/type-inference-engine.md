# Type Inference Engine Design

**Branch**: [`inference`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaTypeInference.kt) (Exploration)  
**Status**: Experimental Exploration  
**Related requirement**: [`TYPE-01`](../../type-system.md) — Basic Type Inference

---

## 1. Overview

Lunar's type inference engine performs **constraint-based, flow-sensitive type analysis** over Lua PSI trees. Rather than a classical unification-based type system, it uses a **bipartite type graph** (a directed acyclic graph of *values* and *uses*) where type information propagates bottom-up from literals and top-down from usage constraints.

The result is a `LuaTypes` snapshot per file, cached via IntelliJ's `CachedValuesManager`, that maps every significant PSI element to its inferred `Type`. The `LuaTypesAnnotator` reads this cache and surfaces type information (and errors) as hover tooltips in the editor.

The engine is an implementation of **cubic biunification**, introduced in Stephen Dolan's 2016 PhD thesis *Algebraic Subtyping* and described in practical tutorial form in the blog series [*Subtype Inference by Example*](https://blog.polybdenum.com/2020/07/04/subtype-inference-by-example-part-1-introducing-cubiml.html) by polybdenum. That series is the primary theoretical reference for this implementation.

---

## 2. Theoretical Background — Cubic Biunification

> **Reference**: [*Subtype Inference by Example* (Parts 1–9)](https://blog.polybdenum.com/2020/07/04/subtype-inference-by-example-part-1-introducing-cubiml.html)

### 2.1 Why Not Hindley–Milner?

Classical Hindley–Milner (HM) type inference is based on *unification* — forcing pairs of types to be exactly *equal*. This causes constraints to flow both forward and backward through the program's dataflow, generating spurious type errors when two values with compatible but unequal types are used in the same place.

Cubic biunification replaces equality constraints with *subtype* (flow) constraints that only travel in the direction of data: a value must be *compatible with* its usages, never forced to be *identical to* them.

### 2.2 Polarized Types

The key insight that makes subtype inference decidable is **polarization**: the type system is split into two disjoint sets:

- **Value types** (positive polarity) — what a *value* guarantees it can produce (e.g., "this expression evaluates to a number").
- **Use types** (negative polarity) — what a *usage site* requires of its input (e.g., "this operand must be a number").

All type constraints take the form `v ≤ u` ("value `v` flows to use `u`"), and never in the reverse direction. This restriction avoids the cycles that make semi-unification undecidable.

### 2.3 Type Variables as Wormholes

A type variable is represented as a *pair* — one value-side node and one use-side node. The semantic invariant is: anything assigned to the variable (flowing into the use node) must be compatible with anything read from the variable (flowing out of the value node).

Rather than encoding this as an explicit `u ≤ v` constraint (which would reintroduce undecidability), biunification maintains it **implicitly via transitivity** of the flow relation. For every variable `(v₁, u₁)`:
- for each value `v₂` flowing into `u₁`, and
- for each use `u₂` that `v₁` flows into,

the algorithm automatically adds the constraint `v₂ ≤ u₂`. Variables therefore act as *wormholes*: whatever flows in one end flows out the other.

In the code this is implemented as a single `VariableNode`, which is simultaneously a `ValueNode` and a `UseNode`. Its value side holds a `WritesType` (a bag of upstream nodes) and its use side holds a `ReadsType` (a bag of downstream nodes).

### 2.4 Incremental Reachability — O(n³)

The flow relation must remain *transitively closed* as new edges are added. This is the **incremental reachability problem**. The algorithm from a [1986 paper (La Poutré & van Leeuwen)](https://core.ac.uk/download/pdf/82234237.pdf) — cited in the series — achieves O(n³) worst-case time, giving cubic biunification its name.

The O(n³) algorithm avoids the naive O(n⁴) approach (iterating all upstream × downstream pairs on every insertion) by inserting edges *recursively*:

> When inserting edge `A → B`:
> 1. For each `C` in `upsets[A]`, recursively insert `C → B`.
> 2. For each `D` in `downsets[B]`, recursively insert `A → D`.

Each insertion does O(n) work and there are at most O(n²) edges, giving O(n³) total. `OrderedSet` (a set + list pair) is used instead of a plain `HashSet` to ensure deterministic iteration order.

This algorithm is directly mirrored in `LuaTypeGraph.addEdge()`.

### 2.5 Type Heads and Compatibility Checking

When a value type node flows to a use type node that both carry *type heads* (i.e., type constructors such as `function`, `table`, `number`), the engine must check that the heads are *compatible* and propagate additional flow edges for their structural sub-components.

For example, a function value type `(T₁ → U₁)` flowing to a function use type `(T₂ → U₂)` requires:
- `U₁ → U₂` (return value flows forward, covariant)
- `T₂ → T₁` (argument flows backward, contravariant)

In cubiml this is implemented by `check_heads`. In Lunar it corresponds to `checkTypes()` in `LuaTypeGraph`, which is **currently a stub** (returns success unconditionally).

### 2.6 Article Series Index

| Part | Title | Key Content |
|------|-------|-------------|
| [1](https://blog.polybdenum.com/2020/07/04/subtype-inference-by-example-part-1-introducing-cubiml.html) | Introducing Cubiml | Motivation; HM vs. subtype inference; language overview |
| [2](https://blog.polybdenum.com/2020/07/11/subtype-inference-by-example-part-2-parsing-and-biunification.html) | Parsing and Biunification | Polarized types; biunification theory; `flow()` interface |
| [3](https://blog.polybdenum.com/2020/07/18/subtype-inference-by-example-part-3-the-typechecker-frontend.html) | The Typechecker Frontend | AST traversal; `check_expr`; scope/bindings |
| [4](https://blog.polybdenum.com/2020/07/25/subtype-inference-by-example-part-4-the-typechecker-core.html) | The Typechecker Core | Type graph; `TypeNode`; `check_heads`; `VTypeHead`/`UTypeHead` |
| [5](https://blog.polybdenum.com/2020/08/01/subtype-inference-by-example-part-5-incremental-reachability.html) | Incremental Reachability | Naive O(n⁴) vs. cubic O(n³) `add_edge`; `OrderedSet` |
| [6](https://blog.polybdenum.com/2020/08/08/subtype-inference-by-example-part-6-error-messages-and-recursion.html) | Error Messages and Recursion | Recursive types; better error reporting |
| [7](https://blog.polybdenum.com/2020/08/15/subtype-inference-by-example-part-7-let-polymorphism.html) | Let Polymorphism | Generics via let-polymorphism |
| [8](https://blog.polybdenum.com/2020/08/22/subtype-inference-by-example-part-8-mutability.html) | Mutability | Mutable variables; invariant type positions |
| [9](https://blog.polybdenum.com/2020/08/29/subtype-inference-by-example-part-9-nonexhaustive-matching-record-extensions-and-row-polymorphism.html) | Row Polymorphism | Non-exhaustive matching; record extensions |

---

## 3. Architecture

Following the cubiml design from [Parts 3–5](https://blog.polybdenum.com/2020/07/18/subtype-inference-by-example-part-3-the-typechecker-frontend.html) of the series, the engine is split into a **frontend** (AST traversal) and a **core** (type graph + reachability).

```
PSI File
   │
   ▼
LuaTypesVisitor          ← FRONTEND (Part 3 of series)
   │  visits every node bottom-up (children before parents)
   │  emits nodes + edges into:
   ▼
LuaTypeGraph             ← CORE (Parts 4–5 of series)
   │  bipartite graph: ValueNode ←→ UseNode, joined by VariableNode
   │  resolves types lazily via ReadsType / WritesType
   ▼
LuaTypes  (interface)
   │  getValueType(element) → Type
   │  getElementError(element) → ElementError?
   ▼
LuaTypesAnnotator        (IntelliJ Annotator)
   │  annotates every LuaExpr / LuaNameRef with a hover tooltip
```

The `LuaScope` companion object tracks the lexical name → node mapping and manages function/block boundaries during the visitor walk.

### 3.1 Correspondence to Cubiml

| Cubiml (series) | Lunar |
|---|---|
| `Value` (positive type) | `ValueNode` / `ValueElement` |
| `Use` (negative type) | `UseNode` / `UseElement` |
| `(Value, Use)` variable pair | `VariableNode` / `VariableElement` |
| `TypeCheckerCore.flow(v, u)` | `LuaTypeGraph.flow(value, use)` |
| `Reachability` (upsets/downsets) | `LuaTypeGraph.upSets` / `downSets` (`OrderedSet`) |
| `Reachability.add_edge()` O(n³) | `LuaTypeGraph.addEdge()` |
| `check_heads(lhs, rhs)` | `LuaTypeGraph.checkTypes()` (**stub**) |
| `Bindings` / `in_child_scope` | `LuaScope` / `LuaScope.child {}` |
| `check_expr` (frontend visitor) | `LuaTypesVisitor` |
| `VTypeHead` / `UTypeHead` | `TypeKind` enum |

---

## 4. Type Representation (`LuaTypes.kt`)

> Corresponds to **type heads** (`VTypeHead`/`UTypeHead`) from [Part 4](https://blog.polybdenum.com/2020/07/25/subtype-inference-by-example-part-4-the-typechecker-core.html) of the series, extended with Lua's richer primitive set and multi-value `ListType`.

### 4.1 `TypeKind` Enum

| Category | Kinds |
|---|---|
| **Primitive** | `NIL`, `BOOLEAN`, `NUMBER`, `STRING`, `FUNCTION`, `USERDATA`, `THREAD`, `TABLE` |
| **Lattice sentinels** | `ANY` (⊤ top), `UNDEFINED` (⊥ bottom) |
| **Composite** | `LIST`, `INDEX`, `DISJUNCTION`, `CONJUNCTION` |
| **Graph-internal** | `VARIABLE` (unresolved node reference) |
| **Trait constraints** | `PROPERTY`, `TRAIT_ORDERED`, `TRAIT_STRINGABLE` |

### 4.2 `Type` Hierarchy

```
Type (interface)
├── UnitType          – singletons: AnyType, UndefinedType
├── BaseType          – primitive singletons: NilType, BooleanType, NumberType, StringType, …
├── ListType          – ordered tuple of types; models Lua's multi-value sequences
├── FunctionType      – captures parameters (NodeList<VariableNode>), varargs flag, returns
├── TableType         – maps Index keys to VariableNode slots (mutable for assignment)
├── PropertyType      – a single index→type pair (used during table indexing)
├── DisjunctionType   – union  (A | B); produced by `or` / merging use-sites (≈ VTypeHead union)
├── ConjunctionType   – intersection (A & B); produced by merging value-sites
├── ReadsType         – live bag of upstream nodes; resolves to ConjunctionType (value side of variable)
└── WritesType        – live bag of downstream nodes; resolves to DisjunctionType (use side of variable)
```

`DisjunctionType` and `ConjunctionType` are the materialized forms of the value/use sides of a variable: when the graph asks "what type does variable X have?", `WritesType.disjointTypes()` unions all values that flowed in, and `ReadsType.conjointTypes()` intersects all usage constraints.

**Singletons** (defined at file scope):
```kotlin
val NilType      = BaseType(TypeKind.NIL)
val BooleanType  = BaseType(TypeKind.BOOLEAN)
val NumberType   = BaseType(TypeKind.NUMBER)
val StringType   = BaseType(TypeKind.STRING)
val UndefinedType = UnitType(TypeKind.UNDEFINED)  // ⊥
val AnyType       = UnitType(TypeKind.ANY)         // ⊤
```

### 4.3 `Index` — Table Key Abstraction

A `TableType` property is keyed by `Index`, which encodes three mutually exclusive key kinds:

| Field | Meaning |
|---|---|
| `element: PsiElement?` | Literal key (identifier or string/number literal) |
| `exprType: Type?` | Non-literal key whose type is known |
| `count: Int?` | Auto-generated integer index (array-style constructor) |

`Index.equals()` performs value-equality on the underlying token text or numeric value, enabling robust property look-up even across separately-constructed `Index` objects.

---

## 5. Type Graph (`LuaTypeInference.kt`)

> Corresponds to **`TypeCheckerCore`** and **`Reachability`** from [Parts 4–5](https://blog.polybdenum.com/2020/07/25/subtype-inference-by-example-part-4-the-typechecker-core.html) of the series.

### 5.1 Node Hierarchy

```
TypeNode (interface)
├── ValueNode   – has write: Type  (produces a value)   ← cubiml: Value
├── UseNode     – has read: Type   (consumes a value)   ← cubiml: Use
└── VariableNode : UseNode, ValueNode  (mutable binding; both producer and consumer) ← cubiml: var()
```

Concrete implementations:

| Class | Role |
|---|---|
| `ValueElement` | Immutable typed value (literals, operators) ← cubiml: `engine.bool()`, etc. |
| `UseElement` | Typed constraint on an input (e.g. arithmetic expects `NUMBER`) ← cubiml: `engine.bool_use()` |
| `VariableElement` | Mutable binding: `write = WritesType`, `read = ReadsType` ← cubiml: `engine.var()` |

`VariableElement.upSet` collects all values flowing *into* the variable (write-side).  
`VariableElement.downSet` collects all uses drawing *from* the variable (read-side).

### 5.2 `LuaTypeGraph` — Core Data Structure

The graph maintains four dictionaries:

```
values   : Map<PsiElement, ValueNode>
uses     : Map<PsiElement, UseNode>
upSets   : Map<TypeNode, OrderedSet<TypeNode>>   // who writes to this node  ← cubiml: Reachability.upsets
downSets : Map<TypeNode, OrderedSet<TypeNode>>   // who reads from this node ← cubiml: Reachability.downsets
```

**Factory methods** on `LuaTypeGraph`:

| Method | Creates | Cubiml equivalent |
|---|---|---|
| `value(element, type)` | Immutable `ValueElement` | `engine.bool()` / `engine.func(…)` etc. |
| `nil()` | `ValueElement` with `NilType` | — |
| `use(element, type)` | Immutable `UseElement` | `engine.bool_use()` etc. |
| `variable(element)` | Mutable `VariableElement` | `engine.var()` |

### 5.3 Flow Propagation

`flow(value: ValueNode, use: UseNode): Result<Unit>` is the core edge-insertion algorithm, corresponding to `TypeCheckerCore.flow()` + `Reachability.add_edge()` from the series:

1. Enqueue `Edge(value, use)`.
2. For each pending edge, call `addEdge` which:
   - Inserts the edge into `downSets[left]` / `upSets[right]`.
   - **Transitively propagates** (the O(n³) step): for each node already upstream of `left`, enqueue an edge to `right`; for each node already downstream of `right`, enqueue an edge from `left`.
3. For each newly confirmed direct use→value pair, calls `checkTypes(use.read, value.write)` — this corresponds to `check_heads()` and is **currently a stub**.

This gives the graph **transitive closure** on edge insertion, so type constraints propagate through chains of variables automatically, maintaining the wormhole invariant from §2.3.

### 5.4 Multi-Value Flow

Lua functions can return (and statements can consume) multiple values — a feature absent from the minimal cubiml language. Three helpers handle this:

| Method | Purpose |
|---|---|
| `flowList(from: NodeList<ValueNode>, to: NodeList<UseNode>)` | Zip values to uses; pads short `from` lists with `nil()` |
| `mutateList(from, to: MutableList<VariableNode>, hasPrev)` | Like `flowList` but grows the `to` list on demand; used for `return` accumulation |
| `NodeList<T>` | Thin wrapper around `List<T>` whose `iterator()` yields elements in order (TODO: flatten last element for vararg results) |

---

## 6. Scope Management (`LuaScope`)

> Corresponds to **`Bindings`** and `in_child_scope` from [Part 3](https://blog.polybdenum.com/2020/07/18/subtype-inference-by-example-part-3-the-typechecker-frontend.html) of the series, but with a **change-journal** optimization instead of map cloning.

`LuaScope` is an environment holding the live `name → TypeNode` mapping for the current lexical scope. It uses a **change journal** rather than copying the map per scope:

- `set(name, node)` records the old value as a `Change` and updates the map.
- `child(action)` captures the journal mark before the action, runs it, then replays the recorded `Change`s in reverse to restore state — effectively a functional scope without allocating a new map per block.
- `function(action)` wraps `child` but additionally swaps out `_params`, `_returns`, and `_returnTypes` so that nested function bodies have independent parameter/return tracking.

In cubiml, `Bindings.in_child_scope` achieves the same by cloning the HashMap. The change-journal approach is more memory-efficient for deeply nested scopes.

### `FunctionDefinition` (inner class of `LuaScope`)

Returned by `scope.function { … }`. Captures:

```kotlin
class FunctionDefinition(
    val parameters: List<VariableNode>,
    val returns: Type,          // WritesType that accumulates return expressions
    val returnTypes: List<VariableNode>, // per-position return variable nodes
    val changes: List<Change>,
)
```

This is consumed by `visitLocalFuncDecl` / `visitFuncDef` to synthesize a `FunctionType`.

---

## 7. Visitor (`LuaTypesVisitor`)

> Corresponds to **`check_expr`** (the typechecker frontend) from [Part 3](https://blog.polybdenum.com/2020/07/18/subtype-inference-by-example-part-3-the-typechecker-frontend.html) of the series.

`LuaTypesVisitor` extends `LuaRecursiveVisitor`. Because the base class visits children first, every `visitX` call sees already-inferred children.

### 7.1 Expression Type Inference

| Expression Kind | Inferred Type | Constraints Applied |
|---|---|---|
| `nil` literal | `NilType` | — |
| `true` / `false` | `BooleanType` | — |
| Number literal | `NumberType` | — |
| String literal | `StringType` | — |
| Unary `-` | `NumberType` | operand typed as `NUMBER` |
| Unary `#` (length) | `NumberType` | — |
| Unary `~` (bitwise not) | `NumberType` | operand typed as `NUMBER` |
| Unary `not` | `BooleanType` | — |
| Arithmetic `+ - * / // ^ %` | `NumberType` | both operands typed as `NUMBER` |
| Bitwise `& \| ~ << >>` | `NumberType` | both operands typed as `NUMBER` |
| Concatenation `..` | `StringType` | both operands typed as `STRING` (TODO: stringable trait) |
| Relational `< <= > >=` | `BooleanType` | both operands typed as `NUMBER` (TODO: ordered trait) |
| Equality `== ~=` | `BooleanType` | no operand constraint |
| Logical `and` / `or` | `VariableNode` (union of both branches) | — |
| Table constructor `{}` | `TableType` | property slots created per field |
| Function call | `FunctionType` (from callee) | — |
| Name reference | Linked to scope binding | — |
| Selector `.name` | Property lookup in `TableType` | — |
| Index `[expr]` | Property lookup in `TableType` (literal only) | — |

### 7.2 Statement Handling

| Statement | Behavior |
|---|---|
| `local x [, y] = expr [, expr]` | Creates `VariableNode`s via `scope.variable`; uses `flowList` to distribute values |
| `x [, y] = expr [, expr]` | Resolves LHS to variable nodes (or creates new ones for unknown names); uses `flowList` |
| `return [exprlist]` | Calls `mutateList` on `scope.returnTypes`; writes result to `scope.returns` |
| `local function f(…)` | Opens new `scope.function` context; synthesizes `FunctionType`; registers in scope |
| `function f(…)` | Same, but result stored without scope registration (top-level binding) |
| Parameter list | Each name → `scope.parameter(nameRef)` → `VariableNode` added to `_params` |

### 7.3 Caching

```kotlin
companion object {
    private val typesKey = Key<FileUserData<LuaTypes>>("LuaTypesAnnotator.KEY_TYPES")

    fun getTypes(element: PsiElement): LuaTypes {
        val bindings = LuaBindingsVisitor.getBindings(element)
        return typesKey.cacheFileUserData(element) { psiFile -> visit(psiFile, bindings) }
    }
}
```

The graph is computed once per file modification and reused across all annotator invocations in the same session.

---

## 8. IDE Integration

### 8.1 `LuaTypesAnnotator`

Registered as an IntelliJ `Annotator`. Fires on every `LuaExpr` and `LuaNameRef`. For each element it:

1. Calls `LuaTypesVisitor.getTypes(target)`.
2. Checks for a recorded `ElementError`; if present, creates an `ERROR` severity annotation with an HTML tooltip.
3. Otherwise calls `getValueType(target)` and creates a silent `INFORMATION` annotation whose tooltip shows `valueType: <typeString>`.

This gives a lightweight "type hover" experience while the system is being developed.

> **Note from the series ([Part 2](https://blog.polybdenum.com/2020/07/11/subtype-inference-by-example-part-2-parsing-and-biunification.html))**: With large-scale type inference, inferred types tend to be verbose and not human-readable. The tooltip approach here treats types as a debugging tool rather than a user-facing feature, which is appropriate for the current stage of development.

### 8.2 Dependency on `LuaBindings`

`LuaTypesVisitor` receives a `LuaBindings` instance at construction (though the current implementation does not yet use it for type resolution — bindings are used separately by `LuaLocalBindingsAnnotator` / `LuaGlobalBindingsAnnotator`). The intent is to eventually feed resolved binding information into scope initialisation to handle cross-file types.

---

## 9. Known Limitations & TODO Items

| Area | Status | Theory Reference | Notes |
|---|---|---|---|
| `checkTypes` / head compatibility | **Stub** | [Part 4](https://blog.polybdenum.com/2020/07/25/subtype-inference-by-example-part-4-the-typechecker-core.html) §check_heads | No type mismatch errors reported yet |
| `NodeList` last-element flattening | **TODO** | — | Vararg / multi-return list expansion not yet handled |
| `..` operands | `STRING` constraint | — | Should be relaxed to a `STRINGABLE` trait |
| Relational operands | `NUMBER` constraint | — | Should be relaxed to an `ORDERED` trait |
| Global name resolution | Not wired to `LuaBindings` | — | Globals inferred per-file only |
| Cross-file inference | Not implemented | — | Requires stub/index integration |
| LuaCATS annotations | Not wired | — | `@param`/`@return`/`@type` tags not consumed as type constraints |
| Flow-sensitive narrowing | Not implemented | [Part 9](https://blog.polybdenum.com/2020/08/29/subtype-inference-by-example-part-9-nonexhaustive-matching-record-extensions-and-row-polymorphism.html) (row poly) | `if type(x) == "string"` does not narrow `x` |
| Table property assignment | **TODO** in `visitAssignmentStatement` | — | Selector/index LHS not updating parent table type |
| `FunctionType` call return | Returns `FunctionType` of callee | [Part 3](https://blog.polybdenum.com/2020/07/18/subtype-inference-by-example-part-3-the-typechecker-frontend.html) §function calls | Should return the *call result* type instead |
| `and`/`or` short-circuit type | Returns union of both branches | — | No truthiness narrowing |
| Recursive types | Not considered | [Part 6](https://blog.polybdenum.com/2020/08/08/subtype-inference-by-example-part-6-error-messages-and-recursion.html) | Graph cycle handling not tested |
| Let polymorphism / generics | Not implemented | [Part 7](https://blog.polybdenum.com/2020/08/15/subtype-inference-by-example-part-7-let-polymorphism.html) | Functions are monomorphically typed |
| Mutability invariance | Not enforced | [Part 8](https://blog.polybdenum.com/2020/08/22/subtype-inference-by-example-part-8-mutability.html) | Table fields treated covariantly |

---

## 10. Data Flow Example

```lua
local x = 10        -- NumberType
local y = x + 1     -- NumberType (arithmetic)
local s = "hello"   -- StringType
local b = x > 0     -- BooleanType
```

Graph edges after visiting:

```
value(10_literal, NUMBER)   → variable(x)
value(1_literal, NUMBER)    → variable(RHS of +)
variable(x)                 → use(LHS of +, NUMBER)   [typedRead]
variable(x)                 → use(RHS of >, NUMBER)   [typedRead]
value(+ expr, NUMBER)       → variable(y)
value("hello", STRING)      → variable(s)
value(> expr, BOOLEAN)      → variable(b)
```

Querying `getValueType(y)` → traverses `variable(y).write` → `WritesType` → resolves disjoint sources → `NumberType`.

---

## 11. Future Work

1. **Implement `checkTypes` / head compatibility** ([Part 4](https://blog.polybdenum.com/2020/07/25/subtype-inference-by-example-part-4-the-typechecker-core.html)) — mirror `check_heads` to report type mismatches (e.g. passing `STRING` where `NUMBER` is required). For function types, this requires covariant return propagation and contravariant argument propagation.
2. **LuaCATS integration** — parse `@type`, `@param`, `@return`, `@class` tags and inject them as `ValueNode`s / `UseNode`s at declaration sites, allowing user-supplied type annotations to flow into the graph.
3. **Cross-file inference** — leverage stub index to propagate `FunctionType` across `require` boundaries.
4. **Flow-sensitive narrowing** — introduce branch-scoped `child {}` contexts that refine variable types inside `if`/`elseif` bodies based on `type()` guards (related to row polymorphism in [Part 9](https://blog.polybdenum.com/2020/08/29/subtype-inference-by-example-part-9-nonexhaustive-matching-record-extensions-and-row-polymorphism.html)).
5. **Vararg multi-return flattening** — complete the `NodeList` last-element expansion for calls that return multiple values.
6. **Trait system** — implement `ORDERED` and `STRINGABLE` traits (as use-type heads) so relational and concatenation operators accept wider operand sets.
7. **Let polymorphism / generics** ([Part 7](https://blog.polybdenum.com/2020/08/15/subtype-inference-by-example-part-7-let-polymorphism.html)) — allow functions to be typed polymorphically so a single definition can be used with multiple argument types.
8. **Mutability invariance** ([Part 8](https://blog.polybdenum.com/2020/08/22/subtype-inference-by-example-part-8-mutability.html)) — enforce invariant typing at mutable table field positions to prevent unsound covariant widening.
9. **Completion / inlay hints** — surface inferred types through the code-completion and inlay-hint IntelliJ extension points.
