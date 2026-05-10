# Phase 1 API Contracts — Type Inference Foundation

**Status**: Draft  
**Depends on**: [Type Inference Engine Design](./type-inference-engine.md), [Type System Integration Plan](./type-system-integration-plan.md)

---

## 0. Scope and Non-Goals

This document defines the Kotlin API contracts for Phase 1 of the type system:

- `LuaGraphType` — sealed graph-internal type representation
- `LuaTypeNodes` — `TypeNode`, `ValueNode`, `UseNode`, `VariableNode`
- `LuaTypeGraph` — core constraint graph
- `LuaTypes` — per-file result snapshot (public interface)
- `LuaScope` — lexical scope manager
- `LuaTypeGraphBridge` — conversion between Layer 1 (`LuaType`) and graph nodes
- `LuaTypesVisitor` — PSI visitor, Phase 1 subset
- `LuaTypesAnnotator` — IntelliJ annotator

Phase 1 deliberately excludes: `FunctionType` structural checking, `TableType`, union `DisjunctionType`, generics, cross-file inference. Those are gated on `checkTypes()` work in Phase 2–4.

---

## 1. Resolved Design Decision: Two-Layer Type Representation

**Decision**: Keep graph-internal `LuaGraphType` and public `LuaType` (Layer 1) **separate**.

**Rationale**:

| Concern | `LuaType` (Layer 1) | `LuaGraphType` (Layer 2) |
|---|---|---|
| Purpose | Public contract for IDE features | Internal graph constraint representation |
| Resolution | Cross-file, lazy, via `StubIndex` | Per-file, eager, flow-propagated |
| Lifecycle | Project scope | File modification scope |
| Complexity | All named types, generics, unions | Phase 1: primitives + ANY + UNDEFINED |
| Consumers | Completion, hover, parameter info | Graph engine internals |

The `LuaTypes` result snapshot converts graph `LuaGraphType` back to `LuaType` via `graphTypeToLuaType()`. Consumers of the type system **always** receive `LuaType`, regardless of which layer produced it. This keeps the `LuaTypesAnnotator` and future IDE extension points decoupled from graph internals.

The consequence: `LuaTypeManager.inferType(element)` (currently returning `LuaPrimitiveType.ANY`) will be implemented by calling `LuaTypesVisitor.getTypes(element).getValueType(element)`.

---

## 2. `LuaGraphType` — Graph-Internal Type Representation

**File**: `lang/psi/types/LuaGraphType.kt`  
**Package**: `net.internetisalie.lunar.lang.psi.types`

This sealed class is the type representation used **inside** the graph engine. It is never exposed to IDE consumers.

```kotlin
/**
 * Graph-internal type representation. Not exposed outside the inference engine.
 * IDE consumers always receive [LuaType] via [LuaTypes.getValueType].
 *
 * Phase 1 includes only primitive heads and the lattice sentinels (ANY, UNDEFINED).
 * FunctionType, TableType, DisjunctionType etc. are added in later phases.
 */
sealed class LuaGraphType {

    /** ⊤ (top type). Every value is assignable to ANY. */
    data object Any : LuaGraphType()

    /** ⊥ (bottom type). UNDEFINED is assignable to every type. Represents uninferred/unknown. */
    data object Undefined : LuaGraphType()

    data object Nil      : LuaGraphType()
    data object Boolean  : LuaGraphType()
    data object Number   : LuaGraphType()
    data object String   : LuaGraphType()

    // --- Phase 2+ ---
    // data class Function(val params: List<VariableNode>, val hasVararg: Boolean, val returns: List<VariableNode>) : LuaGraphType()
    //
    // [className] carries the @class name when this node was seeded from a LuaClassType,
    // or null for anonymous table literals and array types.
    //
    // checkTypes() uses this to select the compatibility algorithm:
    //   named  → named  (same name)  : compatible, no-op
    //   named  → named  (diff name)  : nominal — walk inheritance chain via LuaTypeManager
    //   named  → anon               : structural (duck typing)
    //   anon   → named              : INCOMPATIBLE — anonymous tables never satisfy a named class
    //   anon   → anon               : structural
    //
    // See type-system-integration-plan.md §5 for the full checkTypes() table.
    // data class Table(val className: String?, val properties: MutableMap<Index, VariableNode> = mutableMapOf()) : LuaGraphType()
    // data class Disjunction(val members: List<LuaGraphType>) : LuaGraphType()
    // data class Conjunction(val members: List<LuaGraphType>) : LuaGraphType()

    companion object {
        /** Convert a [LuaType] primitive singleton to its graph equivalent. Returns null for non-primitive types. */
        fun fromLuaType(type: LuaType): LuaGraphType? = when (type) {
            LuaPrimitiveType.ANY     -> Any
            LuaPrimitiveType.NIL     -> Nil
            LuaPrimitiveType.BOOLEAN -> Boolean
            LuaPrimitiveType.NUMBER  -> Number
            LuaPrimitiveType.STRING  -> String
            LuaPrimitiveType.VOID    -> Nil   // treat void as nil for graph purposes
            LuaPrimitiveType.UNKNOWN -> Undefined
            else                     -> null  // complex types handled by bridge
        }
    }
}
```

