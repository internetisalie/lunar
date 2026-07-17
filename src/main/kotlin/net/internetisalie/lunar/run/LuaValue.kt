package net.internetisalie.lunar.run

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaField
import net.internetisalie.lunar.lang.syntax.extractLuaString

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
) : Comparable<LuaValue> {
    // When created with just a PsiElement for PSI-based values, compute kind from PSI
    constructor(element: PsiElement?) : this(
        kind = element?.let { computeKindFromPsi(it) } ?: LuaValueKind.None,
        psiElement = element,
    )

    val typeName: String = kind.typeName

    val text: String?
        get() = psiElement?.text

    fun checkTable(): LuaTable? {
        return if (kind == LuaValueKind.Table) {
            tableValue
        } else null
    }

    override fun compareTo(other: LuaValue): Int {
        // First, compare by type ordering
        val typeOrder = LuaValueKind.entries.indexOf(kind)
        val otherTypeOrder = LuaValueKind.entries.indexOf(other.kind)
        val typeComparison = typeOrder.compareTo(otherTypeOrder)
        if (typeComparison != 0) return typeComparison

        // Within same type, compare by value
        return when (kind) {
            LuaValueKind.Nil -> 0
            LuaValueKind.Boolean -> (boolValue ?: false).compareTo(other.boolValue ?: false)
            LuaValueKind.Number -> (numberValue ?: 0.0).compareTo(other.numberValue ?: 0.0)
            LuaValueKind.String -> (stringValue ?: "").compareTo(other.stringValue ?: "")
            else -> 0
        }
    }

    fun toDisplayString(): String {
        return when (kind) {
            LuaValueKind.Nil -> "nil"
            LuaValueKind.Boolean -> (boolValue ?: false).toString()
            LuaValueKind.Number -> {
                val num = numberValue ?: 0.0
                if (num == num.toLong().toDouble()) {
                    num.toLong().toString()
                } else {
                    num.toString()
                }
            }
            LuaValueKind.String -> stringValue ?: ""
            LuaValueKind.Function -> "function"
            LuaValueKind.Userdata -> "userdata"
            LuaValueKind.Thread -> "thread"
            LuaValueKind.Table -> "table"
            LuaValueKind.None -> "none"
        }
    }

    companion object {
        val NONE = LuaValue()
        val NIL = LuaValue(kind = LuaValueKind.Nil)

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

        fun newString(value: String): LuaValue {
            return LuaValue(kind = LuaValueKind.String, stringValue = value)
        }

        fun newTable(value: LuaTable): LuaValue {
            return LuaValue(kind = LuaValueKind.Table, tableValue = value)
        }

        fun newNumber(value: Double): LuaValue {
            return LuaValue(kind = LuaValueKind.Number, numberValue = value)
        }

        fun newBoolean(value: Boolean): LuaValue {
            return LuaValue(kind = LuaValueKind.Boolean, boolValue = value)
        }
    }
}

open class LuaTable(
    val indexed: MutableList<LuaValue> = mutableListOf(),
    val named: MutableMap<LuaValue, LuaValue> = mutableMapOf(),
) {
    fun pairs(): List<Pair<LuaValue, LuaValue>> {
        val result = mutableListOf<Pair<LuaValue, LuaValue>>()

        // First, add 1-based index/value pairs from indexed
        for ((index, value) in indexed.withIndex()) {
            val key = LuaValue(kind = LuaValueKind.Number, numberValue = (index + 1).toDouble())
            result.add(Pair(key, value))
        }

        // Second, add sorted key/value pairs for named
        result.addAll(named.toList().sortedBy { (key, _) -> key })

        return result
    }

    fun getByName(name: String) : Pair<LuaValue, LuaValue>? {
        val key = LuaValue.newString(name)
        val value = named[key] ?: return null
        return Pair(key, value)
    }

    fun getByIndex(index: Int) : LuaValue? {
        return indexed.getOrNull(index)
    }

    fun addByName(name: String, value: LuaValue) {
        val key = LuaValue.newString(name)
        named[key] = value
    }

    fun push(value: LuaValue) {
        indexed.add(value)
    }

    constructor() : this(
        indexed = mutableListOf(),
        named = mutableMapOf(),
    )
}

val LuaField.name: String?
    get() = if (identifier != null) {
        identifier?.text
    } else if (exprList.size == 2) {
        extractLuaString(exprList[0].text)
    } else {
        null
    }

val LuaField.value: LuaExpr
    get() = exprList.last()
