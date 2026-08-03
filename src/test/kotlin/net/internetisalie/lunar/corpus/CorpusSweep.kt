package net.internetisalie.lunar.corpus

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import net.internetisalie.lunar.lang.LuaRequireReference
import net.internetisalie.lunar.lang.psi.LuaArgs
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import java.io.File

/**
 * Indexes a pinned third-party project and counts invariant-style defects across it: syntax the
 * parser cannot represent, `require(...)` targets the resolver cannot find, and warnings per
 * inspection.
 *
 * These are deliberately expectation-free measurements — they need no golden files, so the corpus
 * can be large and can grow without anyone authoring per-file assertions.
 */
object CorpusSweep {

    private const val SYMBOLS_PER_INSPECTION = 10

    internal data class FileTally(
        val parseErrors: Int,
        val requires: Int,
        val unresolved: Int,
        val errorSites: List<String> = emptyList(),
    )

    private data class SweptFile(val path: String, val file: VirtualFile, val tally: FileTally)

    fun run(fixture: CodeInsightTestFixture, entry: CorpusEntry, checkoutDir: File): CorpusMetrics {
        val swept = entry.roots.flatMap { sweepRoot(fixture, entry.name, it) }.sortedBy { it.path }
        val sink = inspectionHits(fixture, swept)
        return CorpusMetrics(
            commit = entry.commit,
            files = swept.size,
            parseErrors = swept.sumOf { it.tally.parseErrors },
            requires = swept.sumOf { it.tally.requires },
            unresolvedRequires = swept.sumOf { it.tally.unresolved },
            parseErrorFiles = swept
                .filter { it.tally.parseErrors > 0 }
                .flatMap { file -> file.tally.errorSites.map { "${file.path}:$it" } },
            inspectionHits = sink.hits,
            symbolHits = topSymbols(sink.symbols),
            ballast = ballast(checkoutDir, entry),
        )
    }

    /**
     * The top [SYMBOLS_PER_INSPECTION] symbols per inspection. Capped so the baseline stays a
     * reviewable diff rather than a dump of every name in the corpus; ties break by name so the
     * selection is deterministic.
     */
    private fun topSymbols(symbols: Map<String, Int>): Map<String, Int> =
        symbols.entries
            .groupBy { it.key.substringBefore('.') }
            .values
            .flatMap { perTool ->
                perTool.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .take(SYMBOLS_PER_INSPECTION)
            }
            .associate { it.key to it.value }

    /**
     * MAINT-33-07. Ballast is the **complement of what the sweep indexes**: a file is ballast
     * unless it is `.lua` *and* under a declared root. Both conjuncts matter — dropping the root
     * test would exclude `tlconfig.lua` (a Lua-syntax config at the checkout root that is never
     * parsed), and dropping the extension test would exclude the 107 `.tl` files that live inside
     * `luarocks/src` and are the whole Teal signal.
     */
    private fun ballast(checkoutDir: File, entry: CorpusEntry): Map<String, BallastGroup> {
        val rootPaths = entry.roots.map { File(checkoutDir, it).canonicalPath }
        val counts = mutableMapOf<String, Int>()
        val unclaimed = mutableSetOf<String>()
        checkoutDir.walkTopDown()
            .onEnter { it.name != ".git" }
            .filter { it.isFile && it.name != ".corpus-sha" }
            .filterNot { it.extension == "lua" && rootPaths.any { root -> it.canonicalPath.startsWith("$root/") } }
            .forEach { file ->
                val key = groupKey(file.name)
                counts[key] = (counts[key] ?: 0) + 1
                if (!isClaimed(file.name)) unclaimed += key
            }
        return counts.mapValues { (key, count) -> BallastGroup(count, key !in unclaimed) }
    }

    /**
     * Base name when there is no dot after position 0 (`Makefile`, `.busted`, `.luacov`), otherwise
     * the lowercase suffix after the last dot (`tl`, `rockspec`, and `config.ld` → `ld`).
     */
    private fun groupKey(fileName: String): String =
        if (fileName.indexOf('.', startIndex = 1) < 0) fileName else fileName.substringAfterLast('.').lowercase()

    private fun isClaimed(fileName: String): Boolean =
        FileTypeManager.getInstance().getFileTypeByFileName(fileName) != UnknownFileType.INSTANCE

    private fun sweepRoot(
        fixture: CodeInsightTestFixture,
        corpusName: String,
        root: String,
    ): List<SweptFile> {
        val copied = fixture.copyDirectoryToProject("${CorpusManifest.CORPUS_DIR}/$corpusName/$root", root)
        val psiManager = PsiManager.getInstance(fixture.project)
        return collectLuaFiles(copied).mapNotNull { luaFile ->
            val psiFile = psiManager.findFile(luaFile) ?: return@mapNotNull null
            val relativePath = VfsUtilCore.getRelativePath(luaFile, copied) ?: luaFile.name
            SweptFile("$root/$relativePath", luaFile, tally(psiFile))
        }
    }

