package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.run.LuaDebugValueParser
import net.internetisalie.lunar.run.LuaTable
import net.internetisalie.lunar.run.LuaValueKind

/**
 * Reads the browser's metadata from a rockspec's text without running it (BUG-58).
 *
 * A rockspec fetched from a rocks server is untrusted Lua, so it goes through
 * [LuaDebugValueParser], which evaluates literal assignments over the syntax tree and executes
 * nothing. The rockspec's globals are gathered by a trailing `return` of a table naming them.
 */
internal object RockspecMetadataReader {
    private const val GATHER =
        "\nreturn { package = package, version = version, description = description, " +
            "dependencies = dependencies, build = build }"

    /** The metadata [text] declares, or null when it names no `package` and `version`. */
    fun read(
        project: Project,
        text: String,
    ): LuaRockMetadata? {
        val spec = LuaDebugValueParser.parseChunk(project, "do\n$text$GATHER\nend")
        val name = spec.string("package") ?: return null
        val version = spec.string("version") ?: return null
        val description = spec.table("description")
        return LuaRockMetadata(
            name = name,
            version = version,
            summary = description?.string("summary"),
            detailed = description?.string("detailed"),
            license = description?.string("license"),
            homepage = description?.string("homepage"),
            issues = description?.string("issues_url"),
            location = null,
            dependencies =
                spec
                    .table("dependencies")
                    ?.indexed
                    .orEmpty()
                    .mapNotNull { it.stringValue },
            modules =
                spec
                    .table("build")
                    ?.table("modules")
                    ?.named
                    ?.keys
                    .orEmpty()
                    .mapNotNull { it.stringValue },
        )
    }

    private fun LuaTable.string(name: String): String? =
        getByName(name)?.second?.takeIf { it.kind == LuaValueKind.String }?.stringValue

    private fun LuaTable.table(name: String): LuaTable? = getByName(name)?.second?.tableValue
}
