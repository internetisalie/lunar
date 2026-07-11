package net.internetisalie.lunar.lang.surround

import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.editor.LuaBlockStructure

/**
 * Registers Lua on `com.intellij.lang.surroundDescriptor`: [getElementsToSurround] returns the whole
 * [net.internetisalie.lunar.lang.psi.LuaStatement]s under the selection; [getSurrounders] exposes the
 * seven templates in requirement-priority order (M → S → C). Design §2.2 / §3.4.
 */
class LuaStatementsSurroundDescriptor : SurroundDescriptor {

    override fun getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array<PsiElement> {
        val block = LuaBlockStructure.enclosingBlock(file, startOffset, endOffset) ?: return PsiElement.EMPTY_ARRAY
        return LuaBlockStructure.statementsInRange(block, startOffset, endOffset)
            .map { it as PsiElement }
            .toTypedArray()
    }

    override fun getSurrounders(): Array<Surrounder> = arrayOf(
        LuaIfSurrounder(),
        LuaWhileSurrounder(),
        LuaNumericForSurrounder(),
        LuaGenericForSurrounder(),
        LuaFunctionSurrounder(),
        LuaDoSurrounder(),
        LuaPcallSurrounder(),
    )

    override fun isExclusive(): Boolean = false
}
