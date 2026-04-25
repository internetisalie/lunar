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

// LuaValue now supports both:
// 1. PSI-based creation (for remote debugging): LuaValue(psiElement)
// 2. Evaluated value creation (from evaluator): LuaValue(kind=NUMBER, numberValue=123.0, psiElement=null)
data class LuaValue(
    val kind: LuaValueKind = LuaValueKind.None,
    val numberValue: Double? = null,
    val stringValue: String? = null,
    val boolValue: Boolean? = null,
    val tableValue: LuaTable? = null,
    val psiElement: PsiElement? = null,
) {
    // When created with just a PsiElement for PSI-based values, compute kind from PSI
    constructor(element: PsiElement?) : this(
        kind = element?.let { computeKindFromPsi(it) } ?: LuaValueKind.None,
        psiElement = element
    )

    val typeName: String = kind.typeName

    val text: String?
        get() = psiElement?.text

    fun checkTable(): LuaTable? {
        return if (kind == LuaValueKind.Table) {
            tableValue ?: (psiElement as? LuaTableConstructor)?.let { LuaTable(it) }
        } else null
    }

    companion object {
        val NONE = LuaValue()

        private fun computeKindFromPsi(element: PsiElement): LuaValueKind {
            return when (element.firstChild?.elementType) {
                LuaElementTypes.NUMBER -> LuaValueKind.Number
                LuaElementTypes.STRING -> LuaValueKind.String
                LuaElementTypes.NIL -> LuaValueKind.Nil
                LuaElementTypes.FUNCTION -> LuaValueKind.Function
                LuaElementTypes.LCURLY -> LuaValueKind.Table
                else -> LuaValueKind.None
            }
        }
    }
}

// LuaTable now supports both:
// 1. PSI-based creation (for remote debugging): LuaTable(luaTableConstructor)
// 2. Independent storage (from evaluator): LuaTable(indexed=listOf(...), named=mapOf(...))
open class LuaTable(
    val indexed: MutableList<LuaValue> = mutableListOf(),
    val named: MutableMap<String, LuaValue> = mutableMapOf(),
    val psiTable: LuaTableConstructor? = null,
) {
    // Legacy PSI-based constructor for backward compatibility
    constructor(table: LuaTableConstructor?) : this(
        indexed = mutableListOf(),
        named = mutableMapOf(),
        psiTable = table
    )

    fun getFields(): List<LuaField> {
        return psiTable?.fieldList?.fieldList.orEmpty()
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