---

## 3. `LuaTypeNodes` — Node Hierarchy

**File**: `lang/psi/types/LuaTypeNodes.kt`  
**Package**: `net.internetisalie.lunar.lang.psi.types`

```kotlin
/**
 * Base for all nodes in the type graph. Every node is associated with the PSI element
 * that caused its creation (for error reporting).
 */
sealed interface TypeNode {
    val element: PsiElement
}

/**
 * A node that produces a value — the positive polarity side.
 * Holds the concrete type this node asserts.
 */
interface ValueNode : TypeNode {
    val write: LuaGraphType
}

/**
 * A node that consumes a value — the negative polarity side.
 * Holds the type constraint this usage site demands.
 */
interface UseNode : TypeNode {
    val read: LuaGraphType
}

/**
 * A mutable variable binding. Simultaneously a [ValueNode] (its resolved type is the
 * union of all values written to it) and a [UseNode] (its demand is the intersection
 * of all reads from it).
 *
 * Implements the "wormhole" invariant: anything assigned to this variable (flowing into
 * [downSet]) must be compatible with anything read from it (flowing into [upSet]).
 */
interface VariableNode : ValueNode, UseNode {
    /** All upstream nodes that flow values *into* this variable. */
    val upSet: OrderedSet<TypeNode>
    /** All downstream nodes that draw values *from* this variable. */
    val downSet: OrderedSet<TypeNode>

    /** Live write type: disjunction of all upstream write types. */
    override val write: LuaGraphType

    /** Live read type: conjunction of all downstream read types. */
    override val read: LuaGraphType
}

// --- Concrete implementations (internal to the engine) ---

/** Immutable typed value. Created by graph.value() and graph.nil(). */
internal class ValueElement(
    override val element: PsiElement,
    override val write: LuaGraphType,
) : ValueNode

/** Immutable typed constraint. Created by graph.use(). */
internal class UseElement(
    override val element: PsiElement,
    override val read: LuaGraphType,
) : UseNode

/** Mutable variable binding. Created by graph.variable(). */
internal class VariableElement(
    override val element: PsiElement,
) : VariableNode {
    override val upSet: OrderedSet<TypeNode> = OrderedSet()
    override val downSet: OrderedSet<TypeNode> = OrderedSet()

    override val write: LuaGraphType get() = resolveWrite()
    override val read: LuaGraphType get() = resolveRead()

    private fun resolveWrite(): LuaGraphType {
        // Collect all upstream write types (the values flowing in).
        // Phase 1: return the first non-UNDEFINED type, or UNDEFINED if none.
        // Phase 5+: return DisjunctionType of all distinct write types.
        return upSet.asSequence()
            .filterIsInstance<ValueNode>()
            .map { it.write }
            .firstOrNull { it != LuaGraphType.Undefined }
            ?: LuaGraphType.Undefined
    }

    private fun resolveRead(): LuaGraphType {
        // Phase 1: return the first non-ANY constraint, or ANY if unconstrained.
        return downSet.asSequence()
            .filterIsInstance<UseNode>()
            .map { it.read }
            .firstOrNull { it != LuaGraphType.Any }
            ?: LuaGraphType.Any
    }
}

/**
 * An ordered set: preserves insertion order for deterministic iteration
 * while providing O(1) membership testing. Required by the O(n³) reachability algorithm.
 */
class OrderedSet<T> : Iterable<T> {
    private val set = LinkedHashSet<T>()

    /** Returns true if [item] was newly added (false if already present). */
    fun add(item: T): Boolean = set.add(item)

    operator fun contains(item: T): Boolean = item in set

    override fun iterator(): Iterator<T> = set.iterator()

    val size: Int get() = set.size
}
```

---

## 4. `LuaTypeGraph` — Core Constraint Graph

**File**: `lang/psi/types/LuaTypeGraph.kt`  
**Package**: `net.internetisalie.lunar.lang.psi.types`

One `LuaTypeGraph` instance is created per file visit by `LuaTypesVisitor`. It is **not** shared across files and is **not** a singleton.

