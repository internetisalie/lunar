package net.internetisalie.lunar.rocks.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/** TC-BUG-58: the server rockspec is fetched by `luarocks download --rockspec`, the latest when no version. */
class RockspecDownloadArgsTest {
    @Test
    fun `a blank version downloads the latest`() {
        assertEquals(
            listOf("download", "--rockspec", "argparse"),
            LuaRocksMetadataService.rockspecDownloadArgs("argparse", ""),
        )
    }

    @Test
    fun `a version is passed through`() {
        assertEquals(
            listOf("download", "--rockspec", "argparse", "0.7.1-1"),
            LuaRocksMetadataService.rockspecDownloadArgs("argparse", "0.7.1-1"),
        )
    }
}
