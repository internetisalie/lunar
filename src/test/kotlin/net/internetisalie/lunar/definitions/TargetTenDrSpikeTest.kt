package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import kotlin.system.measureTimeMillis

/**
 * THROWAWAY — TARGET-10 Phase 0 de-risking (DR-02, DR-06, DR-04).
 *
 * Delete once the verdicts are folded into `design.md` §3.6 / §3.5.2 and requirements →
 * Non-Functional. Three spikes in one class because each costs a build cycle otherwise.
 *
 * Every fixture below is the *emission shape the generator would actually write*, not a
 * simplification of it — the planning bar's "fixtures must take the same branch as production"
 * rule, which this feature's own review caught twice.
 */
class TargetTenDrSpikeTest : LibraryRootTestCase() {
    /**
     * Completions offered at the caret in [text].
     *
     * A single perfect match is auto-inserted and `completeBasic` returns null, which reads as
     * "nothing offered" — the exact ambiguity `LuaLibraryGlobalCompletionTest` documents. Recover
     * the inserted word from the document instead.
     */
    private fun completionsFor(text: String): List<String> {
        myFixture.configureByText("consumer.lua", text)
        val elements = myFixture.completeBasic()
        if (elements != null) return elements.map { it.lookupString }
        return listOf(
            myFixture.editor.document.text
                .substringBefore('\n')
                .trim(),
        )
    }

    // ---------------------------------------------------------------- DR-02: statics encoding

    /** Branch A (`statics_mode="dotted"`): constructor and statics share the `wx.wxFileName` path. */
    private fun registerBranchATree() {
        registerLibraryRoot(
            mapOf(
                "wx.lua" to
                    """
                    ---@meta

                    ---@class wx
                    wx = {}

                    ---@class wxFileName
                    local wxFileName = {}

                    ---@return string
                    function wxFileName:GetFullPath() end

                    ---@param path string
                    ---@return wxFileName
                    function wx.wxFileName(path) end

                    ---@return string
                    function wx.wxFileName.GetCwd() end

                    return wx
                    """.trimIndent(),
            ),
        )
    }

    /** DR-02(a): the constructor must infer its class — the single most valuable inference here. */
    fun testDr02ConstructorInfersItsClass() {
        registerBranchATree()
        val found = completionsFor("local f = wx.wxFileName(\"x\")\nf:<caret>\n")
        println("DR-02(a) constructor→instance members: $found")
        assertTrue(
            "constructor must infer wxFileName so `f:` offers GetFullPath. Found: $found",
            found.contains("GetFullPath"),
        )
    }

    /** DR-02(b): can a static live on the same path as the constructor function? */
    fun testDr02StaticOnConstructorPath() {
        registerBranchATree()
        val found = completionsFor("wx.wxFileName.<caret>\n")
        println("DR-02(b) statics on constructor path: $found")
        println("DR-02 VERDICT: " + if (found.contains("GetCwd")) "Branch A (dotted)" else "Branch B (on-class)")
    }

    // ---------------------------------------------------------------- DR-06: namespace layout

    /**
     * DR-06 / TC 7a: the namespace table is declared in `wx.lua`; its members are written to a
     * *sibling* file with no `---@class wx` re-anchor. This is the layout design §3.5.2 emits, and
     * neither love2d nor Lunar's own stdlib stub does it.
     */
    fun testDr06SplitNamespaceLayout() {
        registerLibraryRoot(
            mapOf(
                "wx.lua" to "---@meta\n\n---@class wx\nwx = {}\n\nreturn wx\n",
                "wx/wxcore.lua" to
                    """
                    ---@meta

                    ---@type number
                    wx.wxID_ANY = nil

                    ---@class wxFrame
                    local wxFrame = {}

                    ---@param show boolean
                    ---@return boolean
                    function wxFrame:Show(show) end

                    ---@param parent any
                    ---@return wxFrame
                    function wx.wxFrame(parent) end
                    """.trimIndent(),
            ),
        )
        val constant = completionsFor("wx.wxID_<caret>\n")
        println("DR-06 split constant from sibling file: $constant")
        val ctor = completionsFor("local f = wx.wxFrame(nil)\nf:<caret>\n")
        println("DR-06 split constructor from sibling file: $ctor")
        println("DR-06 SPLIT VERDICT: " + if (constant.contains("wxID_ANY")) "works" else "DOES NOT RESOLVE")
    }

