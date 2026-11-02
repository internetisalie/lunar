package net.internetisalie.lunar.analysis.inspections

import com.intellij.psi.PsiElement
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
import kotlin.test.assertFalse

/**
 * BUG-504: a bare name never names a dotted or colon function. A bare, undeclared `close` in a file
 * that `require`s a module declaring `---@deprecated function M:close()` resolves, through the
 * required file's bindings, to the method's name leaf, and used to warn.
 *
 * Heavy, real-disk fixture because `require` resolution reads `LocalFileSystem` only; a light
 * fixture's `temp://` files are never found, so the case would pass without reaching the defect.
 * The first assertion is that reach precondition: a bare `close` resolved to the method's name leaf
 * until BUG-512 was fixed, and the precondition now asserts that it no longer does.
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

            assertFalse(closeResolvesIntoTheModule(), "a bare 'close' does not resolve to M:close's name leaf")
            assertEquals(emptyList(), deprecatedRanges(), "a bare 'close' is not the method M:close")
        }
    }

    /**
     * MAINT-53 TC-21: a table key never names a file-scope binding of a required file. `lib.lua` binds
     * `insert` (a local) and `gins` (a global) at file scope; no member or function-name segment of
     * that name in `main.lua` resolves into it.
     */
    @Test
    fun aTableKeyDoesNotResolveToARequiredFilesBinding() {
        runInEdtAndWait {
            myFixture.addFileToProject(
                "lib.lua",
                "local insert = table.insert\ngins = 1\nlocal M = {}\nfunction M.go() end\nreturn M\n",
            )
            val main =
                myFixture.addFileToProject(
                    "main.lua",
                    "local lib = require(\"lib\")\nlocal t = {}\nt.insert(1)\nprint(t.insert)\n" +
                        "function t.insert() end\nfunction t:insert() end\nlocal u = make()\nu.insert(1)\n" +
                        "print(u.gins)\nfunction u.gins() end\n",
                )
            myFixture.configureFromExistingVirtualFile(main.virtualFile)

            val expected =
                mapOf(
                    3 to listOf("main.lua:5"),
                    4 to listOf("main.lua:5"),
                    5 to emptyList(),
                    6 to emptyList(),
                    8 to emptyList(),
                    9 to listOf("main.lua:10"),
                    10 to emptyList(),
                )
            assertEquals(expected, tableKeyTargetsByLine(), "no table key reaches lib.lua")
        }
    }

    /**
     * MAINT-53 TC-22: a member of a `require`d module resolves through the module's returned type, so
     * another module's same-named (and deprecated) member is not a target.
     *
     * **Mutation**: delete `requiredModuleType`'s call in `headType`, or skip `typedTargets`; both
     * `M.clear` declarations resolve.
     */
    @Test
    fun aMemberOfARequiredModuleResolvesToThatModulesDeclaration() {
        runInEdtAndWait {
            myFixture.addFileToProject(
                "codelens.lua",
                "local M = {}\n---@deprecated\nfunction M.clear(client_id, bufnr) end\nreturn M\n",
            )
            myFixture.addFileToProject("manage.lua", "local M = {}\nfunction M.clear(plugins) end\nreturn M\n")
            val main = myFixture.addFileToProject("main.lua", "local M = require(\"manage\")\nM.clear()\n")
            myFixture.configureFromExistingVirtualFile(main.virtualFile)

            val clear =
                PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java).single {
                    it.text ==
                        "clear"
                }
            val targets = (clear.reference as PsiPolyVariantReference).multiResolve(false).mapNotNull { it.element }

            assertEquals(
                listOf("manage.lua:function M.clear(plugins) end"),
                targets.map { "${it.containingFile.name}:${it.text}" },
            )
        }
    }

    private fun tableKeyTargetsByLine(): Map<Int, List<String>> =
        PsiTreeUtil
            .findChildrenOfType(myFixture.file, LuaNameRef::class.java)
            .filter { it.text == "insert" || it.text == "gins" }
            .associate { ref ->
                val targets = (ref.reference as PsiPolyVariantReference).multiResolve(false).mapNotNull { it.element }
                lineOf(ref) to targets.map { "${it.containingFile.name}:${lineOf(it)}" }
            }

    private fun lineOf(element: PsiElement): Int =
        element.containingFile.text
            .substring(0, element.textOffset)
            .count { it == '\n' } + 1

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
