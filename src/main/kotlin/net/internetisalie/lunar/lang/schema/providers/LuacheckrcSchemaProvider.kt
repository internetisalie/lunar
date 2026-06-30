package net.internetisalie.lunar.lang.schema.providers

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import net.internetisalie.lunar.lang.schema.LuaSchemaFileProvider

/**
 * Maps `.luacheckrc` (and `.luacheckrc.lua`) files to the bundled luacheck-config JSON schema
 * (SCHEMA-03), proving the SCHEMA-01 engine generalises beyond rockspec. Registered as an
 * application-level `schemaFileProvider` extension, so no [com.intellij.openapi.project.Project] is
 * injected; claiming the file needs only its name.
 */
class LuacheckrcSchemaProvider : LuaSchemaFileProvider() {

    override fun getName(): String = PROVIDER_NAME

    override fun getSchemaFile(): VirtualFile? =
        JsonSchemaProviderFactory.getResourceFile(this::class.java, SCHEMA_RESOURCE_PATH)

    override fun isAvailable(file: VirtualFile): Boolean =
        file.name == LUACHECKRC_NAME || file.name == LUACHECKRC_LUA_NAME

    companion object {
        private const val PROVIDER_NAME = "Luacheckrc"
        private const val SCHEMA_RESOURCE_PATH = "/jsonschema/luacheck-config.schema.json"
        private const val LUACHECKRC_NAME = ".luacheckrc"
        private const val LUACHECKRC_LUA_NAME = ".luacheckrc.lua"
    }
}
