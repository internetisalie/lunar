package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * TC-BUG-58: a rockspec downloaded for a rock that is not installed is read for the detail pane
 * from its syntax tree, without running it.
 */
class RockspecMetadataReaderTest : BasePlatformTestCase() {
    fun `test reads a server rockspec`() {
        val meta = read(ARGPARSE)

        assertNotNull(meta)
        assertEquals("argparse", meta?.name)
        assertEquals("0.7.2-1", meta?.version)
        assertEquals("A feature-rich command-line argument parser", meta?.summary)
        assertEquals("MIT", meta?.license)
        assertEquals("https://github.com/luarocks/argparse", meta?.homepage)
        assertEquals(listOf("lua >= 5.1, < 5.6"), meta?.dependencies)
        assertEquals(listOf("argparse"), meta?.modules)
    }

    fun `test a long-bracket description is read as text`() {
        val meta = read("package = \"x\"\nversion = \"1.0-1\"\ndescription = { detailed = [[\nTwo\nlines]] }")

        assertEquals("Two\nlines", meta?.detailed)
    }

    fun `test a rockspec that calls functions is read without running them`() {
        val meta =
            read(
                "os.execute(\"touch pwned\")\npackage = \"x\"\nversion = \"1.0-1\"\n" +
                    "description = { summary = io.open(\"f\"):read(\"a\") }",
            )

        assertEquals("x", meta?.name)
        assertNull(meta?.summary)
    }

    fun `test text without a package and version is not a rockspec`() {
        assertNull(read("description = { summary = \"orphan\" }"))
    }

    private fun read(text: String): LuaRockMetadata? =
        runReadActionBlocking { RockspecMetadataReader.read(project, text) }

    private companion object {
        val ARGPARSE =
            """
            package = "argparse"
            version = "0.7.2-1"
            source = {
               url = "https://github.com/luarocks/argparse/archive/0.7.2.zip",
               dir = "argparse-0.7.2"
            }
            description = {
               summary = "A feature-rich command-line argument parser",
               detailed = "Argparse supports positional arguments, options, flags and subcommands.",
               homepage = "https://github.com/luarocks/argparse",
               license = "MIT"
            }
            dependencies = {
               "lua >= 5.1, < 5.6"
            }
            build = {
               type = "builtin",
               modules = {
                  argparse = "src/argparse.lua"
               }
            }
            """.trimIndent()
    }
}
