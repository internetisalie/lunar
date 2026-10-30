package net.internetisalie.lunar.corpus

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * MAINT-40-02, TC 2: every counter the baseline carries is derived from the findings list by
 * [CorpusFindingCounts.derive].
 *
 * Deliberately **not** named `*Corpus*`, class or method: it needs no fixture and no fetched
 * corpus, so it belongs in the routine suite (BUG-497; `SweepNamingGuardTest` enforces it).
 */
@RunWith(JUnit4::class)
class SweepFindingCountsTest {
    @Test
    fun deriveComputesEveryCounterFromTheFindings() {
        val derived = CorpusFindingCounts.derive(tc2Findings())
        assertEquals(expectedMetrics(), derived)
    }

    @Test
    fun theFourScalarCountersAreNotInterchangeable() {
        val derived = CorpusFindingCounts.derive(tc2Findings())
        assertEquals(
            listOf(4, 1, 2, 3),
            listOf(
                derived.unresolvedRequires,
                derived.oracleFalseAccepts,
                derived.oracleTimeouts,
                derived.lexerRoundTripFailures,
            ),
        )
    }

    private fun expectedMetrics(): CorpusMetrics =
        CorpusMetrics(
            commit = "",
            files = 0,
            parseErrors = 3,
            requires = 0,
            unresolvedRequires = 4,
            parseErrorFiles = listOf("a.lua:3:e1", "b.lua:1:e2", "b.lua:9:e3"),
            inspectionHits =
                mapOf(
                    "LuaUnusedLocal" to 2,
                    CorpusMetrics.UNATTRIBUTED to 2,
                    CorpusMetrics.HIGHLIGHT_FAILURES to 1,
                ),
            symbolHits = mapOf("LuaUnusedLocal.x" to 2),
            oracleDisagreements = 2,
            oracleFalseAccepts = 1,
            oracleSites = listOf("falseReject:c.lua", "falseReject:d.lua", "falseAccept:e.lua"),
            oracleTimeouts = 2,
            lexerRoundTripFailures = 3,
            unmergedTokens = 9,
            crashes = mapOf("parse:IllegalStateException" to 2, "lex:StackOverflowError" to 1),
            allSymbolHits = mapOf("LuaUnusedLocal.x" to 2),
            findings = tc2Findings(),
        )

    /** The TC 2 fixture: files a–f of requirements.md. */
    private fun tc2Findings(): List<CorpusFinding> = fileA() + fileB() + fileC() + fileD() + fileE() + fileF()

    private fun fileA(): List<CorpusFinding> {
        val a = FindingSite("a.lua", null)
        return listOf(at("a.lua", CorpusRules.PARSE_ERROR, 3).copy(message = "e1")) +
            List(3) { at("a.lua", CorpusRules.UNATTRIBUTED_HIGHLIGHT) } +
            List(2) { at("a.lua", CorpusRules.inspection("LuaUnusedLocal")).copy(symbol = "x") } +
            a.fileFinding(CorpusRules.CRASH, "parse:IllegalStateException")
    }

    private fun fileB(): List<CorpusFinding> {
        val b = FindingSite("b.lua", null)
        return listOf(
            at("b.lua", CorpusRules.PARSE_ERROR, 1).copy(message = "e2"),
            at("b.lua", CorpusRules.PARSE_ERROR, 9).copy(message = "e3"),
            b.fileFinding(CorpusRules.CRASH, "parse:IllegalStateException"),
            b.fileFinding(CorpusRules.CRASH, "lex:StackOverflowError"),
            b.fileFinding(CorpusRules.ORACLE_TIMEOUT, CorpusRules.ORACLE_TIMEOUT),
        )
    }

    private fun fileC(): List<CorpusFinding> {
        val c = FindingSite("c.lua", null)
        return listOf(
            c.fileFinding(CorpusRules.UNMERGED_TOKENS, CorpusRules.UNMERGED_TOKENS, count = 7),
            c.fileFinding(CorpusRules.LEXER_ROUND_TRIP, CorpusRules.LEXER_ROUND_TRIP),
            c.fileFinding(CorpusRules.ORACLE_FALSE_REJECT, CorpusRules.ORACLE_FALSE_REJECT),
            c.fileFinding(CorpusRules.HIGHLIGHT_FAILURE, CorpusRules.HIGHLIGHT_FAILURE),
        )
    }

    private fun fileD(): List<CorpusFinding> {
        val d = FindingSite("d.lua", null)
        return listOf(
            d.fileFinding(CorpusRules.UNMERGED_TOKENS, CorpusRules.UNMERGED_TOKENS, count = 2),
            d.fileFinding(CorpusRules.ORACLE_FALSE_REJECT, CorpusRules.ORACLE_FALSE_REJECT),
            d.fileFinding(CorpusRules.LEXER_ROUND_TRIP, CorpusRules.LEXER_ROUND_TRIP),
        ) +
            listOf(
                "\"m\"",
                "\"n\"",
                "\"o\"",
                "\"p\"",
            ).map { at("d.lua", CorpusRules.UNRESOLVED_REQUIRE).copy(symbol = it) }
    }

    private fun fileE(): List<CorpusFinding> =
        listOf(FindingSite("e.lua", null).fileFinding(CorpusRules.ORACLE_FALSE_ACCEPT, CorpusRules.ORACLE_FALSE_ACCEPT))

    private fun fileF(): List<CorpusFinding> {
        val f = FindingSite("f.lua", null)
        return listOf(
            f.fileFinding(CorpusRules.ORACLE_TIMEOUT, CorpusRules.ORACLE_TIMEOUT),
            f.fileFinding(CorpusRules.LEXER_ROUND_TRIP, CorpusRules.LEXER_ROUND_TRIP),
        )
    }

    /** A located finding with offsets 0, column 0 and the rule as its message. */
    private fun at(
        path: String,
        rule: String,
        line: Int = 0,
    ): CorpusFinding = CorpusFinding(rule, path, 0, 0, line, 0, rule)
}
