package net.internetisalie.lunar.corpus

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaFileType
import java.io.File

/**
 * MAINT-35-06. Sweeps a pinned fuzzer corpus with the oracle-free invariants and the parse oracle.
 *
 * The name **must** contain `Corpus`: `build.gradle.kts:270`'s `excludeTestsMatching("*Corpus*")`
 * is what keeps this out of the routine loop and out of CI, where nothing is fetched. Naming it
 * `LuaTortureTest` would let it escape that filter.
 *
 * Nothing here measures inspections or requires. These inputs are not a Lua project — they are
 * minimized fuzzer findings, mostly invalid Lua by construction — so the only meaningful questions
 * are the ones that hold for *any* byte sequence: does the lexer round-trip it, does it merge
 * cleanly, does anything throw, and does PUC agree about whether it parses.
 */
class LuaTortureCorpusTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = System.getProperty("user.dir")

    fun testFuzzingLuaCorpus() = sweepAndRatchet("fuzzing-lua")

    private fun sweepAndRatchet(name: String) {
        val repoRoot = File(testDataPath)
        val member = TortureManifest.member(repoRoot, name)
        TortureManifest.assertFetched(repoRoot, member)
        // Same fail-fast as the project sweep: an oracle that is absent, or that has stopped
        // discriminating, must be caught before a single input is judged (MAINT-35-03).
        ParseOracle.assertDiscriminates(repoRoot, member.luaLevel)

        val startedAt = System.nanoTime()
        val observed = sweep(repoRoot, member)
        report(name, observed, (System.nanoTime() - startedAt) / 1_000_000)

        val baselineFile = TortureBaseline.file(repoRoot, name)
        if (System.getProperty("lunar.corpus.record") == "true") {
            recordBaseline(baselineFile, observed)
        } else {
            TortureBaseline.assertRatchet(baselineFile, observed)
        }
    }

    private fun sweep(repoRoot: File, member: TortureMember): TortureMetrics {
        val root = TortureManifest.checkoutDir(repoRoot, member.name)
        val judged = inputsUnder(root).map { input ->
            judge(repoRoot, member, TortureInput(input.relativeTo(root).path, input.readBytes()))
        }
        return TortureMetrics(
            sha256 = member.sha256,
            files = judged.size,
            parseErrors = judged.count { it.parseErrors > 0 },
            oracleDisagreements = judged.count { it.falseReject },
            oracleFalseAccepts = judged.count { it.falseAccept },
            oracleSites = oracleSites(judged),
            oracleTimeouts = judged.count { it.oracle is ParseOracle.Verdict.NotJudged },
            lexerRoundTripFailures = judged.count { it.roundTripFailed },
            unmergedTokens = judged.sumOf { it.unmergedTokens },
            crashes = judged.mapNotNull { it.crash }.groupingBy { it }.eachCount(),
        )
    }

    /**
     * `onEnter` prunes the `.git` **directory**, which a dot-*file* filter does not: `walkTopDown`
     * would descend into it and sweep `config`, `HEAD` and loose objects, none of which are
     * dot-named. Every remaining file is an input regardless of extension — the corpus is
     * deliberately extensionless, so filtering on `.lua` would sweep nothing at all.
     */
    private fun inputsUnder(root: File): List<File> =
        root.walkTopDown()
            .onEnter { it.name != ".git" }
            .filter { it.isFile && !it.name.startsWith(".") }
            .sortedBy { it.relativeTo(root).path }
            .toList()

    /** One input: its path for reporting, and the **bytes**, which both sides must agree on. */
    private class TortureInput(val path: String, val bytes: ByteArray)

    /**
     * ISO-8859-1, never UTF-8. A fuzz corpus contains invalid UTF-8 by construction, and a lossy
     * decode would substitute U+FFFD — breaking the round-trip invariant at the *decode* rather than
     * at the lexer, and manufacturing failures that look like lexer defects. ISO-8859-1 is total:
     * every byte maps to exactly one character and back.
     *
     * This decode is for **Lunar's** side only. The oracle is handed the raw bytes via
     * `ParseOracle.judgeBytes`, because the general `judge` re-encodes to UTF-8 — which changed 657
     * of the 1 696 inputs, so the two sides were being asked about different bytes.
     */
    private fun decode(input: ByteArray): String = String(input, Charsets.ISO_8859_1)

    private data class Judged(
        val path: String,
        val parseErrors: Int,
        val roundTripFailed: Boolean,
        val unmergedTokens: Int,
        val crash: String?,
        val oracle: ParseOracle.Verdict?,
    ) {
        /**
         * PUC accepts and Lunar does not — the gated direction, per design §2.3. A crashed input is
         * excluded automatically: [oracle] is null whenever [crash] is set.
         */
        val falseReject get() = oracle == ParseOracle.Verdict.Accept && parseErrors > 0

        /**
         * Lunar accepts and PUC does not. Counted, never gated — but **not** for the project
         * corpus's level-superset reason, which does not apply to a single-level fuzz corpus. See
         * [TortureMetrics.oracleFalseAccepts]: what this measures is Lunar's deliberate parser
         * leniency, and it is 364 of 1 696.
         */
        val falseAccept get() = oracle is ParseOracle.Verdict.Reject && parseErrors == 0
    }

    /**
     * A crashed input is excluded from the oracle comparison: it is neither an accept nor a reject,
     * and reporting `parseErrors = 0` for it would score a crash as a false accept.
     */
    private fun judge(repoRoot: File, member: TortureMember, input: TortureInput): Judged {
        val source = decode(input.bytes)
        val lex = LexerInvariants.check(source)
        val parsed = runCatching { parseErrorsIn(source) }
        val crash = lex.crash?.let { "lex:$it" }
            ?: parsed.exceptionOrNull()?.let { "parse:${it::class.java.simpleName}" }
        return Judged(
            path = input.path,
            parseErrors = parsed.getOrDefault(0),
            roundTripFailed = lex.roundTripFailed,
            unmergedTokens = lex.unmergedTokens,
            crash = crash,
            oracle = if (crash != null) null else ParseOracle.judgeBytes(repoRoot, input.bytes, member.luaLevel),
        )
    }

    /**
     * The inputs have no extension, so nothing in the platform would type them as Lua. The synthetic
     * `.lua` name is what selects the language; the on-disk name is irrelevant.
     */
    private fun parseErrorsIn(source: String): Int {
        val psiFile: PsiFile = PsiFileFactory.getInstance(project)
            .createFileFromText("torture-input.lua", LuaFileType, source)
        return PsiTreeUtil.findChildrenOfType(psiFile, PsiErrorElement::class.java).size
    }

    /** Gated direction first, then capped — see `CorpusSweep.oracleSites` for why the order matters. */
    private fun oracleSites(judged: List<Judged>): List<String> {
        val rejects = judged.filter { it.falseReject }.map { "falseReject:${it.path}" }.sorted()
        val accepts = judged.filter { it.falseAccept }.map { "falseAccept:${it.path}" }.sorted()
        return (rejects + accepts).take(ORACLE_SITES_CAP)
    }

    private fun report(name: String, observed: TortureMetrics, elapsedMs: Long) {
        println(
            "[torture:$name] files=${observed.files} parseErrors=${observed.parseErrors} " +
                "elapsedMs=$elapsedMs",
        )
        println(
            "[torture:$name] oracleDisagreements=${observed.oracleDisagreements} " +
                "oracleFalseAccepts=${observed.oracleFalseAccepts} " +
                "oracleTimeouts=${observed.oracleTimeouts} " +
                "lexerRoundTripFailures=${observed.lexerRoundTripFailures} " +
                "unmergedTokens=${observed.unmergedTokens}",
        )
        observed.oracleSites.forEach { println("[torture:$name] oracle site $it") }
        observed.crashes.toSortedMap().forEach { (key, count) -> println("[torture:$name] crash $key=$count") }
    }

    private fun recordBaseline(baselineFile: File, observed: TortureMetrics) {
        baselineFile.parentFile.mkdirs()
        val rendered = TortureBaseline.render(observed)
        baselineFile.writeText(rendered)
        // Echoed as well as written: the suite runs on the remote builder, so the console is the
        // reliable way to get a freshly recorded baseline back into the working tree.
        println("[torture] recorded ${baselineFile.path}:\n$rendered")
    }

    private companion object {
        /** Mirrors `CorpusSweep.ORACLE_SITES_CAP`: the baseline must stay a reviewable diff. */
        const val ORACLE_SITES_CAP = 20
    }
}
