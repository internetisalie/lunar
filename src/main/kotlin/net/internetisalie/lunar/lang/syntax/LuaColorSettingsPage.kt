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
import net.internetisalie.lunar.lang.LuaIcons
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

/**
 * Created by IntelliJ IDEA.
 * User: jon
 * Date: Apr 3, 2010
 * Time: 1:52:31 AM
 */
class LuaColorSettingsPage : ColorSettingsPage {
    private val demoText: String = """
        <platform>require</platform> "os"
                             
        <global>a</global> = { 
            <global>foo</global>.<field>bar</field>,  
            <global>foo</global>.<field>bar</field>(), 
            <global>fx</global>(), 
            <field>f</field> = <global>a</global>, 
            1,  
            <global>FOO</global> 
        } -- url http://www.url.com 
        
        local <local>x</local>,<local>y</local> = 20,nil
        for <local>i</local>=1,10 do
          local <local>y</local> = 0
          <global>a</global>[<local>i</local>] = function() 
            <local><upval>y</upval></local>=<local><upval>y</upval></local>+1; return <local><upval>x</upval></local>+<local>y</local>
          end
        end
        
        <package>os</package>.<call-platform>getenv</call-platform>("VARNAME")
        
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
        function <global>globalFunction</global>(<parameter>name</parameter>)
          return "two"
        end
        
        <call-global>globalFunction</call-global>("one")
        
        if true then
            local function <local>localFunction</local>(<parameter>name</parameter>)
                return "four"
            end
            <call-local>localFunction</call-local>("three")
            
            local globalFunction
        end
        
        <label>::here::</label>
        if false then 
            goto <label>here</label>
        end
        
        <global>a</global> = "BAD
        """

    private val attributeDescriptors: Array<AttributesDescriptor> = setOf(
        Pair("color.number", LuaHighlight.NUMBER),
        Pair("color.string", LuaHighlight.STRING),
        Pair("color.longstring", LuaHighlight.LONGSTRING),
        Pair("color.keyword", LuaHighlight.KEYWORD),
        Pair("color.constant.keywords", LuaHighlight.DEFINED_CONSTANTS),
        Pair("color.comment", LuaHighlight.COMMENT),
        Pair("color.longcomment", LuaHighlight.LONGCOMMENT),
        Pair("color.longstring.braces", LuaHighlight.LONGSTRING_BRACES),
        Pair("color.longcomment.braces", LuaHighlight.LONGCOMMENT_BRACES),
        Pair("color.operation", LuaHighlight.OPERATORS),
        Pair("color.brackets", LuaHighlight.BRACKETS),
        Pair("color.parenths", LuaHighlight.PARENTHESES),
        Pair("color.braces", LuaHighlight.BRACES),
        Pair("color.comma", LuaHighlight.COMMA),
        Pair("color.semi", LuaHighlight.SEMI),
        Pair("color.bad_character", LuaHighlight.BAD_CHARACTER),
        Pair("color.luadoc", LuaHighlight.DOC_COMMENT),
        Pair("color.luadoc.tag", LuaHighlight.DOC_TAG),
        Pair("color.luadoc.value", LuaHighlight.DOC_VALUE),
        // Identifiers
        Pair("color.platform", LuaHighlight.REF_PLATFORM),
        Pair("color.globals", LuaHighlight.GLOBAL_VAR),
        Pair("color.locals", LuaHighlight.LOCAL_VAR),
        Pair("color.field", LuaHighlight.FIELD),
        Pair("color.parameter", LuaHighlight.PARAMETER),
        Pair("color.upvalue", LuaHighlight.UPVAL),
        Pair("color.label", LuaHighlight.LABEL),
        Pair("color.package", LuaHighlight.PACKAGE),
        Pair("color.call.platform", LuaHighlight.CALL_PLATFORM),
        Pair("color.call.global", LuaHighlight.CALL_GLOBAL),
        Pair("color.call.local", LuaHighlight.CALL_LOCAL),
    )
        .map { pair: Pair<String, TextAttributesKey> ->
            AttributesDescriptor(
                LuaBundle.message(pair.first),
                pair.second
            )
        }
        .toTypedArray()

    private val highlightingTagToDescriptorMap: Map<String, TextAttributesKey> = mapOf(
        // Annotated Identifiers
        Pair("platform", LuaHighlight.REF_PLATFORM),
        Pair("global", LuaHighlight.GLOBAL_VAR),
        Pair("local", LuaHighlight.LOCAL_VAR),
        Pair("undefined", LuaHighlight.REF_UNDEFINED),
        Pair("field", LuaHighlight.FIELD),
        Pair("parameter", LuaHighlight.PARAMETER),
        Pair("upval", LuaHighlight.UPVAL),
        Pair("label", LuaHighlight.LABEL),
        Pair("package", LuaHighlight.PACKAGE),
        Pair("shadowed", LuaHighlight.REF_SHADOWED),
        Pair("call-platform", LuaHighlight.CALL_PLATFORM),
        Pair("call-global", LuaHighlight.CALL_GLOBAL),
        Pair("call-local", LuaHighlight.CALL_LOCAL),
        // Docs
        Pair("luadoc", LuaHighlight.DOC_COMMENT),
        Pair("luadoc-tag", LuaHighlight.DOC_TAG),
        Pair("luadoc-value", LuaHighlight.DOC_VALUE),
    )

    override fun getDisplayName(): String {
        return LuaBundle.message("color.name")
    }

    override fun getIcon(): Icon? {
        return LuaIcons.FILE
    }

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> {
        return attributeDescriptors
    }

    override fun getColorDescriptors(): Array<ColorDescriptor> {
        return arrayOf()
    }

    override fun getHighlighter(): SyntaxHighlighter {
        return LuaSyntaxHighlighter()
    }

    override fun getDemoText(): @NonNls String {
        return demoText
    }

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> {
        return highlightingTagToDescriptorMap
    }
}
