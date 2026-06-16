package net.internetisalie.lunar.rocks.publish

import com.intellij.execution.configurations.GeneralCommandLine

/**
 * Pure builder for the `luarocks upload` command (ROCKS-08-04).
 *
 * The documented luarocks CLI form is `luarocks upload <file.rockspec> --api-key=<KEY>`.
 * The key is passed via the single-token `--api-key=` flag so it is never word-split, and
 * `--force` is appended when re-uploading an already-published version.
 *
 * Kept free of IDE/credential/network state so it is exercised headlessly by
 * [RockUploadCommandTest].
 */
object RockUploadCommand {
    /** Assembles the argument list for `luarocks upload`. */
    fun arguments(rockspecPath: String, apiKey: String, force: Boolean = false): List<String> =
        buildList {
            add("upload")
            add(rockspecPath)
            add("--api-key=$apiKey")
            if (force) add("--force")
        }

    /** Builds the [GeneralCommandLine] using [executablePath] as the `luarocks` binary. */
    fun build(
        executablePath: String,
        rockspecPath: String,
        apiKey: String,
        force: Boolean = false,
    ): GeneralCommandLine =
        GeneralCommandLine(executablePath)
            .withParameters(arguments(rockspecPath, apiKey, force))
}