```kotlin
/**
 * Bipartite type graph implementing cubic biunification.
 *
 * Maintains transitive closure of value→use flow edges. Every call to [flow] that
 * introduces a new edge triggers the O(n³) propagation step to preserve closure.
 *
 * Not thread-safe. Assumed to be built by a single-threaded PSI visitor and then
 * read-only by [LuaTypesAnnotator].
 */
class LuaTypeGraph {

    // --- Node registries ---

    /** Lookup table: PSI element → its [ValueNode] (if any was created for it). */
    private val values: MutableMap<PsiElement, ValueNode> = HashMap()

    /** Lookup table: PSI element → its [UseNode] (if any was created for it). */
    private val uses: MutableMap<PsiElement, UseNode> = HashMap()

    /** Accumulated type errors keyed by the PSI element where the error occurred. */
    private val errors: MutableMap<PsiElement, ElementError> = HashMap()

    // --- Factory methods ---

    /**
     * Create an immutable [ValueNode] asserting [type] for [element].
     * If a node already exists for [element] it is returned unchanged.
     */
    fun value(element: PsiElement, type: LuaGraphType): ValueNode =
        values.getOrPut(element) { ValueElement(element, type) } as ValueNode

    /** Convenience: create a [ValueNode] with [LuaGraphType.Nil]. */
    fun nil(element: PsiElement): ValueNode = value(element, LuaGraphType.Nil)

    /**
     * Create an immutable [UseNode] asserting [type] as a constraint for [element].
     * If a use node already exists for [element] the existing one is returned.
     */
    fun use(element: PsiElement, type: LuaGraphType): UseNode =
        uses.getOrPut(element) { UseElement(element, type) } as UseNode

    /**
     * Create a mutable [VariableNode] for [element].
     * If a variable node was already registered for [element] it is returned unchanged.
     */
    fun variable(element: PsiElement): VariableNode {
        val existing = values[element]
        if (existing is VariableNode) return existing
        val node = VariableElement(element)
        values[element] = node
        uses[element] = node
        return node
    }

    // --- Node accessors ---

    fun getValueNode(element: PsiElement): ValueNode? = values[element]

    fun getUseNode(element: PsiElement): UseNode? = uses[element]

    // --- Flow propagation ---

    /**
     * Add a flow edge from [from] (value producer) to [to] (value consumer) and
     * maintain transitive closure. This is the O(n³) algorithm.
     *
     * Returns [Result.failure] with an [ElementError] if [checkTypes] finds the
     * edge to be incompatible (Phase 2+). In Phase 1, always returns success.
     */
    fun flow(from: ValueNode, to: UseNode): Result<Unit> {
        addEdge(from, to)
        return Result.success(Unit)
    }

    /**
     * Convenience overload for flowing a [VariableNode] into a [UseNode].
     * [VariableNode] implements [ValueNode], so this is a direct delegation.
     */
    fun flow(from: VariableNode, to: UseNode): Result<Unit> = flow(from as ValueNode, to)

    /**
     * Convenience overload for flowing a [ValueNode] into a [VariableNode].
     * [VariableNode] implements [UseNode], so this is a direct delegation.
     */
    fun flow(from: ValueNode, to: VariableNode): Result<Unit> = flow(from, to as UseNode)

    /**
     * Flow a list of [ValueNode]s into a corresponding list of [UseNode]s (positional).
     * If [from] is shorter than [to], the missing positions receive [nil] nodes.
     * Extra elements in [from] beyond [to]'s length are silently dropped.
     */
    fun flowList(from: List<ValueNode>, to: List<UseNode>, sourceElement: PsiElement) {
        for (i in to.indices) {
            val valueNode = from.getOrElse(i) { nil(sourceElement) }
            flow(valueNode, to[i])
        }
    }

    // --- Error recording ---

    /** Record a type incompatibility error at [element]. Later phases call this from [checkTypes]. */
    fun recordError(element: PsiElement, error: ElementError) {
        errors[element] = error
    }

    fun getError(element: PsiElement): ElementError? = errors[element]

    fun allErrors(): Map<PsiElement, ElementError> = errors

    // --- Internal: transitive closure ---

    /**
     * Insert edge [left] → [right] and propagate transitively:
     * - For every node already upstream of [left], add an edge to [right].
     * - For every node already downstream of [right], add an edge from [left].
     *
     * This maintains full transitive closure in O(n³) total.
     * [checkTypes] is called for each newly confirmed direct edge.
     */
    private fun addEdge(left: ValueNode, right: UseNode) {
        val pending = ArrayDeque<Pair<ValueNode, UseNode>>()
        pending.add(left to right)

        while (pending.isNotEmpty()) {
            val (l, r) = pending.removeFirst()

            // Only insert and propagate if this is a genuinely new edge.
            if (l is VariableElement) {
                if (!l.downSet.add(r)) continue   // already present
            }
            if (r is VariableElement) {
                if (!r.upSet.add(l)) continue
            }

            // Propagate: all upstreams of l must also flow to r.
            if (l is VariableElement) {
                for (upstream in l.upSet) {
                    if (upstream is ValueNode) pending.add(upstream to r)
                }
            }

            // Propagate: l must also flow to all downstreams of r.
            if (r is VariableElement) {
                for (downstream in r.downSet) {
                    if (downstream is UseNode) pending.add(l to downstream)
                }
            }

            // Phase 2+: checkTypes(r.read, l.write) — currently a no-op.
        }
    }
}

/**
 * A recorded type incompatibility. Attached to a [PsiElement] and surfaced by
 * [LuaTypesAnnotator] as a red-underline error annotation.
 */
data class ElementError(
    val message: String,
    val severity: ErrorSeverity = ErrorSeverity.ERROR,
)

enum class ErrorSeverity { ERROR, WARNING }
```

