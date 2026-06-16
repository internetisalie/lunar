package net.internetisalie.lunar.lang.insight

import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.psi.LuaBinOpExpr
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaUnOpExpr

object LuaConditionInverter {

    private val flippedRelational: Map<IElementType, String> = mapOf(
        LuaElementTypes.EQ to "~=",
        LuaElementTypes.NE to "==",
        LuaElementTypes.LT to ">=",
        LuaElementTypes.LE to ">",
        LuaElementTypes.GT to "<=",
        LuaElementTypes.GE to "<",
    )

    fun invertedText(condition: LuaExpr): String {
        invertRelational(condition)?.let { return it }
        invertNot(condition)?.let { return it }
        return "not (" + condition.text + ")"
    }

    private fun invertRelational(condition: LuaExpr): String? {
        if (condition !is LuaBinOpExpr) return null
        val operatorType = condition.binOp.firstChild?.node?.elementType ?: return null
        val flippedOp = flippedRelational[operatorType] ?: return null
        val left = condition.left.text
        val right = condition.right?.text ?: return null
        return "$left $flippedOp $right"
    }

    private fun invertNot(condition: LuaExpr): String? {
        if (condition !is LuaUnOpExpr) return null
        if (condition.unOp.firstChild?.node?.elementType != LuaElementTypes.NOT) return null
        return condition.expr?.text
    }
}
