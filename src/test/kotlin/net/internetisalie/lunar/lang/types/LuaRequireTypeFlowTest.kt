package net.internetisalie.lunar.lang.types

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.lang.LuaRequireReference
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaRequireTypeFlowTest : IndexedBasePlatformTestCase() {

    @Test
    fun testLocalRequireResolution() {
        myFixture.addFileToProject(
            "mylib.lua",
            """
            ---@class MyLib
            local lib = {}
            
            ---@return string
            function lib.hello() return "world" end
            
            return lib
            """.trimIndent()
        )

        myFixture.configureByText(
            "main.lua",
            """
            local mylib = require("mylib")
            """.trimIndent()
        )

        myFixture.configureByFiles("main.lua", "mylib.lua")

        val offset = myFixture.file.text.indexOf("\"mylib\"") + 1
        val element = myFixture.file.findElementAt(offset)!!.parent
        val reference = element.references.firstOrNull { it is LuaRequireReference }
        assertNotNull("Reference should not be null", reference)

        val resolved = reference!!.resolve()
        assertNotNull("Should resolve mylib", resolved)
        assertTrue("Resolved element should be a LuaFile", resolved is LuaFile)
        assertEquals("mylib.lua", (resolved as LuaFile).name)
    }

    @Test
    fun testLocalNestedRequireResolution() {
        myFixture.addFileToProject(
            "foo/bar.lua",
            """
            ---@class Bar
            local bar = {}
            return bar
            """.trimIndent()
        )

        myFixture.configureByText(
            "main.lua",
            """
            local bar = require("foo.bar")
            """.trimIndent()
        )

        myFixture.configureByFiles("main.lua", "foo/bar.lua")

        val offset = myFixture.file.text.indexOf("\"foo.bar\"") + 5
        val element = myFixture.file.findElementAt(offset)!!.parent
        val reference = element.references.firstOrNull { it is LuaRequireReference }
        assertNotNull("Reference should not be null", reference)

        val resolved = reference!!.resolve()
        assertNotNull("Should resolve foo.bar", resolved)
        assertTrue(resolved is LuaFile)
        assertEquals("bar.lua", (resolved as LuaFile).name)
    }

    @Test
    fun testLibraryRequireResolution() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val settings = LuaProjectSettings.getInstance(project)
            settings.setTargetAndNotify(Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4")))
        }

        myFixture.configureByText(
            "main.lua",
            """
            local math = require("math")
            """.trimIndent()
        )

        val offset = myFixture.file.text.indexOf("\"math\"") + 1
        val element = myFixture.file.findElementAt(offset)!!.parent
        val reference = element.references.firstOrNull { it is LuaRequireReference }
        assertNotNull("Reference to math should not be null", reference)

        val resolved = reference!!.resolve()
        assertNotNull("Should resolve math.lua from standard libraries", resolved)
        assertTrue(resolved is LuaFile)
        assertEquals("math.lua", (resolved as LuaFile).name)
        assertTrue("math.lua should be resolved to external library", (resolved as LuaFile).virtualFile!!.path.contains("runtime/standard/lua-5.4"))
    }

    @Test
    fun testTypeFlowFromRequire() {
        // Create the library file
        myFixture.addFileToProject(
            "mylib.lua",
            """
            ---@class MyLib
            ---@field version string
            local lib = {}
            
            ---@param name string
            ---@return number
            function lib.calculate(name) return 42 end
            
            return lib
            """.trimIndent()
        )

        // Configure main file requiring it
        myFixture.configureByText(
            "main.lua",
            """
            local mylib = require("mylib")
            local v = mylib.version
            local result = mylib.calculate("test")
            """.trimIndent()
        )

        // Make sure both are configured and indexed
        myFixture.configureByFiles("main.lua", "mylib.lua")

        val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
        assertNotNull("Snapshot should not be null", snapshot)

        // Find variables and check their types
        val varDecls = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaLocalVarDecl::class.java)
        
        val mylibVar = varDecls.first { it.text.contains("local mylib") }.attNameList.first().nameRef
        val mylibType = snapshot.getValueType(mylibVar)
        val resolvedMylib = LuaTypeManager.getInstance(project).resolveType("MyLib", myFixture.file)
        throw AssertionError("mylibType: $mylibType, resolvedMylib: $resolvedMylib, members: ${resolvedMylib?.getMembers()?.keys}")
    }

    @Test
    fun testCyclicRequireDoesNotHang() {
        myFixture.addFileToProject(
            "a.lua",
            """
            local b = require("b")
            return { name = "a" }
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "b.lua",
            """
            local a = require("a")
            return { name = "b" }
            """.trimIndent()
        )

        myFixture.configureByText(
            "main.lua",
            """
            local a = require("a")
            local name = a.name
            """.trimIndent()
        )

        myFixture.configureByFiles("main.lua", "a.lua", "b.lua")

        val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
        assertNotNull(snapshot)
        
        val varDecls = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaLocalVarDecl::class.java)
        val aVar = varDecls.first { it.text.contains("local a") }.attNameList.first().nameRef
        val aType = snapshot.getValueType(aVar)
        assertNotNull(aType)
    }
}
