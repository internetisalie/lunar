package net.internetisalie.lunar.run

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.syntax.extractLuaString

class LuaDebugValueParser(private val project: Project? = null) {
    private val localScope: MutableMap<String, LuaValue> = mutableMapOf()

    fun evaluateExpression(expr: LuaExpr?): LuaValue? {
        if (expr == null) {
            return null
        }

        return when (expr) {
            is LuaTerminalExpr -> evaluateTerminalExpr(expr)
            is LuaTableConstructor -> evaluateTableConstructor(expr)
            is LuaFuncDef -> evaluateFuncDef(expr)
            is LuaPrefixExpr -> evaluatePrefixExpr(expr)
            is LuaUnOpExpr -> evaluateUnOpExpr(expr)
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
                    psiElement = expr.number,
                )
            }

            expr.string != null -> {
                val stringValue = extractLuaString(expr.string!!.text)
                LuaValue(
                    kind = LuaValueKind.String,
                    stringValue = stringValue,
                    psiElement = null,
                )
            }

            expr.firstChild?.elementType == LuaElementTypes.NIL -> {
                LuaValue(kind = LuaValueKind.Nil)
            }

            expr.text == "true" -> {
                LuaValue(
                    kind = LuaValueKind.Boolean,
                    boolValue = true,
                    psiElement = expr,
                )
            }

            expr.text == "false" -> {
                LuaValue(
                    kind = LuaValueKind.Boolean,
                    boolValue = false,
                    psiElement = expr,
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
                    table.addByName(field.name!!, fieldValue)
                } else {
                    // Positional field
                    table.push(fieldValue)
                }
            }
        }

        return LuaValue(
            kind = LuaValueKind.Table,
            tableValue = table,
            psiElement = expr,
        )
    }

    private fun evaluateFuncDef(expr: LuaFuncDef): LuaValue {
        return LuaValue(
            kind = LuaValueKind.Function,
            psiElement = expr,
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
        return null
    }

    private fun evaluateUnOpExpr(expr: LuaUnOpExpr): LuaValue? {
        val rightExpr = expr.expr ?: return null
        val rightValue = evaluateExpression(rightExpr) ?: return null
        return when (val op = expr.unOp.text) {
            "-" -> {
                // Negate a number
                if (rightValue.kind == LuaValueKind.Number && rightValue.numberValue != null) {
                    LuaValue(
                        kind = LuaValueKind.Number,
                        numberValue = -rightValue.numberValue,
                        psiElement = expr,
                    )
                } else {
                    null
                }
            }

            "not" -> {
                // Logical NOT - negate a boolean
                val boolVal = when (rightValue.kind) {
                    LuaValueKind.Nil -> false
                    LuaValueKind.Boolean -> rightValue.boolValue ?: false
                    else -> true
                }
                LuaValue(
                    kind = LuaValueKind.Boolean,
                    boolValue = !boolVal,
                    psiElement = expr,
                )
            }

            "#" -> {
                // Length operator - only meaningful for tables and strings
                val length = when (rightValue.kind) {
                    LuaValueKind.Table -> (rightValue.tableValue?.indexed?.size ?: 0).toDouble()
                    LuaValueKind.String -> (rightValue.stringValue?.length ?: 0).toDouble()
                    else -> 0.0
                }
                LuaValue(
                    kind = LuaValueKind.Number,
                    numberValue = length,
                    psiElement = expr,
                )
            }

            "~" -> {
                // Bitwise NOT - for numbers, negate all bits
                if (rightValue.kind == LuaValueKind.Number && rightValue.numberValue != null) {
                    val intValue = rightValue.numberValue.toLong()
                    val result = intValue.inv().toDouble()
                    LuaValue(
                        kind = LuaValueKind.Number,
                        numberValue = result,
                        psiElement = expr,
                    )
                } else {
                    null
                }
            }

            else -> {
                log.warn("Unknown unary operator: $op")
                null
            }
        }
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
                return null
            }

            val indexExpr = suffix.indexExpr
            currentValue = evaluateVarSuffixIndex(currentValue, indexExpr)
        }

        return currentValue
    }

    private fun evaluateVarSuffixIndex(
        baseValue: LuaValue,
        indexExpr: LuaIndexExpr,
    ): LuaValue {
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
                    return table.getByName(key)?.second ?: LuaValue.NIL
                }
            }
        }

        // Handle dot notation: t.field
        val nameRef = indexExpr.nameRef
        if (nameRef != null) {
            val fieldName = nameRef.text
            return table.getByName(fieldName)?.second ?: LuaValue.NIL
        }

        return LuaValue.NIL
    }

    fun parse(doStatement: LuaDoStatement): LuaTable {
        // Execute each statement in the block
        val block = doStatement.block
        val statements = block.statementList

        for (i in statements.indices) {
            val stmt = statements[i]
            val isLastStatement = i == statements.size - 1

            when {
                isLastStatement && stmt is LuaFinalStatement -> {
                    // Process return statement - if it's a single table, return it directly
                    val exprList = stmt.exprList?.exprList ?: emptyList()
                    if (exprList.size == 1) {
                        val value = evaluateExpression(exprList[0])
                        if (value != null && value.kind == LuaValueKind.Table && value.tableValue != null) {
                            return value.tableValue
                        }
                    }
                    // Otherwise wrap multiple return values in a table
                    val table = LuaTable()
                    for (expr in exprList) {
                        val value = evaluateExpression(expr)
                        if (value != null) {
                            table.push(value)
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

                stmt is LuaEmptyStatement -> {}

                else -> {
                    // Don't return, just continue to next statement
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

    companion object {
        private val log = logger<LuaDebugValueParser>()

        fun parseStringAsLuaValue(project: Project, content: String): LuaValue? {
            return try {
                // Wrap in a table to preserve structure (e.g., {$value} ensures tables stay intact)
                val wrappedCode = "do return {$content} end"
                val file = LuaElementFactory.createFile(project, wrappedCode)
                val doStatement = PsiTreeUtil.findChildOfType(file, LuaDoStatement::class.java)
                    ?: return null

                val table = LuaDebugValueParser(project).parse(doStatement)

                // Extract the single parsed value from the result table
                table.indexed.firstOrNull()
                    ?: table.named.values.firstOrNull()
                    ?: LuaValue.newTable(table)
            } catch (e: Exception) {
                log.warn("Failed to parse stringified value: $content", e)
                null
            }
        }

        fun parseChunk(project: Project, text: String): LuaTable {
            val file = LuaElementFactory.createFile(project, text)
            return parseFile(file, project)
        }

        fun parseFile(file: PsiFile, project: Project? = null): LuaTable {
            val doStatement = PsiTreeUtil.findChildOfType(file, LuaDoStatement::class.java)
                ?: return LuaTable()
            val parser = LuaDebugValueParser(project)
            return parser.parse(doStatement)
        }
    }
}
