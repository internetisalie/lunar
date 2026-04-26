package net.internetisalie.lunar.lang.structure

import com.intellij.ide.util.treeView.smartTree.TreeElement
import net.internetisalie.lunar.lang.psi.*

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
                is LuaLocalVarDecl -> getLocalVariableNameListChildren(childElement.attNameList)
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

    private fun getLocalVariableNameListChildren(attNameList: List<LuaAttName>): List<TreeElement> {
        return attNameList.map {
            LuaLocalVariableStructureViewTreeElement(it.nameRef.identifier)
        }
    }
}
