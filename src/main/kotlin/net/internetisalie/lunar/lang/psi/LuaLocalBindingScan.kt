package net.internetisalie.lunar.lang.psi

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil

/**
 * COMP-09-10 / design §4.14 — **Rule S**, the shadowing rule the index arm is gated on.
 *
 * > The index arm may be taken only if the consumer file contains **no binding occurrence of the
 * > receiver name anywhere in it**. Any binding occurrence, at any depth, routes to today's path.
 *
 * **Derived, not invented.** Today's precedence lives in `LuaTypesVisitor.visitNameRef`: `scope.lookup`
 * first, and only a name the file's scope chain does not bind falls through to `freeGlobalSeed` →
 * `resolveGlobal`. This is that predicate with the *lexical position* refinement dropped, so it is
 * exhaustive over the ten `LuaScope.declare` sites in `LuaTypesVisitor` rather than precise. The
 * over-approximation is deliberate and its asymmetry is the whole argument: `do local Shadow = {} end`
 * followed by `Shadow.<caret>` reports "bound" where `scope.lookup` at the caret would not, which routes
 * to today's path and costs **latency only**. Missing a binding sends a shadowed receiver to the index
 * and **invents members the user never declared**.
 *
 * **`LuaFileBindingsIndex` is not usable here and must not be substituted** (design §4.14, "Prior art,
 * named and rejected"). Its `extractBindings` walks `file.getBlockList().forEach { block ->
 * block.statementList… }` — *file-scope statements only* — so it sees no parameter, no for-loop
 * variable and no nested `local`, which is the member-inventing direction. It is also stale for the
 * completion **copy**, which is not the file it indexed.
 *
 * **`seedAmbientGlobals` (`LuaTypesVisitor.kt:1360`) is deliberately outside this rule.** It declares a
 * *stub file's* globals into the consumer's scope, and Rule S asks about the consumer file. The omission
 * is measured rather than assumed: it fires only on Redis/Valkey, where it declares exactly `KEYS` and
 * `ARGV`, and the index answers those identically to today (design §1.10.8, TC 10h).
 *
 * Cost is O(file) — 18–30 µs small, 8–16 ms on a 4 002-line file — which is why the call site asks the
 * index **first** and only reaches here when the index could actually answer (design §4.13 rule 2).
 */
object LuaLocalBindingScan {
    /**
     * `self` is bound by every method body (`LuaTypesVisitor.kt:1414`) and is never a global.
     *
     * It is the one clause with no PSI shape behind it, so no clause deletion can express it — TC 10i
     * is its gate and its separate mutation proof (design §4.14).
     */
    private const val SELF_NAME = "self"

    /** True if [file] binds [name] anywhere in it, over-approximating `LuaScope.lookup`. */
    fun binds(
        file: PsiFile,
        name: String,
    ): Boolean {
        if (name == SELF_NAME) return true
        var bound = false
        PsiTreeUtil.processElements(file) { element ->
            ProgressManager.checkCanceled()
            bound = declaresName(element, name)
            !bound
        }
        return bound
    }

    /** The seven binding forms of design §4.14's table, one clause each. */
    private fun declaresName(
        element: PsiElement,
        name: String,
    ): Boolean =
        when (element) {
            is LuaLocalVarDecl -> element.attNameList.any { it.nameRef.text == name }
            is LuaLocalFuncDecl -> childText(element, LuaElementTypes.NAME_REF) == name
            is LuaFuncDecl -> funcDeclBaseName(element) == name
            is LuaParList -> bindsInNameList(element.nameList, name)
            is LuaGenericForStatement -> bindsInNameList(element.nameList, name)
            is LuaNumericForStatement -> childText(element, LuaElementTypes.IDENTIFIER) == name
            is LuaAssignmentStatement -> element.varList.varList.any { bareTargetName(it) == name }
            else -> false
        }

    /**
     * The parameter list (`LuaTypesVisitor.kt:1425`) and the generic-`for` variable list
     * (`:539`) are the same PSI shape, so they are the same clause twice. Nullable on `parList`, which
     * is `'...'` alone in its varargs-only form.
     */
    private fun bindsInNameList(
        nameList: LuaNameList?,
        name: String,
    ): Boolean {
        val nameRefs = nameList?.nameRefList ?: return false
        return nameRefs.any { it.text == name }
    }

    /**
     * Read through the AST node rather than the generated getter: SYNTAX-18 made a pinned partial
     * declaration able to lack the child its `@NotNull` getter promises, and `LuaTypesVisitor` reads
     * these same two the same way for the same reason.
     */
    private fun childText(
        element: PsiElement,
        childType: IElementType,
    ): String? = element.node.findChildByType(childType)?.text

    /** `function R.m()` binds a fresh `R` when nothing else did (`LuaTypesVisitor.kt:776`). */
    private fun funcDeclBaseName(decl: LuaFuncDecl): String? {
        val funcNameNode = decl.node.findChildByType(LuaElementTypes.FUNC_NAME) ?: return null
        val funcName = funcNameNode.psi as? LuaFuncName ?: return null
        return funcName.nameRef.text
    }

    /** A suffix-free `R = …` target — the file-scope global write of `declareFileGlobals`. */
    private fun bareTargetName(target: LuaVar): String? =
        if (target.varSuffixList.isEmpty()) target.nameRef?.text else null
}
