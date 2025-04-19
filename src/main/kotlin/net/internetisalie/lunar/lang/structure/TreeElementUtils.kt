package net.internetisalie.lunar.lang.structure

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.*

class TreeElementUtils {
    companion object {
        fun getRootChildren(root : LuaFile): Array<TreeElement> {
            val result = ArrayList<TreeElement>()
            var childElement = root.firstChild
            while (childElement != null) {
                if (childElement.elementType == LuaElementTypes.BLOCK) {
                    result.addAll(getBlockChildren(childElement as LuaBlock))
                }
                childElement = childElement.firstChild
            }
            return result.toTypedArray()
        }

        fun getFuncBodyChildren(parList: LuaParList?, block: LuaBlock) : Array<TreeElement> {
            val result = ArrayList<TreeElement>()
            result.addAll(getParListChildren(parList))
            result.addAll(getBlockChildren(block))
            return result.toTypedArray()
        }

        private fun getParListChildren(parList : LuaParList?): Array<TreeElement> {
            val result = ArrayList<TreeElement>()
            var childElement = parList?.nameList?.firstChild
            while (childElement != null) {
                if (childElement.elementType == LuaElementTypes.IDENTIFIER) {
                    result.add(LuaFunctionParameterStructureViewTreeElement(childElement))
                }
                childElement = childElement.nextSibling
            }
            return result.toTypedArray()
        }

        private fun getBlockChildren(block : LuaBlock) : Array<TreeElement> {
            val result = ArrayList<TreeElement>()
            var childElement = block.firstChild
            while (childElement != null) {
                when {
                    childElement.elementType == LuaElementTypes.STATEMENT -> result.addAll(getStatementChildren(childElement as LuaStatement))
                    childElement.elementType == LuaElementTypes.FINAL_STATEMENT -> result.addAll(getFinalStatementChildren(childElement as LuaFinalStatement))
                }
                childElement = childElement.nextSibling
            }
            return result.toTypedArray()
        }

        private fun getStatementChildren(statement: LuaStatement) : Array<TreeElement> {
            val result = ArrayList<TreeElement>()
            var childElement = statement.firstChild
            while (childElement != null) {
                when (childElement.elementType) {
                    LuaElementTypes.LABEL -> result.add(LuaLabelStructureViewTreeElement(childElement as LuaLabel))
                    LuaElementTypes.FUNC_DECL -> result.add(LuaFunctionStructureViewTreeElement(childElement as LuaFuncDecl))
                    LuaElementTypes.LOCAL_FUNC_DECL -> result.add(LuaLocalFunctionStructureViewTreeElement(childElement as LuaLocalFuncDecl))
                    LuaElementTypes.LOCAL_VAR_DECL -> result.addAll(getLocalVariableNameListChildren((childElement as LuaLocalVarDecl).nameList))
                }
                childElement = childElement.nextSibling
            }
            return result.toTypedArray()
        }

        private fun getFinalStatementChildren(statement: LuaFinalStatement) : Array<TreeElement> {
            val result = ArrayList<TreeElement>()
            result.add(LuaReturnStructureViewTreeElement(statement))
            return result.toTypedArray()
        }

        private fun getLocalVariableNameListChildren(nameList : LuaNameList) : Array<TreeElement> {
            val result = ArrayList<TreeElement>()
            var childElement = nameList.firstChild
            while (childElement != null) {
                if (childElement.elementType == LuaElementTypes.IDENTIFIER) {
                    result.add(LuaLocalVariableStructureViewTreeElement(childElement))
                }
                childElement = childElement.nextSibling
            }
            return result.toTypedArray()
        }
    }
}