package net.internetisalie.lunar.toolchain.provision

import java.nio.file.Path

/**
 * Pure re-detection core (design §9 note, TOOLING-04-16): given an environment root and the set
 * of already-registered TOOLING-02 environment ids, decide whether the root holds a Lunar-
 * provisioned tree (`.lunar-env.json`) whose `environmentId` has no matching record — i.e. an
 * orphaned tree the user can re-register in one click. Swing-/platform-free so it is unit-testable
 * without a live project. The [LuaProjectActivity][net.internetisalie.lunar.toolchain.provision]
 * wrapper handles the off-EDT scan + notification.
 */
object LuaEnvRedetection {
    /** The manifest of an orphaned Lunar tree at [rootDir], or null when absent or already registered. */
    fun findOrphan(rootDir: Path, registeredEnvIds: Set<String>): LuaEnvManifest? {
        val manifest = LuaEnvManifest.read(rootDir) ?: return null
        if (manifest.environmentId in registeredEnvIds) return null
        return manifest
    }

    /** Rebuilds the registration payload from a manifest so the orphan can be re-registered as-is. */
    fun toResult(rootDir: Path, manifest: LuaEnvManifest): LuaProvisionResult {
        val components = manifest.components.map { (kindId, component) ->
            val binaries = component.binaries.map { rootDir.resolve(it) }
            LuaProvisionedComponent(
                kindId = kindId,
                resolvedVersion = component.resolvedVersion,
                strategyId = component.strategyId,
                primaryBinary = binaries.firstOrNull() ?: rootDir.resolve("bin/$kindId"),
                extraBinaries = binaries.drop(1),
                identifiersHash = component.identifiersHash,
            )
        }
        return LuaProvisionResult(manifest.environmentId, manifest.environmentName, rootDir.toString(), components)
    }
}
