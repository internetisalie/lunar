package net.internetisalie.lunar.definitions

/**
 * BUG-394: a global declared in a **library** file must complete at a bare-identifier caret.
 *
 * `GlobalSymbolRankingService` searched `GlobalSearchScope.projectScope`, which excludes library
 * files by construction, so no library symbol ever reached completion in any caret position — and
 * this was never only about definition libraries: `print` lives in the bundled `builtin.lua` stub
 * and reaches completion through the same path, so the entire Lua standard library was missing from
 * the one place a user is most likely to look for it.
 *
 * Distinct from BUG-395/398/399, which were all the *member* caret after a dot. This is the caret
 * before it.
 */
class LuaLibraryGlobalCompletionTest : LibraryRootTestCase() {
    /**
     * The bundled stdlib case, which needs no registered root of its own — `PlatformLibraryProvider`
     * already contributes `builtin.lua`. This is the plainest possible statement of the bug: typing
     * `pri` in a Lua file did not offer `print`.
     */
    fun testStdlibGlobalCompletes() {
        val found = completionsFor("pri<caret>\n")
        assertTrue("`print` from the bundled stdlib stub must complete. Found: $found", found.contains("print"))
    }

    /** The definition-library case: a global function contributed by a registered root. */
    fun testLibraryGlobalCompletes() {
        registerLibraryRoot(mapOf("busted.lua" to "---@meta\nfunction describe(name, block) end\n"))
        val found = completionsFor("descr<caret>\n")
        assertTrue("a library-declared global must complete. Found: $found", found.contains("describe"))
    }

    /**
     * Accepting a library global must insert the name and nothing else.
     *
     * Widening the scope handed library symbols to the auto-import insert handler, which had no
     * reason to expect one: completing `print` wrote
     * `require("…/lunar-0.18.0.jar!/runtime/standard/lua-5.4/builtin")` into the file. `print` is
     * ambient and a definition library's `@meta` file is not a requirable module, so neither gets an
     * import.
     */
    fun testAcceptingALibraryGlobalInsertsNoRequire() {
        myFixture.configureByText("consumer.lua", "pri<caret>\n")
        myFixture.completeBasic()
        val text = myFixture.editor.document.text
        assertFalse("a library global must not be auto-imported. Document: $text", text.contains("require"))
        assertTrue("the name itself must be inserted. Document: $text", text.contains("print"))
    }

    /**
     * A project global must still win over a library one of equal name-prefix — widening the search
     * scope must not cost the ranking that made project symbols surface first.
     */
    fun testProjectGlobalsOutrankLibraryGlobals() {
        // Project file first: registerLibraryRoot ends with the index wait, so seeding after it
        // would leave `nearby.lua` unindexed and the assertion would fail for the wrong reason.
        myFixture.addFileToProject("nearby.lua", "function sharedprefix_project() end\n")
        registerLibraryRoot(mapOf("lib.lua" to "---@meta\nfunction sharedprefix_library() end\n"))
        val found = completionsFor("sharedprefix_<caret>\n")
        assertTrue(
            "both must be offered. Found: $found",
            found.containsAll(listOf("sharedprefix_project", "sharedprefix_library")),
        )
        assertTrue(
            "the project's own global must be ranked first. Found: $found",
            found.indexOf("sharedprefix_project") < found.indexOf("sharedprefix_library"),
        )
    }
}
