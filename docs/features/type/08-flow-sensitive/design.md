---
id: "TYPE-08-DESIGN"
title: "Technical Design"
type: "design"
status: "todo"
parent_id: "TYPE-08"
folders:
  - "[[features/type/08-flow-sensitive/requirements|requirements]]"
---

# Technical Design: TYPE-08 — Flow-Sensitive Types

## 1. Architecture Overview

### Current State

`LuaTypesVisitor.visitIfStatement` (`LuaTypesVisitor.kt:165`) evaluates condition expressions
and visits each block in a child scope, but conditions do not influence the types of variables
inside those blocks. A guard like `if type(x) == "string" then` evaluates to `Boolean` and
propagates no type constraint on `x`. The user must add `---@type string` inside the block to
get narrowing.

The `LuaScope` class (`LuaScope.kt:9`) supports `child()` blocks and `declare(name, node)` —
scope injection into blocks is already a well-worn path (e.g., `visitLocalVarDecl` declares
locals; `visitCreateFunctionScope` injects `self` for `:` methods).

### Prior Art in This Repo
- **`LuaTypesVisitor.visitIfStatement`** (`src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt:165`) — already visits conditions and creates child scopes per block. This design **EXTENDS** that method, not replaces it. `visitBlock` (`LuaTypesVisitor.kt:155`) already calls `scope.child()`, so narrowing is injected *after* child-scope creation but *before* block traversal.
- **`LuaScope.declare`** (`LuaScope.kt:20`) — binds a name to a `VariableNode` in the current scope. The narrowing mechanism shadows the original variable binding.
- **`self` injection in `visitFunctionBody`** (`LuaTypesVisitor.kt:584-588`) — the pattern for injecting a synthetic binding before visiting a block: `funcScope.declare("self", selfNode)`. The flow-sensitive design follows this same pattern.
- **`LuaTypeAlgebra`** — the existing union canonicalization machinery. A "union minus" helper (remove one member from a `LuaGraphType.Union`) will be added to this utility.

### Target State

`visitIfStatement` is extended to detect type-guard patterns in condition expressions. When a
guard is recognized, the block's child scope receives a narrowed `VariableNode` bound to the
guarded variable name. The original variable's `VariableNode` remains in the enclosing scope,
so narrowing is block-local.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor` (modified)

No new top-level class — the feature modifies the existing `visitIfStatement` and adds a
private helper object.

- **Responsibility**: detect type-guard patterns in `LuaIfStatement` conditions and inject
  narrowed scope bindings before visiting each block.
- **Threading**: runs inside `LuaTypesSnapshot.forFile()` → read action (unchanged).
- **Collaborators**: `LuaScope.declare()`, `LuaTypeGraph.variable()`, `LuaTypeGraph.value()`.
- **Key API** (new private members):

```kotlin
// LuaTypesVisitor — additions

/** Parsed type guard extracted from a conditional expression. */
private data class TypeGuard(
    val variableName: String,         // the guarded local/param name
    val narrowedType: LuaGraphType,   // the type to narrow *into* on match
    val removeType: LuaGraphType,     // the type to *remove* on match (= narrowedType for equality, complement for inequality)
)

