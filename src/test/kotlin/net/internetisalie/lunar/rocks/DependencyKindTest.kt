package net.internetisalie.lunar.rocks

import net.internetisalie.lunar.rocks.deps.DependencyKind
import net.internetisalie.lunar.rocks.deps.DependencyNode
import net.internetisalie.lunar.rocks.deps.DependencySpec
import net.internetisalie.lunar.rocks.deps.LuaRocksVersion
import net.internetisalie.lunar.rocks.ui.DependencyLabels
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** BUG-54: project rocks and the `lua` runtime are not installed rocks. */
class DependencyKindTest {
    private fun runtime(
        version: String?,
        constraint: String,
    ) = DependencyNode(
        "lua",
        isTransitive = false,
        resolvedVersion = version?.let { LuaRocksVersion.parse(it) },
        kind = DependencyKind.RUNTIME,
    ).also { node -> node.requiredConstraints += requireNotNull(DependencySpec.parse("lua $constraint")).constraints }

    @Test
    fun `lua without a configured runtime is not reported missing`() {
        val lua = runtime(null, ">= 5.1")

        VersionConflictEngine.annotate(lua)

        assertTrue(lua.conflicts.isEmpty())
        assertEquals("lua (no runtime configured)", DependencyLabels.treeText(lua))
    }

    @Test
    fun `lua is checked against the runtime version`() {
        val lua = runtime("5.4", "< 5.4")

        VersionConflictEngine.annotate(lua)

        assertEquals(1, lua.conflicts.size)
    }

    @Test
    fun `an unversioned project rock is not reported missing`() {
        val root = DependencyNode("app", false, kind = DependencyKind.PROJECT, location = "app.rockspec")

        VersionConflictEngine.annotate(root)

        assertTrue(root.conflicts.isEmpty())
    }

    @Test
    fun `two project roots of one name are told apart by their rockspec`() {
        val first =
            DependencyNode("app", false, LuaRocksVersion.parse("1.0-1"), DependencyKind.PROJECT, "a/app-1.0-1.rockspec")
        val second =
            DependencyNode("app", false, LuaRocksVersion.parse("1.0-1"), DependencyKind.PROJECT, "b/app-1.0-1.rockspec")

        assertNotEquals(DependencyLabels.treeText(first), DependencyLabels.treeText(second))
        assertEquals("project rock", DependencyLabels.scopeText(first))
    }
}
