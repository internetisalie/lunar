package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsArgType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCastModifier
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCastTag

/**
 * BUG-99: applies `---@cast name T` (and the `+T`, `-T`, `-?` forms) by rebinding [name] in the
 * current scope to a FRESH variable seeded with the cast result.
 *
 * The new variable is deliberately not connected to the old node. Cutting that connection is the fix:
 * the old node is fed by the parameter's declared value, every caller's argument and the callee's own
 * returns, so a declared demand raised by a use after the cast would otherwise pair with all of them.
 */
internal class LuaCastBinder(
    private val graph: LuaTypeGraph,
) {
    fun apply(
        tag: LuaCatsCastTag,
        scope: LuaScope,
    ) {
        val name = tag.argName.text
        val original = scope.lookup(name) ?: return
        val modifiers = tag.argTypeList.mapNotNull(::modifierOf)
        if (modifiers.isEmpty()) return
        val result = modifiers.fold(original.write) { current, modifier -> applyModifier(current, modifier) }
        val seed = graph.value(tag, result, declaredOrigin = true)
        val castVariable = graph.variable(tag)
        graph.addEdge(seed, castVariable)
        scope.declare(name, castVariable)
    }

    private fun modifierOf(argType: LuaCatsArgType): LuaCatsCastModifier? =
        PsiTreeUtil.getChildOfType(argType, LuaCatsCastModifier::class.java)
            ?: PsiTreeUtil.findChildOfType(argType, LuaCatsCastModifier::class.java)

    private fun applyModifier(
        current: LuaGraphType,
        modifier: LuaCatsCastModifier,
    ): LuaGraphType {
        val sign = modifier.addOrSubtract?.text
        val operand = modifier.type?.let { resolve(it.text.trim(), modifier) } ?: LuaGraphType.Nil
        return when (sign) {
            "+" -> LuaGraphType.Union.create(membersOf(current) + operand)
            "-" -> subtract(current, operand)
            else -> operand
        }
    }

    private fun resolve(
        typeText: String,
        modifier: LuaCatsCastModifier,
    ): LuaGraphType = LuaGraphType.fromLuaType(TypeParser.parse(typeText, modifier), graph)

    private fun membersOf(type: LuaGraphType): List<LuaGraphType> =
        (type as? LuaGraphType.Union)?.types?.toList() ?: listOf(type)

    private fun subtract(
        current: LuaGraphType,
        operand: LuaGraphType,
    ): LuaGraphType {
        val removed = membersOf(operand)
        return LuaTypeAlgebra.canonicalize(
            membersOf(current).filterNot { member ->
                removed.any { sameType(member, it) }
            },
        )
    }

    /** Class-named tables compare by name: their member maps are distinct instances per materialization. */
    private fun sameType(
        member: LuaGraphType,
        other: LuaGraphType,
    ): Boolean {
        val memberClass = (member as? LuaGraphType.Table)?.className
        val otherClass = (other as? LuaGraphType.Table)?.className
        return member == other || (memberClass != null && memberClass == otherClass)
    }
}