    /**
     * MAINT-33-06. One highlight pass per file, attributed by tool id; a null id is bucketed under
     * [CorpusMetrics.UNATTRIBUTED] **net of** that file's parse errors, because every
     * `PsiErrorElement` also yields a null-id ERROR info and is already gated by `parseErrors`.
     */
    private fun inspectionHits(fixture: CodeInsightTestFixture, swept: List<SweptFile>): HitSink {
        val sink = HitSink()
        val hits = sink.hits
        swept.forEach { entry ->
            // Deliberately catching Throwable, not Exception: BUG-390 overflows the stack inside
            // the type engine, and a single pathological file must not abort the measurement of a
            // whole corpus. The count is gated (CorpusMetrics.HIGHLIGHT_FAILURES), so a rise still
            // fails the build — the failure is recorded, never swallowed.
            @Suppress("TooGenericExceptionCaught")
            try {
                accumulateHits(fixture, entry, sink)
            } catch (throwable: Throwable) {
                println("[corpus] highlight failed on ${entry.path}: ${throwable.javaClass.simpleName}")
                hits[CorpusMetrics.HIGHLIGHT_FAILURES] = (hits[CorpusMetrics.HIGHLIGHT_FAILURES] ?: 0) + 1
            }
        }
        return sink
    }

    /**
     * MAINT-33-10. Per-inspection counts plus, for each, the symbols it fired on most often —
     * enough to tell a missing-definitions problem (`wx`, one huge bucket) from a resolution defect
     * (a long tail of names the plugin should already know).
     */
    private class HitSink(
        val hits: MutableMap<String, Int> = mutableMapOf(),
        val symbols: MutableMap<String, Int> = mutableMapOf(),
    )

    /** The symbol an inspection names, e.g. `Undeclared variable 'wx'` → `wx`. */
    private val QUOTED_SYMBOL = Regex("'([^']+)'")

    private fun accumulateHits(
        fixture: CodeInsightTestFixture,
        entry: SweptFile,
        sink: HitSink,
    ) {
        val hits = sink.hits
        fixture.openFileInEditor(entry.file)
        var nullIds = 0
        fixture.doHighlighting(HighlightSeverity.WEAK_WARNING).forEach { info ->
            val toolId = info.inspectionToolId
            if (toolId == null) {
                nullIds++
            } else {
                hits[toolId] = (hits[toolId] ?: 0) + 1
                QUOTED_SYMBOL.find(info.description.orEmpty())?.groupValues?.get(1)?.let { symbol ->
                    val key = "$toolId.$symbol"
                    sink.symbols[key] = (sink.symbols[key] ?: 0) + 1
                }
            }
        }
        val unattributed = (nullIds - entry.tally.parseErrors).coerceAtLeast(0)
        if (unattributed > 0) {
            hits[CorpusMetrics.UNATTRIBUTED] = (hits[CorpusMetrics.UNATTRIBUTED] ?: 0) + unattributed
        }
    }

    private fun collectLuaFiles(root: VirtualFile): List<VirtualFile> {
        val found = mutableListOf<VirtualFile>()
        VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
            if (!file.isDirectory && file.extension == "lua") found += file
            true
        }
        return found
    }

    private fun tally(psiFile: PsiFile): FileTally {
        // Both hosts, deliberately: `require("m")` anchors its reference on the LuaTerminalExpr,
        // `require "m"` on the enclosing LuaArgs (BUG-389 — a reference cannot hang on a bare leaf).
        // Collecting only the former is exactly the blind spot that made this metric report 3
        // recognised requires across 132 luacheck files, and would have hidden the fix as well.
        val requireHosts = PsiTreeUtil.findChildrenOfType(psiFile, LuaTerminalExpr::class.java) +
            PsiTreeUtil.findChildrenOfType(psiFile, LuaArgs::class.java)
        val requireReferences = requireHosts
            .flatMap { it.references.asIterable() }
            .filterIsInstance<LuaRequireReference>()
        return FileTally(
            parseErrors = PsiTreeUtil.findChildrenOfType(psiFile, PsiErrorElement::class.java).size,
            requires = requireReferences.size,
            unresolved = requireReferences.count { it.resolve() == null },
            errorSites = describeParseErrors(psiFile),
        )
    }

    /**
     * `line:description` for each `PsiErrorElement`, so a parse regression is locatable from the
     * baseline diff alone instead of needing a bisect over the file (BUG-392 needed exactly that).
     */
    private fun describeParseErrors(psiFile: PsiFile): List<String> {
        val document = psiFile.viewProvider.document
        return PsiTreeUtil.findChildrenOfType(psiFile, PsiErrorElement::class.java).map { error ->
            val line = document?.getLineNumber(error.textOffset)?.plus(1) ?: 0
            "$line:${error.errorDescription}"
        }
    }
}
