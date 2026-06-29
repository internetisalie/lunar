package net.internetisalie.lunar.lang.schema

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory

class LuaSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return EP_NAME.extensionList
    }

    companion object {
        val EP_NAME: ExtensionPointName<LuaSchemaFileProvider> = 
            ExtensionPointName.create("net.internetisalie.lunar.schemaFileProvider")
    }
}
