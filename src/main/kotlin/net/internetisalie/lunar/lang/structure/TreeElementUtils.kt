package net.internetisalie.lunar.lang.structure

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.*
import kotlin.collections.flatten

object TreeElementUtils {
    fun getRootChildren(root: LuaFile): List<TreeElement> {
        return root.getBlockList().map {
            getBlockChildren(it)
        }.flatten()
    }

    fun getFuncBodyChildren(parList: LuaParList?, block: LuaBlock): List<TreeElement> {
        return listOf(
            getParListChildren(parList),
            getBlockChildren(block),
        ).flatten()
    }

    private fun getBlockChildren(block: LuaBlock): List<TreeElement> {
        return block.statementList.map { childElement ->
            when (childElement) {
                is LuaFinalStatement -> listOf<TreeElement>(LuaReturnStructureViewTreeElement(childElement))
                is LuaLabel -> listOf<TreeElement>(LuaLabelStructureViewTreeElement(childElement))
                is LuaFuncDecl -> listOf<TreeElement>(LuaFunctionStructureViewTreeElement(childElement))
                is LuaLocalFuncDecl -> listOf<TreeElement>(LuaLocalFunctionStructureViewTreeElement(childElement))
                is LuaLocalVarDecl -> getLocalVariableNameListChildren(childElement.nameList)
                else -> emptyList()
            }
        }.flatten()
    }

    private fun getParListChildren(parList: LuaParList?): List<TreeElement> {
        val nameRefList = parList?.nameList?.nameRefList ?: return emptyList()
        return nameRefList.map {
            LuaFunctionParameterStructureViewTreeElement(it.identifier)
        }
    }

    private fun getLocalVariableNameListChildren(nameList: LuaNameList): List<TreeElement> {
        return nameList.nameRefList.map {
            LuaLocalVariableStructureViewTreeElement(it.identifier)
        }
    }
}