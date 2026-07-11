package net.internetisalie.lunar.lang.surround

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.editor.LuaBlockStructure
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaStatement

/**
 * Shared skeleton for the seven statement-list surrounders: validate whole-statement input, build the
 * wrapped source, swap it in under a write command, reformat, and place the caret. Subclasses supply only
 * the template shape via [wrap]. Stateless; retains no `Project`/`Editor`. Design §2.3 / §3.1.
 */
abstract class LuaStatementSurrounder(private val description: String) : Surrounder {

    final override fun getTemplateDescription(): String = description

    final override fun isApplicable(elements: Array<PsiElement>): Boolean =
        elements.isNotEmpty() && elements.all { it is LuaStatement }

    final override fun surroundElements(project: Project, editor: Editor, elements: Array<PsiElement>): TextRange? {
        val statements = elements.filterIsInstance<LuaStatement>()
        if (statements.isEmpty()) return null
        val source = wrap(LuaBlockStructure.statementsText(statements))
        var caret = -1
        WriteCommandAction.runWriteCommandAction(project) {
            caret = applyWrap(project, statements, source)
        }
        return if (caret < 0) null else TextRange(caret, caret)
    }

    private fun applyWrap(project: Project, statements: List<LuaStatement>, source: String): Int {
        val dummy = LuaElementFactory.createFile(project, source)
        val newStatement = PsiTreeUtil.findChildOfType(dummy, LuaStatement::class.java) ?: return -1
        val inserted = LuaBlockStructure.replaceStatements(statements.first(), statements.last(), newStatement)
        return LuaBlockStructure.caretAfterWrap(inserted)
    }

    /** Replacement Lua source wrapping [bodyText]; header templates embed [LuaBlockStructure.CARET]. Design §2.4. */
    protected abstract fun wrap(bodyText: String): String
}
