package net.internetisalie.lunar.lang.path

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaRequireReference

/**
 * BUG-102: a bare module name accepts a `<name>.lua` anywhere, but an `init.lua` only as
 * `<name>/init.lua`, never a foreign directory's `init.lua`.
 */
class LuaModuleFileResolverInitLuaTest : BasePlatformTestCase() {
    fun testBareNameDoesNotResolveToForeignInitLua() {
        myFixture.addFileToProject("sub/init.lua", "return {}")
        assertEmpty(candidates("checks"))
    }

    fun testBareNameResolvesToOwnInitLua() {
        myFixture.addFileToProject("checks/init.lua", "return {}")
        val found = candidates("checks")
        assertEquals(1, found.size)
        assertTrue(found.single().endsWith("checks/init.lua"))
    }

    fun testBareNameStillMatchesNamedFileAnywhere() {
        myFixture.addFileToProject("plugins/json.lua", "return {}")
        val found = candidates("json")
        assertEquals(1, found.size)
        assertTrue(found.single().endsWith("plugins/json.lua"))
    }

    fun testRequireNavigationSkipsForeignInitLua() {
        myFixture.addFileToProject("sub/init.lua", "return {}")
        myFixture.configureByText("use.lua", "local c = require(\"checks\")\n")
        val targets =
            runReadAction {
                PsiTreeUtil
                    .collectElements(myFixture.file) { true }
                    .flatMap { it.references.asIterable() }
                    .filterIsInstance<LuaRequireReference>()
                    .map { it.resolve() }
            }
        assertEquals(1, targets.size)
        assertNull(targets.single())
    }

    private fun candidates(moduleName: String): List<String> =
        runReadAction { resolveModuleCandidates(project, moduleName).map { it.virtualFile.path }.toList() }
}
