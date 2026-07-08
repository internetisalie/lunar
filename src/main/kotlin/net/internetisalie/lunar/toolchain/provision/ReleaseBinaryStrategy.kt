package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedAsset
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedVersion
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeedLoader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.io.path.writeText

/**
 * Fetch seam over [LuaArtifactDownloader] (design §2.6) so [ReleaseBinaryStrategy] can be
 * unit-tested against a local fixture archive without touching the network.
 */
interface LuaArtifactFetcher {
    fun fetch(asset: LuaFeedAsset, indicator: ProgressIndicator): Path
}

/** Production fetcher: verifies + caches via [LuaArtifactDownloader] (mandatory SHA-256 check). */
class DownloaderFetcher(private val downloader: LuaArtifactDownloader = LuaArtifactDownloader()) : LuaArtifactFetcher {
    override fun fetch(asset: LuaFeedAsset, indicator: ProgressIndicator): Path =
        downloader.fetch(listOf(asset.url), asset.sha256, asset.size, indicator)
}

/**
 * Bundles a downloaded feed asset with its resolved kind identity for passing to helpers that
 * need both (keeps those functions under the 3-arg engineering-contract cap).
 */
private data class ResolvedAsset(
    val kindId: String,
    val resolved: LuaFeedVersion,
    val asset: LuaFeedAsset,
)

/**
 * Bundles a feed asset with its downloaded archive file and kind identity so the materialize
 * helpers stay at ≤2 args (context is the other parameter).
 */
private data class MaterializeInput(
    val asset: LuaFeedAsset,
    val file: Path,
    val kindId: String,
)

/**
 * Prebuilt-release strategy (design §3.7, §4.6): downloads the platform-matching feed asset,
 * verifies it (via [LuaArtifactDownloader]), then materializes it per the asset's `layout`:
 *  - `single-binary` — one executable copied to `<rootDir>/bin/{name}{.exe on Windows}`.
 *  - `tree`         — extracted under `<rootDir>/tools/{kindId}/`, primary = `binaryPath` within.
 *  - `win-lua-binaries` — LuaBinaries Win64 zip: `lua{XY}.exe`/`luac{XY}.exe` copied to the
 *    canonical `lua.exe`/`luac.exe` (originals and the `.dll` kept beside them).
 *
 * On Windows LuaRocks it additionally writes `<rootDir>/luarocks-config.lua` (§4.6). Runs only
 * on the provisioning orchestrator's background task.
 */
