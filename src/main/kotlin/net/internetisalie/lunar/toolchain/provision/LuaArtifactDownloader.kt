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
    /** Bundles the verification inputs for a single artifact so helpers stay under the 3-arg cap. */
    private class FetchPlan(
        val urls: List<String>,
        val cacheKey: String,
        val sha256: String,
        val size: Long,
    )

    fun fetch(urls: List<String>, sha256: String, size: Long, indicator: ProgressIndicator): Path {
        val plan = FetchPlan(urls, cacheKey(urls.first()), sha256, size)
        val cached = cacheDir.resolve(plan.cacheKey)
        if (cached.exists()) {
            if (verifies(cached, plan.sha256, plan.size)) return cached
            cached.deleteIfExists()
        }
        return downloadFromMirrors(plan, indicator)
    }

    private fun downloadFromMirrors(plan: FetchPlan, indicator: ProgressIndicator): Path {
        cacheDir.createDirectories()
        val target = cacheDir.resolve(plan.cacheKey)
        val failures = mutableListOf<String>()
        for (url in plan.urls) {
            val attempt = attemptDownload(url, plan, indicator)
            if (attempt == null) return target
            failures += "$url: $attempt"
        }
        throw LuaProvisionException("All download mirrors failed:\n" + failures.joinToString("\n"))
    }

    /** Returns null on success, or the failure reason for this mirror. */
    private fun attemptDownload(url: String, plan: FetchPlan, indicator: ProgressIndicator): String? {
        val target = cacheDir.resolve(plan.cacheKey)
        val tmp = target.resolveSibling(target.fileName.toString() + ".part")
        return try {
            HttpRequests.request(url).productNameAsUserAgent().saveToFile(tmp.toFile(), indicator)
            verifyOrFail(tmp, plan.sha256, plan.size)
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
