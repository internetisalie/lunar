package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager

/**
 * THROWAWAY — COMP-09 DR-09, follow-up.
 *
 * DR-09b found one membership divergence: today's enumeration of `Shapes` includes `deep`, from
 * `Shapes.nested.deep = 1`. That contradicts `memberNameOf`'s "exactly one separator" rule, which is
 * what design §4.4 was derived from — and `LuaImplicitFields.singleFieldSuffixName` rejects
 * `base.x.y` explicitly, so it is not that path either.
 *
 * So a third membership rule exists somewhere. This isolates which door produces `deep` and whether
 * it is attributed to `Shapes` or reached through `Shapes.nested`.
 */
class CompNineDr09bTest : LibraryRootTestCase() {
    fun testWhereDeepComesFrom() {
        registerLibraryRoot(
            mapOf(
                "chain.lua" to
                    """
                    ---@meta

                    ---@class Shapes
                    Shapes = {}

                    Shapes.nested = {}
                    Shapes.nested.deep = 1
                    Shapes.nested.alsoDeep = "s"

                    Shapes.direct = 2

                    return Shapes
                    """.trimIndent(),
                // The same chain with NO `---@class`, so only the graph door can answer.
                "plain.lua" to
                    """
                    Plain = {}
                    Plain.mid = {}
                    Plain.mid.leaf = 1
                    """.trimIndent(),
            ),
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        runReadAction {
            val manager = LuaTypeManager.getInstance(project)
            val context = myFixture.file
            listOf("Shapes", "Plain").forEach { name ->
                val viaGlobal = manager.resolveGlobal(name, context)
                val viaType = manager.resolveType(name, context)
                val globalDoor = viaGlobal?.let { it::class.simpleName }
                val typeDoor = viaType?.let { it::class.simpleName }
                println("DR-09-deep $name resolveGlobal=$globalDoor resolveType=$typeDoor")
                viaGlobal?.let {
                    val g = LuaGraphType.materialize(it, context).getMembers()
                    println("DR-09-deep   GLOBAL door members = ${g.keys.sorted()}")
                    g["nested"]?.let { n -> println("DR-09-deep   nested node write=${n.write}") }
                    g["mid"]?.let { n -> println("DR-09-deep   mid node write=${n.write}") }
                }
                viaType?.let {
                    val t = LuaGraphType.materialize(it, context).getMembers()
                    println("DR-09-deep   TYPE door members   = ${t.keys.sorted()}")
                }
            }
        }
    }
}
