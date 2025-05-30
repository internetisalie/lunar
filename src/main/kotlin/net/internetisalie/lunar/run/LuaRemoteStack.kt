package net.internetisalie.lunar.run

import ai.grazie.utils.text
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
import java.util.logging.LogManager

class LuaRemoteStack(
    table: LuaTableConstructor?,
) {
    private val virtualFiles : MutableMap<String, VirtualFile?> = mutableMapOf()
    val entries: List<LuaRemoteStackEntry> = LuaTable(table).getFieldValues()
            .map { it -> LuaRemoteStackEntry(it as LuaTableConstructor, virtualFiles) }

    companion object {
        fun create(project: Project, text: String): LuaRemoteStack {
            val codeFragment = LuaElementFactory.createExpressionCodeFragment(project, text, null, true)
            return create(codeFragment)
        }
        fun create(file: PsiFile): LuaRemoteStack {
            val table = PsiTreeUtil.findChildOfType(file, LuaTableConstructor::class.java)
                ?: return LuaRemoteStack(null)
            return LuaRemoteStack(table)
        }

    }

}

// { {frame}, {locals}, {upvalues} }
class LuaRemoteStackEntry(
    stackEntryTable: LuaTableConstructor,
    private val virtualFiles: MutableMap<String, VirtualFile?>,
) : LuaTable(stackEntryTable) {
    init {
        val path = frame.path
        if (!virtualFiles.contains(path)) {
            val file = LocalFileSystem.getInstance().findFileByPath(path)
            if (file != null) virtualFiles[path] = file
        }
    }

    // { "c", "stack.lua", 3, 5, "Lua", "local", "/home/mini/Documents/src/lua/test/stack.lua" }
    val frame: LuaRemoteStackFrame
        get() = LuaRemoteStackFrame(getTableField(0), virtualFiles)

    // { f = { 3, "3" } }
    val locals: LuaRemoteScope
        get() = LuaRemoteScope(getTableField(1))

    // { d = { 1, "1" }, e = { 2, "2" }, _ENV = { {...}, "table: 0x5e930bee3c50" } }
    val upvalues: LuaRemoteScope
        get() = LuaRemoteScope(getTableField(2))

}

// { "c", "stack.lua", 3, 5, "Lua", "local", "/home/mini/Documents/src/lua/test/stack.lua" }
// { "b", "stack.lua", 2, 6, "Lua", "local", "/home/mini/Documents/src/lua/test/stack.lua" }
// { "a", "stack.lua", 1, 8, "Lua", "local", "/home/mini/Documents/src/lua/test/stack.lua" }
// { nil, "stack.lua", 0, 10, "main", "", "/home/mini/Documents/src/lua/test/stack.lua" }
// { nil, "=[C]", -1, -1, "C", "", "[C]" }
class LuaRemoteStackFrame(
    stackFrameTable: LuaTableConstructor?,
    virtualFiles: MutableMap<String, VirtualFile?>,
) : LuaTable(stackFrameTable) {
    val name: String
        get() = getStringField(0) ?: ""
    val file: String
        get() = getStringField(1) ?: "unknown"

    val index: Int
        get() = getIntField(2) ?: 0

    val line: Int
        get() = getIntField(3) ?: 0

    val path: String
        get() = getStringField(6) ?: ""

    val virtualFile : VirtualFile? = virtualFiles.getOrDefault(path, null)

}

class LuaRemoteScope(
    scopeTable: LuaTableConstructor?,
) : LuaTable(scopeTable) {
    val variables : List<LuaRemoteVariable>
        get() = getFields().map { field -> LuaRemoteVariable(field) }
    fun getVariable(name: String): LuaRemoteVariable? {
        val field = getField(name) ?: return null
        return LuaRemoteVariable(field)
    }

}

class LuaRemoteVariable(
    private val variableField: LuaField
) {
    val name : String
        get() = variableField.name ?: "anonymous"
    val value : LuaExpr?
        get() = LuaTable(variableField.value as? LuaTableConstructor).getFieldValue(0)

    val displayValue : String?
        get() = LuaTable(variableField.value as? LuaTableConstructor).getStringField(1)

}

object LuaRemoteResultFactory {
    var log = logger<LuaRemoteResultFactory>()

    init {
        log.setLevel(LogLevel.ALL)
    }

    fun create(file: PsiFile) : LuaValue {
        if (file !is LuaFile) return LuaValue.NONE

        val variables = mutableMapOf<String, LuaValue>()
        val wrapper = PsiTreeUtil.findChildOfType(file, LuaDoStatement::class.java)
            ?: return LuaValue.NONE

        for (statement in wrapper.block.statementList) {
            when (statement) {
                is LuaLocalVarDecl -> {
                    log.warn("local var declaration: ${statement.attNameList.joinToString(", "){ it.nameRef.text } }")
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

