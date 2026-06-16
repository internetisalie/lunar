package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredNames
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaStatement
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.LuaVarList

/**
 * REFACT-06-01: when an undeclared name is the simple write target of an assignment
 * (`x = expr`), declare it as a `local` (`local x = expr`). Offered only on a write target,
 * never on a read or an already-declared name.
 */
class LuaCreateLocalVariableIntention : BaseIntentionAction() {
    private var variableName: String = ""

    override fun getFamilyName(): String = "Lua"
    override fun getText(): String = "Create local variable '$variableName'"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is LuaFile) return false
        val ref = nameRefAt(editor, file) ?: return false
        if (!isSimpleWriteTarget(ref)) return false
        if (!LuaUndeclaredNames.isUnresolvedNonGlobal(ref)) return false
        variableName = ref.identifier.text
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val ref = nameRefAt(editor, file) ?: return
        val assignment = PsiTreeUtil.getParentOfType(ref, LuaAssignmentStatement::class.java) ?: return
        WriteCommandAction.runWriteCommandAction(project, text, null, {
            val throwaway = LuaElementFactory.createFile(project, "local " + assignment.text)
            val declaration = PsiTreeUtil.findChildOfType(throwaway, LuaStatement::class.java)
                ?: return@runWriteCommandAction
            assignment.replace(declaration)
        })
    }

    private fun nameRefAt(editor: Editor, file: PsiFile): LuaNameRef? {
        val offset = editor.caretModel.offset
        val here = PsiTreeUtil.getParentOfType(file.findElementAt(offset), LuaNameRef::class.java)
        if (here != null) return here
        val before = file.findElementAt((offset - 1).coerceAtLeast(0))
        return PsiTreeUtil.getParentOfType(before, LuaNameRef::class.java)
    }

    private fun isSimpleWriteTarget(ref: LuaNameRef): Boolean {
        val luaVar = ref.parent as? LuaVar ?: return false
        return luaVar.varSuffixList.isEmpty() && luaVar.parent is LuaVarList
    }
}
