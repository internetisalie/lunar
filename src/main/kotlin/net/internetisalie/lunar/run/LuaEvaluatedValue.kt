package net.internetisalie.lunar.run

import com.intellij.psi.PsiElement

enum class LuaEvaluatedValueKind {
    Nil,
    Boolean,
    Number,
    String,
    Table,
    Function,
}

data class LuaEvaluatedValue(
    val kind: LuaEvaluatedValueKind,
    val boolValue: Boolean = false,
    val numberValue: Double = 0.0,
    val stringValue: String = "",
    val tableValue: LuaEvaluatedTable? = null,
    val psiElement: PsiElement? = null,  // For scalars and functions
)

class LuaEvaluatedTable(
    val indexed: MutableList<LuaEvaluatedValue> = mutableListOf(),
    val named: MutableMap<String, LuaEvaluatedValue> = mutableMapOf(),
)
