package net.internetisalie.lunar.toolchain.provision

import java.io.File
import java.nio.file.Path

/** A selected dev tool and its chosen version (design §2.12 field 6). */
data class LuaToolChoice(val kindId: String, val versionSpec: String)

/** Which field a validation message binds to, so the dialog can attach the [ValidationInfo]. */
enum class LuaProvisionField { NAME, ROOT_DIR, RUNTIME_VERSION }

/** A failed validation: a message plus the field it belongs to (design §2.12 validation). */
data class LuaProvisionValidation(val message: String, val field: LuaProvisionField)

/**
 * Swing-free snapshot of the [LuaProvisionDialog] fields (design §2.12). Holds the pure
 * `toRequest`/`validate` derivations so they are unit-testable without showing the dialog.
 *
 * `includeLuaRocks` is the effective value (already forced `true` by the dialog when any rock
 * tool is selected). `selectedTools` lists only the checked dev tools.
 */
data class LuaProvisionFormState(
    val name: String,
    val rootDir: String,
    val runtime: LuaToolChoice,
    val luaRocks: LuaRocksChoice,
    val selectedTools: List<LuaToolChoice>,
) {
    /** Items ordered runtime, luarocks, release-binary tools, rock tools (design §2.12). */
    fun toRequest(): LuaProvisionRequest =
        LuaProvisionRequest(
            environmentName = name.trim(),
            rootDir = rootDir.trim(),
            items = orderedItems(),
        )

    private fun orderedItems(): List<LuaProvisionItem> {
        val items = mutableListOf(LuaProvisionItem(runtime.kindId, runtime.versionSpec))
        if (luaRocks.included) {
            items.add(LuaProvisionItem("luarocks", luaRocks.versionSpec))
        }
        items.addAll(toolItems(LuaToolCatalog.RELEASE_BINARY_TOOL_KINDS))
        items.addAll(toolItems(LuaToolCatalog.ROCK_TOOL_KINDS))
        return items
    }

    private fun toolItems(kinds: List<String>): List<LuaProvisionItem> =
        kinds.mapNotNull { kindId ->
            selectedTools.firstOrNull { it.kindId == kindId }?.let { LuaProvisionItem(kindId, it.versionSpec) }
        }

    /** Design §2.12 validation; [existingNames] are the existing TOOLING-02 environment names. */
    fun validate(existingNames: Set<String>): LuaProvisionValidation? =
        validateName(existingNames) ?: validateRootDir() ?: validateRuntime()

    private fun validateName(existingNames: Set<String>): LuaProvisionValidation? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return fail("Name is required", LuaProvisionField.NAME)
        if (trimmed in existingNames) {
            return fail("An environment named '$trimmed' already exists", LuaProvisionField.NAME)
        }
        return null
    }

    private fun validateRootDir(): LuaProvisionValidation? {
        val trimmed = rootDir.trim()
        if (trimmed.isEmpty()) return fail("Directory is required", LuaProvisionField.ROOT_DIR)
        if (trimmed.contains('"') || trimmed.contains(';')) {
            return fail("Directory must not contain quotes or semicolons", LuaProvisionField.ROOT_DIR)
        }
        return validateExistingDir(File(trimmed))
    }

    private fun validateExistingDir(dir: File): LuaProvisionValidation? {
        val children = dir.takeIf { it.isDirectory }?.list() ?: return null
        if (children.isEmpty()) return null
        if (!isLunarEnvironment(dir.toPath())) {
            return fail("Directory is not empty and is not a Lunar environment", LuaProvisionField.ROOT_DIR)
        }
        return null
    }

    private fun isLunarEnvironment(dir: Path): Boolean = LuaEnvManifest.read(dir) != null

    private fun validateRuntime(): LuaProvisionValidation? {
        if (runtime.versionSpec.isBlank()) {
            return fail("Runtime version is required", LuaProvisionField.RUNTIME_VERSION)
        }
        return null
    }

    private fun fail(message: String, field: LuaProvisionField) = LuaProvisionValidation(message, field)
}

/** LuaRocks inclusion + version (design §2.12 field 5). */
data class LuaRocksChoice(val included: Boolean, val versionSpec: String)
