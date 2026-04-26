package net.internetisalie.lunar.run

import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaDoStatement
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaField
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFinalStatement
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaTableConstructor

class LuaRemoteStack(
    stack: LuaTable?,
) {
    private val virtualFiles: MutableMap<String, VirtualFile?> = mutableMapOf()
    val entries: List<LuaRemoteStackEntry> = stack?.indexed?.map {
        LuaRemoteStackEntry(it.checkTable()!!, virtualFiles)
    } ?: emptyList()

    companion object {
        fun create(project: Project, text: String): LuaRemoteStack {
            val table = LuaDebugValueParser.parseChunk(project, text)
            return LuaRemoteStack(table)
        }

        fun create(file: PsiFile): LuaRemoteStack {
            val table = LuaDebugValueParser.parseFile(file)
            return LuaRemoteStack(table)
        }
    }
}

// { {frame}, {locals}, {upvalues} }
class LuaRemoteStackEntry(
    private val stackEntryTable: LuaTable,
    private val virtualFiles: MutableMap<String, VirtualFile?>,
) {
    init {
        val path = frame.path
        if (!virtualFiles.contains(path)) {
            val file = LocalFileSystem.getInstance().findFileByPath(path)
            if (file != null) virtualFiles[path] = file
        }
    }

    // { "c", "stack.lua", 3, 5, "Lua", "local", "/home/mini/Documents/src/lua/test/stack.lua" }
    val frame: LuaRemoteStackFrame
        get() = LuaRemoteStackFrame(stackEntryTable.indexed.get(0).checkTable(), virtualFiles)

    // { f = { 3, "3" } }
    val locals: LuaRemoteScope
        get() = LuaRemoteScope(stackEntryTable.indexed.get(1).checkTable())

    // { d = { 1, "1" }, e = { 2, "2" }, _ENV = { {...}, "table: 0x5e930bee3c50" } }
    val upvalues: LuaRemoteScope
        get() = LuaRemoteScope(stackEntryTable.indexed.get(2).checkTable())

}

// { "c", "stack.lua", 3, 5, "Lua", "local", "/home/mini/Documents/src/lua/test/stack.lua" }
// { "b", "stack.lua", 2, 6, "Lua", "local", "/home/mini/Documents/src/lua/test/stack.lua" }
// { "a", "stack.lua", 1, 8, "Lua", "local", "/home/mini/Documents/src/lua/test/stack.lua" }
// { nil, "stack.lua", 0, 10, "main", "", "/home/mini/Documents/src/lua/test/stack.lua" }
// { nil, "=[C]", -1, -1, "C", "", "[C]" }
class LuaRemoteStackFrame(
    private val stackFrameTable: LuaTable?,
    virtualFiles: MutableMap<String, VirtualFile?>,
) {
    val name: String
        get() = stackFrameTable?.getByIndex(0)?.stringValue ?: ""
    val file: String
        get() = stackFrameTable?.getByIndex(1)?.stringValue ?: "unknown"

    val index: Int
        get() = stackFrameTable?.getByIndex(2)?.numberValue?.toInt() ?: 0

    val line: Int
        get() = stackFrameTable?.getByIndex(3)?.numberValue?.toInt() ?: 0

    val path: String
        get() = stackFrameTable?.getByIndex(6)?.stringValue ?: ""

    val virtualFile: VirtualFile? = virtualFiles.getOrDefault(path, null)

}

class LuaRemoteScope(
    private val scopeTable: LuaTable?,
) {
    val variables: List<LuaRemoteVariable>
        get() = scopeTable?.pairs()?.map { field -> LuaRemoteVariable(field) } ?: emptyList()

    fun getVariable(name: String): LuaRemoteVariable? {
        val field = scopeTable?.getByName(name) ?: return null
        return LuaRemoteVariable(field)
    }

}

class LuaRemoteVariable(
    private val variableField: Pair<LuaValue, LuaValue>,
) {
    val name: String
        get() = variableField.first.stringValue ?: "anonymous"
    val value: LuaValue
        get() = variableField.second.checkTable()?.getByIndex(0) ?: LuaValue.NONE

    val displayValue: String?
        get() = variableField.second.checkTable()?.getByIndex(1)?.stringValue

}

object LuaRemoteResultFactory {
    var log = logger<LuaRemoteResultFactory>()

    init {
        log.setLevel(LogLevel.ALL)
    }

    fun create(file: PsiFile): LuaValue {
        if (file !is LuaFile) return LuaValue.NONE

        val variables = mutableMapOf<String, LuaValue>()
        val wrapper = PsiTreeUtil.findChildOfType(file, LuaDoStatement::class.java)
            ?: return LuaValue.NONE

        for (statement in wrapper.block.statementList) {
            when (statement) {
                is LuaLocalVarDecl -> {
                    log.warn("local var declaration: ${statement.attNameList.joinToString(", ") { it.nameRef.text }}")
                    for ((index, attName) in statement.attNameList.withIndex()) {
                        val name = attName.nameRef.text
                        val psiValue = statement.exprList?.exprList[index] ?: continue
                        variables[name] = LuaValue(psiValue)
                    }
                }

                is LuaAssignmentStatement -> {
                    log.warn("assignment statement: ${statement.varList.text}")

                }

                is LuaFinalStatement -> {
                    log.warn("return statement: ${statement.exprList?.text}")
                    val varName = statement.exprList?.exprList[0]?.text ?: continue

                    return if (!variables.containsKey(varName)) {
                        // missing variable
                        LuaValue.NONE
                    } else {
                        // return the value
                        variables[varName]!!
                    }
                }
            }
        }

        // missing return statement
        return LuaValue.NONE
    }
}

