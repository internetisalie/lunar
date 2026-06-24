package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiPolyVariantReference
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Follow-ups (1) + (2): resolving a dotted member access `a.b` must be driven by the receiver `a`'s
 * type, not by the bare member name `b`. Today `package.path` mis-resolves to every same-short-name
 * `path.*` function of an unrelated module (multiResolve returns dozens of candidates, resolve()=null),
 * which breaks Go-to-declaration and quick documentation.
 *
 * Target behaviour: `package.path` resolves to the `package` table's own `path` field declaration
 * (in the stdlib stub here), and to nothing from the unrelated `path` module.
 */
@RunWith(JUnit4::class)
class ReceiverAwareMemberResolutionTest : IndexedBasePlatformTestCase() {

    @Test
    fun testPackagePathResolvesToReceiverFieldNotUnrelatedModule() {
        myFixture.addFileToProject(
            "luarocks/path.lua",
            """
            local path = {}
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
            ---Paths for searching modules
            ---@type string
            package.path = ""
            """.trimIndent(),
        )

        myFixture.configureByText("test.lua", "local p = package.pa<caret>th\n")

        val ref = myFixture.getReferenceAtCaretPosition()
        val results = (ref as? PsiPolyVariantReference)?.multiResolve(false)?.toList().orEmpty()

        // (2) / NAV-12-04: a dotted member must not resolve to unrelated same-short-name module
        // *functions* (the index is keyed by receiver, so a bare "path" lookup used to pull in every
        // `path.*` function). Every result must now be the `path` field identifier itself.
        assertTrue("package.path must resolve to something", results.isNotEmpty())
        assertTrue(
            "every result must be the `path` field identifier, never a `path.*` function; got " +
                "${results.map { it.element?.text?.take(40) }}",
            results.all { it.element?.text == "path" },
        )

        // NAV-12-02: it DOES resolve to the receiver's own `path` field declaration in the stub.
        assertTrue(
            "package.path should resolve to its `path` field declaration in package.lua; got " +
                "${results.map { "${it.element?.text?.take(20)}@${it.element?.containingFile?.name}" }}",
            results.any { it.element?.text == "path" && it.element?.containingFile?.name == "package.lua" },
        )
    }
}
