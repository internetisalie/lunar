package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.TestInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * MAINT-28 TC-24: the cross-file completion provider must query the real, indexed, on-disk file
 * (`parameters.originalFile`), not the never-indexed in-memory completion copy. Before the #24 fix
 * the copy was passed to `FileBasedIndex.fileScope`, so the require graph resolved nothing and no
 * cross-file global was ever offered.
 *
 * Uses the same heavy real-disk fixture as [LuaCrossFileCompletionHeavyTest] so `require()`
 * resolution and stub indexing behave as in a running IDE.
 */
class LuaCrossFileCompletionOriginalFileTest {

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
        }
    }

    @AfterTest
    fun tearDown() {
        runInEdtAndWait { myFixture.tearDown() }
    }

    @Test
    fun `TC-24 cross-file require phase resolves against the indexed original file`() {
        runInEdtAndWait {
            // Two `foo`-prefixed globals so completeBasic shows a popup (a single match would be
            // auto-inserted and lookupElementStrings would be null).
            myFixture.addFileToProject(
                "mod.lua",
                """
                function foobar() end
                function foobaz() end
                return {}
                """.trimIndent() + "\n",
            )
            myFixture.configureByText(
                "main.lua",
                """
                require("mod")
                foo<caret>
                """.trimIndent() + "\n",
            )

            myFixture.completeBasic()

            val strings = myFixture.lookupElementStrings
            assertNotNull(strings, "Completion lookup should not be null")
            assertTrue(
                strings.contains("foobar"),
                "Cross-file require phase must offer 'foobar' from the indexed original file. Found: $strings",
            )
        }
    }
}
