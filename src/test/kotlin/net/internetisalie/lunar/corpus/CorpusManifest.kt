package net.internetisalie.lunar.corpus

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.internetisalie.lunar.lang.LuaLanguageLevel
import java.io.File

/**
 * One pinned third-party project from `tooling/corpus/corpus.json`.
 *
 * [roots] are the subdirectories the sweep indexes — deliberately narrower than the checkout, so
 * that vendored/build trees never enter the measurement.
 */
data class CorpusEntry(
    val name: String,
    val commit: String,
    val roots: List<String>,
    val luaLevel: LuaLanguageLevel,
    /**
     * The subdirectory module names resolve against, when the project puts its Lua tree on
     * `package.path` rather than requiring from the checkout root — KOReader's
     * `require("ui/uimanager")` means `frontend/ui/uimanager.lua`. Null means "resolve from the
     * checkout root", which is what every currently-pinned project does.
     */
    val moduleRoot: String?,
)

/**
 * Reads the corpus manifest. The sweep needs every field but `url` and `prune`, which are
 * `fetch-corpus.py`'s business.
 *
 * BUG-407: this was positional TSV, parsed by hand here *and* in `fetch-corpus.sh`. The two drifted
 * — the shell bound six fields with IFS-whitespace collapsing, so every column after an empty one
 * shifted left and `prune` became the language level, which then fed an `rm -rf`. Both hand-rolled
 * parsers are gone: Gson here (already used in eight production files, platform-provided), stdlib
 * `json` in the fetcher.
 */
object CorpusManifest {
    const val CORPUS_DIR = "test/corpus"

    private const val MANIFEST_PATH = "tooling/corpus/corpus.json"

    fun load(repoRoot: File): List<CorpusEntry> {
        val manifest = File(repoRoot, MANIFEST_PATH)
        require(manifest.isFile) { "No corpus manifest at $MANIFEST_PATH" }
        return JsonParser
            .parseString(manifest.readText())
            .asJsonObject
            .getAsJsonArray("corpora")
            .map { parseEntry(it.asJsonObject) }
    }

    fun entry(
        repoRoot: File,
        name: String,
    ): CorpusEntry {
        val matches = load(repoRoot).filter { it.name == name }
        // Distinguished deliberately: a bare singleOrNull would report a duplicate as absent,
        // sending the reader to fetch-corpus.py for a manifest problem.
        require(matches.size <= 1) { "Duplicate corpus entry '$name' in $MANIFEST_PATH" }
        return matches.singleOrNull()
            ?: error("No corpus entry named '$name' in $MANIFEST_PATH")
    }

    /** The commit actually on disk, stamped by `fetch-corpus.py`; null when the corpus is absent. */
    fun checkedOutCommit(
        repoRoot: File,
        name: String,
    ): String? = File(repoRoot, "$CORPUS_DIR/$name/.corpus-sha").takeIf { it.isFile }?.readText()?.trim()

    /** `<repoRoot>/test/corpus/<name>` — the whole checkout, wider than the indexed [roots]. */
    fun checkoutDir(
        repoRoot: File,
        name: String,
    ): File = File(repoRoot, "$CORPUS_DIR/$name")

    private fun parseEntry(entry: JsonObject): CorpusEntry {
        val name = entry.required("name")
        return CorpusEntry(
            name = name,
            commit = entry.required("commit"),
            roots =
                entry.getAsJsonArray("roots")?.map { it.asString }
                    ?: error("Corpus entry '$name' declares no roots"),
            // Defaults to the same level as LuaProjectSettings, so omitting the key is a no-op.
            luaLevel =
                entry
                    .get("luaLevel")
                    ?.asString
                    ?.takeIf { it.isNotBlank() }
                    ?.let { LuaLanguageLevel.valueOf(it) }
                    ?: LuaLanguageLevel.LUA54,
            moduleRoot =
                entry
                    .get("moduleRoot")
                    ?.asString
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
        )
    }

    private fun JsonObject.required(key: String): String =
        get(key)?.asString?.takeIf { it.isNotBlank() }
            ?: error("Corpus entry is missing required key '$key' in $MANIFEST_PATH")
}
