package net.internetisalie.lunar.lang.doc

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.CommonProcessors
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaDocSearchEverywhereContributorTest : BasePlatformTestCase() {

    private val progressIndicator by lazy { EmptyProgressIndicator() }

    @Test
    fun testSingleWordPatternMatches() {
        myFixture.configureByText("test.lua", """
            ---@class Vector Represents a 2D vector
            local Vector = {}
        """.trimIndent())

        val contributor = LuaDocSearchEverywhereContributor(project)
        val processor = CommonProcessors.CollectProcessor<LuaDocSearchItem>()
        contributor.fetchElements("vector", progressIndicator, processor)

        val results = processor.results.toList()
        assertEquals(1, results.size)
        assertEquals("Vector", results[0].symbolName)
    }

    @Test
    fun testMultiWordPatternMatches() {
        myFixture.configureByText("test.lua", """
            ---@class Vector Represents a 2D vector
            local Vector = {}
        """.trimIndent())

        val contributor = LuaDocSearchEverywhereContributor(project)
        val processor = CommonProcessors.CollectProcessor<LuaDocSearchItem>()
        contributor.fetchElements("2d vector", progressIndicator, processor)

        val results = processor.results.toList()
        assertEquals(1, results.size)
        assertEquals("Vector", results[0].symbolName)
    }

    @Test
    fun testCaseInsensitiveSubstringMatches() {
        myFixture.configureByText("test.lua", """
            ---@class Vector Represents a 2D vector
            local Vector = {}
        """.trimIndent())

        val contributor = LuaDocSearchEverywhereContributor(project)
        val processor = CommonProcessors.CollectProcessor<LuaDocSearchItem>()
        contributor.fetchElements("vEc", progressIndicator, processor)

        val results = processor.results.toList()
        assertEquals(1, results.size)
        assertEquals("Vector", results[0].symbolName)
    }

    @Test
    fun testShortPatternReturnsEmpty() {
        myFixture.configureByText("test.lua", """
            ---@class Vector Represents a 2D vector
            local Vector = {}
        """.trimIndent())

        val contributor = LuaDocSearchEverywhereContributor(project)
        val processor = CommonProcessors.CollectProcessor<LuaDocSearchItem>()
        contributor.fetchElements("v", progressIndicator, processor)

        val results = processor.results.toList()
        assertTrue(results.isEmpty())
    }

    @Test
    fun testDumbModeReturnsEmpty() {
        myFixture.configureByText("test.lua", """
            ---@class Vector Represents a 2D vector
            local Vector = {}
        """.trimIndent())

        val contributor = LuaDocSearchEverywhereContributor(project)
        val processor = CommonProcessors.CollectProcessor<LuaDocSearchItem>()

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            contributor.fetchElements("vector", progressIndicator, processor)
        }

        val results = processor.results.toList()
        assertTrue(results.isEmpty())
    }

    @Test
    fun testNavigationToDeclaration() {
        val file = myFixture.configureByText("test.lua", """
            ---@class Vector Represents a 2D vector
            local Vector = {}
        """.trimIndent())

        val contributor = LuaDocSearchEverywhereContributor(project)
        val processor = CommonProcessors.CollectProcessor<LuaDocSearchItem>()
        contributor.fetchElements("vector", progressIndicator, processor)

        val results = processor.results.toList()
        assertEquals(1, results.size)
        val item = results[0]

        item.navigate(true)
        
        val expectedOffset = file.text.indexOf("local Vector")
        assertEquals(expectedOffset, myFixture.caretOffset)
    }

    @Test
    fun testResultPresentationSnippetAndRelativePath() {
        myFixture.addFileToProject("src/geom/vec.lua", """
            ---@class Vector Represents a 2D vector
            local Vector = {}
        """.trimIndent())

        val contributor = LuaDocSearchEverywhereContributor(project)
        val processor = CommonProcessors.CollectProcessor<LuaDocSearchItem>()
        contributor.fetchElements("vector", progressIndicator, processor)

        val results = processor.results.toList()
        assertEquals(1, results.size)
        val item = results[0]

        val presentation = item.presentation
        assertNotNull(presentation)
        assertEquals("Vector", presentation!!.presentableText)
        assertEquals("src/geom/vec.lua", presentation.locationString)
        assertNotNull(presentation.getIcon(false))
    }

    @Test
    fun testSameFileCollisionsReturned() {
        myFixture.configureByText("test.lua", """
            ---@class Vector Represents a 2D vector
            local Vector = {}

            ---@class Matrix Represents a 2D matrix
            local Matrix = {}
        """.trimIndent())

        val contributor = LuaDocSearchEverywhereContributor(project)
        val processor = CommonProcessors.CollectProcessor<LuaDocSearchItem>()
        contributor.fetchElements("represents", progressIndicator, processor)

        val results = processor.results.toList()
        assertEquals(2, results.size)
        val names = results.map { it.symbolName }
        assertTrue(names.contains("Vector"))
        assertTrue(names.contains("Matrix"))
    }
}
