package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.doc.LuaDocumentationTargetProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-12-03: quick documentation on a dotted member field renders the field's own `---@type`/doc
 * comment, choosing the *documented* declaration over a bare re-assignment.
 */
@RunWith(JUnit4::class)
class MemberFieldQuickDocTest : IndexedBasePlatformTestCase() {

    @Test
    fun testPackagePathQuickDocResolvesToDocumentedField() {
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
        // An undocumented re-assignment elsewhere must NOT be chosen as the doc target.
        myFixture.addFileToProject("setup.lua", "package.path = package.path\n")
        myFixture.configureByText("test.lua", "local p = package.pa<caret>th\n")

        val targets = LuaDocumentationTargetProvider()
            .documentationTargets(myFixture.file, myFixture.caretOffset)
        assertTrue("quick documentation must resolve for package.path", targets.isNotEmpty())

        val navigatable = targets.first().navigatable as? PsiElement
        assertEquals(
            "must anchor on the documented field declaration, not the bare re-assignment in setup.lua",
            "package.lua",
            navigatable?.containingFile?.name,
        )

        assertNotNull("the documented field must render documentation", targets.first().computeDocumentation())
    }
}