---

## 5. `LuaTypes` — Per-File Result Snapshot

**File**: `lang/psi/types/LuaTypes.kt`  
**Package**: `net.internetisalie.lunar.lang.psi.types`

This is the **public interface** for all type queries. It returns `LuaType` (Layer 1), not `LuaGraphType`.

```kotlin
/**
 * Immutable snapshot of type inference results for a single Lua file.
 *
 * Obtained via [LuaTypesVisitor.getTypes]. Cached per file modification via
 * [cacheFileUserData]; stale after any PSI change to the file.
 *
 * All methods return [LuaType] (the public Layer 1 type), regardless of whether
 * the type was inferred by the graph engine or supplied by a LuaCATS annotation.
 */
interface LuaTypes {

    /**
     * The inferred type of [element] as a producer (value polarity).
     * Returns [LuaPrimitiveType.UNKNOWN] if no type was inferred.
     */
    fun getValueType(element: PsiElement): LuaType

    /**
     * Any type incompatibility error recorded at [element].
     * Returns null if the element is type-correct (or not checked yet in Phase 1).
     */
    fun getElementError(element: PsiElement): ElementError?
}

/** Default implementation, constructed by [LuaTypesVisitor]. */
internal class LuaTypesSnapshot(private val graph: LuaTypeGraph) : LuaTypes {

    override fun getValueType(element: PsiElement): LuaType {
        val node = graph.getValueNode(element) ?: return LuaPrimitiveType.UNKNOWN
        return graphTypeToLuaType(node.write)
    }

    override fun getElementError(element: PsiElement): ElementError? =
        graph.getError(element)

    companion object {
        /**
         * Convert a [LuaGraphType] to the corresponding [LuaType] singleton for
         * consumption by IDE extension points.
         *
         * Phase 1: primitives only.
         * Phase 2+: extend for FunctionType, TableType, DisjunctionType.
         */
        fun graphTypeToLuaType(graphType: LuaGraphType): LuaType = when (graphType) {
            is LuaGraphType.Any       -> LuaPrimitiveType.ANY
            is LuaGraphType.Undefined -> LuaPrimitiveType.UNKNOWN
            is LuaGraphType.Nil       -> LuaPrimitiveType.NIL
            is LuaGraphType.Boolean   -> LuaPrimitiveType.BOOLEAN
            is LuaGraphType.Number    -> LuaPrimitiveType.NUMBER
            is LuaGraphType.String    -> LuaPrimitiveType.STRING
        }
    }
}
```

---

## 6. `LuaScope` — Lexical Scope Manager (Phase 1 Subset)

**File**: `lang/psi/types/LuaScope.kt`  
**Package**: `net.internetisalie.lunar.lang.psi.types`

Phase 1 needs only local variable tracking and function boundary isolation. The change-journal approach avoids copying the map on every block.

