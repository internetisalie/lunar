package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import kotlin.system.measureTimeMillis

/**
 * THROWAWAY — COMP-09 de-risking: DR-01 (golden enumeration), DR-02 (bucket timings), DR-06
 * (the dot/colon sink asymmetry).
 *
 * DR-06 exists because COMP-09-01 claimed replacing `collectMethodMembers`' key scan with
 * `getElements(KEY, receiver)` is "a strict simplification". A review of the sink says otherwise:
 * `LuaFuncStubElementType:69-75` only sinks a receiver key when the name contains a **dot**, while
 * `memberNameOf:466` matches `receiver.` *and* `receiver:`. If true, the swap silently drops every
 * colon-declared method. Read establishes it; this runs it.
 */
class CompNineDrSpikeTest : LibraryRootTestCase() {
    private fun consumer(text: String) = myFixture.configureByText("consumer.lua", text)

    /** A library shaped like a real definition tree: dot members, colon methods, a `@class`. */
    private fun registerEnumerationFixture() {
        registerLibraryRoot(
            mapOf(
                "wx.lua" to
                    """
                    ---@meta

                    ---@class wx
                    wx = {}

                    ---@type number
                    wx.wxID_ANY = nil

                    ---@param filename string
                    ---@return boolean
                    function wx.wxFileExists(filename) end

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
                // The colon form, declared against a @class the way idiomatic Lua does it. DR-01's
                // golden file MUST contain this or it certifies the regression as behaviour-preserving.
                "colonlib.lua" to
                    """
                    ---@meta

                    ---@class ColonHost
                    local ColonHost = {}

                    ---@return string
                    function ColonHost:dotless() end

                    ---@param n number
                    ---@return number
                    function ColonHost:scale(n) end

                    ---@return string
                    function ColonHost.staticDot() end
                    """.trimIndent(),
            ),
        )
    }

    // ------------------------------------------------------------------ DR-06

    /**
     * The decisive test. `getElements(KEY, "<receiver>")` is what COMP-09-01 proposed swapping the
     * scan for. If the sink is dot-only, the colon-declared methods are absent from that query while
     * present in the index under their full key.
     */
    fun testDr06DotVersusColonReceiverKeying() {
        registerEnumerationFixture()
        consumer("local x = 1\n")
        val scope = GlobalSearchScope.allScope(project)

        runReadAction {
            val allKeys: Collection<String> =
                StubIndex.getInstance().getAllKeys(LuaGlobalDeclarationIndex.KEY, project)
            val colonKeys = allKeys.filter { it.startsWith("ColonHost") }.sorted()
            println("DR-06 keys beginning 'ColonHost': $colonKeys")

            val byReceiver =
                StubIndex
                    .getElements<String, LuaFuncDecl>(
                        LuaGlobalDeclarationIndex.KEY,
                        "ColonHost",
                        project,
                        scope,
                        LuaFuncDecl::class.java,
                    ).map { it.funcName.text }
                    .sorted()
            println("DR-06 getElements(KEY, \"ColonHost\") -> $byReceiver")

            val wxByReceiver =
                StubIndex
                    .getElements<String, LuaFuncDecl>(
                        LuaGlobalDeclarationIndex.KEY,
                        "wx",
                        project,
                        scope,
                        LuaFuncDecl::class.java,
                    ).map { it.funcName.text }
                    .sorted()
            println("DR-06 getElements(KEY, \"wx\") -> $wxByReceiver")

            val colonFound = byReceiver.any { it.contains(":") }
            val verdict =
                if (colonFound) {
                    "DOES cover the colon form — COMP-09-01's claim holds"
                } else {
                    "is DOT-ONLY — the swap would drop colon methods"
                }
            println(
                "DR-06 VERDICT: receiver key $verdict; " +
                    "dot form covered=${wxByReceiver.isNotEmpty()}",
            )
        }
    }

    /**
     * DR-02c — the severity determinant. Both caches depend on project-wide
     * `PsiModificationTracker`, which *implies* a keystroke in the consumer file invalidates the
     * library's snapshot and the next `wx.` pays the full cost again. Implication is not measurement.
     */
    fun testDr02cInvalidationOnUnrelatedEdit() {
        val root = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(3400) { i -> root.append("---@type number\nwx.wxC_$i = nil\n\n") }
        root.append("return wx\n")
        registerLibraryRoot(mapOf("wx.lua" to root.toString()))
        consumer("local x = 1\n")

        fun resolve() = runReadAction { LuaTypeManager.getInstance(project).resolveGlobal("wx", myFixture.file) }

        val cold = measureTimeMillis { resolve() }
        val warm = measureTimeMillis { resolve() }

        // One keystroke in an unrelated file — the consumer, not the library.
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(myFixture.editor.document.textLength, "-- k\n")
            com.intellij.psi.PsiDocumentManager
                .getInstance(project)
                .commitAllDocuments()
        }

        val afterEdit = measureTimeMillis { resolve() }
        println("DR-02c cold=${cold}ms  warm=${warm}ms  after-one-keystroke-in-CONSUMER=${afterEdit}ms")
        println(
            "DR-02c VERDICT: " +
                if (afterEdit > cold / 2) {
                    "PER-KEYSTROKE — an unrelated edit invalidates and the full cost is repaid"
                } else {
                    "once per session — an unrelated edit does NOT invalidate the library snapshot"
                },
        )
    }

    // ------------------------------------------------------------------ DR-01

    /** Today's exact enumeration result, for the golden file COMP-09-07 compares against. */
    fun testDr01GoldenEnumeration() {
        registerEnumerationFixture()
        consumer("local x = 1\n")
        runReadAction {
            listOf("wx", "wxFrame", "ColonHost").forEach { name ->
                dumpEnumeration(project, name)
            }
        }
    }

    private fun dumpEnumeration(
        project: Project,
        name: String,
    ) {
        val context = myFixture.file
        val resolved = LuaTypeManager.getInstance(project).resolveGlobal(name, context)
        if (resolved == null) {
            println("DR-01 $name -> resolveGlobal returned null")
            return
        }
        val members =
            LuaGraphType
                .materialize(resolved, context)
                .getMembers()
                .map { (member, node) -> "$member : ${node.write}" }
                .sorted()
        println("DR-01 $name -> ${members.size} members")
        members.forEach { println("DR-01   $it") }
    }

    // ------------------------------------------------------------------ DR-02

    /**
     * Bucket timings on the real path. If `materialize` dominates and precedes every `addElement`,
     * time-to-first-result equals time-to-exhaustive **by construction** — which is what DR-02a was
     * raised to measure and what this establishes without a first-element observer.
     */
    fun testDr02BucketTimings() {
        val files = mutableMapOf<String, String>()
        val root = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(3400) { i -> root.append("---@type number\nwx.wxC_$i = nil\n\n") }
        repeat(300) { i ->
            root.append("---@class wxG$i\nlocal wxG$i = {}\n\n")
            repeat(8) { m -> root.append("---@return boolean\nfunction wxG$i:M$m() end\n\n") }
            root.append("---@return wxG$i\nfunction wx.wxG$i() end\n\n")
        }
        root.append("return wx\n")
        files["wx.lua"] = root.toString()
        println("DR-02 root = ${root.length / 1024} KiB")
        registerLibraryRoot(files)
        consumer("local x = 1\n")

        runReadAction {
            val context = myFixture.file
            var resolved: Any? = null
            val resolveMs =
                measureTimeMillis {
                    resolved = LuaTypeManager.getInstance(project).resolveGlobal("wx", context)
                }
            val type = resolved as? net.internetisalie.lunar.lang.psi.types.LuaType
            if (type == null) {
                println("DR-02 resolveGlobal returned null — cannot bucket")
                return@runReadAction
            }
            var graph: LuaGraphType? = null
            val materializeMs = measureTimeMillis { graph = LuaGraphType.materialize(type, context) }
            var count = 0
            val getMembersMs = measureTimeMillis { count = graph!!.getMembers().size }
            println(
                "DR-02 resolveGlobal=${resolveMs}ms  materialize=${materializeMs}ms  " +
                    "getMembers=${getMembersMs}ms  members=$count",
            )
            println(
                "DR-02 => all of it precedes the first addElement, " +
                    "so time-to-first == time-to-exhaustive by construction",
            )

            // DR-02b: resolveGlobal is 99.9% of it. Is the cost LuaTypesSnapshot.forFile on the
            // DECLARING file? Time a snapshot build directly, and time a second resolveGlobal to
            // see what the CachedValuesManager memoization (MAINT-30-02) actually buys.
            val secondMs = measureTimeMillis { LuaTypeManager.getInstance(project).resolveGlobal("wx", context) }
            println("DR-02b second resolveGlobal (warm, no PSI change) = ${secondMs}ms")
        }
    }
}