    /** DR-06 fallback: one self-contained file per namespace — the layout love2d and our own stdlib use. */
    fun testDr06SingleFileLayout() {
        registerLibraryRoot(
            mapOf(
                "wx.lua" to
                    """
                    ---@meta

                    ---@class wx
                    wx = {}

                    ---@type number
                    wx.wxID_ANY = nil

                    ---@class wxFrame
                    local wxFrame = {}

                    ---@param show boolean
                    ---@return boolean
                    function wxFrame:Show(show) end

                    ---@param parent any
                    ---@return wxFrame
                    function wx.wxFrame(parent) end

                    return wx
                    """.trimIndent(),
            ),
        )
        val constant = completionsFor("wx.wxID_<caret>\n")
        val ctor = completionsFor("local f = wx.wxFrame(nil)\nf:<caret>\n")
        println("DR-06 single constant: $constant")
        println("DR-06 single constructor: $ctor")
        println(
            "DR-06 SINGLE VERDICT: " +
                if (constant.contains("wxID_ANY") && ctor.contains("Show")) "works" else "ALSO FAILS",
        )
        assertTrue(
            "single-file layout must resolve a namespace constant. Found: $constant",
            constant.contains("wxID_ANY"),
        )
    }

    /** Cross-file, cross-namespace inheritance (TC 8) — flat type names must resolve across files. */
    fun testDr06CrossNamespaceInheritance() {
        registerLibraryRoot(
            mapOf(
                "wx.lua" to
                    """
                    ---@meta

                    ---@class wx
                    wx = {}

                    ---@class wxControl
                    local wxControl = {}

                    ---@param enable boolean
                    ---@return boolean
                    function wxControl:Enable(enable) end

                    return wx
                    """.trimIndent(),
                "wxstc.lua" to
                    """
                    ---@meta

                    ---@class wxstc
                    wxstc = {}

                    ---@class wxStyledTextCtrl : wxControl
                    local wxStyledTextCtrl = {}

                    ---@param lexer number
                    function wxStyledTextCtrl:SetLexer(lexer) end

                    ---@param parent any
                    ---@return wxStyledTextCtrl
                    function wxstc.wxStyledTextCtrl(parent) end

                    return wxstc
                    """.trimIndent(),
            ),
        )
        val found = completionsFor("local c = wxstc.wxStyledTextCtrl(nil)\nc:<caret>\n")
        println("DR-06 cross-namespace inheritance: $found")
        println("  own member SetLexer: ${found.contains("SetLexer")}; inherited Enable: ${found.contains("Enable")}")
    }

    /** `require("wx")` must resolve by file name (TC 14). */
    fun testRequireResolvesIntoTheRoot() {
        registerLibraryRoot(mapOf("wx.lua" to "---@meta\n\n---@class wx\nwx = {}\n\nreturn wx\n"))
        myFixture.configureByText("consumer.lua", "local wx = require(\"wx\")\n")
        val resolved =
            runReadAction {
                myFixture.file
                    .findReferenceAt(myFixture.file.text.indexOf("wx\"") + 1)
                    ?.resolve()
                    ?.containingFile
                    ?.virtualFile
                    ?.path
            }
        println("require(\"wx\") resolved to: $resolved")
        assertNotNull("require(\"wx\") must resolve into the library root", resolved)
    }

    /**
     * DR-06c: the option neither earlier run tested — split files that **re-declare** the same
     * `---@class wx` + `wx = {}` header. DR-06 showed a sibling file with NO re-anchor resolves
     * nothing; DR-06-single showed one self-contained file resolves but DR-04 then measures 24s to
     * first completion. If re-declaring merges, both problems go away at once.
     */
    fun testDr06cSplitWithReanchor() {
        registerLibraryRoot(
            mapOf(
                "wx.lua" to "---@meta\n\n---@class wx\nwx = {}\n\nreturn wx\n",
                "wx/wxcore.lua" to
                    """
                    ---@meta

                    ---@class wx
                    wx = {}

                    ---@type number
                    wx.wxID_ANY = nil

                    ---@class wxFrame
                    local wxFrame = {}

                    ---@param show boolean
                    ---@return boolean
                    function wxFrame:Show(show) end

                    ---@param parent any
                    ---@return wxFrame
                    function wx.wxFrame(parent) end
                    """.trimIndent(),
                "wx/wxbase.lua" to
                    """
                    ---@meta

                    ---@class wx
                    wx = {}

                    ---@param filename string
                    ---@return boolean
                    function wx.wxFileExists(filename) end
                    """.trimIndent(),
            ),
        )
        val constant = completionsFor("wx.wxID_<caret>\n")
        val ctor = completionsFor("local f = wx.wxFrame(nil)\nf:<caret>\n")
        val free = completionsFor("wx.wxFileExi<caret>\n")
        println("DR-06c re-anchored constant: $constant")
        println("DR-06c re-anchored constructor: $ctor")
        println("DR-06c re-anchored free function (2nd file): $free")
        val ok =
            constant.any { it.contains("wxID_ANY") } &&
                ctor.contains("Show") &&
                free.any { it.contains("wxFileExists") }
        println("DR-06c VERDICT: " + if (ok) "split-with-reanchor WORKS" else "does not merge")
    }

