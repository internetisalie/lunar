package net.internetisalie.lunar.run

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaField
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.lang.syntax.extractLuaString
import kotlin.collections.orEmpty

enum class LuaValueKind(
    val typeName: String,
) {
    None("none"),
    Nil("nil"),
    Boolean("boolean"),
    Number("number"),
    String("string"),
    Function("function"),
    Userdata("userdata"),
    Thread("thread"),
    Table("table"),
}

// LuaValue wraps a literal PsiElement parsed from a debugger response
open class LuaValue(
    val element: PsiElement?,
) {
    val kind: LuaValueKind
        get() = when (element?.firstChild.elementType) {
            LuaElementTypes.NUMBER -> LuaValueKind.Number
            LuaElementTypes.STRING -> LuaValueKind.String
            LuaElementTypes.NIL -> LuaValueKind.Nil
            LuaElementTypes.FUNCTION -> LuaValueKind.Function
            LuaElementTypes.LCURLY -> LuaValueKind.Table
            else -> LuaValueKind.None
        }

    val typeName: String = kind.typeName

    val text: String?
        get() = element?.text

    fun checkTable(): LuaTable? {
        return if (kind == LuaValueKind.Table && element is LuaTableConstructor) LuaTable(element)
        else null
    }

    companion object {
        val NONE = LuaValue(null)
    }
}

open class LuaTable(
    protected val table: LuaTableConstructor?
) : LuaValue(table) {
    fun getFields(): List<LuaField> {
        return table?.fieldList?.fieldList.orEmpty()
    }

    fun getField(index: Int): LuaField? {
        val fields = getFields()
        if (index < fields.size) return fields[index]
        return null
    }

    fun getField(name: String): LuaField? {
        return getFields().find { it.name == name }
    }

    fun getFieldValue(index: Int): LuaExpr? {
        return getField(index)?.value
    }

    fun getFieldValue(name: String): LuaExpr? {
        return getField(name)?.value
    }

    fun getFieldValues(): List<LuaExpr> {
        return getFields().map { it.value }
    }

    fun getStringField(index: Int): String? {
        val terminalExpr = getFieldValue(index) as? LuaTerminalExpr ?: return null
        val stringValue = terminalExpr.string ?: return null
        return extractLuaString(stringValue.text ?: "")
    }

    fun getIntField(index: Int): Int? {
        val terminalExpr = getFieldValue(index) as? LuaTerminalExpr ?: return null
        val numberValue = terminalExpr.number ?: return null
        return numberValue.text.toInt()
    }

    fun getIntField(name: String): Int? {
        val terminalExpr = getFieldValue(name) as? LuaTerminalExpr ?: return null
        val numberValue = terminalExpr.number ?: return null
        return numberValue.text.toInt()
    }

    fun getTableField(index: Int): LuaTableConstructor? {
        return getFieldValue(index) as? LuaTableConstructor
    }

    fun getTableField(name: String): LuaTableConstructor? {
        return getFieldValue(name) as? LuaTableConstructor
    }
}

val LuaField.name: String?
    get() = if (identifier != null) {
        identifier!!.text
    } else if (exprList.size == 2) {
        extractLuaString(exprList[0].text)
    } else {
        null
    }

val LuaField.value: LuaExpr
    get() = exprList.last()
