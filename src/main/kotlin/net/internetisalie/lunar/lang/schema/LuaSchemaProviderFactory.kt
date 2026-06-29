package net.internetisalie.lunar.lang.schema

import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory

abstract class LuaSchemaProviderFactory : JsonSchemaProviderFactory {
    abstract override fun getProviders(project: Project): List<JsonSchemaFileProvider>
}
