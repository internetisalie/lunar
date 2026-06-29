package net.internetisalie.lunar.lang.schema

import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType

abstract class LuaSchemaFileProvider : JsonSchemaFileProvider {
    override fun getSchemaType(): SchemaType = SchemaType.userSchema
}

