/*
 * Copyright 2010 Jon S Akhtar (Sylvanaar)
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
package net.internetisalie.lunar.lang.syntax

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.LuaIcons.FILE
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

/**
 * Created by IntelliJ IDEA.
 * User: jon
 * Date: Apr 3, 2010
 * Time: 1:52:31 AM
 */
class LuaColorSettingsPage : ColorSettingsPage {
    val DEMO_TEXT: String =
        """
<builtin>require</builtin> "os"
<package>os</package>.getenv("VARNAME")
                     
<global>a</global> = { <global>foo</global>.<field>bar</field>,  <global>foo</global>.<field>bar</field>(), <global>fx</global>(), <field>f</field> = <global>a</global>, 1,  <global>FOO</global> } -- url http://www.url.com 
local <local>x</local>,<local>y</local> = 20,nil
for <local>i</local>=1,10 do
  local <local>y</local> = 0
  <global>a</global>[<local>i</local>] = function() <local><upval>y</upval></local>=<local><upval>y</upval></local>+1; return <local><upval>x</upval></local>+<local>y</local>; end
end

--[[   Multiline
  Comment
]]

<luadoc>--- External Documentation URL (shift-F1)</luadoc>
<luadoc>-- This is called by shift-F1 on the symbol, or by the</luadoc>
<luadoc>-- external documentation button on the quick help panel</luadoc>
<luadoc>-- <luadoc-tag>@class</luadoc-tag> <luadoc-value>tag-name</luadoc-value> The name to get documentation for.</luadoc>
<luadoc>-- <luadoc-tag>@param</luadoc-tag> <parameter>name</parameter> The name to get documentation for.</luadoc>
<luadoc>-- <luadoc-tag>@return</luadoc-tag> the URL of the external documentation</luadoc>
function <global>getDocumentationUrl</global>(<parameter>name</parameter>) 
  local <local>p1</local>, <local>p2</local> = <global>string</global>.<field>match</field>(<parameter>name</parameter>, "(%a+)\.?(%a*)")
  local <local>url</local> = <global>BASE_URL</global> .. "/docs/api/" .. <local>p1</local> .. [[long string]]

  if <local>p2</local> and true then <local>url</local> = <local>url</local> .. <local>p2</local>; end

  function() local <local>upval_parameter</local> = <parameter><upval>name</upval></parameter> end

  <local><upval>x</upval></local>, <local><upval>y</upval></local> = <local>p1</local>, <local>p2</local>

  return <local>url</local>
end

-- Some comment
function <global>localFunction</global>(<parameter>name</parameter>
  return "value"
end

<label>::here::</label>
if false then 
    goto <label>here</label>
end

<global>a</global> = "BAD
"""

    override fun getDisplayName(): String {
        return LuaBundle.message("color.name")
    }

    override fun getIcon(): Icon? {
        return FILE
    }

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> {
        return DESCRIPTORS
    }

    override fun getColorDescriptors(): Array<ColorDescriptor> {
        return arrayOf()
    }

    override fun getHighlighter(): SyntaxHighlighter {
        return LuaSyntaxHighlighter()
    }

    override fun getDemoText(): @NonNls String {
        return DEMO_TEXT
    }

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? {
        return ATTR_MAP
    }

    companion object {
        private val highlight = LuaHighlight

        private val DESCRIPTORS: Array<AttributesDescriptor> = setOf(
            Pair("color.number", highlight.NUMBER),
            Pair("color.string", highlight.STRING),
            Pair("color.longstring", highlight.LONGSTRING),
            Pair("color.keyword", highlight.KEYWORD),
            Pair("color.constant.keywords", highlight.DEFINED_CONSTANTS),
            Pair("color.globals", highlight.GLOBAL_VAR),
//            Pair("color.identifier", highlight.IDENTIFIER),
            Pair("color.locals", highlight.LOCAL_VAR),
            Pair("color.field", highlight.FIELD),
            Pair("color.parameter", highlight.PARAMETER),
            Pair("color.upvalue", highlight.UPVAL),
            Pair("color.label", highlight.LABEL),
            Pair("color.comment", highlight.COMMENT),
            Pair("color.longcomment", highlight.LONGCOMMENT),
            Pair("color.longstring.braces", highlight.LONGSTRING_BRACES),
            Pair("color.longcomment.braces", highlight.LONGCOMMENT_BRACES),
            Pair("color.operation", highlight.OPERATORS),
            Pair("color.brackets", highlight.BRACKETS),
            Pair("color.parenths", highlight.PARENTHESES),
            Pair("color.braces", highlight.BRACES),
            Pair("color.comma", highlight.COMMA),
            Pair("color.semi", highlight.SEMI),
            Pair("color.bad_character", highlight.BAD_CHARACTER),
            Pair("color.luadoc", highlight.DOC_COMMENT),
            Pair("color.luadoc.tag", highlight.DOC_TAG),
            Pair("color.luadoc.value", highlight.DOC_VALUE),
            Pair("color.package", highlight.PACKAGE),
            Pair("color.builtin", highlight.BUILTIN),
        )
            .map { pair: Pair<String, TextAttributesKey> ->
                AttributesDescriptor(
                    LuaBundle.message(pair.first),
                    pair.second
                )
            }
            .toTypedArray()

        private val ATTR_MAP: Map<String, TextAttributesKey> = mapOf(
            Pair("local", highlight.LOCAL_VAR),
            Pair("global", highlight.GLOBAL_VAR),
            Pair("field", highlight.FIELD),
            Pair("upval", highlight.UPVAL),
            Pair("parameter", highlight.PARAMETER),
            Pair("label", highlight.LABEL),
            Pair("luadoc", highlight.DOC_COMMENT),
            Pair("luadoc-tag", highlight.DOC_TAG),
            Pair("luadoc-value", highlight.DOC_VALUE),
            Pair("package", highlight.PACKAGE),
            Pair("builtin", highlight.BUILTIN),
        )
    }
}
