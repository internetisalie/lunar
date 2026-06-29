package net.internetisalie.lunar.lang.schema

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr

open class LuaValueAdapter(private val element: PsiElement) : JsonValueAdapter {
    override fun getDelegate(): PsiElement = element

    override fun isObject(): Boolean = asObject != null

    override fun isArray(): Boolean = asArray != null

    override fun isStringLiteral(): Boolean = element is LuaTerminalExpr && element.string != null

    override fun isNumberLiteral(): Boolean = element is LuaTerminalExpr && element.number != null

    override fun isBooleanLiteral(): Boolean = element is LuaTerminalExpr &&
            (element.firstChild?.node?.elementType == LuaElementTypes.TRUE || 
             element.firstChild?.node?.elementType == LuaElementTypes.FALSE)

    override fun isNull(): Boolean = element is LuaTerminalExpr && 
            element.firstChild?.node?.elementType == LuaElementTypes.NIL

    override fun getAsObject(): JsonObjectValueAdapter? {
        if (element is LuaTableConstructor && isObjectTable(element)) {
            return LuaObjectAdapter(element)
        }
        return null
    }

    override fun getAsArray(): JsonArrayValueAdapter? {
        if (element is LuaTableConstructor && !isObjectTable(element)) {
            return LuaArrayAdapter(element)
        }
        return null
    }

    companion object {
        fun isObjectTable(table: LuaTableConstructor): Boolean {
            return table.fieldList?.fieldList?.any { field ->
                field.identifier != null ||
                (field.exprList.size > 1 && isStringKey(field.exprList.first()))
            } ?: false
        }

        fun isStringKey(expr: LuaExpr): Boolean {
            return expr is LuaTerminalExpr && expr.string != null
        }
    }
}