```kotlin
/**
 * Change-journal lexical scope.
 *
 * Tracks the live [name → VariableNode] mapping as the visitor descends the PSI tree.
 * On entering a new block, the current state is implicitly captured via a journal mark;
 * on exit, recorded changes are rolled back in reverse.
 *
 * [function] additionally isolates [returnTypes] so nested functions track their own returns.
 */
class LuaScope(private val graph: LuaTypeGraph) {

    private val bindings: MutableMap<String, VariableNode> = HashMap()
    private val journal: MutableList<Change> = ArrayList()

    /** Parameter and return tracking for the current function body (null at file scope). */
    private var currentFunction: FunctionContext? = null

    // --- Variable management ---

    /**
     * Bind [name] to [node] in the current scope.
     * Records the previous binding in the journal for rollback.
     */
    fun set(name: String, node: VariableNode) {
        journal.add(Change(name, bindings[name]))
        bindings[name] = node
    }

    /** Look up [name] in the current scope chain. Returns null if not in scope. */
    fun get(name: String): VariableNode? = bindings[name]

    /**
     * Create a new [VariableNode] for [element], register it in the graph, and return it.
     * Does not bind the node to any name — callers must call [set] separately.
     */
    fun variable(element: PsiElement): VariableNode = graph.variable(element)

    // --- Scope boundaries ---

    /**
     * Execute [action] within a child scope. Any bindings set during [action] are
     * rolled back on return. Used for `do...end` blocks and `if` branches.
     */
    fun child(action: LuaScope.() -> Unit) {
        val mark = journal.size
        action()
        rollback(mark)
    }

    /**
     * Execute [action] within a function boundary. Returns a [FunctionDefinition]
     * capturing the parameters, return variable nodes, and all changes so they can be
     * inspected after the function body has been visited.
     *
     * Isolates: name bindings (via journal) and [FunctionContext] (swapped and restored).
     */
    fun function(
        paramElements: List<PsiElement>,
        action: LuaScope.(params: List<VariableNode>) -> Unit,
    ): FunctionDefinition {
        val mark = journal.size
        val prevContext = currentFunction
        val context = FunctionContext()
        currentFunction = context

        // Create and register parameter nodes.
        val params = paramElements.map { elem ->
            graph.variable(elem).also { node -> set(elem.text, node) }
        }
        context.parameters.addAll(params)

        action(params)

        val def = FunctionDefinition(
            parameters = context.parameters.toList(),
            returnTypes = context.returnTypes.toList(),
        )
        currentFunction = prevContext
        rollback(mark)
        return def
    }

    /** Add a return-position node to the current function's return list. */
    fun addReturnType(index: Int, node: VariableNode) {
        val ctx = currentFunction ?: return
        while (ctx.returnTypes.size <= index) {
            ctx.returnTypes.add(graph.variable(node.element))
        }
        graph.flow(node, ctx.returnTypes[index])
    }

    /** The return variable nodes for the current function, or empty list at file scope. */
    val returnTypes: List<VariableNode> get() = currentFunction?.returnTypes ?: emptyList()

    // --- Internal ---

    private fun rollback(mark: Int) {
        for (i in journal.indices.reversed().take(journal.size - mark)) {
            val change = journal[i]
            if (change.previous == null) bindings.remove(change.name)
            else bindings[change.name] = change.previous
        }
        repeat(journal.size - mark) { journal.removeAt(journal.size - 1) }
    }

    private data class Change(val name: String, val previous: VariableNode?)

    private class FunctionContext {
        val parameters: MutableList<VariableNode> = ArrayList()
        val returnTypes: MutableList<VariableNode> = ArrayList()
    }

    data class FunctionDefinition(
        val parameters: List<VariableNode>,
        val returnTypes: List<VariableNode>,
    )
}
```

---

## 7. `LuaTypeGraphBridge` — Layer 1 ↔ Layer 2 Conversion

**File**: `lang/psi/types/LuaTypeGraphBridge.kt`  
**Package**: `net.internetisalie.lunar.lang.psi.types`

