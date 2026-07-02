package net.internetisalie.lunar.lang.indexing

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaMemberFieldIndexTest : BasePlatformTestCase() {

    @Test
    fun testQualifiedFieldKeysPresent() {
        myFixture.configureByText(
            "t.lua",
            """
            self.width = 1
            self.height = 2
            """.trimIndent(),
        )
        val keys = FileBasedIndex.getInstance().getAllKeys(LuaMemberFieldIndex.KEY, project)
        assertTrue("Expected key 'self.width'", keys.contains("self.width"))
        assertTrue("Expected key 'self.height'", keys.contains("self.height"))
    }

    @Test
    fun testDeepQualifiedKeyPresent() {
        myFixture.configureByText("t.lua", "a.b.c = 1")
        val keys = FileBasedIndex.getInstance().getAllKeys(LuaMemberFieldIndex.KEY, project)
        assertTrue("Expected deep key 'a.b.c'", keys.contains("a.b.c"))
    }

    @Test
    fun testBareAndBracketTargetsExcluded() {
        myFixture.configureByText(
            "t.lua",
            """
            x = 1
            t[i] = 1
            """.trimIndent(),
        )
        val keys = FileBasedIndex.getInstance().getAllKeys(LuaMemberFieldIndex.KEY, project)
        assertFalse("Bare assignment 'x' must not be indexed", keys.contains("x"))
        assertFalse("Bracket target 't' must not be indexed", keys.contains("t"))
        assertFalse("Bracket target 't[i]' must not produce a dotted key", keys.contains("t[i]"))
        assertFalse("Bracket target must not produce a 't.i' key", keys.contains("t.i"))
    }
}
