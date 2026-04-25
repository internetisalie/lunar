package net.internetisalie.lunar.run

import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaDoStatement
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaFinalStatement
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaIndexExpr
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaPrefixExpr
import net.internetisalie.lunar.lang.psi.LuaStatement
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.LuaVarOrExp
import net.internetisalie.lunar.lang.syntax.extractLuaString

class LuaDebugValueParser {
    private val localScope: MutableMap<String, LuaValue> = mutableMapOf()
    private val log = logger<LuaDebugValueParser>()

    fun evaluateExpression(expr: LuaExpr?): LuaValue? {
        if (expr == null) {
            return null
        }

        return when (expr) {
            is LuaTerminalExpr -> evaluateTerminalExpr(expr)
            is LuaTableConstructor -> evaluateTableConstructor(expr)
            is LuaFuncDef -> evaluateFuncDef(expr)
            is LuaPrefixExpr -> evaluatePrefixExpr(expr)
            else -> {
                log.warn("Unsupported expression type: ${expr::class.simpleName}")
                null
            }
        }
    }

    private fun evaluateTerminalExpr(expr: LuaTerminalExpr): LuaValue? {
        return when {
            expr.number != null -> {
                val numberValue = expr.number!!.text.toDoubleOrNull() ?: return null
                LuaValue(
                    kind = LuaValueKind.Number,
                    numberValue = numberValue,
                    psiElement = expr.number
                )
            }
            expr.string != null -> {
                val stringValue = extractLuaString(expr.string!!.text)
                LuaValue(
                    kind = LuaValueKind.String,
                    stringValue = stringValue,
                    psiElement = expr.string
                )
            }
            expr.firstChild?.elementType == LuaElementTypes.NIL -> {
                LuaValue(kind = LuaValueKind.Nil)
            }
            expr.text == "true" -> {
                LuaValue(
                    kind = LuaValueKind.Boolean,
                    boolValue = true,
                    psiElement = expr
                )
            }
            expr.text == "false" -> {
                LuaValue(
                    kind = LuaValueKind.Boolean,
                    boolValue = false,
                    psiElement = expr
                )
            }
            else -> null
        }
    }

    private fun evaluateTableConstructor(expr: LuaTableConstructor): LuaValue {
        val table = LuaTable()

        expr.fieldList?.fieldList?.forEach { field ->
            val fieldValue = evaluateExpression(field.value)
            if (fieldValue != null) {
                if (field.name != null) {
                    // Named field
                    table.named[field.name!!] = fieldValue
                } else {
                    // Positional field
                    table.indexed.add(fieldValue)
                }
            }
        }

        return LuaValue(
            kind = LuaValueKind.Table,
            tableValue = table,
            psiElement = expr
        )
    }

    private fun evaluateFuncDef(expr: LuaFuncDef): LuaValue {
        return LuaValue(
            kind = LuaValueKind.Function,
            psiElement = expr
        )
    }

    private fun evaluatePrefixExpr(expr: LuaPrefixExpr): LuaValue? {
        // prefixExpr ::= varOrExp nameAndArgs*
        // For now we only handle varOrExp with no function calls (nameAndArgs)
        val varOrExp = expr.varOrExp
        val `var` = varOrExp.`var`
        val exprVal = varOrExp.expr

        // Try to resolve as a variable reference
        if (`var` != null) {
            return evaluateVar(`var`)
        }

        // Evaluate parenthesized expression
        if (exprVal != null) {
            return evaluateExpression(exprVal)
        }

        // Function calls and other prefix expressions not supported
        log.warn("Unsupported prefix expression")
        return null
    }

