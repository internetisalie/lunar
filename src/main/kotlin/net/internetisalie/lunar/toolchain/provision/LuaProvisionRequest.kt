package net.internetisalie.lunar.toolchain.provision

/**
 * One runtime/tool to provision into an environment (design §2.1).
 * @param kindId the [net.internetisalie.lunar.toolchain] kind id (`lua`, `luarocks`, `luacheck`, …).
 * @param versionSpec a feed version spec or alias (`5.4`, `latest`, `3.13.0`, …).
 */
data class LuaProvisionItem(val kindId: String, val versionSpec: String)

/**
 * Immutable description of one provisioning job (design §2.1).
 *
 * `rootDir` is expected to already be canonicalized by the orchestrator (§3.1 step 1);
 * this value type performs no path I/O in its constructor.
 */
data class LuaProvisionRequest(
    val environmentName: String,
    val rootDir: String,
    val items: List<LuaProvisionItem>,
)
