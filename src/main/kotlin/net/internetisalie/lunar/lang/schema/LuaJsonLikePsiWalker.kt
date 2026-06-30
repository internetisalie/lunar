package net.internetisalie.lunar.lang.schema

import com.intellij.json.pointer.JsonPointerPosition
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaExprList
import net.internetisalie.lunar.lang.psi.LuaField
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFinalStatement
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaExprStatement
import net.internetisalie.lunar.lang.psi.LuaStatement
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr

class LuaJsonLikePsiWalker : JsonLikePsiWalker {
    companion object {
        val INSTANCE: LuaJsonLikePsiWalker = LuaJsonLikePsiWalker()
    }

    override fun isName(element: PsiElement): ThreeState {
        val parent = element.parent
        if (parent is LuaField) {
            if (parent.identifier == element) return ThreeState.YES
            if (element is LuaExpr && parent.exprList.size > 1 &&
                parent.exprList.first() == element && LuaValueAdapter.isStringKey(element)
            ) {
                return ThreeState.YES
            }
        }

        if (element is LuaExpr && isIncompleteKeyPosition(element)) return ThreeState.YES

        return ThreeState.NO
    }

    /** A still-being-typed bare identifier that occupies a key position (shape A top level or an array slot). */
    private fun isIncompleteKeyPosition(element: LuaExpr): Boolean {
        val stat = PsiTreeUtil.getParentOfType(element, LuaStatement::class.java)
        if (stat != null && stat.parent is LuaBlock && stat.parent?.parent is LuaFile) {
            if (stat is LuaAssignmentStatement &&
                stat.varList.varList.firstOrNull()?.let { PsiTreeUtil.isAncestor(it, element, false) } == true
            ) {
                return true
            }
            if (stat is LuaExprStatement) return true
        }

        val field = PsiTreeUtil.getParentOfType(element, LuaField::class.java)
        return field != null && field.identifier == null && field.exprList.size == 1 &&
            PsiTreeUtil.isAncestor(field.exprList.first(), element, false)
    }

    override fun isPropertyWithValue(element: PsiElement): Boolean {
        if (element is LuaField) {
            return element.identifier != null || element.exprList.size > 1
        }
        if (element is LuaAssignmentStatement) {
            return true
        }
        return false
    }

    override fun isTopJsonElement(element: PsiElement): Boolean {
        if (element is LuaFile) return true
        if (element is LuaTableConstructor) {
            val exprList = element.parent as? LuaExprList ?: return false
            val finalStmt = exprList.parent as? LuaFinalStatement ?: return false
            return isTopLevelFinalStatement(finalStmt)
        }
        return false
    }

    override fun requiresNameQuotes(): Boolean = false

    override fun isQuotedString(element: PsiElement): Boolean {
        return element is LuaTerminalExpr && element.string != null
    }

    override fun findElementToCheck(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null && current !is LuaFile) {
            if (current is LuaField || current is LuaTableConstructor || current is LuaAssignmentStatement || current is LuaExpr) {
                return current
            }
            current = current.parent
        }
        return null
    }

    override fun createValueAdapter(element: PsiElement): JsonValueAdapter? {
        if (element is LuaFile) return LuaFileObjectAdapter(element)
        if (element is LuaTableConstructor) {
            return if (LuaValueAdapter.isObjectTable(element)) LuaObjectAdapter(element)
            else LuaArrayAdapter(element)
        }
        if (element is LuaExpr) return LuaValueAdapter(element)
        return null
    }

    override fun hasMissingCommaAfter(element: PsiElement): Boolean = false

    override fun getParentPropertyAdapter(element: PsiElement): JsonPropertyAdapter? {
        var current: PsiElement? = element
        while (current != null && current !is LuaFile) {
            if (current is LuaField) {
                if (current.identifier != null || current.exprList.size > 1) {
                    return LuaPropertyAdapter(current)
                }
            }
            if (current is LuaAssignmentStatement) {
                return LuaAssignmentPropertyAdapter(current)
            }
            current = current.parent
        }
        return null
    }

    override fun getPropertyNamesOfParentObject(
        originalPosition: PsiElement,
        computedPosition: PsiElement?
    ): Set<String> {
        val parent = getParentPropertyAdapter(originalPosition)?.parentObject
        return parent?.propertyList?.mapNotNull { it.name }?.toSet() ?: emptySet()
    }

    override fun getPropertyNameElement(property: PsiElement?): PsiElement? {
        if (property is LuaField) {
            return property.identifier ?: if (property.exprList.size > 1) property.exprList.first() else null
        }
        if (property is LuaAssignmentStatement) {
            return property.varList.varList.firstOrNull()?.nameRef
        }
        return null
    }

    override fun findPosition(element: PsiElement, forceLastTransition: Boolean): JsonPointerPosition? {
        val position = JsonPointerPosition()

        // When asked to include the last transition (quick-doc / completion on a key itself), add the
        // element's own property step — the loop below only contributes ancestor steps. Mirrors the
        // `position == element && forceLastTransition` branch of YamlJsonPsiWalker.findPosition.
        if (forceLastTransition) {
            when (element) {
                is LuaAssignmentStatement -> LuaAssignmentPropertyAdapter(element).name?.let { position.addPrecedingStep(it) }
                is LuaField -> LuaPropertyAdapter(element).name?.let { position.addPrecedingStep(it) }
            }
        }

        var current: PsiElement? = element

        while (current != null && current !is LuaFile) {
            val parent = current.parent
            
            if (parent is LuaField) {
                val propName = LuaPropertyAdapter(parent).name
                if (propName != null) {
                    position.addPrecedingStep(propName)
                } else if (parent.identifier == null && parent.exprList.size == 1) {
                    // Do not add index step if the element is being typed as a key
                    if (isName(element) != ThreeState.YES) {
                        val table = parent.parent as? LuaTableConstructor
                        if (table != null) {
                            val index = table.fieldList?.fieldList?.indexOf(parent)
                            if (index != null && index != -1) {
                                position.addPrecedingStep(index)
                            }
                        }
                    }
                }
                current = parent.parent 
                continue
            }
            
            if (parent is LuaAssignmentStatement) {
                val propName = LuaAssignmentPropertyAdapter(parent).name
                if (propName != null) {
                    position.addPrecedingStep(propName)
                }
            }
            
            current = parent
        }

        return position
    }

    override fun requiresValueQuotes(): Boolean = false

    override fun allowsSingleQuotes(): Boolean = true

    override fun acceptsEmptyRoot(): Boolean = true

    override fun getDefaultObjectValue(): String = "{}"

    override fun getDefaultArrayValue(): String = "{}"
    
    override fun getRoots(file: PsiFile): Collection<PsiElement> {
        if (file !is LuaFile) return emptyList()

        val table = topLevelReturnTable(file)
        if (table != null) return listOf(table)

        return listOf(file)
    }

    /** Shape B root: the table of a top-level `return { … }`, ignoring `return`s nested inside functions. */
    private fun topLevelReturnTable(file: LuaFile): LuaTableConstructor? {
        val finalStmt = PsiTreeUtil.findChildrenOfType(file, LuaFinalStatement::class.java)
            .lastOrNull { isTopLevelFinalStatement(it) } ?: return null
        return finalStmt.exprList?.exprList?.firstOrNull() as? LuaTableConstructor
    }

    private fun isTopLevelFinalStatement(stmt: LuaFinalStatement): Boolean {
        val parent = stmt.parent
        return parent is LuaBlock && parent.parent is LuaFile
    }
}
