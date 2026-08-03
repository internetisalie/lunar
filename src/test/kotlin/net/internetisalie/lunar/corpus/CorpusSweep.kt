package net.internetisalie.lunar.corpus

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import net.internetisalie.lunar.lang.LuaRequireReference
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr

/**
 * Indexes a pinned third-party project and counts invariant-style defects across it: syntax the
 * parser cannot represent, `require(...)` targets the resolver cannot find, and warnings per
 * inspection.
 *
 * These are deliberately expectation-free measurements — they need no golden files, so the corpus
 * can be large and can grow without anyone authoring per-file assertions.
 */
object CorpusSweep {

    internal data class FileTally(val parseErrors: Int, val requires: Int, val unresolved: Int)

    private data class SweptFile(val path: String, val file: VirtualFile, val tally: FileTally)

    fun run(fixture: CodeInsightTestFixture, entry: CorpusEntry): CorpusMetrics {
        val swept = entry.roots.flatMap { sweepRoot(fixture, entry.name, it) }.sortedBy { it.path }
        return CorpusMetrics(
            commit = entry.commit,
            files = swept.size,
            parseErrors = swept.sumOf { it.tally.parseErrors },
            requires = swept.sumOf { it.tally.requires },
            unresolvedRequires = swept.sumOf { it.tally.unresolved },
            parseErrorFiles = swept.filter { it.tally.parseErrors > 0 }.map { it.path },
            inspectionHits = inspectionHits(fixture, swept),
        )
    }

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
    private fun inspectionHits(fixture: CodeInsightTestFixture, swept: List<SweptFile>): Map<String, Int> {
        val hits = mutableMapOf<String, Int>()
        swept.forEach { entry ->
            // Deliberately catching Throwable, not Exception: BUG-390 overflows the stack inside
            // the type engine, and a single pathological file must not abort the measurement of a
            // whole corpus. The count is gated (CorpusMetrics.HIGHLIGHT_FAILURES), so a rise still
            // fails the build — the failure is recorded, never swallowed.
            @Suppress("TooGenericExceptionCaught")
            try {
                accumulateHits(fixture, entry, hits)
            } catch (throwable: Throwable) {
                println("[corpus] highlight failed on ${entry.path}: ${throwable.javaClass.simpleName}")
                hits[CorpusMetrics.HIGHLIGHT_FAILURES] = (hits[CorpusMetrics.HIGHLIGHT_FAILURES] ?: 0) + 1
            }
        }
        return hits
    }

    private fun accumulateHits(
        fixture: CodeInsightTestFixture,
        entry: SweptFile,
        hits: MutableMap<String, Int>,
    ) {
        fixture.openFileInEditor(entry.file)
        var nullIds = 0
        fixture.doHighlighting(HighlightSeverity.WEAK_WARNING).forEach { info ->
            val toolId = info.inspectionToolId
            if (toolId == null) nullIds++ else hits[toolId] = (hits[toolId] ?: 0) + 1
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
        val requireReferences = PsiTreeUtil.findChildrenOfType(psiFile, LuaTerminalExpr::class.java)
            .flatMap { it.references.asIterable() }
            .filterIsInstance<LuaRequireReference>()
        return FileTally(
            parseErrors = PsiTreeUtil.findChildrenOfType(psiFile, PsiErrorElement::class.java).size,
            requires = requireReferences.size,
            unresolved = requireReferences.count { it.resolve() == null },
        )
    }
}
