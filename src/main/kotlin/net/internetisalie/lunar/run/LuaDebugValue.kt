/*
 * Copyright 2011 Jon S Akhtar (Sylvanaar)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package net.internetisalie.lunar.run

import com.intellij.icons.AllIcons
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XNumericValuePresentation
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.intellij.xdebugger.frame.presentation.XStringValuePresentation
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import javax.swing.Icon

class LuaDebugValue : XValue {
    private val typeName: String
    private val displayValue: String?
    private val rawValue: LuaValue
    private val icon: Icon?
    private val identityValue: String?

    constructor(typeName: String, displayValue: String, icon: Icon?) {
        this.typeName = typeName
        this.displayValue = displayValue
        this.rawValue = LuaValue(null)
        this.icon = icon
        this.identityValue = null
    }

    constructor(luaValue: LuaValue, identityValue : String?, icon: Icon?) {
        this.typeName = luaValue.typeName
        this.rawValue = luaValue
        this.displayValue = luaValue.psiElement?.text ?: luaValue.toDisplayString()
        this.icon = icon
        this.identityValue = identityValue
    }

    constructor(errorMessage: String?) : this(
        "error",
        "Error during evaluation: $errorMessage",
        AllIcons.Nodes.ErrorMark
    )

    val isString: Boolean
        get() = typeName == "string"

    val isNumber: Boolean
        get() = typeName == "number"

    val isBool: Boolean
        get() = typeName == "boolean"

    val isTable: Boolean
        get() = typeName == "table"

    val raw : LuaValue
        get() = rawValue

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val presentation: XValuePresentation = this.presentation
        node.setPresentation(icon, presentation, this.isTable)
    }

    override fun computeChildren(node: XCompositeNode) {
        if (!isTable) {
            node.addChildren(XValueChildrenList.EMPTY, true)
            return
        }

        val fields = rawValue.checkTable()?.pairs() ?: run {
            node.addChildren(XValueChildrenList.EMPTY, true)
            return
        }

        val xValues = XValueChildrenList(fields.size)
        fields.forEach { field ->
            val key = when (field.first.kind) {
                LuaValueKind.String -> field.first.stringValue ?: "?"
                LuaValueKind.Number -> "[" + (field.first.numberValue?.toInt() ?: 0) + "]"
                else -> "[" + field.first.toDisplayString() + "]"
            }
            val debugValue = LuaDebugValue(field.second, null, AllIcons.Nodes.Field)
            xValues.add(key, debugValue)
        }

        node.addChildren(xValues, true)
    }

    private val presentation: XValuePresentation
        get() {
            val stringValue = displayValue ?: ""
            if (this.isNumber) return XNumericValuePresentation(stringValue)
            if (this.isString) return XStringValuePresentation(stringValue)
            if (this.isBool) {
                return object : XValuePresentation() {
                    override fun renderValue(renderer: XValueTextRenderer) {
                        renderer.renderValue(stringValue)
                    }
                }
            }
            return XRegularValuePresentation(
                stringValue.ifEmpty { null } ?: identityValue ?: "",
                typeName,
            )
        }
}
