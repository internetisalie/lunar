package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * BUG-102: a bare `require` of a module with no project file used to resolve to an unrelated
 * `init.lua`, closing a fabricated cycle whose findings depended on which file was read first.
 */
class RequireRootIndependenceTest : BasePlatformTestCase() {
    private fun addFixtures(): Pair<PsiFile, PsiFile> {
        val sub =
            myFixture.addFileToProject(
                "sub/init.lua",
                "local u = require(\"user\")\nlocal S = { name = \"s\" }\n---@type number\nlocal un = u.name\nreturn S\n",
            )
        val user =
            myFixture.addFileToProject(
                "user.lua",
                "local checks = require(\"checks\")\nlocal U = { name = \"s\" }\n---@type number\nlocal cn = checks.name\nreturn U\n",
            )
        return user to sub
    }

    private fun findings(file: PsiFile): List<String> =
        runReadAction { LuaTypesSnapshot.forFile(file).getErrors().map { it.message } }

    fun testUserFirst() {
        val (user, sub) = addFixtures()
        val userFindings = findings(user)
        val subFindings = findings(sub)
        assertEmpty(userFindings)
        assertTrue("sub/init.lua must report: $subFindings", subFindings.any { it.contains("is not assignable") })
    }

    fun testSubFirst() {
        val (user, sub) = addFixtures()
        val subFindings = findings(sub)
        val userFindings = findings(user)
        assertEmpty(userFindings)
        assertTrue("sub/init.lua must report: $subFindings", subFindings.any { it.contains("is not assignable") })
    }

    fun testNonCycleControlIsOrderIndependent() {
        val c =
            myFixture.addFileToProject(
                "c.lua",
                "local d = require(\"d\")\n---@type number\nlocal x = d.name\nreturn {}\n",
            )
        val d = myFixture.addFileToProject("d.lua", "return { name = \"s\" }\n")
        val cFirst = findings(c)
        findings(d)
        assertTrue(cFirst.any { it.contains("is not assignable") })
    }
}
