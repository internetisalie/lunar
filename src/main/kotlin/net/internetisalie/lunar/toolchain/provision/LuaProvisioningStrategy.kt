package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import java.nio.file.Path

/**
 * The outcome of provisioning one requested component (design §2.4): the resolved version,
 * the strategy that produced it, the primary binary registered as the tool, any extra
 * binaries (e.g. `luac` beside `lua`), and the idempotency hash recorded in the manifest.
 */
data class LuaProvisionedComponent(
    val kindId: String,
    val resolvedVersion: String,
    val strategyId: String,
    val primaryBinary: Path,
    val extraBinaries: List<Path>,
    val identifiersHash: String,
)

/**
 * The per-call execution context handed to a [LuaProvisioningStrategy] (design §2.4).
 *
 * Bundling the call state into one value object keeps [LuaProvisioningStrategy.provision] a
 * two-argument function under the engineering-contract §3 parameter cap. The [project] is held
 * transiently for the duration of a single `provision` call only — strategies must never
 * retain it in a field (engineering-contract §4).
 */
data class LuaProvisionContext(
    val project: Project,
    val request: LuaProvisionRequest,
    val platform: LuaHostPlatform,
    val feed: LuaToolchainFeed,
    val rootDir: Path,
    val indicator: ProgressIndicator,
    val toolchain: LuaCompilerProbe.Toolchain?,
)

/**
 * One provisioning mechanism (design §2.4, tooling-architecture §6). Implementations run
 * exclusively on the provisioning orchestrator's background task — never the EDT — and touch
 * no PSI/VFS.
 */
interface LuaProvisioningStrategy {
    /** Stable id persisted into `.lunar-env.json`: `release-binary` / `source-build` / `luarocks-install`. */
    val id: String

    /** Whether this strategy can provision [item] on [platform] given the [feed]'s assets. */
    fun supports(item: LuaProvisionItem, platform: LuaHostPlatform, feed: LuaToolchainFeed): Boolean

    /** Provisions [item] into `context.rootDir`; throws [LuaProvisionException] on failure. */
    fun provision(context: LuaProvisionContext, item: LuaProvisionItem): LuaProvisionedComponent
}
