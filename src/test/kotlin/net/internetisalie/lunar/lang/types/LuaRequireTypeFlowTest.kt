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

        // `require("mylib")` returns the module's `lib` table, annotated `---@class MyLib` with a
        // `version` field — so the inferred type of `mylib` must flow that class through: it should
        // surface the MyLib class name and its `version` member.
        val rendered = mylibType.toString()
        assertTrue("mylib should carry the MyLib class type, was: $rendered", rendered.contains("MyLib"))
        assertTrue("mylib type should expose the 'version' field, was: $rendered", rendered.contains("version"))

        // The class itself resolves with its annotated member.
        val resolvedMylib = LuaTypeManager.getInstance(project).resolveType("MyLib", myFixture.file)
        assertNotNull("MyLib class should resolve", resolvedMylib)
        assertTrue(
            "MyLib should expose the 'version' field",
            resolvedMylib!!.getMembers().keys.contains("version"),
        )
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

    /**
     * MAINT-25-03 / TC-04: module resolution must run inside a plain read action without a
     * synchronous VFS refresh (`refreshIfNeeded = false`). A synchronous refresh under the read lock
     * would trip a platform assertion; reaching the assertion below without an exception proves the
     * lookup no longer refreshes under the lock, and an on-disk module still resolves.
     */
    @Test
    fun testModuleResolutionUnderReadActionDoesNotRefresh() {
        myFixture.addFileToProject(
            "diskmod.lua",
            """
            ---@class DiskMod
            local m = {}
            return m
            """.trimIndent(),
        )
        myFixture.configureByText("main.lua", "local dm = require(\"diskmod\")")
        myFixture.configureByFiles("main.lua", "diskmod.lua")

        val requireDecl = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaLocalVarDecl::class.java)
            .first { it.text.contains("require") }

        var resolved = false
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            com.intellij.openapi.application.ReadAction.run<RuntimeException> {
                resolved = LuaTypeManager.getInstance(project).resolveModule("diskmod", requireDecl) != null
            }
        }
        assertTrue("On-disk module resolves under a read action with no synchronous refresh", resolved)
    }
}
