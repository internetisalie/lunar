package net.internetisalie.lunar.rocks.publish

import com.intellij.execution.configurations.GeneralCommandLine
import net.internetisalie.lunar.rocks.LuaRocksEnvironment

/**
 * Pure builder for the `luarocks upload` command (ROCKS-08-04, ROCKS-06-05).
 *
 * The documented luarocks CLI form is `luarocks [--server <url>] upload <file.rockspec> --api-key=<KEY>`.
 * `--server` is a luarocks **global** flag and must appear before the subcommand; it is injected
 * via [LuaRocksEnvironment.withServer] which prepends ["--server", url] before "upload".
 *
 * The key is passed via the single-token `--api-key=` flag so it is never word-split, and
 * `--force` is appended when re-uploading an already-published version.
 *
 * Kept free of IDE/credential/network state so it is exercised headlessly by
 * [RockUploadCommandTest].
 */
object RockUploadCommand {

    /**
     * Assembles the subcommand argument list for `luarocks upload` (without `--server`, which
     * is a global flag handled by [build]).
     */
    fun arguments(
        rockspecPath: String,
        apiKey: String,
        server: String? = null,
    ): List<String> =
        LuaRocksEnvironment.withServer(
            listOf("upload", rockspecPath, "--api-key=$apiKey"),
            server,
        )

    /** Builds the [GeneralCommandLine] using [luarocksBinary] as the `luarocks` binary. */
    fun build(
        luarocksBinary: String,
        rockspecPath: String,
        apiKey: String,
        server: String? = null,
    ): GeneralCommandLine =
        GeneralCommandLine(luarocksBinary)
            .withParameters(arguments(rockspecPath, apiKey, server))
}