    /** DR-04b: the same large surface, but split across re-anchored files. Compare with DR-04. */
    fun testDr04bSplitWithReanchorTiming() {
        val files = mutableMapOf<String, String>()
        files["wx.lua"] = "---@meta\n\n---@class wx\nwx = {}\n\nreturn wx\n"
        repeat(15) { group ->
            val sb = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
            repeat(370) { i -> sb.append("---@type number\nwx.wxCONST_${group}_$i = nil\n\n") }
            repeat(18) { c ->
                sb.append("---@class wxGen${group}_$c\nlocal wxGen${group}_$c = {}\n\n")
                repeat(15) { m ->
                    sb.append("---@param a number\n---@param b string\n---@return boolean\n")
                    sb.append("function wxGen${group}_$c:Method$m(a, b) end\n\n")
                }
                sb.append("---@return wxGen${group}_$c\nfunction wx.wxGen${group}_$c() end\n\n")
            }
            files["wx/group$group.lua"] = sb.toString()
        }
        val indexMs = measureTimeMillis { registerLibraryRoot(files) }
        val firstMs = measureTimeMillis { completionsFor("wx.wxCONST_3_<caret>\n") }
        val found = completionsFor("wx.wxCONST_3_1<caret>\n")
        println("DR-04b split+reanchor: ${files.size} files, index ${indexMs}ms, first completion ${firstMs}ms")
        println("DR-04b resolves: ${found.take(3)}")
    }

    /**
     * DR-04c: is the 24s first-completion cost driven by FILE SIZE or by CANDIDATE COUNT?
     * Same single large file; query a prefix with few matches and a class-member caret. If both are
     * fast, the cost is the ~5550 same-prefix candidates and splitting files cannot help — only
     * emitting fewer symbols can.
     */
    fun testDr04cSizeVersusCandidateCount() {
        var constants = 0
        val whole = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(15) { group ->
            repeat(370) { i ->
                whole.append("---@type number\nwx.wxCONST_${group}_$i = nil\n\n")
                constants++
            }
            repeat(18) { c ->
                whole.append("---@class wxGen${group}_$c\nlocal wxGen${group}_$c = {}\n\n")
                repeat(15) { m ->
                    whole.append("---@param a number\n---@return boolean\n")
                    whole.append("function wxGen${group}_$c:Method$m(a) end\n\n")
                }
                whole.append("---@return wxGen${group}_$c\nfunction wx.wxGen${group}_$c() end\n\n")
            }
        }
        whole.append("return wx\n")
        val text = whole.toString()
        println("DR-04c single file: ${text.toByteArray().size / 1024} KiB, $constants constants")
        registerLibraryRoot(mapOf("wx.lua" to text))

        val broadMs = measureTimeMillis { completionsFor("wx.wxCONST_<caret>\n") }
        println("DR-04c broad prefix (thousands of candidates): ${broadMs}ms")
        val narrowMs = measureTimeMillis { completionsFor("wx.wxGen3_5<caret>\n") }
        println("DR-04c narrow prefix (few candidates): ${narrowMs}ms")
        val memberMs = measureTimeMillis { completionsFor("local g = wx.wxGen3_5()\ng:<caret>\n") }
        val members = completionsFor("local g = wx.wxGen3_5()\ng:<caret>\n")
        println("DR-04c class-member caret: ${memberMs}ms, resolves=${members.take(2)}")
    }

