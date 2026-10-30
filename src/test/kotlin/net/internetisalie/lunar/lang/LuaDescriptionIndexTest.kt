package net.internetisalie.lunar.lang

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.DescriptionRecord
import net.internetisalie.lunar.lang.indexing.LuaDescriptionIndex
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class LuaDescriptionIndexTest : BasePlatformTestCase() {
    @Test
    fun testIndexerWithClassDescription() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@class Vector Represents a 2D vector
                local Vector = {}
                """.trimIndent(),
            )

        val fileUrl = file.virtualFile.url
        val scope = GlobalSearchScope.allScope(project)

        val index = FileBasedIndex.getInstance()

        val representsValues = index.getValues(LuaDescriptionIndex.KEY, "represents", scope)
        assertEquals(1, representsValues.size)
        val records = DescriptionRecord.parseAll(representsValues.first())
        assertEquals(1, records.size)
        assertEquals("Vector", records[0].ownerName)
        assertEquals(fileUrl, records[0].fileUrl)
        val offset = records[0].offset
        val expectedOffset = file.text.indexOf("local Vector")
        assertEquals(expectedOffset, offset)

        val vectorValues = index.getValues(LuaDescriptionIndex.KEY, "vector", scope)
        assertEquals(1, vectorValues.size)
        assertEquals(representsValues.first(), vectorValues.first())
    }

    @Test
    fun testIndexerDeduplicatesWithinSameComment() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@class Vector Represents a vector with a vector
            local Vector = {}
            """.trimIndent(),
        )

        val scope = GlobalSearchScope.allScope(project)
        val index = FileBasedIndex.getInstance()

        val vectorValues = index.getValues(LuaDescriptionIndex.KEY, "vector", scope)
        assertEquals(1, vectorValues.size)
        assertFalse(vectorValues.first().contains("|"))
    }

    @Test
    fun testIndexerMergesSameFileCollisions() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@class Vector Represents a 2D vector
            local Vector = {}

            ---@class Matrix Represents a 2D matrix
            local Matrix = {}
            """.trimIndent(),
        )

        val scope = GlobalSearchScope.allScope(project)
        val index = FileBasedIndex.getInstance()

        val representsValues = index.getValues(LuaDescriptionIndex.KEY, "represents", scope)
        assertEquals(1, representsValues.size)
        val value = representsValues.first()
        assertTrue(value.contains("|"))
        val records = DescriptionRecord.parseAll(value)
        assertEquals(2, records.size)
        assertEquals("Vector", records[0].ownerName)
        assertEquals("Matrix", records[1].ownerName)
    }

    @Test
    fun testIndexerWithFuncDescription() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@param name string The name of the player
                function setPlayerName(name)
                end
                """.trimIndent(),
            )

        val fileUrl = file.virtualFile.url
        val scope = GlobalSearchScope.allScope(project)
        val index = FileBasedIndex.getInstance()

        val values = index.getValues(LuaDescriptionIndex.KEY, "player", scope)
        assertEquals(1, values.size)
        val record = DescriptionRecord.parseAll(values.first()).first()
        assertEquals("setPlayerName", record.ownerName)
        assertEquals(fileUrl, record.fileUrl)
        assertEquals(file.text.indexOf("function setPlayerName"), record.offset)
    }

    @Test
    fun testIndexerWithLocalFuncDescription() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@return number The coordinate value
                local function getX()
                    return 0
                end
                """.trimIndent(),
            )

        val fileUrl = file.virtualFile.url
        val scope = GlobalSearchScope.allScope(project)
        val index = FileBasedIndex.getInstance()

        val values = index.getValues(LuaDescriptionIndex.KEY, "coordinate", scope)
        assertEquals(1, values.size)
        val record = DescriptionRecord.parseAll(values.first()).first()
        assertEquals("getX", record.ownerName)
        assertEquals(fileUrl, record.fileUrl)
        assertEquals(file.text.indexOf("local function getX"), record.offset)
    }

    /**
     * Regression guardrail: the description index of a pinned, LuaCATS-heavy corpus member stays under
     * MAX_BYTES_PER_LINE bytes per line of that member (measured in MAINT-49 DR-02).
     */
    @Test
    fun testIndexRealWorldProjectAndMeasureSize() {
        val memberDir = File(System.getProperty("user.dir"), SIZE_MEMBER)
        assertTrue("corpus not fetched: $SIZE_MEMBER (tooling/corpus/fetch-corpus.py)", memberDir.isDirectory)

        val totalLines = addLuaFiles(memberDir)
        assertEquals(
            "$SIZE_MEMBER is not the pinned checkout the budget was measured on; re-measure (MAINT-49 DR-02)",
            PINNED_LINES,
            totalLines,
        )

        val bytes = projectIndexBytes()
        val limit = totalLines * MAX_BYTES_PER_LINE
        assertTrue(
            "Estimated size of index ($bytes bytes) exceeds limit ($limit bytes) for $totalLines lines of $SIZE_MEMBER",
            bytes < limit,
        )
    }

    private fun addLuaFiles(root: File): Int {
        var totalLines = 0
        root.walkTopDown().filter { it.isFile && it.extension == "lua" }.forEach { file ->
            val text = file.readText()
            myFixture.addFileToProject(file.relativeTo(root).path, text)
            totalLines += text.lineSequence().count()
        }
        return totalLines
    }

    private fun projectIndexBytes(): Int {
        val index = FileBasedIndex.getInstance()
        val scope = GlobalSearchScope.projectScope(project)
        var bytes = 0
        for (key in index.getAllKeys(LuaDescriptionIndex.KEY, project)) {
            val values = index.getValues(LuaDescriptionIndex.KEY, key, scope)
            if (values.isEmpty()) continue
            bytes += key.toByteArray(Charsets.UTF_8).size + 2
            for (value in values) {
                bytes += value.toByteArray(Charsets.UTF_8).size + 2
                DescriptionRecord.parseAll(value).forEach { record ->
                    assertFalse("bundled definition counted: ${record.fileUrl}", record.fileUrl.startsWith("jar:"))
                }
            }
        }
        return bytes
    }

    private companion object {
        const val SIZE_MEMBER = "test/corpus/lazynvim"
        const val PINNED_LINES = 14_327
        const val MAX_BYTES_PER_LINE = 10
    }
}
