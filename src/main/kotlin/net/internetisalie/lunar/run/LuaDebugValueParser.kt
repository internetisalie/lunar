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
    private val localScope: MutableMap<String, LuaEvaluatedValue> = mutableMapOf()
    private val log = logger<LuaDebugValueParser>()

    fun evaluateExpression(expr: LuaExpr?): LuaEvaluatedValue? {
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

    private fun evaluateTerminalExpr(expr: LuaTerminalExpr): LuaEvaluatedValue? {
        return when {
            expr.number != null -> {
                val numberValue = expr.number!!.text.toDoubleOrNull() ?: return null
                LuaEvaluatedValue(
                    kind = LuaEvaluatedValueKind.Number,
                    numberValue = numberValue,
                    psiElement = expr.number
                )
            }
            expr.string != null -> {
                val stringValue = extractLuaString(expr.string!!.text)
                LuaEvaluatedValue(
                    kind = LuaEvaluatedValueKind.String,
                    stringValue = stringValue,
                    psiElement = expr.string
                )
            }
            expr.firstChild?.elementType == LuaElementTypes.NIL -> {
                LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Nil)
            }
            expr.text == "true" -> {
                LuaEvaluatedValue(
                    kind = LuaEvaluatedValueKind.Boolean,
                    boolValue = true,
                    psiElement = expr
                )
            }
            expr.text == "false" -> {
                LuaEvaluatedValue(
                    kind = LuaEvaluatedValueKind.Boolean,
                    boolValue = false,
                    psiElement = expr
                )
            }
            else -> null
        }
    }

    private fun evaluateTableConstructor(expr: LuaTableConstructor): LuaEvaluatedValue {
        val table = LuaEvaluatedTable()

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

        return LuaEvaluatedValue(
            kind = LuaEvaluatedValueKind.Table,
            tableValue = table,
            psiElement = expr
        )
    }

    private fun evaluateFuncDef(expr: LuaFuncDef): LuaEvaluatedValue {
        return LuaEvaluatedValue(
            kind = LuaEvaluatedValueKind.Function,
            psiElement = expr
        )
    }

    private fun evaluatePrefixExpr(expr: LuaPrefixExpr): LuaEvaluatedValue? {
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

    private fun evaluateVar(`var`: LuaVar): LuaEvaluatedValue? {
        // var ::= (nameRef | '(' expr ')') varSuffix*
        // varSuffix ::= nameAndArgs* indexExpr
        val nameRef = `var`.nameRef
        val exprVal = `var`.expr
        val varSuffixList = `var`.varSuffixList

        // Get base value
        var currentValue: LuaEvaluatedValue = if (nameRef != null) {
            // Simple name reference
            localScope[nameRef.text] ?: LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Nil)
        } else if (exprVal != null) {
            // Parenthesized expression
            evaluateExpression(exprVal) ?: LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Nil)
        } else {
            LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Nil)
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
                ?: LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Nil)
        }

        return currentValue
    }

    private fun evaluateVarSuffixIndex(
        baseValue: LuaEvaluatedValue,
        indexExpr: LuaIndexExpr,
    ): LuaEvaluatedValue? {
        if (baseValue.kind != LuaEvaluatedValueKind.Table || baseValue.tableValue == null) {
            return LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Nil)
        }

        val table = baseValue.tableValue

        // Handle bracket notation: t[expr]
        val expr = indexExpr.expr
        if (expr != null) {
            val keyValue = evaluateExpression(expr)
            if (keyValue != null) {
                val key = when (keyValue.kind) {
                    LuaEvaluatedValueKind.String -> keyValue.stringValue
                    LuaEvaluatedValueKind.Number -> keyValue.numberValue.toInt().toString()
                    else -> null
                }

                if (key != null) {
                    return table.named[key] ?: LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Nil)
                }
            }
        }

        // Handle dot notation: t.field
        val nameRef = indexExpr.nameRef
        if (nameRef != null) {
            val fieldName = nameRef.text
            return table.named[fieldName] ?: LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Nil)
        }

        return LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Nil)
    }

    fun parse(doStatement: LuaDoStatement): LuaEvaluatedTable {
        // Execute each statement in the block
        val block = doStatement.block ?: return LuaEvaluatedTable()
        val statements = block.statementList

        for (i in statements.indices) {
            val stmt = statements[i]
            val isLastStatement = i == statements.size - 1

            when {
                isLastStatement && stmt is LuaFinalStatement -> {
                    // Process return statement and wrap results in table
                    val table = LuaEvaluatedTable()
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
        return LuaEvaluatedTable()
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
            localScope[name] = value ?: LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Nil)
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
            localScope[varName] = value ?: LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Nil)
        }
    }

    fun getLocalVariable(name: String): LuaEvaluatedValue? {
        return localScope[name]
    }

    fun setLocalVariable(name: String, value: LuaEvaluatedValue) {
        localScope[name] = value
    }
}
