package net.internetisalie.lunar.lang.types

import net.internetisalie.lunar.lang.doc.LuaDocumentationTargetProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Regression for the quick-documentation mis-resolution: the doc shown for `package.path`
 * resolved to an unrelated `path.rockspec_name_from_rock` from a LuaRocks `path` module.
 *
 * LuaGlobalDeclarationIndex is keyed by receiver name, so looking up the bare member segment
 * "path" returns every `path.*` function of the unrelated module; the documentation provider then
 * picked the first. A dotted member segment must only resolve through its qualified name, never an
 * arbitrary top-level symbol that merely shares the short name.
 */
@RunWith(JUnit4::class)
class QualifiedMemberResolutionTest : IndexedBasePlatformTestCase() {

    @Test
    fun testPackagePathQuickDocDoesNotResolveToUnrelatedModuleFunction() {
        myFixture.addFileToProject(
            "luarocks/path.lua",
            """
            local path = {}
            ---Infer rockspec filename from a rock filename.
            ---@return any
            function path.rockspec_name_from_rock(rock_name) end
            function path.root_dir(tree) end
            return path
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "package.lua",
            """
            ---@meta
            ---@class package
            package = {}
            ---@type string
            package.path = ""
            """.trimIndent(),
        )

        myFixture.configureByText("test.lua", "local p = package.pa<caret>th\n")

        val targets = LuaDocumentationTargetProvider()
            .documentationTargets(myFixture.file, myFixture.caretOffset)
        val presentations = targets.map { it.computePresentation().presentableText }

        assertTrue(
            "Quick documentation for package.path must not resolve to an unrelated path.* function; got $presentations",
            presentations.none { it.contains("rockspec_name_from_rock") || it.contains("root_dir") },
        )
    }
}
