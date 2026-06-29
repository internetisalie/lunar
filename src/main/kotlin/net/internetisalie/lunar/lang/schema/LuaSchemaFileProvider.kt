package net.internetisalie.lunar.lang.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType

abstract class LuaSchemaFileProvider(
    val project: Project,
    private val name: String,
    private val schemaResourcePath: String
) : JsonSchemaFileProvider {
    abstract override fun isAvailable(file: VirtualFile): Boolean
    
    override fun getName(): String = name
    
    override fun getSchemaFile(): VirtualFile? {
        // Implementation provided by SCHEMA-01
        return null
    }
    
    override fun getSchemaType(): SchemaType = SchemaType.schema
}
