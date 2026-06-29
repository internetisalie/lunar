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
            if (parent.exprList.size > 1 && parent.exprList.first() == element) {
                if (LuaValueAdapter.isStringKey(element as LuaExpr)) return ThreeState.YES
            }
        }
        
        // Handle incomplete typing cases
        if (element is LuaExpr) {
            val stat = PsiTreeUtil.getParentOfType(element, LuaStatement::class.java)
            if (stat != null && stat.parent is LuaBlock && stat.parent?.parent is LuaFile) {
                if (stat is LuaAssignmentStatement) {
                    if (stat.varList.varList.firstOrNull()?.let { PsiTreeUtil.isAncestor(it, element, false) } == true) {
                        return ThreeState.YES
                    }
                }
                if (stat is LuaExprStatement) {
                    return ThreeState.YES
                }
            }
            
            val field = PsiTreeUtil.getParentOfType(element, LuaField::class.java)
            if (field != null && field.identifier == null && field.exprList.size == 1) {
                if (PsiTreeUtil.isAncestor(field.exprList.first(), element, false)) {
                    return ThreeState.YES
                }
            }
        }

        return ThreeState.NO
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
            val parent = element.parent
            if (parent is LuaExprList && parent.parent is LuaFinalStatement) return true
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
        
        val finalStmt = PsiTreeUtil.findChildrenOfType(file, LuaFinalStatement::class.java).lastOrNull()
        if (finalStmt != null) {
            val exprList = finalStmt.exprList
            if (exprList != null) {
                val table = exprList.exprList.firstOrNull() as? LuaTableConstructor
                if (table != null) return listOf(table)
            }
        }
        
        return listOf(file)
    }
}
