package net.internetisalie.lunar.lang.completion

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import net.internetisalie.lunar.lang.psi.LuaArgs
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.lang.syntax.extractLuaString

/**
 * Detects whether a module path is already pulled in by an existing `require(...)` call,
 * so auto-import never inserts a duplicate (COMP-03-AC-05, TC-03-06).
 *
 * Matching is by module path only, independent of the local binding name. `require` calls
 * with non-string-literal arguments are ignored intentionally (the path is not statically
 * known). Mirrors the require-extraction performed by `LuaFileBindingsIndex`.
 */
object LuaDeduplicationChecker {

    fun isAlreadyRequired(file: LuaFile, modulePath: String): Boolean =
        modulePath in collectRequirePaths(file)

    private fun collectRequirePaths(file: LuaFile): Set<String> {
        val paths = mutableSetOf<String>()
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is LuaFuncCall) {
                    extractRequirePath(element)?.let { paths.add(it) }
                }
                super.visitElement(element)
            }
        })
        return paths
    }

    private fun extractRequirePath(call: LuaFuncCall): String? {
        val luaVar = call.varOrExp?.`var` ?: return null
        if (luaVar.nameRef?.identifier?.text != "require") return null

        val args = call.nameAndArgsList.firstOrNull()?.args ?: return null
        val stringElem = args.string ?: singleExprListString(args)
        return stringElem?.let { extractLuaString(it.text) }
    }

    private fun singleExprListString(args: LuaArgs): PsiElement? {
        val exprList = args.exprList?.exprList ?: return null
        val single = exprList.singleOrNull() as? LuaTerminalExpr ?: return null
        return single.string
    }
}
