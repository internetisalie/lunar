package net.internetisalie.lunar.toolchain.provision

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.progress.ProgressIndicator
import net.internetisalie.lunar.toolchain.exec.LuaExecResult
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedRock
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedVersion
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeedLoader
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Run seam over [LuaToolExecutionService] (mirrors [LuaArtifactFetcher]) so
 * [LuaRocksInstallStrategy] can be unit-tested against a canned [LuaExecResult] without a real
 * `luarocks` subprocess. The production path still routes through the TOOLING-03 service.
 */
interface LuaRockInstaller {
    fun run(cmd: GeneralCommandLine, indicator: ProgressIndicator): LuaExecResult
}

/** Production installer: the INSTALL-timeout capture mandated by design §3.8 (contract §10.6). */
class ExecServiceRockInstaller(
    private val execService: LuaToolExecutionService = LuaToolExecutionService.getInstance(),
) : LuaRockInstaller {
    override fun run(cmd: GeneralCommandLine, indicator: ProgressIndicator): LuaExecResult =
        execService.capture(cmd, LuaExecTimeout.INSTALL, indicator = indicator)
}

/**
 * LuaRocks-install strategy (design §3.8, §4.4): installs a pure/native rock into the
 * environment tree using the environment's own `luarocks`, then verifies the expected binary
 * wrapper landed under `<rootDir>/bin`. A non-`COMPLETED` outcome, a non-zero exit, or a missing
 * wrapper aborts with a classified [LuaProvisionException] (C-toolchain guidance vs. generic).
 *
 * Runs only on the provisioning orchestrator's background task (blocking subprocess). Holds no
 * `Project`/PSI/VFS reference; `context.project` is used transiently, never retained.
 */
class LuaRocksInstallStrategy(
    private val installer: LuaRockInstaller = ExecServiceRockInstaller(),
) : LuaProvisioningStrategy {
    override val id: String = "luarocks-install"

    override fun supports(item: LuaProvisionItem, platform: LuaHostPlatform, feed: LuaToolchainFeed): Boolean =
        rockOf(item, platform, feed) != null

    override fun provision(context: LuaProvisionContext, item: LuaProvisionItem): LuaProvisionedComponent {
        val resolved = LuaToolchainFeedLoader.resolveVersion(context.feed, item.kindId, item.versionSpec, context.platform)
        val rock = resolved.rock
            ?: throw LuaProvisionException("No rock defined for ${item.kindId} ${resolved.version}")
        val argv = buildArgv(context.rootDir, rock, context.platform)
        val cmd = commandLine(argv, context.rootDir, context.platform)
        val result = installer.run(cmd, context.indicator)
        if (!result.isSuccess) throw classify(result, rock)
        val wrapper = wrapperPath(context.rootDir, rock, context.platform)
        if (!wrapper.exists()) {
            throw LuaProvisionException("install succeeded but no ${rock.binName} wrapper")
        }
        return component(RockComponent(item.kindId, resolved.version, rock, wrapper), context)
    }

    private fun commandLine(argv: List<String>, rootDir: Path, platform: LuaHostPlatform): GeneralCommandLine {
        val cmd = GeneralCommandLine(argv).withWorkDirectory(File(rootDir.toString()))
        if (platform.os == LuaOs.WINDOWS) {
            cmd.withEnvironment(mapOf("LUAROCKS_CONFIG" to "$rootDir/luarocks-config.lua"))
        }
        return cmd
    }

    private fun rockOf(item: LuaProvisionItem, platform: LuaHostPlatform, feed: LuaToolchainFeed): LuaFeedRock? =
        runCatching { LuaToolchainFeedLoader.resolveVersion(feed, item.kindId, item.versionSpec, platform) }
            .getOrNull()?.rock

    private fun component(rock: RockComponent, context: LuaProvisionContext): LuaProvisionedComponent {
        val pin = rock.rock.pinnedVersion ?: "latest"
        val input = LuaIdentifiersHashInput(
            kindId = rock.kindId,
            resolvedVersion = rock.resolvedVersion,
            strategyId = id,
            os = context.platform.os,
            arch = context.platform.arch,
            canonicalRootDir = context.rootDir.toString(),
            artifact = "rock=${rock.rock.rockName}@$pin",
            compatDefines = "",
        )
        return LuaProvisionedComponent(
            rock.kindId,
            rock.resolvedVersion,
            id,
            rock.wrapper,
            emptyList(),
            LuaIdentifiersHash.compute(input),
        )
    }

    /** Bundles the resolved rock identity so [component] stays a two-argument function. */
    private data class RockComponent(
        val kindId: String,
        val resolvedVersion: String,
        val rock: LuaFeedRock,
        val wrapper: Path,
    )

    companion object {
        private const val OUTPUT_TAIL = 20

        private val C_TOOLCHAIN = Regex(
            "(?i)(gcc|cc1|cl)\\b.*(not found|no such file)|error: failed (compiling|building)|" +
                "lua\\.h: no such file|could not find a c compiler",
        )

        /** Argv for `luarocks install` (design §3.8 step 2); pin appended as one element when set. */
        fun buildArgv(rootDir: Path, rock: LuaFeedRock, platform: LuaHostPlatform): List<String> {
            val luarocks = luarocksPath(rootDir, platform).toString()
            val prefix = if (platform.os == LuaOs.WINDOWS) {
                listOf(luarocks, "--lua-dir", rootDir.toString(), "--tree", rootDir.toString())
            } else {
                listOf(luarocks)
            }
            val pin = rock.pinnedVersion?.let { listOf(it) } ?: emptyList()
            return prefix + listOf("install", rock.rockName) + pin
        }

        private fun luarocksPath(rootDir: Path, platform: LuaHostPlatform): Path =
            if (platform.os == LuaOs.WINDOWS) rootDir.resolve("bin/luarocks.exe") else rootDir.resolve("bin/luarocks")

        private fun wrapperPath(rootDir: Path, rock: LuaFeedRock, platform: LuaHostPlatform): Path {
            val name = if (platform.os == LuaOs.WINDOWS) "${rock.binName}.bat" else rock.binName
            return rootDir.resolve("bin/$name")
        }

        /** Classifies a failed install into C-toolchain guidance vs. a generic message (design §4.4). */
        fun classify(result: LuaExecResult, rock: LuaFeedRock): LuaProvisionException {
            val output = (result.stdout + "\n" + result.stderr)
            val tail = output.trim().lines().takeLast(OUTPUT_TAIL).joinToString("\n").ifBlank { "(no output)" }
            val message = if (C_TOOLCHAIN.containsMatchIn(output)) {
                cToolchainMessage(rock.rockName)
            } else {
                "luarocks install ${rock.rockName} failed (exit ${result.exitCode})"
            }
            return LuaProvisionException("$message\n$tail")
        }

        private fun cToolchainMessage(rockName: String): String =
            "Installing $rockName requires a C toolchain (it builds native modules). " +
                "Linux: `sudo apt install build-essential`; macOS: `xcode-select --install`. " +
                "On Windows, C rocks are not supported by the provisioner in v1."
    }
}
