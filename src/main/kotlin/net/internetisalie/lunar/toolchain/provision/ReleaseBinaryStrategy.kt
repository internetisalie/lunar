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

    override fun provision(context: LuaProvisionContext, item: LuaProvisionItem): LuaProvisionedComponent {
        val resolved = LuaToolchainFeedLoader.resolveVersion(context.feed, item.kindId, item.versionSpec, context.platform)
        val asset = matchAsset(resolved, context.platform)
            ?: throw LuaProvisionException("No prebuilt asset for ${item.kindId} on ${context.platform.os}-${context.platform.arch}")
        val file = fetcher.fetch(asset, context.indicator)
        val primary = materialize(asset, file, item.kindId, context)
        writeWindowsLuaRocksConfig(item.kindId, resolved, context)
        return component(item.kindId, resolved, asset, primary, context)
    }

    private fun materialize(asset: LuaFeedAsset, file: Path, kindId: String, context: LuaProvisionContext): Path =
        when (asset.layout) {
            "single-binary" -> materializeSingleBinary(asset, file, context)
            "tree" -> materializeTree(asset, file, kindId, context)
            "win-lua-binaries" -> materializeWinLuaBinaries(file, context)
            else -> throw LuaProvisionException("Unknown asset layout '${asset.layout}' for $kindId")
        }

    private fun materializeSingleBinary(asset: LuaFeedAsset, file: Path, context: LuaProvisionContext): Path {
        val source = if (asset.packaging == "binary") {
            file
        } else {
            val temp = createTempDirectory("lunar-release")
            LuaArchiveExtractor.extract(file, temp, asset.rootPrefix, context.indicator)
            temp.resolve(asset.binaryPath)
        }
        val binDir = context.rootDir.resolve("bin").also { it.createDirectories() }
        val name = Path.of(asset.binaryPath).name + if (context.platform.os == LuaOs.WINDOWS) ".exe" else ""
        val dest = binDir.resolve(name)
        FileUtil.copy(source.toFile(), dest.toFile())
        LuaArchiveExtractor.restoreExecBit(dest)
        return dest
    }

    private fun materializeTree(asset: LuaFeedAsset, file: Path, kindId: String, context: LuaProvisionContext): Path {
        val toolsDir = context.rootDir.resolve("tools/$kindId").also { it.createDirectories() }
        LuaArchiveExtractor.extract(file, toolsDir, asset.rootPrefix, context.indicator)
        val primary = toolsDir.resolve(asset.binaryPath)
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

    private fun writeWindowsLuaRocksConfig(kindId: String, resolved: LuaFeedVersion, context: LuaProvisionContext) {
        if (kindId != "luarocks" || context.platform.os != LuaOs.WINDOWS) return
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

    private fun component(kindId: String, resolved: LuaFeedVersion, asset: LuaFeedAsset, primary: Path, context: LuaProvisionContext): LuaProvisionedComponent {
        val input = LuaIdentifiersHashInput(
            kindId = kindId,
            resolvedVersion = resolved.version,
            strategyId = id,
            os = context.platform.os,
            arch = context.platform.arch,
            canonicalRootDir = context.rootDir.toString(),
            artifact = asset.sha256,
            compatDefines = "",
        )
        return LuaProvisionedComponent(kindId, resolved.version, id, primary, emptyList(), LuaIdentifiersHash.compute(input))
    }

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
