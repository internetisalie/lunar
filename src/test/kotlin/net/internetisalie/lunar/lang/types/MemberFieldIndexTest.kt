package net.internetisalie.lunar.lang.types

import com.intellij.psi.search.GlobalSearchScope
import net.internetisalie.lunar.lang.navigation.LuaMemberFieldNavigation
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-12-01: the qualified member-field index/navigation finds a `receiver.field = value` declaration
 * by its qualified name, across files, and nothing for unknown names.
 */
@RunWith(JUnit4::class)
class MemberFieldIndexTest : IndexedBasePlatformTestCase() {

    @Test
    fun testIndexFindsQualifiedFieldDeclaration() {
        myFixture.addFileToProject(
            "package.lua",
            """
            ---@meta
            ---@class package
            package = {}
            ---Paths for searching modules
            ---@type string
            package.path = ""
            """.trimIndent(),
        )
        myFixture.configureByText("use.lua", "local p = package.path\n")

        val results = LuaMemberFieldNavigation.find(
            project,
            "package.path",
            GlobalSearchScope.allScope(project),
        )

        // `package.path` is declared (and re-assigned) in several files; every hit must be the `path`
        // field identifier (never a `path.*` module function), and the stub declaration is among them.
        assertTrue("package.path must resolve to at least one field declaration", results.isNotEmpty())
        assertTrue(
            "every result must be the `path` field identifier; got ${results.map { it.text }}",
            results.all { it.text == "path" },
        )
        assertTrue(
            "the `package.lua` field declaration must be among the results; got ${results.map { it.containingFile.name }}",
            results.any { it.containingFile.name == "package.lua" },
        )
    }

    @Test
    fun testUnknownNameResolvesToNothing() {
        myFixture.configureByText("use.lua", "local p = nope.nope\n")
        val results = LuaMemberFieldNavigation.find(
            project,
            "nope.nope",
            GlobalSearchScope.allScope(project),
        )
        assertTrue("unknown qualified field must resolve to nothing", results.isEmpty())
    }
}
