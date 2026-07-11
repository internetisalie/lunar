package net.internetisalie.lunar.lang.insight.unwrap

import com.intellij.codeInsight.unwrap.AbstractUnwrapper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import net.internetisalie.lunar.lang.psi.LuaBlock
import org.jetbrains.annotations.Nls

/**
 * Lua base for the unwrap/remove operations: supplies the whitespace-aware [Context] so subclasses only
 * implement `isApplicableTo` + `doUnwrap`. The platform runs `unwrap` inside its own `WriteCommandAction`
 * (from `UnwrapHandler`) and `collectAffectedElements` read-only for the preview — subclasses must not open
 * their own write command. Stateless. Design §2.2.
 */
abstract class LuaUnwrapper(description: @Nls String) : AbstractUnwrapper<LuaUnwrapper.Context>(description) {

    override fun createContext(): Context = Context()

    class Context : AbstractContext() {
        override fun isWhiteSpace(element: PsiElement): Boolean = element is PsiWhiteSpace

        /** Hoist [block]'s statements before [from] via the platform `extract`/`addRangeBefore` path (§2.2). */
        fun extractBlockBody(block: LuaBlock, from: PsiElement) {
            val statements = block.statementList
            val first = statements.firstOrNull() ?: return
            extract(first, statements.last(), from)
        }
    }
}