```kotlin
/**
 * Bridge between [LuaType] (Layer 1, cross-file stub resolution) and [ValueNode]
 * (Layer 2, per-file constraint graph).
 *
 * [typeToValueNode] is called during PSI visitation whenever a LuaCATS annotation
 * is found that supplies type information from outside the current file.
 */
object LuaTypeGraphBridge {

    /**
     * Convert a [LuaType] from Layer 1 into a [ValueNode] suitable for injection
     * into [graph] as a seed constraint.
     *
     * [element] is the PSI node that is the source of the annotation (used for
     * error attribution and as the graph node key).
     *
     * Phase 1: handles primitives, ANY, UNKNOWN. Complex types fall through to
     * [LuaGraphType.Any] with a TODO comment; they will be expanded in Phase 4+.
     */
    fun typeToValueNode(
        type: LuaType,
        graph: LuaTypeGraph,
        element: PsiElement,
    ): ValueNode {
        val graphType = LuaGraphType.fromLuaType(type)
            ?: LuaGraphType.Any  // Phase 2+: handle LuaClassType, LuaFunctionType, etc.
        return graph.value(element, graphType)
    }

    /**
     * Inject LuaCATS `@type` annotation into [graph] for [varDecl].
     *
     * Looks up the first `@type` tag on the declaration's comment, resolves the
     * annotated type via [LuaTypeManager], and flows the resulting [ValueNode] into
     * [varNode].
     */
    fun injectTypeAnnotation(
        varDecl: LuaCatsCommentOwner,
        varNode: VariableNode,
        graph: LuaTypeGraph,
        typeManager: LuaTypeManager,
    ) {
        val comment = LuaPsiImplUtil.getCatsComment(varDecl) ?: return
        val typeTag = comment.getTypeTagList().firstOrNull() ?: return
        val typeStr = typeTag.argType.text

        val luaType = typeManager.resolveType(typeStr, varDecl as PsiElement)
            ?: TypeParser.parse(typeStr, varDecl as PsiElement)

        val seedNode = typeToValueNode(luaType, graph, varDecl as PsiElement)
        graph.flow(seedNode, varNode)
    }

    /**
     * Inject LuaCATS `@param` annotations for a function definition's parameter nodes.
     *
     * For each `@param` tag on [comment], finds the matching [VariableNode] in [params]
     * by name and flows a seed value node of the annotated type into it.
     */
    fun injectParamAnnotations(
        comment: LuaCatsComment,
        params: List<VariableNode>,
        paramNames: List<String>,
        graph: LuaTypeGraph,
        typeManager: LuaTypeManager,
        context: PsiElement,
    ) {
        for (paramTag in comment.getParamTagList()) {
            val name = paramTag.argName?.text ?: continue
            val typeStr = paramTag.argType.text
            val idx = paramNames.indexOf(name)
            if (idx < 0 || idx >= params.size) continue

            val luaType = typeManager.resolveType(typeStr, context)
                ?: TypeParser.parse(typeStr, context)
            val seedNode = typeToValueNode(luaType, graph, context)
            graph.flow(seedNode, params[idx])
        }
    }

    /**
     * Inject LuaCATS `@return` annotations into a function's return type nodes.
     *
     * Each `@return` tag (positional) flows a seed value node into the corresponding
     * index of [returnTypes].
     */
    fun injectReturnAnnotations(
        comment: LuaCatsComment,
        returnTypes: List<VariableNode>,
        graph: LuaTypeGraph,
        typeManager: LuaTypeManager,
        context: PsiElement,
    ) {
        comment.getReturnTagList().forEachIndexed { i, returnTag ->
            if (i >= returnTypes.size) return@forEachIndexed
            val typeStr = returnTag.argType.text
            val luaType = typeManager.resolveType(typeStr, context)
                ?: TypeParser.parse(typeStr, context)
            val seedNode = typeToValueNode(luaType, graph, context)
            graph.flow(seedNode, returnTypes[i])
        }
    }
}
```

---

## 8. `LuaTypesVisitor` — PSI Visitor (Phase 1 Subset)

**File**: `lang/psi/types/LuaTypesVisitor.kt`  
**Package**: `net.internetisalie.lunar.lang.psi.types`

The visitor extends `LuaRecursiveVisitor`, which visits children before parents (bottom-up). All `visitX` implementations therefore see already-inferred children.

Phase 1 covers: literal expressions, local variable declarations with `@type`, local and global function definitions with `@param`/`@return`.

