package net.internetisalie.lunar.toolchain.provision

import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.HttpRequests
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.moveTo

/**
 * How a downloaded artifact's size + SHA-256 pin is enforced.
 *
 * [STRICT] is the default and the only correct stance for anything that will be **executed** —
 * the Lua/LuaRocks binaries and source tarballs TOOLING provisions.
 *
 * [ADVISORY] logs a mismatch and keeps the file. Its safety argument is a property of the
 * **caller**, not of this class, and holds only where all three of these are true: the pinned URL
 * already identifies immutable content by other means, the artifact is never executed, and
 * transport is authenticated. TARGET-08's definition libraries satisfy all three — the URL embeds
 * an upstream commit SHA (itself a content hash), the payload is `---@meta` stubs that are only
 * indexed, and fetches are HTTPS — and `LuaDefinitionCatalogLoader` *enforces* the first by
 * rejecting a catalog entry whose URLs do not contain its pinned version. A new ADVISORY caller
 * must establish the same three properties; do not assume this comment covers it.
 */
enum class ArtifactVerification { STRICT, ADVISORY }

/**
 * What identifies and verifies one downloadable artifact: its ordered mirror list and its size +
 * SHA-256 pin. A value object so [LuaArtifactDownloader.fetch] stays inside the 3-argument cap.
 */
data class ArtifactPin(
    val urls: List<String>,
    val sha256: String,
    val size: Long,
)

/**
 * Mirror-aware artifact acquisition with an on-disk cache and size + SHA-256 verification
 * (design §2.6, §3.4).
 *
 * Under [ArtifactVerification.STRICT] a cached file is re-verified on every use: a size/hash
 * mismatch deletes it and forces a re-download from the next mirror. The mirror list is tried in
 * order; each per-URL failure is recorded and the next mirror attempted. When every mirror fails
 * the accumulated errors are surfaced together via [LuaProvisionException].
 *
 * Runs only on the provisioning orchestrator's background task — it performs blocking I/O and
 * must never be invoked on the EDT. It touches no PSI/VFS, so no read/write action is needed.
 */
class LuaArtifactDownloader(
    private val cacheDir: Path = defaultCacheDir(),
) {
    /** Bundles the verification inputs for a single artifact so helpers stay under the 3-arg cap. */
    private class FetchPlan(
        val pin: ArtifactPin,
        val cacheKey: String,
        val verification: ArtifactVerification,
    )

    fun fetch(
        pin: ArtifactPin,
        indicator: ProgressIndicator,
        verification: ArtifactVerification = ArtifactVerification.STRICT,
    ): Path {
        val plan = FetchPlan(pin, cacheKey(pin.urls.first()), verification)
        val cached = cacheDir.resolve(plan.cacheKey)
        if (cached.exists()) {
            // Re-verified even under ADVISORY: it costs one hash, and skipping it would log the
            // mismatch warning exactly once ever and then stay silent for the file's whole life.
            // A mismatch is only *kept* rather than fatal — see [ArtifactVerification].
            if (verifies(cached, plan.pin)) return cached
            if (plan.verification == ArtifactVerification.ADVISORY) {
                warnPinMismatch(plan)
                return cached
            }
            cached.deleteIfExists()
        }
        return downloadFromMirrors(plan, indicator)
    }

    private fun downloadFromMirrors(
        plan: FetchPlan,
        indicator: ProgressIndicator,
    ): Path {
        cacheDir.createDirectories()
        val target = cacheDir.resolve(plan.cacheKey)
        val failures = mutableListOf<String>()
        for (url in plan.pin.urls) {
            val attempt = attemptDownload(url, plan, indicator)
            if (attempt == null) return target
            failures += "$url: $attempt"
        }
        throw LuaProvisionException("All download mirrors failed:\n" + failures.joinToString("\n"))
    }

    /** Returns null on success, or the failure reason for this mirror. */
    private fun attemptDownload(
        url: String,
        plan: FetchPlan,
        indicator: ProgressIndicator,
    ): String? {
        val target = cacheDir.resolve(plan.cacheKey)
        val tmp = target.resolveSibling(target.fileName.toString() + ".part")
        return try {
            HttpRequests.request(url).productNameAsUserAgent().saveToFile(tmp.toFile(), indicator)
            verify(tmp, plan)
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

    /** Enforces [FetchPlan.verification]: STRICT throws, ADVISORY logs and keeps the file. */
    private fun verify(
        file: Path,
        plan: FetchPlan,
    ) {
        if (plan.verification == ArtifactVerification.STRICT) {
            verifyOrFail(file, plan.pin.sha256, plan.pin.size)
            return
        }
        if (!verifies(file, plan.pin)) warnPinMismatch(plan)
    }

    /** Worded for any ADVISORY caller — this class knows nothing about who fetches what. */
    private fun warnPinMismatch(plan: FetchPlan) {
        LOG.warn(
            "Artifact ${plan.cacheKey} does not match its recorded pin (expected sha256 " +
                "${plan.pin.sha256}, size ${plan.pin.size}); keeping it because the caller " +
                "requested ${ArtifactVerification.ADVISORY} verification.",
        )
    }

    private fun verifyOrFail(
        file: Path,
        sha256: String,
        size: Long,
    ) {
        val actualSize = file.fileSize()
        if (actualSize != size) {
            throw LuaProvisionException("size mismatch (expected $size, got $actualSize)")
        }
        val actualHash = sha256Of(file)
        if (!actualHash.equals(sha256, ignoreCase = true)) {
            throw LuaProvisionException("sha256 mismatch (expected $sha256, got $actualHash)")
        }
    }

    private fun verifies(
        file: Path,
        pin: ArtifactPin,
    ): Boolean = file.fileSize() == pin.size && sha256Of(file).equals(pin.sha256, ignoreCase = true)

    private fun sha256Of(file: Path): String = Files.asByteSource(file.toFile()).hash(Hashing.sha256()).toString()

    /**
     * Cache key = the URL's last path segment, or its second-to-last when the last segment is
     * the literal `download` (the SourceForge `…/files/{ver}/{group}/{file}/download` pattern).
     */
    private fun cacheKey(url: String): String {
        val segments =
            url
                .substringBefore('?')
                .substringBefore('#')
                .trimEnd('/')
                .split('/')
        val last = segments.lastOrNull().orEmpty()
        return if (last == "download" && segments.size >= 2) segments[segments.size - 2] else last
    }

    companion object {
        private val LOG = Logger.getInstance(LuaArtifactDownloader::class.java)

        fun defaultCacheDir(): Path = Path.of(PathManager.getSystemPath(), "lunar", "downloads")
    }
}
