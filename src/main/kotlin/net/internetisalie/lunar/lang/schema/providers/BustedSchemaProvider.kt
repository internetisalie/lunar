package net.internetisalie.lunar.lang.schema.providers

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import net.internetisalie.lunar.lang.schema.LuaSchemaFileProvider

/**
 * Maps `.busted` files to the bundled busted-config JSON schema (SCHEMA-04), exercising the
 * SCHEMA-01 engine's shape-B (`return { <profile> = { … } }`) returned-table root. Registered as an
 * application-level `schemaFileProvider` extension, so no [com.intellij.openapi.project.Project] is
 * injected; claiming the file needs only its name.
 */
class BustedSchemaProvider : LuaSchemaFileProvider() {

    override fun getName(): String = PROVIDER_NAME

    override fun getSchemaFile(): VirtualFile? =
        JsonSchemaProviderFactory.getResourceFile(this::class.java, SCHEMA_RESOURCE_PATH)

    override fun isAvailable(file: VirtualFile): Boolean =
        file.name == BUSTED_NAME

    companion object {
        private const val PROVIDER_NAME = "Busted Config"
        private const val SCHEMA_RESOURCE_PATH = "/jsonschema/busted-config.schema.json"
        private const val BUSTED_NAME = ".busted"
    }
}
