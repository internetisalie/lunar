package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.indexing.FileBasedIndex
import org.junit.jupiter.api.TestInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Direct coverage for [LuaFileBindingsIndex] `require()` extraction (MAINT-10-06, TC-09/TC-10).
 *
 * A heavy, real-disk fixture ([TempDirTestFixtureImpl]) is required because the index's
 * [LuaFileInputFilter] rejects any file whose URL is not `file:`-scheme; light
 * `BasePlatformTestCase` fixtures are served from the in-memory `temp://` filesystem and are
 * never indexed. This mirrors the proven approach in `LuaCrossFileCompletionHeavyTest`.
 */
class LuaFileBindingsIndexTest {

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
    fun testRequireIsTracked() {
        runInEdtAndWait {
            val virtualFile = myFixture.addFileToProject("m.lua", "require(\"mymodule\")\n").virtualFile
            val record = readBindingsRecord(virtualFile)
            assertTrue(
                record.requires.contains("mymodule"),
                "require(\"mymodule\") should record 'mymodule'. Found: ${record.requires}",
            )
        }
    }

    @Test
    fun testNonRequireCallIgnored() {
        runInEdtAndWait {
            val virtualFile = myFixture.addFileToProject("n.lua", "notrequire(\"mymodule\")\n").virtualFile
            val record = readBindingsRecord(virtualFile)
            assertTrue(
                record.requires.isEmpty(),
                "A non-require call should record no requires. Found: ${record.requires}",
            )
        }
    }

    @Test
    fun testDottedAssignmentIsNotABinding() {
        // TC-07 (MAINT-30-01): `t.f = 1` is a member-field assignment owned by the member-field index,
        // not a file-scope binding — neither `t` nor `f` is recorded.
        runInEdtAndWait {
            val virtualFile = myFixture.addFileToProject("dotted.lua", "t.f = 1\n").virtualFile
            val record = readBindingsRecord(virtualFile)
            assertTrue(
                record.bindings.none { it.name == "t" || it.name == "f" },
                "Dotted assignment must record no bare binding. Found: ${record.bindings.map { it.name }}",
            )
        }
    }

    @Test
    fun testUsageIsNotABinding() {
        // TC-01 (MAINT-30-01): a bare-name usage `print(bar)` records no `bar` binding — only the
        // declaration container `function bar() end` in c.lua does (declaration-only index, #20 fix).
        runInEdtAndWait {
            val usageFile = myFixture.addFileToProject("usage.lua", "print(bar)\n").virtualFile
            val declFile = myFixture.addFileToProject("decl.lua", "function bar() end\n").virtualFile

            val usageRecord = readBindingsRecord(usageFile)
            assertTrue(
                usageRecord.bindings.none { it.name == "bar" },
                "A usage `print(bar)` must not record a `bar` binding. Found: ${usageRecord.bindings.map { it.name }}",
            )
            val declRecord = readBindingsRecord(declFile)
            assertTrue(
                declRecord.bindings.any { it.name == "bar" },
                "A declaration `function bar()` must record a `bar` binding. Found: ${declRecord.bindings.map { it.name }}",
            )
        }
    }

    private fun readBindingsRecord(virtualFile: VirtualFile): LuaFileBindingsRecord {
        assertTrue(
            virtualFile.url.startsWith("file:"),
            "Fixture file must have a file:-scheme URL to be indexed. Got: ${virtualFile.url}",
        )
        val scope = GlobalSearchScope.fileScope(myFixture.project, virtualFile)
        val values = FileBasedIndex.getInstance()
            .getValues(LuaFileBindingsIndexName, ForwardIndexer.KEY, scope)
        assertEquals(1, values.size, "Expected exactly one indexed record for the file")
        return values.first()
    }
}
