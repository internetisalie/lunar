package net.internetisalie.lunar.lang.indexing

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaCatsTypeNameIndexTest : BasePlatformTestCase() {

    @Test
    fun testBareClassTagIsIndexed() {
        val file = myFixture.configureByText("t.lua", "--- @class MyType")
        val scope = GlobalSearchScope.allScope(project)
        val files = FileBasedIndex.getInstance().getContainingFiles(LuaCatsTypeNameIndex.KEY, "MyType", scope)
        assertTrue("Expected bare @class MyType to be indexed", files.contains(file.virtualFile))
    }

    @Test
    fun testBareAliasTagIsIndexed() {
        val file = myFixture.configureByText("t.lua", "--- @alias MyAlias string")
        val scope = GlobalSearchScope.allScope(project)
        val files = FileBasedIndex.getInstance().getContainingFiles(LuaCatsTypeNameIndex.KEY, "MyAlias", scope)
        assertTrue("Expected bare @alias MyAlias to be indexed", files.contains(file.virtualFile))
    }

    @Test
    fun testKeySetHasAnnotatedNamesOnly() {
        myFixture.configureByText(
            "t.lua",
            """
            --- @class Foo
            --- @alias Bar number
            local baz = 1
            """.trimIndent(),
        )
        val keys = FileBasedIndex.getInstance().getAllKeys(LuaCatsTypeNameIndex.KEY, project)
        assertTrue("Expected @class Foo in key set", keys.contains("Foo"))
        assertTrue("Expected @alias Bar in key set", keys.contains("Bar"))
        assertFalse("Un-annotated local 'baz' must not be indexed", keys.contains("baz"))
    }
}