class ReleaseBinaryStrategy(
    private val fetcher: LuaArtifactFetcher = DownloaderFetcher(),
) : LuaProvisioningStrategy {
    override val id: String = "release-binary"

    override fun supports(item: LuaProvisionItem, platform: LuaHostPlatform, feed: LuaToolchainFeed): Boolean =
        assetFor(item, platform, feed) != null

    override fun identityHash(context: LuaProvisionContext, item: LuaProvisionItem): String {
        val resolved = LuaToolchainFeedLoader.resolveVersion(context.feed, item.kindId, item.versionSpec, context.platform)
        val asset = matchAsset(resolved, context.platform)
            ?: throw LuaProvisionException("No prebuilt asset for ${item.kindId} on ${context.platform.os}-${context.platform.arch}")
        return LuaIdentifiersHash.compute(hashInput(ResolvedAsset(item.kindId, resolved, asset), context))
    }

    override fun provision(context: LuaProvisionContext, item: LuaProvisionItem): LuaProvisionedComponent {
        val resolved = LuaToolchainFeedLoader.resolveVersion(context.feed, item.kindId, item.versionSpec, context.platform)
        val asset = matchAsset(resolved, context.platform)
            ?: throw LuaProvisionException("No prebuilt asset for ${item.kindId} on ${context.platform.os}-${context.platform.arch}")
        val ra = ResolvedAsset(item.kindId, resolved, asset)
        val mi = MaterializeInput(asset, fetcher.fetch(asset, context.indicator), item.kindId)
        val primary = materialize(mi, context)
        writeWindowsLuaRocksConfig(ra, context)
        return component(ra, primary, context)
    }

    private fun materialize(mi: MaterializeInput, context: LuaProvisionContext): Path =
        when (mi.asset.layout) {
            "single-binary" -> materializeSingleBinary(mi, context)
            "tree" -> materializeTree(mi, context)
            "win-lua-binaries" -> materializeWinLuaBinaries(mi.file, context)
            else -> throw LuaProvisionException("Unknown asset layout '${mi.asset.layout}' for ${mi.kindId}")
        }

    private fun materializeSingleBinary(mi: MaterializeInput, context: LuaProvisionContext): Path {
        val source = if (mi.asset.packaging == "binary") {
            mi.file
        } else {
            val temp = createTempDirectory("lunar-release")
            LuaArchiveExtractor.extract(mi.file, temp, mi.asset.rootPrefix, context.indicator)
            temp.resolve(mi.asset.binaryPath)
        }
        val binDir = context.rootDir.resolve("bin").also { it.createDirectories() }
        val name = Path.of(mi.asset.binaryPath).name + if (context.platform.os == LuaOs.WINDOWS) ".exe" else ""
        val dest = binDir.resolve(name)
        FileUtil.copy(source.toFile(), dest.toFile())
        LuaArchiveExtractor.restoreExecBit(dest)
        return dest
    }

    private fun materializeTree(mi: MaterializeInput, context: LuaProvisionContext): Path {
        val toolsDir = context.rootDir.resolve("tools/${mi.kindId}").also { it.createDirectories() }
        LuaArchiveExtractor.extract(mi.file, toolsDir, mi.asset.rootPrefix, context.indicator)
        val primary = toolsDir.resolve(mi.asset.binaryPath)
        LuaArchiveExtractor.restoreExecBit(primary)
        return primary
    }

    private fun materializeWinLuaBinaries(file: Path, context: LuaProvisionContext): Path {
        val binDir = context.rootDir.resolve("bin").also { it.createDirectories() }
        LuaArchiveExtractor.extract(file, binDir, null, context.indicator)
        val xy = winDigits(binDir)
        FileUtil.copy(binDir.resolve("lua$xy.exe").toFile(), binDir.resolve("lua.exe").toFile())
        FileUtil.copy(binDir.resolve("luac$xy.exe").toFile(), binDir.resolve("luac.exe").toFile())
        return binDir.resolve("lua.exe")
    }

    private fun winDigits(binDir: Path): String =
        binDir.toFile().list().orEmpty()
            .firstNotNullOfOrNull { WIN_LUA_EXE.matchEntire(it)?.groupValues?.get(1) }
            ?: throw LuaProvisionException("No lua{XY}.exe found in LuaBinaries archive.")

    private fun writeWindowsLuaRocksConfig(ra: ResolvedAsset, context: LuaProvisionContext) {
        if (ra.kindId != "luarocks" || context.platform.os != LuaOs.WINDOWS) return
        val label = runtimeLabel(context)
        val config = context.rootDir.resolve("luarocks-config.lua")
        config.writeText(windowsConfigText(context.rootDir, label))
    }

    private fun windowsConfigText(rootDir: Path, label: String): String = buildString {
        appendLine("lua_dir = [[$rootDir]]")
        appendLine("lua_version = \"$label\"")
        appendLine("rocks_trees = {")
        appendLine("    { name = \"env\", root = [[$rootDir]] },")
        appendLine("}")
    }

    private fun runtimeLabel(context: LuaProvisionContext): String {
        val runtimeItem = context.request.items.firstOrNull { it.kindId == "lua" || it.kindId == "luajit" }
            ?: return "5.4"
        val resolved = LuaToolchainFeedLoader.resolveVersion(context.feed, runtimeItem.kindId, runtimeItem.versionSpec, context.platform)
        return resolved.version.split(".").take(2).joinToString(".")
    }

    private fun component(ra: ResolvedAsset, primary: Path, context: LuaProvisionContext): LuaProvisionedComponent {
        return LuaProvisionedComponent(ra.kindId, ra.resolved.version, id, primary, emptyList(), LuaIdentifiersHash.compute(hashInput(ra, context)))
    }

    private fun hashInput(ra: ResolvedAsset, context: LuaProvisionContext): LuaIdentifiersHashInput =
        LuaIdentifiersHashInput(
            kindId = ra.kindId,
            resolvedVersion = ra.resolved.version,
            strategyId = id,
            os = context.platform.os,
            arch = context.platform.arch,
            canonicalRootDir = context.rootDir.toString(),
            artifact = ra.asset.sha256,
            compatDefines = "",
        )

    private fun assetFor(item: LuaProvisionItem, platform: LuaHostPlatform, feed: LuaToolchainFeed): LuaFeedAsset? {
        val resolved = runCatching { LuaToolchainFeedLoader.resolveVersion(feed, item.kindId, item.versionSpec, platform) }
            .getOrNull() ?: return null
        return matchAsset(resolved, platform)
    }

    private fun matchAsset(resolved: LuaFeedVersion, platform: LuaHostPlatform): LuaFeedAsset? =
        resolved.assets.firstOrNull { it.os == platform.os.name.lowercase() && it.arch == platform.arch.name.lowercase() }

    private companion object {
        private val WIN_LUA_EXE = Regex("^lua(\\d{2})\\.exe$")
    }
}
