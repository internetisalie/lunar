package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Consumer
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFinalStatement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl

/**
 * NAV-09-03: Integrates with IntelliJ's HighlightUsagesHandlerFactory to provide exit-point
 * highlighting when the caret is on a `return` or `function` keyword.
 */
class LuaReturnHighlightUsagesHandlerFactory : HighlightUsagesHandlerFactoryBase() {

    override fun createHighlightUsagesHandler(
        editor: Editor,
        file: PsiFile,
        target: PsiElement
    ): HighlightUsagesHandlerBase<PsiElement>? {
        if (!isReturnKeyword(target) && !isFunctionKeyword(target)) return null
        return LuaReturnHighlightHandler(editor, file, target)
    }

    private fun isReturnKeyword(element: PsiElement): Boolean =
        element.node.elementType == LuaElementTypes.RETURN

    private fun isFunctionKeyword(element: PsiElement): Boolean =
        element.node.elementType == LuaElementTypes.FUNCTION
}

/**
 * NAV-09-01/02: Collects all `return` keywords in the same function scope as the caret target,
 * excluding returns from nested functions.
 *
 * When [target] is a `return` keyword — highlights all same-scope returns (NAV-09-01).
 * When [target] is a `function` keyword — also highlights the `function` keyword itself (NAV-09-02).
 */
private class LuaReturnHighlightHandler(
    editor: Editor,
    file: PsiFile,
    private val target: PsiElement
) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {

    override fun getTargets(): List<PsiElement> = listOf(target)

    override fun selectTargets(
        targets: List<PsiElement>,
        selectionConsumer: Consumer<in List<PsiElement>>
    ) {
        selectionConsumer.consume(targets)
    }

    override fun computeUsages(targets: List<PsiElement>) {
        val scope = enclosingFunction(target) ?: return
        collectReturns(scope).forEach { addOccurrence(it) }

        // NAV-09-02: when caret is on the `function` keyword, also highlight it
        if (target.node.elementType == LuaElementTypes.FUNCTION) {
            functionKeyword(scope)?.let { addOccurrence(it) }
        }
    }
}

/**
 * Returns the nearest enclosing function node: [LuaFuncDecl], [LuaLocalFuncDecl], [LuaFuncDef],
 * or the [LuaFile] itself for top-level (module) returns.
 */
internal fun enclosingFunction(element: PsiElement): PsiElement? =
    PsiTreeUtil.getParentOfType(
        element,
        LuaFuncDecl::class.java,
        LuaLocalFuncDecl::class.java,
        LuaFuncDef::class.java,
        LuaFile::class.java
    )

/**
 * Collects every `return` keyword leaf under [scope] whose own enclosing function is
 * **identical** to [scope], thereby excluding returns that belong to nested functions.
 */
internal fun collectReturns(scope: PsiElement): List<PsiElement> {
    val returns = mutableListOf<PsiElement>()
    scope.accept(object : PsiRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element.node.elementType == LuaElementTypes.RETURN &&
                element.parent is LuaFinalStatement &&
                enclosingFunction(element) === scope
            ) {
                returns.add(element)
            }
            super.visitElement(element)
        }
    })
    return returns
}

/**
 * Finds the `function` keyword leaf of a function declaration node, if applicable.
 * Returns null for [LuaFile] (top-level scope has no `function` keyword).
 */
private fun functionKeyword(scope: PsiElement): PsiElement? {
    if (scope is LuaFile) return null
    return scope.node.getChildren(null)
        .firstOrNull { it.elementType == LuaElementTypes.FUNCTION }
        ?.psi
}