companion object {
    /** Maps type() return strings → LuaGraphType. */
    private val TYPEOF_MAP: Map<String, LuaGraphType> = mapOf(...)

    /** Try to recognize a type() guard: type(v) ==/!= "typename". Returns null if no match. */
    private fun tryParseTypeofGuard(condition: LuaExpr): TypeGuard? { ... }

    /** Try to recognize a nil guard: v ==/!= nil. Returns null if no match. */
    private fun tryParseNilGuard(condition: LuaExpr): TypeGuard? { ... }

    /** Subtract `remove` from `original` union. Returns `remove` if `original` is the bare `remove` type. */
    private fun subtractType(original: LuaGraphType, remove: LuaGraphType): LuaGraphType { ... }
}
```

### 2.2 `net.internetisalie.lunar.lang.psi.types.LuaTypeAlgebra` (modified — union-minus)

A new companion function `subtractMember(union: LuaGraphType.Union, member: LuaGraphType): LuaGraphType`
removes a member from a union and re-canonicalizes.

```kotlin
// LuaTypeAlgebra companion addition
fun subtractMember(union: LuaGraphType.Union, toRemove: LuaGraphType): LuaGraphType {
    val remaining = union.types.filterNot { it == toRemove }.toSet()
    return when {
        remaining.isEmpty() -> LuaGraphType.Undefined
        remaining.size == 1 -> remaining.first()
        else -> create(remaining)
    }
}
```

## 3. Algorithms

### 3.1 `tryParseTypeofGuard(condition: LuaExpr): TypeGuard?`

**Input → Output**: `LuaExpr` (the condition of an `if`/`elseif`) → `TypeGuard?` (null if
the expression is not a recognized type-guard).

**Steps**:

1. **Check bin-op**: `condition` must be a `LuaBinOpExpr`. If not, return null.
   ```kotlin
   val binOp = (condition as? LuaBinOpExpr) ?: return null
   ```

2. **Identify left and right**: `val left = binOp.left; val right = binOp.right ?: return null`

3. **Determine orientation**: The `type()` function call can be on either `left` or `right`.
   - If `left` is a `LuaFuncCall` with callee `"type"` → it is the `type()` side; `right` is the string.
   - Else if `right` is a `LuaFuncCall` with callee `"type"` → swap: `right` is the `type()` side; `left` is the string.
   - Else return null (not a `type()` guard).

   Callee check: `(funcCall.varOrExp as? LuaNameRef)?.text == "type"`

4. **Extract guarded variable name**: from the `LuaFuncCall`, get the first positional argument:
   ```kotlin
   val nameAndArgs = funcCall.nameAndArgsList.firstOrNull() ?: return null
   val exprList = nameAndArgs.args.exprList ?: return null
   val arg0 = exprList.exprList.firstOrNull() ?: return null
   val varNameRef = (arg0.unwrapExpr()) as? LuaNameRef ?: return null
   val variableName = varNameRef.text
   ```
   `unwrapExpr()` here means: if `arg0` is a `LuaExpr` wrapper of a `LuaVar` containing a bare `LuaNameRef`, unwrap to the `LuaNameRef`. (Use `PsiTreeUtil.findChildOfType(arg0, LuaNameRef::class.java)` as a simpler fallback.)

5. **Extract type-name string**: the string side must be a `LuaTerminalExpr` with a non-null `string` child:
   ```kotlin
   val terminal = (stringSide as? LuaTerminalExpr) ?: return null
   val typeName = terminal.string?.text?.trim('"', '\'') ?: return null
   ```

6. **Map to `LuaGraphType`**: look up `typeName` in the `TYPEOF_MAP`.
   ```kotlin
   val narrowedType = TYPEOF_MAP[typeName] ?: LuaGraphType.Any
   ```
   `TYPEOF_MAP`:
   ```kotlin
   mapOf(
       "string"   to LuaGraphType.String,
       "number"   to LuaGraphType.Number,
       "boolean"  to LuaGraphType.Boolean,
       "nil"      to LuaGraphType.Nil,
       "table"    to LuaGraphType.Table(),
       "function" to LuaGraphType.Function(emptyList(), emptyList()),
       "thread"   to LuaGraphType.Any,
       "userdata" to LuaGraphType.Any,
   )
   ```

7. **Determine match polarity**: get the bin-op token text.
   ```kotlin
   val opText = binOp.binOp.text  // "==" or "~="
   ```
   - `==`: `TypeGuard(variableName, narrowedType = narrowedType, removeType = narrowedType)`
     → on match, the branch gets `narrowedType`; the complement branch gets original minus `removeType`.
   - `~=`: `TypeGuard(variableName, narrowedType = narrowedType, removeType = LuaGraphType.Any)`
     → on match, the branch gets original *with* narrowedType removed (the branch where `~=` is true means "not that type"); we set `removeType = narrowedType` and signal the negation. Actually, simplify: for `~=`, the guard means "the type is NOT narrowedType", so we store `narrowedType` as the type to inject when the guard FAILS (the else branch gets `narrowedType`).

   **Clarified polarity model**:
   ```kotlin
   val isEquality = opText == "=="
   // For ==: match-branch gets narrowedType; else-branch gets original minus narrowedType
   // For ~=: match-branch gets original minus narrowedType; else-branch gets narrowedType
   ```

   Return: `TypeGuard(variableName, narrowedType, isEquality)`

### 3.2 `tryParseNilGuard(condition: LuaExpr): TypeGuard?`

**Steps**:

1. `condition` must be `LuaBinOpExpr` with bin-op `==` or `~=`.
2. One side must be a `LuaTerminalExpr` with `NIL` token (check: `TerminalExpr.firstChild.elementType == LuaElementTypes.NIL`).
3. The other side must resolve to a `LuaNameRef` → the guarded variable name.
4. Return `TypeGuard(variableName, LuaGraphType.Nil, isEquality)`.

### 3.3 `subtractType(original: LuaGraphType, remove: LuaGraphType): LuaGraphType`

Powers the "original minus guard type" narrowing.

**Input → Output**: `(LuaGraphType, LuaGraphType)` → `LuaGraphType`

**Steps**:
1. If `original == remove`: return `LuaGraphType.Undefined`.
2. If `original is LuaGraphType.Union`:
   - Delegate to `LuaTypeAlgebra.subtractMember(original, remove)`.
3. If `original is LuaGraphType.Any`: return `LuaGraphType.Any` (can't meaningfully subtract from top).
4. Otherwise: return `original` (the type is a singleton and `remove` doesn't match — subtraction has no effect).

### 3.4 Modified `visitIfStatement(o: LuaIfStatement)`

**Steps**:

1. **Parse guards** for each condition expression:
   ```kotlin
   val exprs = o.exprList
   val guards = exprs.map { expr ->
       tryParseTypeofGuard(expr) ?: tryParseNilGuard(expr)
   }
   ```

2. **For each block**, compute the narrowed scope and visit:
   ```kotlin
   val blocks = o.blockList
   for (i in blocks.indices) {
       val block = blocks[i]
       val previousScope = scope
       scope = scope.child()
       try {
           if (guards.getOrNull(i) != null) {
               val guard = guards[i]
               val originalNode = scope.lookup(guard.variableName)
               if (originalNode != null) {
                   injectNarrowedBinding(guard, originalNode, matchBranch = true)
               }
           }
           // For the last block (else / fallthrough), inject complements of all preceding guards
           if (i == blocks.lastIndex && guards.any { it != null }) {
               guards.filterNotNull().forEach { guard ->
                   val originalNode = scope.lookup(guard.variableName)
                   if (originalNode != null) {
                       injectNarrowedBinding(guard, originalNode, matchBranch = false)
                   }
               }
           }
           block.accept(this)
       } finally {
           scope = previousScope
       }
   }
   ```

3. **`injectNarrowedBinding(guard, originalNode, matchBranch)`**:
   ```kotlin
   private fun injectNarrowedBinding(guard: TypeGuard, originalNode: VariableNode, matchBranch: Boolean) {
       val narrowedType = if (matchBranch == guard.isEquality) {
           guard.narrowedType            // match: "==" means narrowedType
       } else {
           // complement: subtract narrowedType from the original
           subtractType((originalNode as ValueNode).write, guard.narrowedType)
       }
       val narrowedNode = graph.value(originalNode.element, narrowedType) as ValueNode
       // Create a variable node that flows from the narrowed value
       val varNode = graph.variable(guard.variableName as PsiElement) // anchor element: we need a real PsiElement; use the condition expression
       graph.addEdge(narrowedNode, varNode as VariableNode)
       scope.declare(guard.variableName, varNode as VariableNode)
   }
   ```

   **Correction on anchoring**: The `VariableNode` needs a unique `PsiElement` anchor. Use the guard's condition expression (`guard.anchor`). The `TypeGuard` data class stores:
   ```kotlin
   private data class TypeGuard(
       val variableName: String,
       val narrowedType: LuaGraphType,
       val isEquality: Boolean,
       val anchor: PsiElement,  // the condition expr, for graph node anchoring
   )
   ```

## 4. External Data & Parsing

This feature consumes no external data — all input is the Lua PSI tree already built by the
parser. No CLI output, network responses, or file formats are involved.

## 5. Data Flow

### Example 1: `if type(x) == "string" then … end`

```
LuaIfStatement
├── exprList[0]: LuaBinOpExpr(op="==")
│   ├── left: LuaFuncCall(callee="type", args=[LuaNameRef("x")])
│   └── right: LuaTerminalExpr(string="string")
├── blockList[0]: LuaBlock (then)
```

1. `visitIfStatement` called. `exprList[0]` parsed by `tryParseTypeofGuard` →
   `TypeGuard(variableName="x", narrowedType=String, isEquality=true)`.
2. `blockList[0]` is a match branch → `injectNarrowedBinding(guard, originalNode, matchBranch=true)`.
3. `scope.lookup("x")` finds the `VariableNode` from the local declaration, whose write is
   `LuaGraphType.String | LuaGraphType.Number` (union).
4. New `ValueNode(graph.value(condExpr, LuaGraphType.String))` created.
5. New `VariableNode(graph.variable(condExpr))` flows from the value node.
6. `scope.declare("x", narrowedVarNode)` — shadows the enclosing scope's `x`.
7. `block.accept(this)` → any `visitNameRef("x")` inside the block now resolves to the
   narrowed `VariableNode` whose `write` is `LuaGraphType.String`.

## 6. Edge Cases

1. **Variable not in scope**: if `scope.lookup(variableName)` returns null, the guard is
   silently ignored — no narrowing, no error.
2. **Guard on non-typed variable**: if the variable has type `LuaGraphType.Any` (uninferred),
   `subtractType(Any, String)` returns `Any` — narrowing has no effect, which is correct.
3. **Nested if with same variable**: inner `if` guards create a new child scope each time;
   the outer scope binding is preserved (standard scope nesting).
4. **Guard inside a loop**: `while type(x) == "string" do … end` — not handled by this
   feature (only `if`/`elseif`/`else`), so no narrowing occurs. This is intentional scope
   (see "Out of Scope" in requirements). The `elseif` with no else block has `blockList.size > exprList.size` →
   the last block is the else block; guards on `exprList[0..k]` match `blockList[0..k]`.
6. **`type()` with multiple args**: `type(x, extra)` — the guard is not recognized (only
   single-arg `type()` calls match).
7. **String interning**: the guard's string `"string"` includes both double-quoted and
   single-quoted forms — the `.string` PSI child handles both.

## 7. Integration Points

No `plugin.xml` registrations are needed. The feature modifies the existing type-engine
internals only:

- `LuaTypesVisitor.kt` — modified `visitIfStatement`, new private helpers.
- `LuaTypeAlgebra.kt` — new `subtractMember` companion function.
- `LuaTypeNodes.kt` — unchanged (uses existing `VariableNode`, `ValueNode`, `graph.variable/element`).

These are all internal to `net.internetisalie.lunar.lang.psi.types` — no new extension
points, services, or IDE surfaces.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TYPE-08-01 | M | §3.1 (`tryParseTypeofGuard`), §3.4 (modified `visitIfStatement`) |
| TYPE-08-02 | M | §3.1 (polarity model with `~=`), §3.4 |
| TYPE-08-03 | S | §3.2 (`tryParseNilGuard`), §3.4 |
| TYPE-08-04 | S | §3.2, §3.4 |
| TYPE-08-05 | S | §3.4 (per-block loop handles `elseif` chains — each `elseif` gets its own child scope and its own guard) |
| TYPE-08-06 | C | §3.4 (the "last block gets complement of ALL guards" logic) |

## 9. Alternatives Considered

1. **ControlFlow-based approach**: use `LuaControlFlowBuilder` to annotate the CFG with
   guard information, then query it during type resolution. **Rejected** because the type
   engine is purely tree-walk (no CFG integration), and wiring them together would require
   a much larger refactor. The scope-injection approach works entirely within the existing
   visitor pattern.
2. **Full predicate logic**: track compound conditions (`and`/`or`/`not`) and solve guard
   predicates algebraically. **Deferred** to `TYPE-08-02` (Could) — the single-pattern
   approach covers the vast majority of real-world Lua type guards.

## 10. Open Questions

_None — feature has cleared the planning bar. All decisions are specified above;
open deferred items (compound guards, loop guards) live in `risks-and-gaps.md`._