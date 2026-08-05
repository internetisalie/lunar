package net.internetisalie.lunar.definitions

/**
 * The bundled catalog of community LuaCATS / LuaLS definition libraries (TARGET-08-01).
 *
 * The plugin ships this metadata only — never any community `.lua` file. Each entry names where a
 * definition tree can be fetched from and how to verify it; the tree itself is downloaded on demand
 * and cached per user.
 *
 * [catalogVersion] exists so a future format change is a detectable error rather than a
 * misparse, mirroring `LuaToolchainFeed.feedVersion`.
 */
data class LuaDefinitionCatalog(
    val catalogVersion: Int,
    val libraries: List<LuaDefinitionEntry>,
) {
    /** The entry with [id], or null. Ids are unique — [LuaDefinitionCatalogLoader] rejects duplicates. */
    fun entry(id: String): LuaDefinitionEntry? = libraries.firstOrNull { it.id == id }

    /**
     * [ids] plus everything they [LuaDefinitionEntry.requires], transitively.
     *
     * Enabling a library must pull its dependencies in or it resolves half-way: `busted`'s
     * definitions open with `assert = require("luassert")`, so busted alone leaves `assert`,
     * `spy`, `stub` and `mock` unresolved. Unknown ids are ignored rather than raised — the enable
     * list lives in `lunar.xml` and may name a library this plugin build no longer carries.
     */
    fun withDependencies(ids: Collection<String>): List<LuaDefinitionEntry> {
        val resolved = LinkedHashMap<String, LuaDefinitionEntry>()
        val pending = ArrayDeque(ids)
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (resolved.containsKey(next)) continue
            val entry = entry(next) ?: continue
            resolved[next] = entry
            pending.addAll(entry.requires)
        }
        return resolved.values.toList()
    }
}

/**
 * One definition library, pinned to exactly one immutable revision.
 *
 * [version] carries the upstream **commit SHA**, not a semver: neither the LuaCATS org repos nor
 * `LuaLS/LLS-Addons` publish a single tag (DR-01), so a commit is the only reproducible pin
 * available — the same choice `tooling/corpus/corpus.json` makes for the corpus.
 *
 * [rootPrefix] is the path *to register* inside the extracted archive, not merely a wrapper dir to
 * strip. Addons put their `.lua` definitions in a `<repo>-<sha>/library` directory beside a
 * `config.json`, and registering the archive root would index that `config.json` as project content.
 *
 * [requires] names other catalog ids this library needs; see [LuaDefinitionCatalog.withDependencies].
 */
data class LuaDefinitionEntry(
    val id: String,
    val displayName: String,
    val version: String,
    val urls: List<String>,
    val sha256: String,
    val size: Long,
    val rootPrefix: String,
    val license: String,
    val attributionUrl: String,
    val requires: List<String>,
)
