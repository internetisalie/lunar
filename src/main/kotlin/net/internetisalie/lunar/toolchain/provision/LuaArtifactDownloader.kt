package net.internetisalie.lunar.toolchain.provision

import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.HttpRequests
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.moveTo

/**
 * Mirror-aware artifact acquisition with an on-disk cache and mandatory size + SHA-256
 * verification (design §2.6, §3.4).
 *
 * A cached file is re-verified on every use: a size/hash mismatch deletes it and forces a
 * re-download from the next mirror. The mirror list is tried in order; each per-URL failure
 * is recorded and the next mirror attempted. When every mirror fails the accumulated errors
 * are surfaced together via [LuaProvisionException].
 *
 * Runs only on the provisioning orchestrator's background task — it performs blocking I/O and
 * must never be invoked on the EDT. It touches no PSI/VFS, so no read/write action is needed.
 */
class LuaArtifactDownloader(private val cacheDir: Path = defaultCacheDir()) {
    fun fetch(urls: List<String>, sha256: String, size: Long, indicator: ProgressIndicator): Path {
        val key = cacheKey(urls.first())
        val cached = cacheDir.resolve(key)
        if (cached.exists()) {
            if (verifies(cached, sha256, size)) return cached
            cached.deleteIfExists()
        }
        return downloadFromMirrors(urls, key, sha256, size, indicator)
    }

    private fun downloadFromMirrors(
        urls: List<String>,
        key: String,
        sha256: String,
        size: Long,
        indicator: ProgressIndicator,
    ): Path {
        cacheDir.createDirectories()
        val target = cacheDir.resolve(key)
        val failures = mutableListOf<String>()
        for (url in urls) {
            val attempt = attemptDownload(url, target, sha256, size, indicator)
            if (attempt == null) return target
            failures += "$url: $attempt"
        }
        throw LuaProvisionException("All download mirrors failed:\n" + failures.joinToString("\n"))
    }

    /** Returns null on success, or the failure reason for this mirror. */
    private fun attemptDownload(
        url: String,
        target: Path,
        sha256: String,
        size: Long,
        indicator: ProgressIndicator,
    ): String? {
        val tmp = target.resolveSibling(target.fileName.toString() + ".part")
        return try {
            HttpRequests.request(url).productNameAsUserAgent().saveToFile(tmp.toFile(), indicator)
            verifyOrFail(tmp, sha256, size)
            tmp.moveTo(target, overwrite = true)
            null
        } catch (failure: LuaProvisionException) {
            tmp.deleteIfExists()
            failure.message ?: "verification failed"
        } catch (failure: Exception) {
            tmp.deleteIfExists()
            failure.message ?: failure.javaClass.simpleName
        }
    }

    private fun verifyOrFail(file: Path, sha256: String, size: Long) {
        val actualSize = file.fileSize()
        if (actualSize != size) {
            throw LuaProvisionException("size mismatch (expected $size, got $actualSize)")
        }
        val actualHash = sha256Of(file)
        if (!actualHash.equals(sha256, ignoreCase = true)) {
            throw LuaProvisionException("sha256 mismatch (expected $sha256, got $actualHash)")
        }
    }

    private fun verifies(file: Path, sha256: String, size: Long): Boolean =
        file.fileSize() == size && sha256Of(file).equals(sha256, ignoreCase = true)

    private fun sha256Of(file: Path): String =
        Files.asByteSource(file.toFile()).hash(Hashing.sha256()).toString()

    /**
     * Cache key = the URL's last path segment, or its second-to-last when the last segment is
     * the literal `download` (the SourceForge `…/files/{ver}/{group}/{file}/download` pattern).
     */
    private fun cacheKey(url: String): String {
        val segments = url.substringBefore('?').substringBefore('#').trimEnd('/').split('/')
        val last = segments.lastOrNull().orEmpty()
        return if (last == "download" && segments.size >= 2) segments[segments.size - 2] else last
    }

    companion object {
        fun defaultCacheDir(): Path = Path.of(PathManager.getSystemPath(), "lunar", "downloads")
    }
}
