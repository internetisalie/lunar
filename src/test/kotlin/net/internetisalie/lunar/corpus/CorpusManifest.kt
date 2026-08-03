package net.internetisalie.lunar.corpus

import net.internetisalie.lunar.lang.LuaLanguageLevel
import java.io.File

/**
 * One pinned third-party project from `tooling/corpus/corpus.tsv`.
 *
 * [roots] are the subdirectories the sweep indexes — deliberately narrower than the checkout, so
 * that vendored/build trees never enter the measurement.
 */
data class CorpusEntry(
    val name: String,
    val commit: String,
    val roots: List<String>,
    val luaLevel: LuaLanguageLevel,
)

/**
 * Reads the corpus manifest. Columns are name, url, commit, roots, prune; the sweep needs only
 * name/commit/roots — the url and prune columns are `fetch-corpus.sh`'s business.
 */
object CorpusManifest {

    const val CORPUS_DIR = "test/corpus"

    fun load(repoRoot: File): List<CorpusEntry> =
        File(repoRoot, "tooling/corpus/corpus.tsv")
            .readLines()
            .filterNot { it.isBlank() || it.startsWith("#") }
            .map(::parseRow)

    fun entry(repoRoot: File, name: String): CorpusEntry {
        val matches = load(repoRoot).filter { it.name == name }
        // Distinguished deliberately: a bare singleOrNull would report a duplicate as absent,
        // sending the reader to fetch-corpus.sh for a manifest problem.
        require(matches.size <= 1) { "Duplicate corpus entry '$name' in tooling/corpus/corpus.tsv" }
        return matches.singleOrNull()
            ?: error("No corpus entry named '$name' in tooling/corpus/corpus.tsv")
    }

    /** The commit actually on disk, stamped by `fetch-corpus.sh`; null when the corpus is absent. */
    fun checkedOutCommit(repoRoot: File, name: String): String? =
        File(repoRoot, "$CORPUS_DIR/$name/.corpus-sha").takeIf { it.isFile }?.readText()?.trim()

    /** `<repoRoot>/test/corpus/<name>` — the whole checkout, wider than the indexed [roots]. */
    fun checkoutDir(repoRoot: File, name: String): File = File(repoRoot, "$CORPUS_DIR/$name")

    private fun parseRow(row: String): CorpusEntry {
        val columns = row.split('\t')
        require(columns.size >= 4) { "Malformed corpus.tsv row: $row" }
        val level = columns.getOrNull(5)?.trim().orEmpty()
        return CorpusEntry(
            name = columns[0],
            commit = columns[2],
            roots = columns[3].split(',').filter { it.isNotBlank() },
            // Defaults to the same level as LuaProjectSettings, so omitting the column is a no-op.
            luaLevel = if (level.isEmpty()) LuaLanguageLevel.LUA54 else LuaLanguageLevel.valueOf(level),
        )
    }
}
