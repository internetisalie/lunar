package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.Decompressor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/**
 * Extracts a downloaded archive into a target directory (design §2.7).
 *
 * `*.zip` uses `Decompressor.Zip(...).withZipExtensions()`; `*.tar.gz`/`*.tgz` uses
 * `Decompressor.Tar(...)`. A non-null [rootPrefix] strips the archive's top-level directory,
 * and the cancellable entry filter honours user cancellation on every entry.
 *
 * Packaging `"binary"` is NOT handled here — that plain-copy path lives in the release-binary
 * strategy (design §2.7); this extractor only decompresses zip/tar.gz archives.
 *
 * Runs only on the provisioning orchestrator's background task (blocking I/O; never the EDT).
 */
object LuaArchiveExtractor {
    fun extract(archive: Path, targetDir: Path, rootPrefix: String?, indicator: ProgressIndicator) {
        val decompressor = decompressorFor(archive)
        if (rootPrefix != null) decompressor.removePrefixPath(rootPrefix)
        decompressor.entryFilter {
            indicator.checkCanceled()
            true
        }
        decompressor.extract(targetDir)
    }

    fun restoreExecBit(file: Path) {
        if (SystemInfo.isWindows) return
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    private fun decompressorFor(archive: Path): Decompressor {
        val name = archive.fileName.toString().lowercase()
        return when {
            name.endsWith(".zip") -> Decompressor.Zip(archive).withZipExtensions()
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> Decompressor.Tar(archive)
            else -> throw LuaProvisionException("Unsupported archive format: ${archive.fileName}")
        }
    }
}
