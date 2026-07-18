package net.internetisalie.lunar.analysis.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import net.internetisalie.lunar.lang.psi.LuaAttName
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaNameList
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.lang.psi.LuaParList
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.LuaVarList

/**
 * Flags local variables, loop variables, and parameters that are declared but never read.
 *
 * On-the-fly inspections run against an in-memory copy of the file that is absent from the project
 * index, so [com.intellij.psi.search.searches.ReferencesSearch] (word-index backed) finds nothing.
 * Instead this performs a single pass over the file: it gathers every declaration and every name
 * reference, then resolves each reference once. Resolution walks the local scope directly — so it
 * works on the copy and, crucially, follows closure captures and shadowing correctly (a control-flow
 * graph would miss captures, since its builder does not descend into nested function bodies). The
 * standard `_` ignored-variable idiom is respected.
 */
class LuaUnusedLocalInspection : LocalInspectionTool() {

    @JvmField
    var checkParameters: Boolean = false

    override fun getShortName(): String = "LuaUnusedLocal"

    override fun getGroupDisplayName(): String = "Lua"

    override fun getDisplayName(): String = "Unused local variable or parameter"

    override fun isEnabledByDefault(): Boolean = true

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

    // TODO: Expose configuration UI for `checkParameters` toggle.

    private class Declaration(val identifier: PsiElement, val anchor: PsiElement, val message: String)

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file !is LuaFile) return null

        val declarations = mutableListOf<Declaration>()
        val declLeaves = mutableSetOf<PsiElement>()
        val declNames = mutableSetOf<String>()
        val usages = mutableListOf<LuaNameRef>()

        file.accept(
            object : PsiRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    when (element) {
                        is LuaNameRef -> classify(element, declarations, declLeaves, declNames, usages)
                        is LuaNumericForStatement ->
                            record(element.identifier, element.identifier, declarations, declLeaves, declNames)
                    }
                    super.visitElement(element)
                }
            },
        )
        if (declarations.isEmpty()) return null

        val used = collectUsedDeclarations(usages, declNames, declLeaves)

        return declarations
            .filter { it.identifier.text != "_" && it.identifier !in used }
            .map {
                manager.createProblemDescriptor(
                    it.anchor,
                    "${it.message} '${it.identifier.text}'",
                    isOnTheFly,
                    LocalQuickFix.EMPTY_ARRAY,
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                )
            }
            .toTypedArray()
    }

    private fun classify(
        nameRef: LuaNameRef,
        declarations: MutableList<Declaration>,
        declLeaves: MutableSet<PsiElement>,
        declNames: MutableSet<String>,
        usages: MutableList<LuaNameRef>,
    ) {
        val parent = nameRef.parent
        when {
            parent is LuaAttName ->
                record(nameRef.identifier, nameRef, declarations, declLeaves, declNames, "Unused local variable")
            parent is LuaNameList && parent.parent is LuaGenericForStatement ->
                record(nameRef.identifier, nameRef, declarations, declLeaves, declNames, "Unused local variable")
            parent is LuaNameList && parent.parent is LuaParList -> {
                if (checkParameters) {
                    record(nameRef.identifier, nameRef, declarations, declLeaves, declNames, "Unused parameter")
                }
            }
            isSimpleWriteTarget(nameRef) -> Unit
            else -> usages.add(nameRef)
        }
    }

    private fun isSimpleWriteTarget(nameRef: LuaNameRef): Boolean {
        val luaVar = nameRef.parent as? LuaVar ?: return false
        return luaVar.varSuffixList.isEmpty() && luaVar.parent is LuaVarList
    }

    private fun record(
        identifier: PsiElement,
        anchor: PsiElement,
        declarations: MutableList<Declaration>,
        declLeaves: MutableSet<PsiElement>,
        declNames: MutableSet<String>,
        message: String = "Unused local variable",
    ) {
        declarations.add(Declaration(identifier, anchor, message))
        declLeaves.add(identifier)
        declNames.add(identifier.text)
    }

    private fun collectUsedDeclarations(
        usages: List<LuaNameRef>,
        declNames: Set<String>,
        declLeaves: Set<PsiElement>,
    ): Set<PsiElement> {
        val used = mutableSetOf<PsiElement>()
        for (usage in usages) {
            if (usage.identifier.text !in declNames) continue
            val reference = usage.reference as? PsiPolyVariantReference ?: continue
            for (result in reference.multiResolve(false)) {
                val target = result.element ?: continue
                if (target in declLeaves) used.add(target)
            }
        }
        return used
    }
}
