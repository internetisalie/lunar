package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction

/**
 * BUG-399: `require` must reach a module that lives in a definition-library root, and the
 * `---@class` it exports must materialize.
 *
 * Found re-running TARGET-08 TC 6 live: with `busted` enabled, `assert` *resolved* into the library
 * (Ctrl+B offered busted's `assert = require("luassert")`) but `assert.` completed nothing, because
 * the `---@class luassert` it exports could not materialize — type resolution searched
 * `projectScope`, which excludes library files by construction.
 */
class LuaLibraryModuleResolutionTest : LibraryRootTestCase() {

    /** A library tree shaped like the real luassert. */
    private fun registerLuassertShapedLibrary() {
        registerLibraryRoot(
            mapOf(
                "libmod.lua" to
                    """
                    ---@meta

                    ---@class libmod.internal
                    local internal = {}
                    ---@param value any
                    function internal.inherited(value) end

                    ---@class libmod : libmod.internal
                    local libmod = {}
                    ---@param name string
                    function libmod.own(name) end
                    return libmod
                    """.trimIndent(),
            ),
        )
    }

    /** The first link: `require` of a module that only exists in a library root. */
    fun testRequireResolvesIntoALibraryRoot() {
        registerLuassertShapedLibrary()
        myFixture.configureByText("consumer.lua", "local m = require(\"libmod\")\n")
        val resolved = runReadAction {
            myFixture.file.findReferenceAt(myFixture.file.text.indexOf("libmod\"") + 1)
                ?.resolve()?.containingFile?.virtualFile?.path
        }
        assertNotNull("require must resolve into the definition-library root", resolved)
        assertTrue("expected resolution into the library tree, got $resolved", resolved!!.contains(TEMP_PREFIX))
    }

    /** The second: the `---@class` the module exports must materialize from a library file. */
    fun testMembersOfALibraryClassComplete() {
        registerLuassertShapedLibrary()
        myFixture.configureByText("consumer.lua", "local m = require(\"libmod\")\nm.<caret>\n")
        myFixture.completeBasic()
        val found = myFixture.lookupElementStrings.orEmpty()
        assertTrue("the module's own member must complete. Found: $found", found.contains("own"))
        assertTrue("its inherited member must complete too. Found: $found", found.contains("inherited"))
    }

    /** And the whole TC 6 chain: a global bound to that module, consumed from a project file. */
    fun testGlobalBoundToALibraryModuleCompletesItsMembers() {
        registerLuassertShapedLibrary()
        myFixture.addFileToProject("bootstrap.lua", "---@meta\nglobalmod = require(\"libmod\")\n")
        myFixture.configureByText("consumer.lua", "globalmod.<caret>\n")
        myFixture.completeBasic()
        val found = myFixture.lookupElementStrings.orEmpty()
        assertTrue("TC 6 shape: own member must complete. Found: $found", found.contains("own"))
        assertTrue("TC 6 shape: inherited member must complete. Found: $found", found.contains("inherited"))
    }
}
