package net.internetisalie.lunar.toolchain.provision

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.io.FileUtil
import net.internetisalie.lunar.toolchain.exec.LuaExecResult
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedSource
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedVersion
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeedLoader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * POSIX source-build strategy (design §2.4, §2.9). Dispatches on `kindId` (`lua` → PUC-Lua,
 * `luarocks` → LuaRocks) to a pure recipe, then executes every [BuildStep] through the
 * TOOLING-03 execution service (INSTALL timeout). A non-`COMPLETED` outcome or a non-zero exit
 * aborts with a [LuaProvisionException] carrying the failing command and the last 20 output
 * lines (mirrors `HererocksProvisioner`). Not supported on Windows (`supports()` == false).
 *
 * Runs only on the provisioning orchestrator's background task (blocking I/O + subprocesses).
 */
class SourceBuildStrategy(
    private val downloader: LuaArtifactDownloader = LuaArtifactDownloader(),
    private val execService: LuaToolExecutionService = LuaToolExecutionService.getInstance(),
) : LuaProvisioningStrategy {
    override val id: String = "source-build"

    override fun supports(item: LuaProvisionItem, platform: LuaHostPlatform, feed: LuaToolchainFeed): Boolean {
        if (platform.os == LuaOs.WINDOWS) return false
        if (item.kindId != "lua" && item.kindId != "luarocks") return false
        val resolved = runCatching { LuaToolchainFeedLoader.resolveVersion(feed, item.kindId, item.versionSpec, platform) }
            .getOrNull() ?: return false
        return resolved.source != null
    }

    override fun provision(context: LuaProvisionContext, item: LuaProvisionItem): LuaProvisionedComponent {
        val toolchain = context.toolchain
            ?: throw LuaProvisionException(LuaCompilerProbe.REMEDIATION)
        val resolved = LuaToolchainFeedLoader.resolveVersion(context.feed, item.kindId, item.versionSpec, context.platform)
        val buildDir = context.rootDir.resolve(".build/${item.kindId}-${resolved.version}")
        prepareBuildDir(item, resolved, context)
        val recipeInput = LuaBuildRecipeInput(resolved.version, context.platform.os, toolchain, buildDir, context.rootDir)
        val plan = planFor(item.kindId, recipeInput)
        runSteps(plan, context)
        applyInstall(plan, InstallTarget(item.kindId, resolved.version, context.rootDir))
        FileUtil.delete(buildDir.toFile())
        return component(item.kindId, resolved, context)
    }

    private data class InstallTarget(val kindId: String, val version: String, val rootDir: Path)

    private fun prepareBuildDir(item: LuaProvisionItem, resolved: LuaFeedVersion, context: LuaProvisionContext) {
        val source = sourceOf(item.kindId, resolved)
        val buildDir = context.rootDir.resolve(".build/${item.kindId}-${resolved.version}")
        FileUtil.delete(buildDir.toFile())
        buildDir.createDirectories()
        val archive = downloader.fetch(source.urls, source.sha256, source.size, context.indicator)
        LuaArchiveExtractor.extract(archive, buildDir, source.rootPrefix, context.indicator)
        if (item.kindId == "lua") patchLuaconfFile(buildDir, resolved.version, context.rootDir)
    }

    private fun sourceOf(kindId: String, resolved: LuaFeedVersion): LuaFeedSource =
        resolved.source ?: throw LuaProvisionException("No source tarball for $kindId ${resolved.version}")

    private fun patchLuaconfFile(buildDir: Path, version: String, rootDir: Path) {
        val luaconf = buildDir.resolve("src/luaconf.h")
        val patched = PucLuaBuildRecipe.patchLuaconf(luaconf.readText(), version, rootDir)
        luaconf.writeText(patched)
    }

    private fun planFor(kindId: String, input: LuaBuildRecipeInput): BuildPlan =
        when (kindId) {
            "lua" -> PucLuaBuildRecipe.plan(input)
            "luarocks" -> requireMake(input.toolchain).let { LuaRocksBuildRecipe.plan(input) }
            else -> throw LuaProvisionException("SourceBuildStrategy cannot build kind '$kindId'")
        }

    private fun requireMake(toolchain: LuaCompilerProbe.Toolchain) {
        if (toolchain.make == null) throw LuaProvisionException("GNU make not found on PATH; required to build LuaRocks.")
    }

    private fun runSteps(plan: BuildPlan, context: LuaProvisionContext) {
        for (step in plan.steps) {
            context.indicator.checkCanceled()
            val cmd = GeneralCommandLine(step.command)
                .withWorkDirectory(step.workDir.toFile())
                .withEnvironment(step.env)
            val result = execService.capture(cmd, LuaExecTimeout.INSTALL, indicator = context.indicator)
            if (!result.isSuccess) throw failure(step, result)
        }
    }

    private fun failure(step: BuildStep, result: LuaExecResult): LuaProvisionException {
        val tail = (result.stdout + "\n" + result.stderr).trim().lines().takeLast(OUTPUT_TAIL)
            .joinToString("\n").ifBlank { "(no output)" }
        val command = step.command.joinToString(" ")
        return LuaProvisionException("Build command failed (exit ${result.exitCode}): $command\n$tail")
    }

    private fun applyInstall(plan: BuildPlan, target: InstallTarget) {
        for ((source, dest) in plan.installCopies) {
            dest.parent?.createDirectories()
            FileUtil.copy(source.toFile(), dest.toFile())
        }
        plan.executables.forEach(LuaArchiveExtractor::restoreExecBit)
        if (target.kindId == "lua") PucLuaBuildRecipe.installDirs(target.rootDir, target.version).forEach { it.createDirectories() }
        if (target.kindId == "luarocks") appendLuaRocksConfig(target.rootDir, target.version)
    }

    private fun appendLuaRocksConfig(rootDir: Path, version: String) {
        val label = version.split(".").take(2).joinToString(".")
        val config = rootDir.resolve("etc/luarocks/config-$label.lua")
        config.parent?.createDirectories()
        val existing = if (config.exists()) config.readText() + "\n" else ""
        config.writeText(existing + LuaRocksBuildRecipe.CONFIG_APPEND + "\n")
    }

    private fun component(kindId: String, resolved: LuaFeedVersion, context: LuaProvisionContext): LuaProvisionedComponent {
        val prefix = context.rootDir
        val primary = if (kindId == "luarocks") prefix.resolve("bin/luarocks") else prefix.resolve("bin/lua")
        val extras = if (kindId == "lua") listOf(prefix.resolve("bin/luac")) else emptyList()
        val hash = hashFor(kindId, resolved, context)
        return LuaProvisionedComponent(kindId, resolved.version, id, primary, extras, hash)
    }

    private fun hashFor(kindId: String, resolved: LuaFeedVersion, context: LuaProvisionContext): String {
        val compat = if (kindId == "lua") PucLuaBuildRecipe.compatDefines(resolved.version).joinToString(" ") else ""
        val input = LuaIdentifiersHashInput(
            kindId = kindId,
            resolvedVersion = resolved.version,
            strategyId = id,
            os = context.platform.os,
            arch = context.platform.arch,
            canonicalRootDir = context.rootDir.toString(),
            artifact = resolved.source?.sha256.orEmpty(),
            compatDefines = compat,
        )
        return LuaIdentifiersHash.compute(input)
    }

    private companion object {
        private const val OUTPUT_TAIL = 20
    }
}
