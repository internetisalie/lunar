package net.internetisalie.lunar.corpus

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

/**
 * MAINT-40-01. One hit of a defect counter the corpus baseline carries, at the location it was
 * observed. The baseline's counters are derived from a list of these ([CorpusFindingCounts]).
 *
 * A file-level finding has offsets `-1` and line/column `0`. [count] is `1` except for
 * [CorpusRules.UNMERGED_TOKENS], which carries the file's token count in one finding.
 */
data class CorpusFinding(
    val rule: String,
    val path: String,
    val startOffset: Int,
    val endOffset: Int,
    val line: Int,
    val column: Int,
    val message: String,
    val symbol: String? = null,
    val count: Int = 1,
)

/** Where findings for one swept file are made: its member-root-relative path and its document. */
data class FindingSite(
    val path: String,
    val document: Document?,
) {
    /** A finding at [range]. Line and column are 1-based, or `0` when there is no document. */
    fun finding(
        rule: String,
        range: TextRange,
        message: String,
    ): CorpusFinding {
        val start = range.startOffset
        val line = document?.getLineNumber(start)?.plus(1) ?: 0
        val column = document?.let { start - it.getLineStartOffset(line - 1) + 1 } ?: 0
        return CorpusFinding(rule, path, start, range.endOffset, line, column, message)
    }

    /** A finding about the whole file: offsets `-1`, line and column `0`. */
    fun fileFinding(
        rule: String,
        message: String,
        count: Int = 1,
    ): CorpusFinding = CorpusFinding(rule, path, -1, -1, 0, 0, message, count = count)
}

/** The rule ids findings carry. Inspection hits use [inspection], which shares the baseline's key prefix. */
object CorpusRules {
    const val PARSE_ERROR = "corpus.parseError"
    const val INVALID_STATEMENT = "corpus.invalidStatement"
    const val UNRESOLVED_REQUIRE = "corpus.unresolvedRequire"
    const val CRASH = "corpus.crash"
    const val LEXER_ROUND_TRIP = "corpus.lexerRoundTrip"
    const val UNMERGED_TOKENS = "corpus.unmergedTokens"
    const val ORACLE_FALSE_REJECT = "corpus.oracleFalseReject"
    const val ORACLE_FALSE_ACCEPT = "corpus.oracleFalseAccept"
    const val ORACLE_TIMEOUT = "corpus.oracleTimeout"
    const val HIGHLIGHT_FAILURE = "corpus.highlightFailure"
    const val UNATTRIBUTED_HIGHLIGHT = "corpus.unattributedHighlight"

    fun inspection(toolId: String): String = "${CorpusBaseline.INSPECTION_PREFIX}$toolId"
}