    /**
     * DR-04d: the layout the earlier spikes actually point to. Classes resolve cross-file by flat
     * type name (proved by the inheritance spike), so ONLY namespace-level members -- constants,
     * constructors, free functions -- need to sit in the file declaring `---@class wx`. Class bodies
     * and their methods move to sibling files, which is where the bulk of the bytes are.
     * Also samples the size/latency curve so the design can state a byte budget.
     */
    fun testDr04dNamespaceMembersOnlyInRoot() {
        val files = mutableMapOf<String, String>()
        val root = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(15) { group ->
            repeat(370) { i -> root.append("---@type number\nwx.wxCONST_${group}_$i = nil\n\n") }
            val bodies = StringBuilder("---@meta\n\n")
            repeat(18) { c ->
                bodies.append("---@class wxGen${group}_$c\nlocal wxGen${group}_$c = {}\n\n")
                repeat(15) { m ->
                    bodies.append("---@param a number\n---@return boolean\n")
                    bodies.append("function wxGen${group}_$c:Method$m(a) end\n\n")
                }
                root.append("---@return wxGen${group}_$c\nfunction wx.wxGen${group}_$c() end\n\n")
            }
            files["wx/classes$group.lua"] = bodies.toString()
        }
        root.append("return wx\n")
        files["wx.lua"] = root.toString()
        val rootKib = root.toString().toByteArray().size / 1024
        println("DR-04d root wx.lua = $rootKib KiB; ${files.size - 1} class files")

        registerLibraryRoot(files)
        val constMs = measureTimeMillis { completionsFor("wx.wxCONST_3_<caret>\n") }
        val consts = completionsFor("wx.wxCONST_3_1<caret>\n")
        val memberMs = measureTimeMillis { completionsFor("local g = wx.wxGen3_5()\ng:<caret>\n") }
        val members = completionsFor("local g = wx.wxGen3_5()\ng:<caret>\n")
        println("DR-04d namespace constant: ${constMs}ms resolves=${consts.isNotEmpty()}")
        println("DR-04d class member via constructor: ${memberMs}ms resolves=${members.take(2)}")
    }

    /** DR-04e: latency against root-file size, so the design can state a byte budget. */
    fun testDr04eSizeLatencyCurve() {
        listOf(40, 80, 160, 320).forEach { targetKib ->
            val sb = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
            var i = 0
            while (sb.length < targetKib * 1024) {
                sb.append("---@type number\nwx.wxK${targetKib}_$i = nil\n\n")
                i++
            }
            sb.append("return wx\n")
            registerLibraryRoot(mapOf("wx$targetKib.lua" to sb.toString()))
            val ms = measureTimeMillis { completionsFor("wx.wxK${targetKib}_5<caret>\n") }
            println("DR-04e ${targetKib}KiB / $i constants -> first completion ${ms}ms")
        }
    }

    // ---------------------------------------------------------------- DR-04: indexing budget

    /**
     * DR-04: a tree of the real order of magnitude — ~5,500 constants and ~4,000 methods across the
     * 15 group files design §3.5.2 emits. Reports wall-clock for register + full index.
     */
    fun testDr04IndexingBudget() {
        val files = mutableMapOf<String, String>()
        var constants = 0
        var methods = 0
        val whole = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(15) { group ->
            val sb = StringBuilder()
            repeat(370) { i ->
                sb.append("---@type number\nwx.wxCONST_${group}_$i = nil\n\n")
                constants++
            }
            repeat(18) { c ->
                sb.append("---@class wxGen${group}_$c\nlocal wxGen${group}_$c = {}\n\n")
                repeat(15) { m ->
                    sb.append("---@param a number\n---@param b string\n---@return boolean\n")
                    sb.append("function wxGen${group}_$c:Method$m(a, b) end\n\n")
                    methods++
                }
                sb.append("---@return wxGen${group}_$c\nfunction wx.wxGen${group}_$c() end\n\n")
            }
            whole.append(sb)
        }
        whole.append("return wx\n")
        files["wx.lua"] = whole.toString()
        val bytes = files.values.sumOf { it.toByteArray().size }
        println("DR-04 tree: ${files.size} files, $constants constants, $methods methods, ${bytes / 1024} KiB")

        val elapsed = measureTimeMillis { registerLibraryRoot(files) }
        println("DR-04 register + index wall-clock: ${elapsed}ms")

        val completionMs = measureTimeMillis { completionsFor("wx.wxCONST_3_<caret>\n") }
        println("DR-04 first completion against the tree: ${completionMs}ms")
        val found = completionsFor("wx.wxCONST_3_1<caret>\n")
        println("DR-04 sanity — a constant from the large tree completes: ${found.take(3)}")
        assertTrue("the large single-file tree must still resolve. Found: $found", found.isNotEmpty())
    }
}