```kotlin
/**
 * Bottom-up PSI visitor that populates a [LuaTypeGraph] for a single file.
 *
 * After construction, call [visit] once. The resulting [LuaTypes] snapshot is
 * then cached per file modification via [getTypes].
 *
 * Phase 1 handles:
 * - nil / boolean / number / string literals → primitive [ValueNode]s
 * - [LuaLocalVarDecl] with `@type` annotation → seeded variable node
 * - [LuaLocalFuncDecl] / [LuaFuncDef] with `@param`/`@return` annotations → seeded params/returns
 * - Name references → linked to scope binding
 * - Local variable declarations without annotations → bare [VariableNode]
 *
 * Not thread-safe. One instance per file visit.
 */
class LuaTypesVisitor private constructor(
    private val graph: LuaTypeGraph,
    private val scope: LuaScope,
    private val typeManager: LuaTypeManager,
) : LuaRecursiveVisitor() {

    // -------------------------------------------------------------------------
    // Expressions — Phase 1
    // -------------------------------------------------------------------------

    override fun visitNilExpr(o: LuaNilExpr) {
        graph.nil(o)
    }

    override fun visitTrueExpr(o: LuaTrueExpr) {
        graph.value(o, LuaGraphType.Boolean)
    }

    override fun visitFalseExpr(o: LuaFalseExpr) {
        graph.value(o, LuaGraphType.Boolean)
    }

    override fun visitNumberLiteral(o: LuaNumberLiteral) {
        graph.value(o, LuaGraphType.Number)
    }

    override fun visitStringLiteral(o: LuaStringLiteral) {
        graph.value(o, LuaGraphType.String)
    }

    /**
     * Name reference: link this element to the variable node in scope.
     * If the name is not in scope (global reference), create a fresh variable node.
     * Phase 3+ will feed global types from [LuaTypeManager] here.
     */
    override fun visitNameRef(o: LuaNameRef) {
        val name = o.text
        val node = scope.get(name) ?: run {
            val fresh = graph.variable(o)
            scope.set(name, fresh)
            fresh
        }
        // Re-map this expression element to the same node so getValueType(o) works.
        if (graph.getValueNode(o) == null) {
            graph.flow(node, graph.use(o, LuaGraphType.Any))
        }
    }

    // -------------------------------------------------------------------------
    // Statements — Phase 1
    // -------------------------------------------------------------------------

    /**
     * `local x [, y] = expr [, expr]`
     *
     * 1. Create a [VariableNode] for each declared name.
     * 2. If the declaration has a `@type` tag, inject the annotation seed.
     * 3. Flow RHS expression nodes into the corresponding LHS variable nodes.
     */
    override fun visitLocalVarDecl(o: LuaLocalVarDecl) {
        val names = o.attNameList
        val rhsExprs = o.exprList?.exprList ?: emptyList()

        val varNodes = names.map { attName ->
            graph.variable(attName).also { node ->
                scope.set(attName.nameRef.text, node)
            }
        }

        // Inject @type annotation seed if present.
        varNodes.firstOrNull()?.let { node ->
            LuaTypeGraphBridge.injectTypeAnnotation(o, node, graph, typeManager)
        }

        // Flow RHS into LHS positionally.
        val useNodes: List<UseNode> = varNodes.map { it as UseNode }
        val valueNodes: List<ValueNode> = rhsExprs.mapNotNull { graph.getValueNode(it) }
        graph.flowList(valueNodes, useNodes, o)
    }

    /**
     * `local function f(params) body end`
     */
    override fun visitLocalFuncDecl(o: LuaLocalFuncDecl) {
        visitFunctionBody(o, o.funcBody, o.nameRef?.text)
    }

    /**
     * `function f(params) body end` (non-local)
     */
    override fun visitFuncDef(o: LuaFuncDef) {
        visitFunctionBody(o, o.funcBody, o.funcName?.text)
    }

    /**
     * `return [exprlist]`
     *
     * Flow each return expression into the corresponding return-position node
     * tracked by the enclosing [LuaScope.FunctionContext].
     */
    override fun visitReturnStatement(o: LuaReturnStatement) {
        val exprs = o.exprList?.exprList ?: return
        exprs.forEachIndexed { i, expr ->
            val exprNode = graph.getValueNode(expr) ?: return@forEachIndexed
            val retVar = graph.variable(expr)
            graph.flow(exprNode, retVar)
            scope.addReturnType(i, retVar)
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun visitFunctionBody(owner: LuaCatsCommentOwner, body: LuaFuncBody?, name: String?) {
        if (body == null) return

        val paramElements = body.paramList?.paramNameRefList ?: emptyList()
        val paramNames = paramElements.map { it.text }
        val comment = LuaPsiImplUtil.getCatsComment(owner)

        val funcDef = scope.function(paramElements) { params ->
            // Inject @param annotation seeds.
            if (comment != null) {
                LuaTypeGraphBridge.injectParamAnnotations(
                    comment, params, paramNames, graph, typeManager, owner as PsiElement
                )
            }

            // Visit the body (children already visited by bottom-up traversal,
            // but return statements need scope context — revisit explicitly here).
            body.accept(this)
        }

        // Inject @return annotation seeds into the collected return nodes.
        if (comment != null) {
            LuaTypeGraphBridge.injectReturnAnnotations(
                comment, funcDef.returnTypes, graph, typeManager, owner as PsiElement
            )
        }

        // Register the function name in scope if present.
        // Phase 3: synthesise a FunctionType node and flow it here.
        if (name != null) {
            val funcVar = graph.variable(owner as PsiElement)
            scope.set(name, funcVar)
        }
    }

    // -------------------------------------------------------------------------
    // Static API
    // -------------------------------------------------------------------------

    companion object {
        private val typesKey = Key<FileUserData<LuaTypes>>("LuaTypesVisitor.KEY_TYPES")

        /**
         * Retrieve (or compute and cache) the [LuaTypes] snapshot for the file
         * containing [element].
         *
         * Uses the same document-hash-keyed [cacheFileUserData] strategy as
         * [LuaBindingsVisitor] to ensure the graph is invalidated on any edit.
         */
        fun getTypes(element: PsiElement): LuaTypes {
            return typesKey.cacheFileUserData(element) { psiFile ->
                val typeManager = LuaTypeManager.getInstance(psiFile.project)
                visit(psiFile, typeManager)
            }
        }

        private fun visit(file: PsiFile, typeManager: LuaTypeManager): LuaTypes {
            val graph = LuaTypeGraph()
            val scope = LuaScope(graph)
            val visitor = LuaTypesVisitor(graph, scope, typeManager)
            file.accept(visitor)
            return LuaTypesSnapshot(graph)
        }
    }
}
```

---

## 9. `LuaTypesAnnotator` — IDE Integration

**File**: `lang/syntax/LuaTypesAnnotator.kt`  
**Package**: `net.internetisalie.lunar.lang.syntax`

