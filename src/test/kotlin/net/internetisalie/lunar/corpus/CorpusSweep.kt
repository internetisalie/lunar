package net.internetisalie.lunar.corpus

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.util.TextRange
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
import net.internetisalie.lunar.lang.syntax.LuaSyntaxDiagnostics
import net.internetisalie.lunar.settings.LuaProjectSettings
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
    /**
     * One file's findings (MAINT-40-01) and the counts read from them. The counts are computed from
     * [findings], never stored beside them, so the two cannot disagree.
     */
    internal data class FileTally(
        val requires: Int,
        val findings: List<CorpusFinding>,
        /** MAINT-35-02: PUC's verdict, or null when the file was not judged. */
        val oracle: ParseOracle.Verdict? = null,
    ) {
        val parseErrors get() = CorpusFindingCounts.count(findings, CorpusRules.PARSE_ERROR)

        /** BUG-409: syntax errors Lunar reports without a `PsiErrorElement` (`LuaSyntaxDiagnostics`). */
        val invalidStatements get() = CorpusFindingCounts.count(findings, CorpusRules.INVALID_STATEMENT)

        val unresolved get() = CorpusFindingCounts.count(findings, CorpusRules.UNRESOLVED_REQUIRE)

        /** MAINT-35-04: token texts did not reconstitute the source. */
        val roundTripFailed get() = CorpusFindingCounts.count(findings, CorpusRules.LEXER_ROUND_TRIP) > 0

        /** MAINT-35-04: internal `LONGSTRING*`/`LONGCOMMENT*` tokens escaped the merge (BUG-392). */
        val unmergedTokens get() = CorpusFindingCounts.count(findings, CorpusRules.UNMERGED_TOKENS)

        /** MAINT-35-05: `lex:<Class>` or `parse:<Class>`, else null. */
        val crash get() = findings.firstOrNull { it.rule == CorpusRules.CRASH }?.message
    }

    private data class SweptFile(
        val path: String,
        val file: VirtualFile,
        val tally: FileTally,
    ) {
        // BUG-409: "the parser built no error elements" is not "Lunar considers this valid Lua".
        // `exprStatement ::= expr` is deliberately permissive for error recovery, and the narrowing
        // is enforced by an annotator — so an oracle that counts only PsiErrorElements scores a file
        // Lunar rejects with four visible errors as accepted, and invents a disagreement.
        private val lunarAccepts get() = tally.parseErrors == 0 && tally.invalidStatements == 0

        /** `luac` accepts at the pinned level and Lunar does not — a defect with no confound. */
        val falseReject get() = tally.oracle == ParseOracle.Verdict.Accept && !lunarAccepts

        /** Lunar accepts and `luac` does not — diagnostic; see the level-superset note above. */
        val falseAccept get() = tally.oracle is ParseOracle.Verdict.Reject && lunarAccepts

        /** The tally's findings plus the oracle's verdict on this file, as findings. */
        val findings: List<CorpusFinding> get() = tally.findings + oracleFindings(FindingSite(path, null))

        private fun oracleFindings(site: FindingSite): List<CorpusFinding> =
            listOfNotNull(
                site.fileFinding(CorpusRules.ORACLE_FALSE_REJECT, FALSE_REJECT_MESSAGE).takeIf { falseReject },
                site.fileFinding(CorpusRules.ORACLE_FALSE_ACCEPT, FALSE_ACCEPT_MESSAGE).takeIf { falseAccept },
                site.fileFinding(CorpusRules.ORACLE_TIMEOUT, NOT_JUDGED_MESSAGE).takeIf {
                    tally.oracle is ParseOracle.Verdict.NotJudged
                },
            )
    }

    private const val FALSE_REJECT_MESSAGE = "luac accepts, Lunar rejects"
    private const val FALSE_ACCEPT_MESSAGE = "Lunar accepts, luac rejects"
    private const val NOT_JUDGED_MESSAGE = "oracle did not judge the file"
    private const val ROUND_TRIP_MESSAGE = "token texts do not reconstitute the source"
    private const val UNMERGED_MESSAGE = "internal long-bracket tokens escaped the merge"

    /** What every per-file step needs beyond the fixture: which corpus, and where the repo is. */
    private data class SweepContext(
        val entry: CorpusEntry,
        val repoRoot: File,
    )

    fun run(
        fixture: CodeInsightTestFixture,
        entry: CorpusEntry,
        repoRoot: File,
    ): CorpusMetrics {
        val checkoutDir = CorpusManifest.checkoutDir(repoRoot, entry.name)
        val context = SweepContext(entry, repoRoot)
        applyModuleRoot(fixture, entry, checkoutDir)
        // Fail fast before judging anything: an oracle that is absent — or that answers Accept to
        // everything — must never be discovered halfway through a sweep, and must never degrade to
        // "no disagreements" (MAINT-35-03).
        ParseOracle.assertDiscriminates(repoRoot, entry.luaLevel)
        val swept = entry.roots.flatMap { sweepRoot(fixture, context, it) }.sortedBy { it.path }
        val metrics = metricsOf(context, swept, inspectionHits(fixture, swept))
        return metrics.copy(libraries = CorpusDefinitionLibraries.enabledPins(fixture.project))
    }

    /**
     * Every counter comes from the findings (MAINT-40-02). MAINT-35-02: only FALSE REJECTS are
     * gated. Lunar parses a superset of any single language level and defers enforcement to
     * LuaLanguageLevelInspection, so "luac rejects, Lunar accepts" has a systematic false positive
     * (DR-01) and is diagnostic only.
     */
    private fun metricsOf(
        context: SweepContext,
        swept: List<SweptFile>,
        inspectionFindings: List<CorpusFinding>,
    ): CorpusMetrics {
        val checkoutDir = CorpusManifest.checkoutDir(context.repoRoot, context.entry.name)
        val findings = swept.flatMap { it.findings } + inspectionFindings
        return CorpusFindingCounts.derive(findings).copy(
            commit = context.entry.commit,
            files = swept.size,
            requires = swept.sumOf { it.tally.requires },
            ballast = ballast(checkoutDir, context.entry),
        )
    }

    /**
     * Puts the project's declared [CorpusEntry.moduleRoot] on `package.path`, so a project whose
     * modules are named relative to a subtree (`require("ui/uimanager")` → `frontend/ui/uimanager.lua`)
     * is measured for real resolution failures rather than for a layout the sweep never described.
     *
     * The patterns point at the **on-disk checkout**, not the copied fixture tree: resolution runs
     * through `LuaModuleFileResolver.findByPath`, which consults `LocalFileSystem` only, while
     * `copyDirectoryToProject` materialises into the in-memory `temp://` filesystem. Pointing at
     * `temp://` paths would silently resolve nothing.
     */
    private fun applyModuleRoot(
        fixture: CodeInsightTestFixture,
        entry: CorpusEntry,
        checkoutDir: File,
    ) {
        val moduleRoot = entry.moduleRoot ?: return
        val base = File(checkoutDir, moduleRoot).canonicalPath
        LuaProjectSettings.getInstance(fixture.project).state.sourcePath = "$base/?.lua;$base/?/init.lua"
    }

    /**
     * MAINT-33-07. Ballast is the **complement of what the sweep indexes**: a file is ballast
     * unless it is `.lua` *and* under a declared root. Both conjuncts matter — dropping the root
     * test would exclude `tlconfig.lua` (a Lua-syntax config at the checkout root that is never
     * parsed), and dropping the extension test would exclude the 107 `.tl` files that live inside
     * `luarocks/src` and are the whole Teal signal.
     */
    private fun ballast(
        checkoutDir: File,
        entry: CorpusEntry,
    ): Map<String, BallastGroup> {
        val rootPaths = entry.roots.map { File(checkoutDir, it).canonicalPath }
        val groups = mutableMapOf<String, BallastGroup>()
        checkoutDir
            .walkTopDown()
            .onEnter { it.name != ".git" }
            .filter { it.isFile && it.name != ".corpus-sha" }
            .filterNot { it.extension == "lua" && rootPaths.any { root -> it.canonicalPath.startsWith("$root/") } }
            .forEach { file ->
                val key = groupKey(file.name)
                val running = groups[key] ?: BallastGroup(0, 0)
                // Counted per member, never flagged for the group: one unrecognised file must not
                // hide its claimed siblings. `groupKey` files an extensionless `rockspec` under the
                // same key as `*.rockspec`, which is exactly how that used to happen.
                groups[key] =
                    if (isClaimed(file.name)) {
                        running.copy(claimed = running.claimed + 1)
                    } else {
                        running.copy(unclaimed = running.unclaimed + 1)
                    }
            }
        return groups
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
        context: SweepContext,
        root: String,
    ): List<SweptFile> {
        val corpusName = context.entry.name
        val copied = fixture.copyDirectoryToProject("${CorpusManifest.CORPUS_DIR}/$corpusName/$root", root)
        val psiManager = PsiManager.getInstance(fixture.project)
        return collectLuaFiles(copied).mapNotNull { luaFile ->
            val psiFile = psiManager.findFile(luaFile) ?: return@mapNotNull null
            val relativePath = VfsUtilCore.getRelativePath(luaFile, copied) ?: luaFile.name
            val path = "$root/$relativePath"
            SweptFile(path, luaFile, tallyGuarded(context, psiFile, path))
        }
    }

    /**
     * MAINT-33-06. One highlight pass per file, one finding per highlight: attributed by tool id, or
     * [CorpusRules.UNATTRIBUTED_HIGHLIGHT] for a null id, which [CorpusFindingCounts.inspectionHits]
     * nets against that file's parse errors.
     */
    private fun inspectionHits(
        fixture: CodeInsightTestFixture,
        swept: List<SweptFile>,
    ): List<CorpusFinding> =
        swept.flatMap { entry ->
            // Deliberately catching Throwable, not Exception: BUG-390 overflows the stack inside
            // the type engine, and a single pathological file must not abort the measurement of a
            // whole corpus. The count is gated (CorpusMetrics.HIGHLIGHT_FAILURES), so a rise still
            // fails the build — the failure is recorded, never swallowed.
            @Suppress("TooGenericExceptionCaught")
            try {
                accumulateHits(fixture, entry)
            } catch (throwable: Throwable) {
                println("[corpus] highlight failed on ${entry.path}: ${throwable.javaClass.simpleName}")
                val site = FindingSite(entry.path, null)
                listOf(site.fileFinding(CorpusRules.HIGHLIGHT_FAILURE, throwable.javaClass.simpleName))
            }
        }

    /** The symbol an inspection names, e.g. `Undeclared variable 'wx'` → `wx` (MAINT-33-10). */
    private val QUOTED_SYMBOL = Regex("'([^']+)'")

    private fun accumulateHits(
        fixture: CodeInsightTestFixture,
        entry: SweptFile,
    ): List<CorpusFinding> {
        fixture.openFileInEditor(entry.file)
        val site = FindingSite(entry.path, fixture.editor.document)
        return fixture.doHighlighting(HighlightSeverity.WEAK_WARNING).map { info ->
            val toolId = info.inspectionToolId
            val rule = if (toolId == null) CorpusRules.UNATTRIBUTED_HIGHLIGHT else CorpusRules.inspection(toolId)
            val description = info.description.orEmpty()
            val finding = site.finding(rule, TextRange(info.startOffset, info.endOffset), description)
            val symbol = if (toolId == null) null else QUOTED_SYMBOL.find(description)?.groupValues?.get(1)
            finding.copy(symbol = symbol)
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

    /**
     * MAINT-35-05, parse half. The parse happens *inside* [tally] — `findChildrenOfType` forces
     * `calcTreeElement()` — so wrapping the body is what captures a parser crash. `psiFile.text`
     * is safe to read first: `PsiFileImpl.getText()` returns the view provider's contents and never
     * builds the tree, so it cannot throw here.
     *
     * A crashed file is deliberately **excluded from the oracle comparison**: it is neither an
     * accept nor a reject. Reporting `parseErrors = 0` would make `lunarAccepts` true and score it
     * as a false accept, conflating a crash with a semantic disagreement.
     *
     * The oracle judges `psiFile.text` re-encoded as UTF-8, **not** the on-disk bytes. That is the
     * comparison the gate wants: the question is whether luac accepts the same characters Lunar's
     * parser saw, so both sides must be fed one decoded form. Feeding luac the raw bytes would let a
     * decoding difference masquerade as a parser disagreement.
     */
    private fun tallyGuarded(
        context: SweepContext,
        psiFile: PsiFile,
        path: String,
    ): FileTally {
        val source = psiFile.text
        val site = FindingSite(path, psiFile.viewProvider.document)
        val lex = LexerInvariants.check(source)
        val parsed = runCatching { tally(psiFile, site) }
        val crash =
            lex.crash?.let { "lex:$it" }
                ?: parsed.exceptionOrNull()?.let { "parse:${it::class.java.simpleName}" }

        val base = parsed.getOrNull() ?: FileTally(requires = 0, findings = emptyList())
        return base.copy(
            findings = base.findings + guardFindings(site, lex, crash),
            oracle = if (crash != null) null else ParseOracle.judge(context.repoRoot, source, context.entry.luaLevel),
        )
    }

    /** MAINT-35-04/-05 as findings: the crash, a failed round trip, and escaped internal tokens. */
    private fun guardFindings(
        site: FindingSite,
        lex: LexerInvariants.Result,
        crash: String?,
    ): List<CorpusFinding> =
        listOfNotNull(
            crash?.let { site.fileFinding(CorpusRules.CRASH, it) },
            site.fileFinding(CorpusRules.LEXER_ROUND_TRIP, ROUND_TRIP_MESSAGE).takeIf { lex.roundTripFailed },
            site.fileFinding(CorpusRules.UNMERGED_TOKENS, UNMERGED_MESSAGE, lex.unmergedTokens).takeIf { it.count > 0 },
        )

    private fun tally(
        psiFile: PsiFile,
        site: FindingSite,
    ): FileTally {
        // Both hosts, deliberately: `require("m")` anchors its reference on the LuaTerminalExpr,
        // `require "m"` on the enclosing LuaArgs (BUG-389 — a reference cannot hang on a bare leaf).
        // Collecting only the former is exactly the blind spot that made this metric report 3
        // recognised requires across 132 luacheck files, and would have hidden the fix as well.
        val requireHosts =
            PsiTreeUtil.findChildrenOfType(psiFile, LuaTerminalExpr::class.java) +
                PsiTreeUtil.findChildrenOfType(psiFile, LuaArgs::class.java)
        val requireReferences =
            requireHosts
                .flatMap { it.references.asIterable() }
                .filterIsInstance<LuaRequireReference>()
        val parseErrors =
            PsiTreeUtil.findChildrenOfType(psiFile, PsiErrorElement::class.java).map { error ->
                site.finding(CorpusRules.PARSE_ERROR, error.textRange, error.errorDescription)
            }
        val invalidStatements =
            LuaSyntaxDiagnostics.invalidStatements(psiFile).map { statement ->
                site.finding(CorpusRules.INVALID_STATEMENT, statement.textRange, "invalid statement")
            }
        val unresolved =
            requireReferences.filter { it.resolve() == null }.map { reference ->
                val module = reference.canonicalText
                site.finding(CorpusRules.UNRESOLVED_REQUIRE, reference.absoluteRange, module).copy(symbol = module)
            }
        return FileTally(requires = requireReferences.size, findings = parseErrors + invalidStatements + unresolved)
    }
}
