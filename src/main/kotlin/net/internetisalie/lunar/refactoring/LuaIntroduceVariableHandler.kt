package net.internetisalie.lunar.refactoring

import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
import com.intellij.refactoring.util.CommonRefactoringUtil
import net.internetisalie.lunar.lang.psi.LuaBinOpExpr
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaStatement
import net.internetisalie.lunar.refactoring.rename.LuaNameDeriver

/**
 * Introduce Variable refactoring (REFACT-02): extracts a selected [LuaExpr] into a
 * `local <name> = <expr>` declaration inserted before the enclosing statement and replaces
 * the occurrence(s) with a reference to the new variable.
 *
 * Interactive UI (occurrence chooser, inline-rename template) is skipped under
 * [ApplicationManager.isUnitTestMode] so the handler runs deterministically in fixtures:
 * all equivalent occurrences in the block are replaced and the suggested name is committed.
 */
class LuaIntroduceVariableHandler : RefactoringActionHandler {

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        if (editor == null || file == null) return
        val target = resolveTarget(editor, file)
        if (target == null) {
            showCannotIntroduce(project, editor)
            return
        }
        introduce(project, editor, target)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        // Editor-driven refactoring only; the elements-array entry point is intentionally a no-op.
    }

    private fun resolveTarget(editor: Editor, file: PsiFile): LuaExpr? {
        val selectionModel = editor.selectionModel
        if (selectionModel.hasSelection()) {
            return resolveFromRange(file, selectionModel.selectionStart, selectionModel.selectionEnd)
        }
        val offset = editor.caretModel.offset
        val leaf = file.findElementAt(offset) ?: file.findElementAt((offset - 1).coerceAtLeast(0))
        return PsiTreeUtil.getParentOfType(leaf, LuaExpr::class.java)
    }

    private fun resolveFromRange(file: PsiFile, start: Int, end: Int): LuaExpr? {
        val element = PsiTreeUtil.findElementOfClassAtRange(file, start, end, LuaExpr::class.java)
        if (element != null) return element
        // Selection does not match a single expression exactly: snap to the smallest enclosing one.
        val leaf = file.findElementAt(start)
        val enclosing = PsiTreeUtil.getParentOfType(leaf, LuaExpr::class.java) ?: return null
        return enclosing.takeIf { it.textRange.startOffset <= start && it.textRange.endOffset >= end }
    }

    private fun findAnchor(expr: LuaExpr): LuaStatement? =
        PsiTreeUtil.getParentOfType(expr, LuaStatement::class.java)

    private fun collectOccurrences(target: LuaExpr, block: LuaBlock): List<LuaExpr> {
        val matches = PsiTreeUtil.findChildrenOfType(block, LuaExpr::class.java)
            .filter { it === target || PsiEquivalenceUtil.areElementsEquivalent(it, target) }
        return matches.ifEmpty { listOf(target) }
    }

    private fun introduce(project: Project, editor: Editor, target: LuaExpr) {
        val anchor = findAnchor(target)
        val block = anchor?.parent as? LuaBlock
        if (anchor == null || block == null) {
            showCannotIntroduce(project, editor)
            return
        }
        val name = suggestName(target, block)
        val occurrences = collectOccurrences(target, block)
        // Test mode must be deterministic (no popup): replace every occurrence. A single
        // occurrence needs no choice either. Otherwise let the user pick this-vs-all.
        if (ApplicationManager.getApplication().isUnitTestMode || occurrences.size <= 1) {
            performIntroduce(project, editor, IntroduceContext(target, anchor, block, name, occurrences))
            return
        }
        OccurrencesChooser.simpleChooser<LuaExpr>(editor).showChooser(
            target,
            occurrences,
            Pass.create { choice ->
                val chosen = if (choice == OccurrencesChooser.ReplaceChoice.ALL) occurrences else listOf(target)
                performIntroduce(project, editor, IntroduceContext(target, anchor, block, name, chosen))
            },
        )
    }

    private fun performIntroduce(project: Project, editor: Editor, context: IntroduceContext) {
        val exprText = context.target.text
        WriteCommandAction.runWriteCommandAction(project, RefactoringBundle.message("introduce.variable.title"), null, {
            val throwaway = LuaElementFactory.createFile(project, "local ${context.name} = $exprText")
            val declaration = PsiTreeUtil.findChildOfType(throwaway, LuaStatement::class.java)
                ?: return@runWriteCommandAction
            val block = context.block
            val inserted = block.addBefore(declaration, context.anchor)
            block.addAfter(LuaElementFactory.createNewLine(project), inserted)
            replaceOccurrences(project, context)
            val file = block.containingFile
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
            startInlineRename(project, editor, file, context.name)
        })
    }

    private fun replaceOccurrences(project: Project, context: IntroduceContext) {
        context.occurrences.forEach { occurrence ->
            val reference = LuaElementFactory.createExpression(project, context.name) ?: return@forEach
            occurrence.replace(reference)
        }
    }

    private fun startInlineRename(project: Project, editor: Editor, file: PsiFile, name: String) {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        val declaration = PsiTreeUtil.collectElementsOfType(file, LuaNameRef::class.java)
            .firstOrNull { it.identifier.text == name } ?: return
        val builder = TemplateBuilderImpl(file)
        builder.replaceElement(declaration.identifier, name)
        editor.caretModel.moveToOffset(declaration.textRange.startOffset)
        builder.run(editor, true)
    }

    private fun suggestName(expr: LuaExpr, block: LuaBlock): String {
        val base = baseNameFor(expr)
        return uniquify(base, expr, block)
    }

    private fun baseNameFor(expr: LuaExpr): String =
        LuaNameDeriver.baseName(expr) ?: when (expr) {
            is LuaBinOpExpr -> "result"
            else -> "value"
        }

    private fun uniquify(base: String, target: LuaExpr, block: LuaBlock): String {
        val targetRange = target.textRange
        val taken = PsiTreeUtil.collectElementsOfType(block.containingFile, LuaNameRef::class.java)
            .filterNot { targetRange.contains(it.textRange) }
            .map { it.identifier.text }
            .toSet()
        if (base !in taken) return base
        var index = 1
        while ("$base$index" in taken) index++
        return "$base$index"
    }

    private fun showCannotIntroduce(project: Project, editor: Editor) {
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            RefactoringBundle.message("refactoring.introduce.selection.error"),
            RefactoringBundle.message("introduce.variable.title"),
            null,
        )
    }

    private data class IntroduceContext(
        val target: LuaExpr,
        val anchor: LuaStatement,
        val block: LuaBlock,
        val name: String,
        val occurrences: List<LuaExpr>,
    )
}