    private fun evaluateVar(`var`: LuaVar): LuaValue? {
        // var ::= (nameRef | '(' expr ')') varSuffix*
        // varSuffix ::= nameAndArgs* indexExpr
        val nameRef = `var`.nameRef
        val exprVal = `var`.expr
        val varSuffixList = `var`.varSuffixList

        // Get base value
        var currentValue: LuaValue = if (nameRef != null) {
            // Simple name reference
            localScope[nameRef.text] ?: LuaValue(kind = LuaValueKind.Nil)
        } else if (exprVal != null) {
            // Parenthesized expression
            evaluateExpression(exprVal) ?: LuaValue(kind = LuaValueKind.Nil)
        } else {
            LuaValue(kind = LuaValueKind.Nil)
        }

        // Handle var suffixes (index operations like [key] or .field)
        for (suffix in varSuffixList) {
            // Check for unsupported function calls
            if (suffix.nameAndArgsList.isNotEmpty()) {
                log.warn("Function calls are not supported in expression evaluation")
                return null
            }

            val indexExpr = suffix.indexExpr
            currentValue = evaluateVarSuffixIndex(currentValue, indexExpr)
                ?: LuaValue(kind = LuaValueKind.Nil)
        }

        return currentValue
    }

    private fun evaluateVarSuffixIndex(
        baseValue: LuaValue,
        indexExpr: LuaIndexExpr,
    ): LuaValue? {
        if (baseValue.kind != LuaValueKind.Table || baseValue.tableValue == null) {
            return LuaValue(kind = LuaValueKind.Nil)
        }

        val table = baseValue.tableValue

        // Handle bracket notation: t[expr]
        val expr = indexExpr.expr
        if (expr != null) {
            val keyValue = evaluateExpression(expr)
            if (keyValue != null) {
                val key = when (keyValue.kind) {
                    LuaValueKind.String -> keyValue.stringValue
                    LuaValueKind.Number -> keyValue.numberValue?.toInt()?.toString()
                    else -> null
                }

                if (key != null) {
                    return table.named[key] ?: LuaValue(kind = LuaValueKind.Nil)
                }
            }
        }

        // Handle dot notation: t.field
        val nameRef = indexExpr.nameRef
        if (nameRef != null) {
            val fieldName = nameRef.text
            return table.named[fieldName] ?: LuaValue(kind = LuaValueKind.Nil)
        }

        return LuaValue(kind = LuaValueKind.Nil)
    }

    fun parse(doStatement: LuaDoStatement): LuaTable {
        // Execute each statement in the block
        val block = doStatement.block ?: return LuaTable()
        val statements = block.statementList

        for (i in statements.indices) {
            val stmt = statements[i]
            val isLastStatement = i == statements.size - 1

            when {
                isLastStatement && stmt is LuaFinalStatement -> {
                    // Process return statement and wrap results in table
                    val table = LuaTable()
                    val exprList = stmt.exprList?.exprList ?: emptyList()
                    for (expr in exprList) {
                        val value = evaluateExpression(expr)
                        if (value != null) {
                            table.indexed.add(value)
                        }
                    }
                    return table
                }
                stmt is LuaLocalVarDecl -> {
                    // Execute local variable declaration
                    val names = stmt.attNameList.map { it.nameRef.text }
                    val exprs = stmt.exprList?.exprList ?: emptyList()
                    executeLocalVariable(names, exprs)
                }
                stmt is LuaAssignmentStatement -> {
                    // Execute assignment
                    val vars = stmt.varList.varList.map { it.text }
                    val exprs = stmt.exprList.exprList
                    executeAssignment(vars, exprs)
                }
            }
        }

        // If no return statement, return empty table
        return LuaTable()
    }

    fun executeLocalVariable(
        names: List<String>,
        exprs: List<LuaExpr>,
    ) {
        for ((i, name) in names.withIndex()) {
            val value = if (i < exprs.size) {
                evaluateExpression(exprs[i])
            } else {
                null
            }
            localScope[name] = value ?: LuaValue(kind = LuaValueKind.Nil)
        }
    }

    fun executeAssignment(
        vars: List<String>,
        exprs: List<LuaExpr>,
    ) {
        for ((i, varName) in vars.withIndex()) {
            val value = if (i < exprs.size) {
                evaluateExpression(exprs[i])
            } else {
                null
            }
            localScope[varName] = value ?: LuaValue(kind = LuaValueKind.Nil)
        }
    }

    fun getLocalVariable(name: String): LuaValue? {
        return localScope[name]
    }

    fun setLocalVariable(name: String, value: LuaValue) {
        localScope[name] = value
    }
}
