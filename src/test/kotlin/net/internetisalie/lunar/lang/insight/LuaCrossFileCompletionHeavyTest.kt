package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import org.junit.jupiter.api.TestInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Heavy, real-disk regression guard for COMP-03 recursive cross-file completion.
 *
 * The light [LuaCompletionTest] cannot exercise this (see its `@Disabled`
 * `COMP-03 Verify recursive cross-file completion`): `require()` resolution needs the real
 * [com.intellij.openapi.vfs.LocalFileSystem] and a project-global stub index, neither of which the
 * `LightTempDirTestFixture` provides. This test uses a **heavy** project fixture +
 * [TempDirTestFixtureImpl] (files on real disk) so indexing and require resolution behave as they do
 * in a running IDE — the same scenario the `LuaCrossFileCompletionIntegrationTest` covers end-to-end,
 * but in-process and fast enough for the regular `./gradlew test` suite.
 *
 * Project shape (transitive require graph):
 * ```
 *   main.lua     -> require("module_b")
 *   module_b.lua -> require("module_a"); function helper_from_b() end
 *   module_a.lua -> function helper_from_a() end
 * ```
 * Completing `helper_` in main.lua must offer both `helper_from_b` (direct require) and
 * `helper_from_a` (reached transitively through module_b's require of module_a).
 */
class LuaCrossFileCompletionHeavyTest {

    private lateinit var myFixture: CodeInsightTestFixture

    // Heavy-fixture module building, PSI/VFS writes and completion all require the EDT, so every
    // lifecycle step and the test body run inside runInEdtAndWait.
    @BeforeTest
    fun setUp(testInfo: TestInfo) {
        runInEdtAndWait {
            val factory = IdeaTestFixtureFactory.getFixtureFactory()
            // Heavy project fixture (real, on-disk project) + real-disk temp dir, so require()
            // resolution and stub indexing use the real LocalFileSystem.
            val projectBuilder = factory.createFixtureBuilder(testInfo.displayName)
            myFixture = factory.createCodeInsightFixture(projectBuilder.fixture, TempDirTestFixtureImpl())
            myFixture.setUp()

            // A module-less heavy project indexes nothing: register the temp dir as a source content
            // root so the added .lua files are stub-indexed and reachable by require() resolution and
            // the project-global symbol query.
            val moduleBuilder = projectBuilder.addModule(EmptyModuleFixtureBuilder::class.java)
            moduleBuilder.addSourceContentRoot(myFixture.tempDirPath)
            moduleBuilder.fixture.setUp()
        }
    }

    @AfterTest
    fun tearDown() {
        runInEdtAndWait { myFixture.tearDown() }
    }

    @Test
    fun `COMP-03 recursive cross-file completion offers transitively required globals`() {
        runInEdtAndWait {
            myFixture.addFileToProject(
                "module_a.lua",
                """
                function helper_from_a() end
                return {}
                """.trimIndent() + "\n",
            )
            myFixture.addFileToProject(
                "module_b.lua",
                """
                require("module_a")
                function helper_from_b() end
                return {}
                """.trimIndent() + "\n",
            )
            // main.lua requires only module_b, but completion of `helper_` must also surface
            // helper_from_a reached transitively through module_b's require of module_a.
            myFixture.configureByText(
                "main.lua",
                """
                require("module_b")
                helper_<caret>
                """.trimIndent() + "\n",
            )

            myFixture.completeBasic()

            val strings = myFixture.lookupElementStrings
            assertNotNull(strings, "Completion lookup should not be null")
            assertTrue(
                strings.contains("helper_from_b"),
                "Direct require should offer 'helper_from_b'. Found: $strings",
            )
            assertTrue(
                strings.contains("helper_from_a"),
                "Transitive require should offer 'helper_from_a'. Found: $strings",
            )
        }
    }
}
