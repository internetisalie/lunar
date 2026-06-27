package net.internetisalie.lunar.lang

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaDescriptionIndex
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaDescriptionIndexTest : BasePlatformTestCase() {

    @Test
    fun testIndexerWithClassDescription() {
        val file = myFixture.configureByText("test.lua", """
            ---@class Vector Represents a 2D vector
            local Vector = {}
        """.trimIndent())

        val fileUrl = file.virtualFile.url
        val scope = GlobalSearchScope.allScope(project)

        val index = FileBasedIndex.getInstance()

        val representsValues = index.getValues(LuaDescriptionIndex.KEY, "represents", scope)
        assertEquals(1, representsValues.size)
        val parts = representsValues.first().split('\t')
        assertEquals(3, parts.size)
        assertEquals("Vector", parts[0])
        assertEquals(fileUrl, parts[1])
        val offset = parts[2].toInt()
        val expectedOffset = file.text.indexOf("local Vector")
        assertEquals(expectedOffset, offset)

        val vectorValues = index.getValues(LuaDescriptionIndex.KEY, "vector", scope)
        assertEquals(1, vectorValues.size)
        assertEquals(representsValues.first(), vectorValues.first())
    }

    @Test
    fun testIndexerDeduplicatesWithinSameComment() {
        myFixture.configureByText("test.lua", """
            ---@class Vector Represents a vector with a vector
            local Vector = {}
        """.trimIndent())

        val scope = GlobalSearchScope.allScope(project)
        val index = FileBasedIndex.getInstance()

        val vectorValues = index.getValues(LuaDescriptionIndex.KEY, "vector", scope)
        assertEquals(1, vectorValues.size)
        assertFalse(vectorValues.first().contains("|"))
    }

    @Test
    fun testIndexerMergesSameFileCollisions() {
        myFixture.configureByText("test.lua", """
            ---@class Vector Represents a 2D vector
            local Vector = {}

            ---@class Matrix Represents a 2D matrix
            local Matrix = {}
        """.trimIndent())

        val scope = GlobalSearchScope.allScope(project)
        val index = FileBasedIndex.getInstance()

        val representsValues = index.getValues(LuaDescriptionIndex.KEY, "represents", scope)
        assertEquals(1, representsValues.size)
        val value = representsValues.first()
        assertTrue(value.contains("|"))
        val records = value.split('|')
        assertEquals(2, records.size)

        val record1 = records[0].split('\t')
        val record2 = records[1].split('\t')
        assertEquals("Vector", record1[0])
        assertEquals("Matrix", record2[0])
    }

    @Test
    fun testIndexerWithFuncDescription() {
        val file = myFixture.configureByText("test.lua", """
            ---@param name string The name of the player
            function setPlayerName(name)
            end
        """.trimIndent())

        val fileUrl = file.virtualFile.url
        val scope = GlobalSearchScope.allScope(project)
        val index = FileBasedIndex.getInstance()

        val values = index.getValues(LuaDescriptionIndex.KEY, "player", scope)
        assertEquals(1, values.size)
        val parts = values.first().split('\t')
        assertEquals("setPlayerName", parts[0])
        assertEquals(fileUrl, parts[1])
        val expectedOffset = file.text.indexOf("function setPlayerName")
        assertEquals(expectedOffset, parts[2].toInt())
    }

    @Test
    fun testIndexerWithLocalFuncDescription() {
        val file = myFixture.configureByText("test.lua", """
            ---@return number The coordinate value
            local function getX()
                return 0
            end
        """.trimIndent())

        val fileUrl = file.virtualFile.url
        val scope = GlobalSearchScope.allScope(project)
        val index = FileBasedIndex.getInstance()

        val values = index.getValues(LuaDescriptionIndex.KEY, "coordinate", scope)
        assertEquals(1, values.size)
        val parts = values.first().split('\t')
        assertEquals("getX", parts[0])
        assertEquals(fileUrl, parts[1])
        val expectedOffset = file.text.indexOf("local function getX")
        assertEquals(expectedOffset, parts[2].toInt())
    }

    @Test
    fun testIndexRealWorldProjectAndMeasureSize() {
        val testProjectDir = java.io.File(System.getProperty("user.dir"), "test")
        assertTrue("Test project directory does not exist: ${testProjectDir.absolutePath}", testProjectDir.exists())

        var totalLoc = 0
        var fileCount = 0

        testProjectDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "lua") {
                val content = file.readText()
                val relativePath = file.relativeTo(testProjectDir).path
                myFixture.addFileToProject(relativePath, content)
                totalLoc += content.lineSequence().count()
                fileCount++
            }
        }

        val scope = GlobalSearchScope.allScope(project)
        val index = FileBasedIndex.getInstance()
        val keys = index.getAllKeys(LuaDescriptionIndex.KEY, project)

        var totalEntries = 0
        var estimatedSizeBytes = 0

        for (key in keys) {
            val values = index.getValues(LuaDescriptionIndex.KEY, key, scope)
            val keySize = key.toByteArray(Charsets.UTF_8).size + 2
            estimatedSizeBytes += keySize

            for (value in values) {
                totalEntries++
                val valueSize = value.toByteArray(Charsets.UTF_8).size + 2
                estimatedSizeBytes += valueSize
            }
        }

        // Regression guardrail: the description index must stay under 500 KB per 10k LOC. The
        // vendored test project measures ~6 bytes/LOC, so this leaves ~9x headroom and trips only
        // on a genuine index-size blowup.
        val limitBytes = (totalLoc / 10000.0) * 500 * 1024
        assertTrue(
            "Estimated size of index ($estimatedSizeBytes bytes) exceeds limit ($limitBytes bytes) " +
                "for $totalLoc LOC across $fileCount files ($totalEntries entries)",
            estimatedSizeBytes < limitBytes,
        )
    }
}
