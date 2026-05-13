package net.internetisalie.lunar.lang.psi

import com.intellij.psi.ResolveState
import com.intellij.psi.PsiElement
import com.intellij.psi.scope.PsiScopeProcessor

/**
 * Extension function to implement processDeclarations for LuaFuncDef.
 *
 * Function expressions have parameters and a body.
 * The function's scope includes: (1) parameter names, (2) statements in the body.
 *
 * Example:
 *   local f = function(x, y)
 *       print(x, y)  -- x and y are in scope
 *   end
 */
fun LuaFuncDef.processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement
): Boolean {
    // 1. Process parameter list first (parameters are visible in body)
    val parList = parList
    if (parList != null) {
        if (!processor.execute(parList, state)) {
            return false  // Processor found match, stop walk
        }
    }

    // 2. Process function body
    val block = block
    if (block != null) {
        return block.processDeclarations(processor, state, lastParent, place)
    }

    return true  // Continue walk to parent scope
}

/**
 * Extension function to implement processDeclarations for LuaFuncDecl.
 *
 * Global function declarations: function foo(x, y) ... end
 * May be methods (using `:` notation): function obj:method(x) end
 * Methods have implicit `self` parameter.
 *
 * Example:
 *   function add(a, b)
 *       return a + b
 *   end
 *   -- Parameters: a, b
 *
 *   function obj:method()
 *       print(self)  -- self is implicit
 *   end
 *   -- Parameters: self (implicit), inherited from declaration
 */
fun LuaFuncDecl.processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement
): Boolean {
    // 1. Process parameter list first (parameters are visible in body)
    val parList = parList
    if (parList != null) {
        if (!processor.execute(parList, state)) {
            return false
        }
    }

    // 2. Process function body
    val block = block
    if (block != null && !block.processDeclarations(processor, state, lastParent, place)) {
        return false
    }

    // 3. Process function name and implicit self (for recursion)
    return processor.execute(this, state)
}

/**
 * Extension function to implement processDeclarations for LuaLocalFuncDecl.
 *
 * Local function declarations: local function foo(x, y) ... end
 * Similar to global functions but scoped to the enclosing block.
 *
 * Example:
 *   local function helper(x)
 *       return x * 2
 *   end
 *   print(helper(5))  -- helper is in scope
 */
fun LuaLocalFuncDecl.processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement
): Boolean {
    // 1. Process parameter list first (parameters are visible in body)
    val parList = parList
    if (parList != null) {
        if (!processor.execute(parList, state)) {
            return false  // Processor found match, stop walk
        }
    }

    // 2. Process function body
    val block = block
    if (block != null && !block.processDeclarations(processor, state, lastParent, place)) {
        return false
    }

    // 3. Process function name (for recursion)
    return processor.execute(this, state)
}
