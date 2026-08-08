package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import kotlin.system.measureTimeMillis

/**
 * THROWAWAY — COMP-09: re-measure the figures the plan quotes, with REPETITION AND MEDIANS.
 *
 * The adversarial Step 9 review showed the single-shot `measureTimeMillis` figures in design §1.2
 * and §1.6 spread up to -60 % run to run, so every ratio derived from a pair of them sat inside its
 * own noise floor — and §1.2's harness verdict actually flipped on re-run. Medians of 5, and the
 * spread printed so a reader can judge.
 */
class CompNineMediansTest : LibraryRootTestCase() {
    private fun median(runs: List<Long>) = runs.sorted()[runs.size / 2]

    private fun report(
        label: String,
        runs: List<Long>,
    ) {
        val s = runs.sorted()
        println("MEDIAN $label: median=${median(runs)}ms min=${s.first()} max=${s.last()} runs=$runs")
    }

    /** §1.6's `@class` door, 5 distinct classes so each is a cold class in a warm file. */
    fun testClassDoorMedians() {
        val sb = StringBuilder("---@meta\n\n")
        repeat(10) { c ->
            sb.append("---@class Big$c\nlocal Big$c = {}\n\n")
            repeat(500) { m -> sb.append("---@param a number\n---@return boolean\nfunction Big$c:M$m(a) end\n\n") }
        }
        registerLibraryRoot(mapOf("big.lua" to sb.toString()))
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        println("MEDIAN fixture ${sb.length / 1024} KiB")

        val runs = mutableListOf<Long>()
        runReadAction {
            val m = LuaTypeManager.getInstance(project)
            // Big0 is cold-file + cold-class; 1..5 are warm-file + cold-class.
            val cold = measureTimeMillis { m.resolveType("Big0", myFixture.file) }
            println("MEDIAN classDoor cold-file+cold-class (single, unavoidable) = ${cold}ms")
            (1..5).forEach { i ->
                runs +=
                    measureTimeMillis {
                        m.resolveType("Big$i", myFixture.file)?.let {
                            LuaGraphType.materialize(it, myFixture.file).getMembers().size
                        }
                    }
            }
        }
        report("classDoor warm-file+cold-class (500 members)", runs)
    }

    /**
     * B2's核: is there ANY per-member type cost at add time? `memberNode.write` comes from the
     * already-materialized graph and `displayName()` is a pure structural `when`. If rendering
     * 3700 type strings is ~0ms, then BOTH incremental yield and lazy rendering are unnecessary.
     */
    fun testPerMemberTypeCostIsFree() {
        val sb = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(3400) { i -> sb.append("---@type number\nwx.wxC_$i = nil\n\n") }
        repeat(300) { i -> sb.append("---@return boolean\nfunction wx.f$i() end\n\n") }
        sb.append("return wx\n")
        registerLibraryRoot(mapOf("wx.lua" to sb.toString()))
        myFixture.configureByText("consumer.lua", "local x = 1\n")

        runReadAction {
            val resolved = LuaTypeManager.getInstance(project).resolveGlobal("wx", myFixture.file)!!
            val graph = LuaGraphType.materialize(resolved, myFixture.file)
            val members = graph.getMembers()
            val renders = mutableListOf<Long>()
            repeat(5) {
                renders +=
                    measureTimeMillis {
                        var sink = 0
                        for ((name, node) in members) {
                            val t = node.write
                            val isFn = t is LuaGraphType.Function // the isColon filter at :384
                            sink += name.length + t.displayName().length + if (isFn) 1 else 0
                        }
                        require(sink > 0)
                    }
            }
            report("per-member type+displayName for ${members.size} members", renders)
            println("MEDIAN => if this is single-digit ms, presentation was NEVER the cost")
        }
    }
}
