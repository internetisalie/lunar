package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredNames
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaStatement

/**
 * REFACT-06-02: when an undeclared name is the callee of a call (`myFunc(...)`), generate a
 * `local function myFunc(arg1, …, argN) end` stub above the enclosing top-level statement, with
 * N params matching the call's positional argument count. Not offered when the callee is already
 * declared or is a member access (`obj.method(...)`).
 */
class LuaCreateFunctionIntention : BaseIntentionAction() {
    private var functionName: String = ""

    override fun getFamilyName(): String = "Lua"
    override fun getText(): String = "Create function '$functionName'"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is LuaFile) return false
        val ref = nameRefAt(editor, file) ?: return false
        val callee = calleeOf(ref) ?: return false
        if (callee !== ref) return false
        if (!LuaUndeclaredNames.isUnresolvedNonGlobal(callee)) return false
        functionName = callee.identifier.text
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val ref = nameRefAt(editor, file) ?: return
        val call = PsiTreeUtil.getParentOfType(ref, LuaFuncCall::class.java) ?: return
        val anchor = PsiTreeUtil.getParentOfType(call, LuaStatement::class.java) ?: return
        val block = anchor.parent as? LuaBlock ?: return
        val params = (1..argumentCount(call)).joinToString(", ") { "arg$it" }
        val stub = "local function $functionName($params)\nend"
        WriteCommandAction.runWriteCommandAction(project, text, null, {
            val throwaway = LuaElementFactory.createFile(project, stub)
            val declaration = PsiTreeUtil.findChildOfType(throwaway, LuaStatement::class.java)
                ?: return@runWriteCommandAction
            val inserted = block.addBefore(declaration, anchor)
            block.addAfter(LuaElementFactory.createNewLine(project), inserted)
        })
    }

    private fun nameRefAt(editor: Editor, file: PsiFile): LuaNameRef? {
        val offset = editor.caretModel.offset
        val here = PsiTreeUtil.getParentOfType(file.findElementAt(offset), LuaNameRef::class.java)
        if (here != null) return here
        val before = file.findElementAt((offset - 1).coerceAtLeast(0))
        return PsiTreeUtil.getParentOfType(before, LuaNameRef::class.java)
    }

    private fun calleeOf(ref: LuaNameRef): LuaNameRef? {
        val call = PsiTreeUtil.getParentOfType(ref, LuaFuncCall::class.java) ?: return null
        val varNode = call.varOrExp.`var` ?: return null
        if (varNode.varSuffixList.isNotEmpty()) return null
        return varNode.nameRef
    }

    private fun argumentCount(call: LuaFuncCall): Int =
        call.nameAndArgsList.firstOrNull()?.args?.exprList?.exprList?.size ?: 0
}
