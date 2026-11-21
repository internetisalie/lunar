package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import net.internetisalie.lunar.lang.psi.types.VariableNode
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-97 part 2 — variables wired both ways collapse into one node, and each still reports at its own
 * element.
 *
 * luals `script/parser/compile.lua` joins the `start` and `finish` fields of hundreds of tables into one
 * cluster, and every variable of a cluster of k held every other in its sets: k² entries to propagate
 * and k² per walk. The entries and the walk steps are deterministic, so they are asserted, not a time.
 */
@RunWith(JUnit4::class)
class LuaTypeGraphClusterCollapseTest : BasePlatformTestCase() {
    @Test
    fun aClusterHoldsOneSetNotOnePerMember() {
        val cluster = Cluster(sourceElements())

        // k variables each holding k others on both sides would be 2 * k * (k - 1) entries.
        val closure = cluster.graph.closureSize()
        assertTrue("linear in $SIZE members, not quadratic: closure of $closure entries", closure < SIZE)
    }

    @Test
    fun walksOverAClusterIterateOneSetPerRoot() {
        val cluster = Cluster(sourceElements())
        val before = cluster.graph.walkStepCount()

        cluster.members.forEach { it.write }

        val steps = cluster.graph.walkStepCount() - before
        assertTrue("under ${SIZE * WALK_BUDGET} entries for $SIZE roots; iterated $steps", steps < SIZE * WALK_BUDGET)
    }

    @Test
    fun everyMemberStillReportsAtItsOwnElement() {
        val elements = sourceElements()
        val cluster = Cluster(elements)

        cluster.graph.checkTypes()

        val anchors =
            cluster.graph.errors
                .map { it.element }
                .toSet()
        assertTrue("every member anchors its own error", anchors.containsAll(elements.members))
    }

    @Test
    fun aMemberJoinedLaterIsStillChecked() {
        val elements = sourceElements()
        val cluster = Cluster(elements)
        cluster.graph.checkTypes()
        val late = cluster.graph.variable(elements.late)
        cluster.graph.addEdge(cluster.members.first(), late)
        cluster.graph.addEdge(late, cluster.members.first())

        cluster.graph.checkTypes()

        assertTrue("the late member anchors its own error", elements.late in cluster.graph.errors.map { it.element })
    }

    private class Elements(
        val members: List<PsiElement>,
        val late: PsiElement,
        val value: PsiElement,
        val use: PsiElement,
    )

    private fun sourceElements(): Elements {
        val names = List(SIZE) { "m$it" } + listOf("late", "val", "use")
        val file = myFixture.configureByText("cluster.lua", "local ${names.joinToString(", ")}\n")
        val leaves = PsiTreeUtil.collectElements(file) { it.firstChild == null && it.text in names }

        fun leaf(name: String) = leaves.first { it.text == name }
        return Elements(List(SIZE) { leaf("m$it") }, leaf("late"), leaf("val"), leaf("use"))
    }

    /**
     * [SIZE] variables wired both ways in a line, a string entering at the first and a declared number
     * use leaving the last: every member holds the string against the number.
     */
    private class Cluster(
        elements: Elements,
    ) {
        val graph = LuaTypeGraph()
        val members: List<VariableNode> = elements.members.map { graph.variable(it) }

        init {
            members.zipWithNext().forEach { (left, right) ->
                graph.addEdge(left, right)
                graph.addEdge(right, left)
            }
            graph.addEdge(graph.value(elements.value, LuaGraphType.String), members.first())
            graph.addEdge(members.last(), graph.use(elements.use, LuaGraphType.Number, declaredDemand = true))
        }
    }

    private companion object {
        const val SIZE = 40
        const val WALK_BUDGET = 8
    }
}
