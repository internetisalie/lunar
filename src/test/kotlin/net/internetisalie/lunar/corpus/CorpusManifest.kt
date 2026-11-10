package net.internetisalie.lunar.corpus

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
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
     * checkout root", which is what most pinned projects do.
     */
    val moduleRoot: String?,
    /** Catalog ids from `lunar-definitions-catalog.json` the sweep enables for this member (MAINT-37). */
    val definitionLibraries: List<String> = emptyList(),
    /** The member's non-Standard target (`"<LuaPlatform>:<version label>"`); null pins Standard [luaLevel]. */
    val target: Target? = null,
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

    const val MANIFEST_PATH = "tooling/corpus/corpus.json"

    /** MAINT-56's representative type corpus: same entry shape, audited rather than swept. */
    const val TYPE_MANIFEST_PATH = "tooling/corpus/type-corpus.json"

    fun load(
        repoRoot: File,
        manifestPath: String = MANIFEST_PATH,
    ): List<CorpusEntry> {
        val manifest = File(repoRoot, manifestPath)
        require(manifest.isFile) { "No corpus manifest at $manifestPath" }
        return JsonParser
            .parseString(manifest.readText())
            .asJsonObject
            .getAsJsonArray("corpora")
            .map { parseEntry(it.asJsonObject, manifestPath) }
    }

    fun entry(
        repoRoot: File,
        name: String,
        manifestPath: String = MANIFEST_PATH,
    ): CorpusEntry {
        val matches = load(repoRoot, manifestPath).filter { it.name == name }
        // Distinguished deliberately: a bare singleOrNull would report a duplicate as absent,
        // sending the reader to fetch-corpus.py for a manifest problem.
        require(matches.size <= 1) { "Duplicate corpus entry '$name' in $manifestPath" }
        return matches.singleOrNull()
            ?: error("No corpus entry named '$name' in $manifestPath")
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

    private fun parseEntry(
        entry: JsonObject,
        manifestPath: String,
    ): CorpusEntry {
        val name = entry.required("name", manifestPath)
        return CorpusEntry(
            name = name,
            commit = entry.required("commit", manifestPath),
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
            definitionLibraries = entry.distinctIds("definitionLibraries"),
        ).let { parsed -> parsed.copy(target = entry.target(parsed, manifestPath)) }
    }

    /** Absent means Standard at `luaLevel`; a target whose implicit level differs is refused. */
    private fun JsonObject.target(
        parsed: CorpusEntry,
        manifestPath: String,
    ): Target? {
        val spec = get("target")?.asString?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val platform = LuaPlatform.valueOf(spec.substringBefore(':'))
        val version =
            requireNotNull(PlatformVersionRegistry.findVersion(platform, spec.substringAfter(':', ""))) {
                "Corpus entry '${parsed.name}' names unknown target '$spec' in $manifestPath"
            }
        val target = Target(platform, version)
        require(target.getImplicitLanguageLevel() == parsed.luaLevel) {
            "Corpus entry '${parsed.name}': target '$spec' implies ${target.getImplicitLanguageLevel().name}, not ${parsed.luaLevel.name}"
        }
        return target
    }

    /**
     * Absent means none; blanks are dropped and duplicates collapse, first occurrence kept. Ids are
     * not checked against the catalog here: [CorpusDefinitionLibraries.resolve] refuses unknown ones.
     */
    private fun JsonObject.distinctIds(key: String): List<String> =
        getAsJsonArray(key)
            ?.map { it.asString.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?: emptyList()

    private fun JsonObject.required(
        key: String,
        manifestPath: String,
    ): String =
        get(key)?.asString?.takeIf { it.isNotBlank() }
            ?: error("Corpus entry is missing required key '$key' in $manifestPath")
}
