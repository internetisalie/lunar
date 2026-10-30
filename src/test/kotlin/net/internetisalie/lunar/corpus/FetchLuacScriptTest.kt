package net.internetisalie.lunar.corpus

import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MAINT-49-03. Drives the real `tooling/corpus/fetch-luac.py` against `file://` archives built on
 * the fly, so it needs neither the network nor the real pins. The assertions are on what is
 * **installed** (binary and stamp), because the stamp is what makes a later run trust a binary.
 *
 * The class name deliberately contains no `Corpus` (`build.gradle.kts` excludes `*Corpus*`).
 */
class FetchLuacScriptTest {
    private val repoRoot = File(System.getProperty("user.dir"))

    @Test
    fun `installs the luac member and stamps the archive digest`() {
        val work = tempDir()
        val archive = archive(work, "Lua 5.1.5 compiler")
        val luacRoot = File(work, "out")

        val (exit, output) = runFetch(manifest(work, archive, sha256(archive)), luacRoot)

        assertEquals(0, exit, output)
        val installed = File(luacRoot, "5.1.5/luac").readText()
        assertTrue(installed.contains("compiler"), "installed file was not bin/luac: $installed")
        assertEquals(sha256(archive), File(luacRoot, "5.1.5/.luac-sha").readText().trim())
    }

    @Test
    fun `a digest mismatch installs nothing`() {
        val work = tempDir()
        val archive = archive(work, "Lua 5.1.5 compiler")
        val luacRoot = File(work, "out")

        val (exit, output) = runFetch(manifest(work, archive, "0".repeat(64)), luacRoot)

        assertTrue(exit != 0, "a sha256 mismatch must fail the script: $output")
        assertFalse(File(luacRoot, "5.1.5/luac").exists(), "no binary may be installed")
        assertFalse(File(luacRoot, "5.1.5/.luac-sha").exists(), "no stamp may be written")
    }

    @Test
    fun `a luac that reports another version is not stamped`() {
        val work = tempDir()
        val archive = archive(work, "Lua 5.4.8 compiler")
        val luacRoot = File(work, "out")

        val (exit, output) = runFetch(manifest(work, archive, sha256(archive)), luacRoot)

        assertTrue(exit != 0, "a binary reporting the wrong version must fail the script: $output")
        assertFalse(File(luacRoot, "5.1.5/.luac-sha").exists(), "no stamp may be written")
    }

    @Test
    fun `a stamped install is kept without downloading`() {
        val work = tempDir()
        val archive = archive(work, "Lua 5.1.5 compiler")
        val luacRoot = File(work, "out")
        val digest = sha256(archive)
        File(luacRoot, "5.1.5").mkdirs()
        File(luacRoot, "5.1.5/luac").writeText("kept")
        File(luacRoot, "5.1.5/.luac-sha").writeText("$digest\n")
        val missing = File(work, "missing.tar.gz")

        val (exit, output) = runFetch(manifest(work, missing, digest), luacRoot)

        assertEquals(0, exit, output)
        assertEquals("kept", File(luacRoot, "5.1.5/luac").readText())
    }

    @Test
    fun `an archive without bin luac is refused naming the member`() {
        val work = tempDir()
        val archive = archive(work, null)
        val luacRoot = File(work, "out")

        val (exit, output) = runFetch(manifest(work, archive, sha256(archive)), luacRoot)

        assertTrue(exit != 0, "an archive without luac must fail the script: $output")
        assertTrue(
            output.contains("archive lacks lua-5.1.5-linux-x86_64/bin/luac"),
            "the refusal must name the missing member: $output",
        )
    }

    private fun tempDir(): File = createTempDirectory("lunar-fetch-luac").toFile()

    /** Stages `lua-5.1.5-linux-x86_64/{bin/lua,bin/luac,LICENSE}`; `luacText == null` omits `bin/luac`. */
    private fun archive(
        work: File,
        luacText: String?,
    ): File {
        val top = "lua-5.1.5-linux-x86_64"
        val bin = File(work, "stage/$top/bin").apply { mkdirs() }
        File(bin.parentFile, "LICENSE").writeText("license")
        script(File(bin, "lua"), "Lua 5.1.5 interpreter")
        if (luacText != null) script(File(bin, "luac"), luacText)
        val archive = File(work, "$top.tar.gz")
        val tar = ProcessBuilder("tar", "-czf", archive.path, "-C", File(work, "stage").path, top)
        val exit = tar.redirectErrorStream(true).start().waitFor()
        assertEquals(0, exit, "tar failed")
        return archive
    }

    private fun script(
        target: File,
        text: String,
    ) {
        target.writeText("#!/bin/sh\necho \"$text\"\n")
        target.setExecutable(true)
    }

    private fun manifest(
        work: File,
        archive: File,
        sha256: String,
    ): File {
        val manifest = File(work, "luac.json")
        manifest.writeText(
            """{"builds":[{"level":"LUA51","version":"5.1.5",""" +
                """"url":"${archive.toURI()}","sha256":"$sha256"}]}""",
        )
        return manifest
    }

    private fun runFetch(
        manifest: File,
        luacRoot: File,
    ): Pair<Int, String> {
        val script = File(repoRoot, "tooling/corpus/fetch-luac.py")
        val builder = ProcessBuilder("python3", script.path).redirectErrorStream(true)
        builder.environment()["LUNAR_LUAC_MANIFEST"] = manifest.path
        builder.environment()["LUNAR_LUAC_ROOT"] = luacRoot.path
        val process = builder.start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        return process.waitFor() to output
    }

    private fun sha256(file: File): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
}
