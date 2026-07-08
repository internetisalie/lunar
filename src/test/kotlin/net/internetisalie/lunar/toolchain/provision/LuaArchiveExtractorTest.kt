package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Network-free tests for [LuaArchiveExtractor] (design §2.7) using committed fixture archives
 * under `src/test/resources/toolchain/`:
 *  - `extract-noexec.zip` — a single non-executable file at the archive root.
 *  - `extract-prefixed.tar.gz` — a `pkg-1.0/` prefix dir containing `bin/tool`.
 *
 * Inherits [BasePlatformTestCase] only to obtain a live platform application for
 * [EmptyProgressIndicator]; it uses no `myFixture` PSI/VFS.
 */
class LuaArchiveExtractorTest : BasePlatformTestCase() {
    private val indicator by lazy { EmptyProgressIndicator() }

    private fun fixture(name: String): Path {
        val resourceUrl = javaClass.classLoader.getResource("toolchain/$name")
            ?: error("Missing test fixture: toolchain/$name")
        return File(resourceUrl.toURI()).toPath()
    }

    fun testZipExtractsAndRestoreExecBitSetsRwxrXrX() {
        if (SystemInfo.isWindows) return
        val target = createTempDirectory("lunar-extract-zip")

        LuaArchiveExtractor.extract(fixture("extract-noexec.zip"), target, null, indicator)

        val extracted = target.resolve("plain.sh")
        assertTrue("zip entry must be extracted", extracted.exists())
        assertEquals("noexec-payload", extracted.readText())
        assertFalse("fixture ships without the exec bit", Files.isExecutable(extracted))

        LuaArchiveExtractor.restoreExecBit(extracted)
        val perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(extracted))
        assertEquals("rwxr-xr-x", perms)
    }

    fun testTarGzRemovePrefixPathStripsTopLevelDir() {
        val target = createTempDirectory("lunar-extract-tar")

        LuaArchiveExtractor.extract(fixture("extract-prefixed.tar.gz"), target, "pkg-1.0", indicator)

        assertFalse("the top-level prefix dir must be stripped", target.resolve("pkg-1.0").exists())
        val tool = target.resolve("bin/tool")
        assertTrue("prefix-stripped content must land at the target root", tool.exists())
        assertEquals("tool-payload", tool.readText())
    }

    fun testUnsupportedArchiveFormatThrows() {
        val target = createTempDirectory("lunar-extract-bad")
        val bogus = target.resolve("artifact.7z")
        Files.writeString(bogus, "not-an-archive")

        val failure = runCatching { LuaArchiveExtractor.extract(bogus, target, null, indicator) }
            .exceptionOrNull()
        assertTrue("unsupported format must raise LuaProvisionException", failure is LuaProvisionException)
    }
}
