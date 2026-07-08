package net.internetisalie.lunar.toolchain.provision

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path

/**
 * One provisioned component recorded in a [LuaEnvManifest] (design §2.10, §4.5).
 *
 * @param binaries rootDir-relative paths with `/` separators (the skip rule resolves them
 *   against the environment root).
 */
data class LuaManifestComponent(
    val resolvedVersion: String,
    val strategyId: String,
    val identifiersHash: String,
    val binaries: List<String>,
    val provisionedAtEpochMs: Long,
)

/**
 * `<rootDir>/.lunar-env.json` — the idempotency record and Lunar-provisioned-tree marker
 * (design §2.10, §4.5). Serialized with the bundled Gson.
 *
 * [read] is total: it returns `null` on an absent file OR any parse/shape error, which the
 * orchestrator treats as "provision everything". It never throws.
 */
data class LuaEnvManifest(
    val manifestVersion: Int,
    val environmentId: String,
    val environmentName: String,
    val request: LuaProvisionRequest,
    val components: Map<String, LuaManifestComponent>,
) {
    companion object {
        const val FILE_NAME = ".lunar-env.json"

        private val gson = GsonBuilder().setPrettyPrinting().create()

        fun read(rootDir: Path): LuaEnvManifest? {
            val file = rootDir.resolve(FILE_NAME)
            return try {
                if (!Files.isRegularFile(file)) {
                    return null
                }
                val json = Files.readString(file)
                val parsed = gson.fromJson(json, LuaEnvManifest::class.java)
                if (isWellFormed(parsed)) parsed else null
            } catch (_: Exception) {
                null
            }
        }

        fun write(rootDir: Path, manifest: LuaEnvManifest) {
            val file = rootDir.resolve(FILE_NAME)
            Files.writeString(file, gson.toJson(manifest))
        }

        private fun isWellFormed(manifest: LuaEnvManifest?): Boolean =
            manifest != null &&
                manifest.environmentId.isNotBlank() &&
                manifest.request.items.isNotEmpty() &&
                manifest.components.values.all { it.identifiersHash.isNotBlank() }
    }
}
