package net.internetisalie.lunar.analysis.inspections

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.jupiter.api.TestInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BUG-504: a bare name never names a dotted or colon function. A bare, undeclared `close` in a file
 * that `require`s a module declaring `---@deprecated function M:close()` resolves, through the
 * required file's bindings, to the method's name leaf, and used to warn.
 *
 * Heavy, real-disk fixture because `require` resolution reads `LocalFileSystem` only; a light
 * fixture's `temp://` files are never found, so the case would pass without reaching the defect.
 * The first assertion is that reach precondition. The resolution it relies on is itself a defect,
 * BUG-512; fixing that makes the precondition fail by design, and this test should then be revisited,
 * not deleted.
 */
class LuaDeprecatedBareNameThroughRequireTest {
    private lateinit var myFixture: CodeInsightTestFixture

    @BeforeTest
    fun setUp(testInfo: TestInfo) {
        runInEdtAndWait {
            val factory = IdeaTestFixtureFactory.getFixtureFactory()
            val projectBuilder = factory.createFixtureBuilder(testInfo.displayName)
            myFixture = factory.createCodeInsightFixture(projectBuilder.fixture, TempDirTestFixtureImpl())
            myFixture.setUp()
            val moduleBuilder = projectBuilder.addModule(EmptyModuleFixtureBuilder::class.java)
            moduleBuilder.addSourceContentRoot(myFixture.tempDirPath)
            moduleBuilder.fixture.setUp()
            LuaProjectSettings.getInstance(myFixture.project).state.sourcePath = "${myFixture.tempDirPath}/?.lua"
        }
    }

    @AfterTest
    fun tearDown() {
        runInEdtAndWait { myFixture.tearDown() }
    }

    @Test
    fun aBareNameDoesNotMatchARequiredModulesDeprecatedMethod() {
        runInEdtAndWait {
            myFixture.enableInspections(LuaDeprecatedApiInspection())
            myFixture.addFileToProject(
                "mod.lua",
                "local M = {}\n---@deprecated gone\nfunction M:close() end\nreturn M\n",
            )
            val main = myFixture.addFileToProject("main.lua", "local mod = require(\"mod\")\nclose()\n")
            myFixture.configureFromExistingVirtualFile(main.virtualFile)

            assertTrue(closeResolvesIntoTheModule(), "precondition: bare 'close' resolves to M:close's name leaf")
            assertEquals(emptyList(), deprecatedRanges(), "a bare 'close' is not the method M:close")
        }
    }

    private fun closeResolvesIntoTheModule(): Boolean {
        val close = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java).single { it.text == "close" }
        val targets = (close.reference as? PsiPolyVariantReference)?.multiResolve(false).orEmpty()
        return targets.any { it.element?.containingFile?.name == "mod.lua" }
    }

    private fun deprecatedRanges(): List<Pair<Int, Int>> =
        myFixture
            .doHighlighting()
            .filter { it.description?.startsWith("Deprecated API") == true }
            .map { it.startOffset to it.endOffset }
}
