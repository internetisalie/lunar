package net.internetisalie.lunar.lang.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import net.internetisalie.lunar.lang.LuaFileType

class LuaSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        val providers = mutableListOf<JsonSchemaFileProvider>()
        
        // TEST-ONLY provider for unit tests
        providers.add(object : JsonSchemaFileProvider {
            override fun isAvailable(file: VirtualFile): Boolean {
                return System.getProperty("lunar.test.schema.enabled") == "true" && file.fileType == LuaFileType
            }

            override fun getName(): String = "Test Lua Schema"

            override fun getSchemaFile(): VirtualFile? {
                return JsonSchemaProviderFactory.getResourceFile(
                    LuaSchemaProviderFactory::class.java,
                    "/schema/test-schema.json"
                )
            }

            override fun getSchemaType(): SchemaType = SchemaType.userSchema
        })
        
        return providers
    }
}