```kotlin
/**
 * IntelliJ [Annotator] that reads the [LuaTypes] snapshot for the current file and:
 *
 * 1. Surfaces [ElementError]s as red-underline (ERROR) annotations with HTML tooltips.
 * 2. Attaches silent INFORMATION annotations to every [LuaExpr] and [LuaNameRef]
 *    showing the inferred type — useful as a developer debugging tool while the
 *    type engine is being developed.
 *
 * Registration in plugin.xml:
 * ```xml
 * <annotator language="Lua"
 *     implementationClass="net.internetisalie.lunar.lang.syntax.LuaTypesAnnotator"/>
 * ```
 */
class LuaTypesAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is LuaExpr && element !is LuaNameRef) return

        val types = LuaTypesVisitor.getTypes(element)

        // Error annotation (Phase 2+: populated by checkTypes; Phase 1: never fires).
        types.getElementError(element)?.let { error ->
            val severity = when (error.severity) {
                ErrorSeverity.ERROR   -> HighlightSeverity.ERROR
                ErrorSeverity.WARNING -> HighlightSeverity.WARNING
            }
            holder.newAnnotation(severity, error.message)
                .tooltip("<html>${error.message}</html>")
                .create()
        }

        // Developer-facing type tooltip (INFORMATION — invisible in production).
        val inferredType = types.getValueType(element)
        if (inferredType != LuaPrimitiveType.UNKNOWN) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .tooltip("<html><b>type:</b> ${inferredType.name}</html>")
                .create()
        }
    }
}
```

---

## 10. `LuaTypeManagerImpl` Cache Fix

**File**: `lang/psi/types/LuaTypeManagerImpl.kt`  
**Change**: Replace the unbounded `ConcurrentHashMap` with a `CachedValue` so the project-level type cache is invalidated on PSI changes.

The current `typeCache: ConcurrentHashMap<String, LuaType?>` never clears. After an edit (e.g., adding a new `@field` to a `@class`), the old cached `LuaClassType` will be served indefinitely.

**Required change**:

```kotlin
// BEFORE (broken — never invalidated):
private val typeCache = ConcurrentHashMap<String, LuaType?>()

// AFTER (correct — invalidated on any PSI modification):
private val typeCache: CachedValue<MutableMap<String, LuaType?>> =
    CachedValuesManager.getManager(project).createCachedValue(
        {
            CachedValueProvider.Result.create(
                ConcurrentHashMap(),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        },
        /* trackValue = */ false
    )

// All read accesses replace `typeCache[name]` with `typeCache.value[name]`.
// All write accesses replace `typeCache[name] = ...` with `typeCache.value[name] = ...`.
```

This is a pure refactor with no behavioural change except correct invalidation. The existing `doResolveType`, `materializeClass`, and `materializeAlias` methods are otherwise unchanged.

---

## 11. plugin.xml Registration

Add the following entry to `src/main/resources/META-INF/plugin.xml` under the `<extensions defaultExtensionNs="com.intellij">` block:

```xml
<!-- Type inference annotator: hover tooltips + error underlines -->
<annotator language="Lua"
    implementationClass="net.internetisalie.lunar.lang.syntax.LuaTypesAnnotator"/>
```

No additional service registrations are required for Phase 1. `LuaTypeManager` is already registered as a project service.

---

## 12. Phase 1 Acceptance Criteria

The following observable behaviours verify Phase 1 is complete:

| Scenario | Expected result |
|---|---|
| `local x = 42` | Hovering `x` shows tooltip `type: number` |
| `local s = "hello"` | Hovering `s` shows tooltip `type: string` |
| `---@type number\nlocal x` | Hovering `x` shows tooltip `type: number` (annotation seed) |
| `---@param n number\nlocal function f(n) end` | Hovering `n` in body shows `type: number` |
| `---@return string\nlocal function f() return "a" end\nlocal r = f()` | Hovering `r` shows `type: string` (Phase 1 won't fully achieve this — function call return type requires Phase 3; record as known limitation) |
| Edit a file (add a character) | Types snapshot is recomputed; cached snapshot from before the edit is not served |
| Add a new `@field` to a `@class` in another file | `LuaTypeManagerImpl` cache clears; next resolution sees the new field |

---

## 13. Known Phase 1 Limitations

These are **not** defects — they are expected and will be addressed in later phases:

- `FunctionType` values are not yet synthesised. Function call return types resolve to `UNKNOWN`.
- `TableType` property nodes are not created. Table literal types resolve to `UNKNOWN`.
- Global variable types are not looked up from `LuaBindings` or the stub index.
- `and`/`or` binary expressions produce `UNKNOWN` (union inference is Phase 5).
- Cross-file `require` resolution is not wired (Phase 6).
- `checkTypes()` always succeeds — no type errors are emitted in Phase 1.
