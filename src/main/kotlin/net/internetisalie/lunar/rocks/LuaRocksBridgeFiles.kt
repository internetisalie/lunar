package net.internetisalie.lunar.rocks

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Makes the bundled Lua bridge scripts available as real filesystem paths at runtime.
 *
 * The scripts ship as classpath resources under `lua/` (packaged from `src/main/resources/lua/`).
 * Lua's `dofile`/`require` need files on disk rather than classpath URLs, so the scripts are
 * extracted once into a cached temp directory under [PathManager.getTempPath]. This works
 * identically in the dev sandbox and a packaged plugin jar — the extraction reads from the
 * classpath either way.
 */
object LuaRocksBridgeFiles {
    private val log = logger<LuaRocksBridgeFiles>()

    /** Classpath-relative resource names, mirrored to the extraction directory. */
    private val RESOURCES = listOf(
        "lua/rockspec.lua",
        "lua/lunar/json.lua",
        "lua/lunar/export.lua",
    )

    @Volatile
    private var extractedDir: Path? = null

    /**
     * Extracts the bridge scripts (once) to a cached temp directory and returns its root. Re-extracts
     * any missing files (e.g. if the temp dir was cleaned) but is otherwise idempotent.
     */
    @Synchronized
    fun ensureExtracted(): Path {
        val root = extractedDir
            ?: Path.of(PathManager.getTempPath(), "lunar-rocks", "lua").also { extractedDir = it }
        for (resource in RESOURCES) {
            val target = root.resolve(resource.removePrefix("lua/"))
            if (Files.isRegularFile(target)) continue
            Files.createDirectories(target.parent)
            val stream = javaClass.classLoader.getResourceAsStream(resource)
                ?: error("Bundled bridge script not found on classpath: $resource")
            stream.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
            log.debug("Extracted bridge script $resource -> $target")
        }
        return root
    }

    /** Path to the bridge entry point script. */
    fun rockspecScript(): Path = ensureExtracted().resolve("rockspec.lua")

    /** A `LUNAR_LUA_PATH_TEMPLATE` value resolving `require("lunar.*")` against the extracted dir. */
    fun luaPathTemplate(): String {
        val dir = ensureExtracted().toString()
        return "$dir/?/init.lua;$dir/?.lua"
    }
}
