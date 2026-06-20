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
}
