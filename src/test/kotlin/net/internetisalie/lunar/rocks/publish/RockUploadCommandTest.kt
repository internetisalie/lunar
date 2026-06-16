package net.internetisalie.lunar.rocks.publish

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** TC-ROCKS-08-04: `luarocks upload` command-line assembly (design §4). */
class RockUploadCommandTest {
    @Test
    fun basicUploadArguments() {
        assertEquals(
            listOf("upload", "app-1.rockspec", "--api-key=SECRET"),
            RockUploadCommand.arguments("app-1.rockspec", "SECRET"),
        )
    }

    @Test
    fun forceAppendsFlag() {
        assertEquals(
            listOf("upload", "app-1.rockspec", "--api-key=SECRET", "--force"),
            RockUploadCommand.arguments("app-1.rockspec", "SECRET", force = true),
        )
    }

    @Test
    fun apiKeyIsASingleToken() {
        // The key rides the `--api-key=` flag as one token so it is never word-split.
        val args = RockUploadCommand.arguments("a.rockspec", "key with spaces")
        assertEquals("--api-key=key with spaces", args.last())
    }

    @Test
    fun buildUsesExecutableAsExePath() {
        val command = RockUploadCommand.build("/usr/bin/luarocks", "a.rockspec", "K")
        assertEquals("/usr/bin/luarocks", command.exePath)
        assertEquals(
            listOf("upload", "a.rockspec", "--api-key=K"),
            command.parametersList.list,
        )
    }
}
