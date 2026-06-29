package net.internetisalie.lunar.lang.schema

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaField
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr

class LuaObjectAdapter(private val table: LuaTableConstructor) : LuaValueAdapter(table), JsonObjectValueAdapter {
    override fun isNull(): Boolean = false
    override fun isObject(): Boolean = true
    override fun isArray(): Boolean = false
    override fun getAsObject(): JsonObjectValueAdapter? = this
    override fun getAsArray(): JsonArrayValueAdapter? = null

    override fun getPropertyList(): List<JsonPropertyAdapter> {
        return table.fieldList?.fieldList?.mapNotNull { field ->
            if (field.identifier != null || (field.exprList.size > 1 && LuaValueAdapter.isStringKey(field.exprList.first()))) {
                LuaPropertyAdapter(field)
            } else {
                null
            }
        } ?: emptyList()
    }
}

class LuaArrayAdapter(private val table: LuaTableConstructor) : LuaValueAdapter(table), JsonArrayValueAdapter {
    override fun isNull(): Boolean = false
    override fun isObject(): Boolean = false
    override fun isArray(): Boolean = true
    override fun getAsObject(): JsonObjectValueAdapter? = null
    override fun getAsArray(): JsonArrayValueAdapter? = this

    override fun getElements(): List<JsonValueAdapter> {
        return table.fieldList?.fieldList?.mapNotNull { field ->
            if (field.identifier == null && field.exprList.size == 1) {
                field.exprList.firstOrNull()?.let { LuaValueAdapter(it) }
            } else {
                null
            }
        } ?: emptyList()
    }
}

class LuaPropertyAdapter(private val field: LuaField) : JsonPropertyAdapter {
    override fun getName(): String? {
        val ident = field.identifier
        if (ident != null) return ident.text
        if (field.exprList.size > 1) {
            val key = field.exprList.first()
            if (key is LuaTerminalExpr && key.string != null) {
                val text = key.text
                if (text.length >= 2) {
                    return text.substring(1, text.length - 1)
                }
                return text
            }
        }
        return null
    }

    override fun getNameValueAdapter(): JsonValueAdapter? {
        val ident = field.identifier
        if (ident != null) return LuaValueAdapter(ident)
        if (field.exprList.size > 1) {
            return LuaValueAdapter(field.exprList.first())
        }
        return null
    }

    override fun getValues(): Collection<JsonValueAdapter> {
        val valueExpr = if (field.identifier != null) {
            field.exprList.firstOrNull()
        } else if (field.exprList.size > 1) {
            field.exprList.getOrNull(1)
        } else {
            null
        }
        return if (valueExpr != null) listOf(LuaValueAdapter(valueExpr)) else emptyList()
    }

    override fun getDelegate(): PsiElement = field

    override fun getParentObject(): JsonObjectValueAdapter? {
        val parent = field.parent
        if (parent is LuaTableConstructor) {
            return LuaObjectAdapter(parent)
        }
        return null
    }
}

class LuaFileObjectAdapter(private val file: LuaFile) : LuaValueAdapter(file), JsonObjectValueAdapter {
    override fun isNull(): Boolean = false
    override fun isObject(): Boolean = true
    override fun getAsObject(): JsonObjectValueAdapter? = this

    override fun getPropertyList(): List<JsonPropertyAdapter> {
        return com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, LuaAssignmentStatement::class.java).mapNotNull { stmt ->
            LuaAssignmentPropertyAdapter(stmt)
        }
    }
}

class LuaAssignmentPropertyAdapter(private val stmt: LuaAssignmentStatement) : JsonPropertyAdapter {
    override fun getName(): String? {
        val firstVar = stmt.varList.varList.firstOrNull()
        return firstVar?.nameRef?.text
    }

    override fun getNameValueAdapter(): JsonValueAdapter? {
        val firstVar = stmt.varList.varList.firstOrNull()
        return firstVar?.nameRef?.let { LuaValueAdapter(it) }
    }

    override fun getValues(): Collection<JsonValueAdapter> {
        val valueExpr = stmt.exprList.exprList.firstOrNull()
        return if (valueExpr != null) listOf(LuaValueAdapter(valueExpr)) else emptyList()
    }

    override fun getDelegate(): PsiElement = stmt

    override fun getParentObject(): JsonObjectValueAdapter? {
        val parent = stmt.parent
        if (parent is LuaFile) {
            return LuaFileObjectAdapter(parent)
        }
        return null
    }
}
