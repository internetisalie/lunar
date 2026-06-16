package net.internetisalie.lunar.analysis.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.analysis.controlflow.ControlFlowCache
import net.internetisalie.lunar.analysis.controlflow.LuaControlFlow
import net.internetisalie.lunar.analysis.controlflow.ScopeOwner
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaStatement

/**
 * Flags statements the ANALYSIS-06 control-flow graph proves can never execute — code after a block
 * whose every path abrupts (`do return end` / `if … then return else return end`) and statements a
 * `goto` skips (INSP-04). It is a pure consumer of the shipped CFG ([ControlFlowCache.getControlFlow] +
 * [LuaControlFlow.isReachable]); it builds no graph and reports exactly the unreachability the engine
 * models (no `error()`/`while true` heuristics — out of v1 scope, feature design §3.1).
 *
 * Each scope owner — the file and every function — is analyzed by its own CFG; per-owner graphs are
 * disjoint (the file graph does not descend into function bodies), so no statement is judged twice.
 */
class LuaUnreachableCodeInspection : LocalInspectionTool() {

    override fun getShortName(): String = "LuaUnreachableCode"

    override fun getGroupDisplayName(): String = "Lua"

    override fun getDisplayName(): String = "Unreachable code"

    override fun isEnabledByDefault(): Boolean = true

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file !is LuaFile) return null
        val heads = scopeOwners(file).flatMap { unreachableHeads(it) }.distinct()
        if (heads.isEmpty()) return null
        return heads.map { stmt ->
            manager.createProblemDescriptor(
                stmt,
                MESSAGE,
                isOnTheFly,
                arrayOf<LocalQuickFix>(LuaRemoveUnreachableCodeQuickFix()),
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            )
        }.toTypedArray()
    }

    /** The owners [ControlFlowCache.getControlFlow] accepts: the file, plus every function at any depth. */
    private fun scopeOwners(file: LuaFile): List<ScopeOwner> = buildList {
        add(file)
        addAll(PsiTreeUtil.findChildrenOfType(file, LuaFuncDecl::class.java))
        addAll(PsiTreeUtil.findChildrenOfType(file, LuaLocalFuncDecl::class.java))
        addAll(PsiTreeUtil.findChildrenOfType(file, LuaFuncDef::class.java))
    }

    /**
     * Returns the *head* statement of every dead run within [owner]'s control flow, in document order.
     * A statement is dead when none of its own (non-nested-statement) instructions are reachable; it is
     * a head when the execution point that precedes it — its previous sibling statement, or, when it is
     * first in its block, the enclosing compound statement — is reachable. So a contiguous dead run is
     * reported once (matching the JetBrains "Unreachable code" UX).
     */
    private fun unreachableHeads(owner: ScopeOwner): List<LuaStatement> {
        val flow = ControlFlowCache.getControlFlow(owner)
        val reachable = statementReachability(flow)
        return reachable.entries
            .asSequence()
            .filter { !it.value }
            .map { it.key }
            .filter { isHeadOfDeadRun(it, reachable) }
            .sortedBy { it.textRange.startOffset }
            .toList()
    }

    /**
     * Maps each [LuaStatement] that owns at least one instruction to whether *any* of those instructions
     * is reachable. An instruction is attributed to its innermost enclosing statement, so a nested
     * `return` is judged independently of the `if`/`do` that contains it.
     */
    private fun statementReachability(flow: LuaControlFlow): Map<LuaStatement, Boolean> {
        val result = mutableMapOf<LuaStatement, Boolean>()
        for (inst: Instruction in flow.instructions) {
            val element = inst.element ?: continue
            val stmt = PsiTreeUtil.getParentOfType(element, LuaStatement::class.java, false) ?: continue
            result[stmt] = (result[stmt] ?: false) || flow.isReachable(inst)
        }
        return result
    }

    private fun isHeadOfDeadRun(stmt: LuaStatement, reachable: Map<LuaStatement, Boolean>): Boolean {
        val previous = previousStatementSibling(stmt)
        if (previous != null) {
            // Continuation when the preceding sibling is itself dead; a reachable (or node-less) one
            // means stmt begins a fresh dead run.
            return reachable[previous] != false
        }
        // First statement in its block: it is a head only when the enclosing compound statement is
        // reachable. If the enclosing statement is itself dead, stmt is part of that dead run.
        val enclosing = PsiTreeUtil.getParentOfType(stmt, LuaStatement::class.java) ?: return true
        return reachable[enclosing] != false
    }

    private fun previousStatementSibling(stmt: LuaStatement): LuaStatement? {
        var sibling = stmt.prevSibling
        while (sibling != null) {
            if (sibling is LuaStatement) return sibling
            sibling = sibling.prevSibling
        }
        return null
    }

    companion object {
        private const val MESSAGE = "Unreachable code"
    }
}

class LuaRemoveUnreachableCodeQuickFix : LocalQuickFix {

    override fun getFamilyName(): String = "Remove unreachable code"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val statement = element as? LuaStatement
            ?: PsiTreeUtil.getParentOfType(element, LuaStatement::class.java)
            ?: return
        WriteCommandAction.runWriteCommandAction(project, "Remove unreachable code", null, {
            statement.delete()
        })
    }
}
