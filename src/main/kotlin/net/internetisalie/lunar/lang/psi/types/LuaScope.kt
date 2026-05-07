package net.internetisalie.lunar.lang.psi.types

/**
 * A lexical scope that maps variable names to their [VariableNode]s.
 *
 * The scope forms a tree via [parent] references: lookups walk upward through parent scopes
 * until the name is found or the root is reached.
 */
class LuaScope private constructor(
    private val parent: LuaScope?,
    /** Return variable nodes for the innermost enclosing function, or null at file scope. */
    private val returnNodes: MutableList<VariableNode>?,
) {
    private val bindings: MutableMap<String, VariableNode> = mutableMapOf()

    /**
     * Binds [name] to [node] in this scope.
     * Returns true if this name was already defined in the *current* scope level (re-declaration).
     */
    fun declare(name: String, node: VariableNode): Boolean {
        val isRedeclared = bindings.containsKey(name)
        bindings[name] = node
        return isRedeclared
    }

    /** Returns true if [name] is already bound in a parent scope. */
    fun isShadowing(name: String): Boolean = parent?.lookup(name) != null

    /**
     * Resolves [name] starting from this scope, walking upward through parent scopes.
     * Returns null if the name is not bound in any enclosing scope.
     */
    fun lookup(name: String): VariableNode? = bindings[name] ?: parent?.lookup(name)

    /**
     * Returns the return nodes for the innermost enclosing function scope.
     * Walks up if the current scope itself doesn't hold a function boundary.
     */
    fun enclosingReturnNodes(): MutableList<VariableNode>? =
        returnNodes ?: parent?.enclosingReturnNodes()

    /**
     * Creates a child block scope (e.g., `do…end`, `if…then…end`).
     */
    fun child(): LuaScope {
        return LuaScope(parent = this, returnNodes = null)
    }

    /**
     * Creates a function scope with its own return context.
     */
    fun createFunctionScope(returnNodes: MutableList<VariableNode>): LuaScope {
        return LuaScope(parent = this, returnNodes = returnNodes)
    }

    companion object {
        /** Creates a root (file-level) scope. */
        fun root(returnNodes: MutableList<VariableNode>? = null): LuaScope = LuaScope(parent = null, returnNodes = returnNodes)
    }
}
