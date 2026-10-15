package net.internetisalie.lunar.definitions

import net.internetisalie.lunar.analysis.inspections.LuaDeprecatedApiInspection

/**
 * BUG-504: one `---@deprecated` dotted function used to make every reference to its root table a
 * deprecated-API warning. A root name multi-resolves to the `LuaFuncDecl` of every
 * `function <root>.<name>` declaration, and `LuaDeprecatedApiInspection.getDeprecatedTag` returned
 * the declaration's tag without asking which segment of the function name the reference was.
 *
 * The library tree is laid out like Neovim's: `vim` and `log` are genuine globals there, so their
 * roots stay in the global-declaration index (BUG-509 drops only roots declared `local`).
 */
class LuaDeprecatedRootNameTest : LibraryRootTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaDeprecatedApiInspection())
    }

    private fun registerNeovimLikeLibrary() {
        registerLibraryRoot(
            mapOf(
                "vim/_core/shared.lua" to
                    "_G.vim = _G.vim or {}\n\nfunction vim.list_extend(dst, src) end\n\n" +
                    "--- @deprecated\nfunction vim.tbl_islist(t) end\n",
                "vim/lsp/log.lua" to "log = {}\n\n---@deprecated\nfunction log.should_log(level) end\n",
            ),
        )
    }

    private fun deprecatedRangesIn(
        path: String,
        text: String,
    ): List<Pair<Int, Int>> {
        val file = myFixture.addFileToProject(path, text)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return myFixture
            .doHighlighting()
            .filter { it.description?.startsWith(DEPRECATED_PREFIX) == true }
            .map { it.startOffset to it.endOffset }
            .sortedBy { it.first }
    }

    fun testOnlyTheDeprecatedMemberIsFlagged() {
        registerNeovimLikeLibrary()
        val ranges =
            deprecatedRangesIn(
                "lazy/minit.lua",
                "local islist = vim.islist or vim.tbl_islist\nvim.list_extend({}, {})\nreturn islist\n",
            )
        assertEquals("only 'tbl_islist' names the deprecated member", listOf(33 to 43), ranges)
    }

    fun testADeclarationNamedLikeADeprecatedRootIsNotFlagged() {
        registerNeovimLikeLibrary()
        val manage = deprecatedRangesIn("lazy/manage.lua", "local M = {}\nfunction M.log(opts) end\nreturn M\n")
        val spec =
            deprecatedRangesIn("lazy/spec.lua", "local Spec = {}\nfunction Spec:log(msg, level) end\nreturn Spec\n")
        assertEquals(
            "neither function M.log nor function Spec:log is the deprecated log.should_log",
            mapOf("manage" to emptyList<Pair<Int, Int>>(), "spec" to emptyList()),
            mapOf("manage" to manage, "spec" to spec),
        )
    }

    fun testAParameterOfADeprecatedFunctionIsNotFlagged() {
        val ranges =
            deprecatedRangesIn("param.lua", "local t = {}\n---@deprecated gone\nfunction t.f(x) return x end\n")
        assertEquals("the parameter read 'x' is not the deprecated function", emptyList<Pair<Int, Int>>(), ranges)
    }

    fun testSelfInsideADeprecatedMethodIsNotFlagged() {
        val ranges =
            deprecatedRangesIn("self.lua", "local t = {}\n---@deprecated gone\nfunction t:m() return self end\nt:m()\n")
        assertEquals("only the call site 'm' at 66 warns; 'self' at 55 does not", listOf(66 to 67), ranges)
    }

    fun testSelfMemberAccessInsideADeprecatedMethodIsNotFlagged() {
        val text = "local t = {}\n---@deprecated gone\nfunction t:m() local a = self.x; self:m() return a end\nt:m()\n"
        val ranges = deprecatedRangesIn("self_access.lua", text)
        val selfStarts = Regex("\\bself\\b").findAll(text).map { it.range.first }.toSet()
        val callSite = text.lastIndexOf("m()")
        val onSelf = ranges.filter { it.first in selfStarts }
        assertEquals("no 'self' at $selfStarts warns", emptyList<Pair<Int, Int>>(), onSelf)
        assertTrue("the call site 'm' at $callSite warns, in $ranges", (callSite to callSite + 1) in ranges)
    }

    fun testARootNamedLikeItsDeprecatedMemberFlagsOnlyTheMember() {
        registerLibraryRoot(mapOf("log/log.lua" to "log = {}\n\n---@deprecated gone\nfunction log.log() end\n"))
        val ranges =
            deprecatedRangesIn(
                "user/use_log.lua",
                "local x = log\nlog.log()\nlocal M = {}\nfunction M.log() end\nreturn x\n",
            )
        assertEquals("only the member 'log' of log.log() at 18 warns", listOf(18 to 21), ranges)
    }

    fun testAMemberAccessWithNoQualifiedKeyIsNotFlaggedByARootOccurrence() {
        registerNeovimLikeLibrary()
        val ranges =
            deprecatedRangesIn("lazy/handlers.lua", "local handlers = {{}}\nlocal l = handlers[1].log\nreturn l\n")
        assertEquals("handlers[1].log is not log.should_log", emptyList<Pair<Int, Int>>(), ranges)
    }

    /** The range of the name [name] inside the first occurrence of [needle] in [text]. */
    private fun rangeOf(
        text: String,
        needle: String,
        name: String,
    ): Pair<Int, Int> {
        val start = text.indexOf(needle) + needle.indexOf(name)
        return start to start + name.length
    }

    /** A deprecated `local`/`global` declaration whose body reads a parameter: only the call warns. */
    private fun assertOnlyTheCallWarns(
        path: String,
        declaration: String,
    ) {
        val text = "---@deprecated gone\n$declaration\ntimeout(5)\n"
        assertEquals(
            "only the call timeout(5) warns; a parameter read inside the declaration does not",
            listOf(rangeOf(text, "timeout(5)", "timeout")),
            deprecatedRangesIn(path, text),
        )
    }

    fun testAFieldWrittenInsideADeprecatedFunctionIsNotDeprecated() {
        val text =
            "local M = {}\nlocal config = {}\n---@deprecated gone\n" +
                "function M.timeout(ms) config.timeout = ms end\n" +
                "function M.get() return config.timeout end\nM.timeout(5)\nreturn M\n"
        assertEquals(
            "only the call M.timeout(5) warns; config.timeout read in M.get does not",
            listOf(rangeOf(text, "M.timeout(5)", "timeout")),
            deprecatedRangesIn("field_same.lua", text),
        )
    }

    fun testAFieldWrittenInsideADeprecatedFunctionIsNotDeprecatedFromAnotherFile() {
        myFixture.addFileToProject(
            "field_decl.lua",
            "M = {}\nlocal config = {}\n---@deprecated gone\nfunction M.timeout(ms) config.timeout = ms end\n",
        )
        val text = "local v = config.timeout\nM.timeout(5)\nreturn v\n"
        assertEquals(
            "only the call M.timeout(5) warns; the cross-file read config.timeout does not",
            listOf(rangeOf(text, "M.timeout(5)", "timeout")),
            deprecatedRangesIn("field_user.lua", text),
        )
    }

    fun testAParameterNamedLikeADeprecatedLocalFunctionIsNotFlagged() =
        assertOnlyTheCallWarns("shadow_local_func.lua", "local function timeout(timeout) return timeout end")

    fun testAParameterNamedLikeADeprecatedLocalVariableIsNotFlagged() =
        assertOnlyTheCallWarns("shadow_local_var.lua", "local timeout = function(timeout) return timeout end")

    fun testAParameterOfADeprecatedGlobalVariableIsNotFlagged() =
        assertOnlyTheCallWarns("param_global_var.lua", "global timeout = function(ms) return ms end")

    fun testABareCallOfADeprecatedGlobalFunctionWarnsInTheSameFile() {
        val ranges = deprecatedRangesIn("global_same.lua", "---@deprecated gone\nfunction f() end\nf()\n")
        assertEquals("the bare call f() at 37 warns", listOf(37 to 38), ranges)
    }

    fun testABareCallOfADeprecatedGlobalFunctionWarnsFromAnotherFile() {
        myFixture.addFileToProject("legacy_lib.lua", "---@deprecated gone\nfunction legacy() end\n")
        val ranges =
            deprecatedRangesIn("global_other.lua", "local M = {}\nfunction M.legacy() end\nlegacy()\nreturn M\n")
        assertEquals("the bare call legacy() at 37 warns; the declaration M.legacy does not", listOf(37 to 43), ranges)
    }

    fun testAParameterOfADeprecatedBareFunctionIsNotFlagged() {
        val ranges = deprecatedRangesIn("bare_param.lua", "---@deprecated gone\nfunction f(x) return x end\n")
        assertEquals("the parameter read 'x' is not the deprecated function 'f'", emptyList<Pair<Int, Int>>(), ranges)
    }

    private companion object {
        const val DEPRECATED_PREFIX = "Deprecated API"
    }
}
