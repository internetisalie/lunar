package net.internetisalie.lunar.lang.syntax

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Quick fix that upgrades the project's configured language level to support the used feature.
 */
class UpgradeLanguageLevelFix(
    private val requiredLevel: LuaLanguageLevel
) : BaseIntentionAction() {

    override fun getFamilyName(): String = "Lua Language Level"

    override fun getText(): String = "Upgrade project to Lua $requiredLevel"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val settings = LuaProjectSettings.getInstance(project)
        settings.state.languageLevel = requiredLevel
    }

    override fun startInWriteAction(): Boolean = true
}

/**
 * Quick fix that removes a goto statement.
 */
class RemoveGotoFix : BaseIntentionAction() {

    override fun getFamilyName(): String = "Lua Language Level"

    override fun getText(): String = "Remove goto statement"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        
        // Find parent goto statement
        var current: PsiElement? = element
        while (current != null) {
            if (current.javaClass.simpleName == "LuaGotoStatement") {
                current.delete()
                return
            }
            current = current.parent
        }
    }

    override fun startInWriteAction(): Boolean = true
}

/**
 * Quick fix that removes a label.
 */
class RemoveLabelFix : BaseIntentionAction() {

    override fun getFamilyName(): String = "Lua Language Level"

    override fun getText(): String = "Remove label"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        
        // Find parent label
        var current: PsiElement? = element
        while (current != null) {
            if (current.javaClass.simpleName == "LuaLabel") {
                current.delete()
                return
            }
            current = current.parent
        }
    }

    override fun startInWriteAction(): Boolean = true
}

/**
 * Quick fix that replaces integer division with regular division and floor.
 */
class ReplaceIntegerDivisionFix : BaseIntentionAction() {

    override fun getFamilyName(): String = "Lua Language Level"

    override fun getText(): String = "Replace // with / and math.floor()"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        
        // Find the // operator and replace it
        var current: PsiElement? = element
        while (current != null) {
            if (current.text.contains("//")) {
                val replacement = current.text.replace("//", "/")
                // Wrap in math.floor for proper integer division semantics
                val newText = "math.floor($replacement)"
                
                // Use document to replace
                val document = editor.document
                val startOffset = current.textRange.startOffset
                val endOffset = current.textRange.endOffset
                document.replaceString(startOffset, endOffset, newText)
                return
            }
            current = current.parent
        }
    }

    override fun startInWriteAction(): Boolean = true
}
