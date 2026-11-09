package net.internetisalie.lunar.rocks

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.TestOnly
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
    private val RESOURCES =
        listOf(
            "lua/rockspec.lua",
            "lua/lunar/json.lua",
            "lua/lunar/export.lua",
        )

    @Volatile
    private var extractedDir: Path? = null

    /**
     * Extracts the bridge scripts to a cached temp directory and returns its root. The first call in
     * each IDE run overwrites every script, so an upgraded plugin never runs a previous version's
     * copy left in the temp directory (BUG-50); later calls re-extract only files that went missing.
     */
    @Synchronized
    fun ensureExtracted(): Path {
        val known = extractedDir
        val root = known ?: Path.of(PathManager.getTempPath(), "lunar-rocks", "lua")
        for (resource in RESOURCES) {
            val target = root.resolve(resource.removePrefix("lua/"))
            if (known != null && Files.isRegularFile(target)) continue
            extract(resource, target)
        }
        extractedDir = root
        return root
    }

    private fun extract(
        resource: String,
        target: Path,
    ) {
        Files.createDirectories(target.parent)
        val stream =
            javaClass.classLoader.getResourceAsStream(resource)
                ?: error("Bundled bridge script not found on classpath: $resource")
        stream.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
        log.debug("Extracted bridge script $resource -> $target")
    }

    /** Makes the next [ensureExtracted] behave like the first one of an IDE run. */
    @TestOnly
    fun forgetExtraction() {
        extractedDir = null
    }

    /** Path to the bridge entry point script. */
    fun rockspecScript(): Path = ensureExtracted().resolve("rockspec.lua")

    /** A `LUNAR_LUA_PATH_TEMPLATE` value resolving `require("lunar.*")` against the extracted dir. */
    fun luaPathTemplate(): String {
        val dir = ensureExtracted().toString()
        return "$dir/?/init.lua;$dir/?.lua"
    }
}
