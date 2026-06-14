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
import net.internetisalie.lunar.luacats.lang.syntax.LuaCatsHighlight
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.io.InputStreamReader
import javax.swing.Icon

/**
 * Created by IntelliJ IDEA.
 * User: jon
 * Date: Apr 3, 2010
 * Time: 1:52:31 AM
 */
class LuaColorSettingsPage : ColorSettingsPage {
    private val demoText: String = """
        <call-platform>require</call-platform> "os"

        <global>a</global> = {
            <global>foo</global>.<field>bar</field>,
            <global>foo</global>.<field>bar</field>(),
            <global>fx</global>(),
            <field>f</field> = <global>a</global>,
            1,
            <global>FOO</global>
        } -- url http://www.url.com

        local <local>x</local>,<local>y&lt;<attrib-name>const</attrib-name>&gt;</local> = 20,nil
        for <local>i</local>=1,10 do
          local <local>y</local> = 0
          <global>a</global>[<local>i</local>] = function()
            <local><upval>y</upval></local>=<local><upval>y</upval></local>+1; return <local><upval>x</upval></local>+<local>y</local>
          end
        end

        <package>os</package>.<call-platform>getenv</call-platform>("VARNAME")

        <comment-brackets>--[[</comment-brackets>   Multiline
          Comment
        <comment-brackets>]]</comment-brackets>

        <documentation>--- Documentation using LuaCats format (shift-F1)</documentation>
        <documentation>-- This is called by shift-F1 on the symbol, or by the</documentation>
        <documentation>-- external documentation button on the quick help panel</documentation>
        <documentation>-- <luacats-tag>@class</luacats-tag> <luacats-name>Player</luacats-name></documentation>
        <documentation>-- <luacats-tag>@field</luacats-tag> <luacats-name>name</luacats-name> <luacats-type>string</luacats-type></documentation>
        <documentation>-- <luacats-tag>@param</luacats-tag> <luacats-name>id</luacats-name> <luacats-type>number</luacats-type> The ID</documentation>
        <documentation>-- <luacats-tag>@return</luacats-tag> <luacats-type>Player</luacats-type></documentation>
        <documentation>-- <luacats-tag>@deprecated</luacats-tag> Use something else</documentation>
        function <func-global>getDocumentationUrl</func-global>(<parameter>name</parameter>)
          local <local>p1</local>, <local>p2</local> = <global>string</global>.<field>match</field>(<parameter>name</parameter>, "(%a+)\.?(%a*)")
          local <local>url</local> = <global>BASE_URL</global> .. "/docs/api/" .. <local>p1</local> .. <string-brackets>[[</string-brackets>long string<string-brackets>]]</string-brackets>

          if <local>p2</local> and true then <local>url</local> = <local>url</local> .. <local>p2</local>; end

          function() local <local>upval_parameter</local> = <parameter><upval>name</upval></parameter> end

          <local><upval>x</upval></local>, <local><upval>y</upval></local> = <local>p1</local>, <local>p2</local>

          return <local>url</local>
        end

        -- Some comment
        function <func-global>globalFunction</func-global>(<parameter>name</parameter>)
          return "two"
        end

        <call-global>globalFunction</call-global>("one")

        if true then
            local function <func-local>localFunction</func-local>(<parameter>name</parameter>)
                return "four"
            end
            <call-local>localFunction</call-local>("three")

            local <local>globalFunction</local>
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
        // Docs
        Pair("color.luacats", LuaCatsHighlight.CONTENT),
        Pair("color.luacats.tag", LuaCatsHighlight.TAG),
        Pair("color.luacats.keyword", LuaCatsHighlight.KEYWORD),
        Pair("color.luacats.value", LuaCatsHighlight.VALUE),
        Pair("color.luacats.type", LuaCatsHighlight.TYPE),
        Pair("color.luacats.name", LuaCatsHighlight.NAME),
        Pair("color.luacats.symbol", LuaCatsHighlight.SYMBOL),
        Pair("color.luacats.brackets", LuaCatsHighlight.BRACKETS),
        Pair("color.luacats.deprecated", LuaCatsHighlight.DEPRECATED),
        // Identifiers
        Pair("color.platform", LuaHighlight.VAR_PLATFORM),
        Pair("color.globals", LuaHighlight.VAR_GLOBAL),
        Pair("color.locals", LuaHighlight.VAR_LOCAL),
        Pair("color.field", LuaHighlight.FIELD),
        Pair("color.parameter", LuaHighlight.PARAMETER),
        Pair("color.upvalue", LuaHighlight.VAR_UP_VALUE),
        Pair("color.shadowed", LuaHighlight.VAR_SHADOWED),
        Pair("color.label", LuaHighlight.LABEL),
        Pair("color.package", LuaHighlight.PACKAGE),
        Pair("color.func.platform", LuaHighlight.FUNC_PLATFORM),
        Pair("color.func.global", LuaHighlight.FUNC_GLOBAL),
        Pair("color.func.local", LuaHighlight.FUNC_LOCAL),
        Pair("color.call.platform", LuaHighlight.CALL_PLATFORM),
        Pair("color.call.global", LuaHighlight.CALL_GLOBAL),
        Pair("color.call.local", LuaHighlight.CALL_LOCAL),
        Pair("color.attrib", LuaHighlight.ATTRIB_NAME),
        Pair("color.inferred.localCall", LuaHighlight.INFERRED_LOCAL_CALL),
        Pair("color.inferred.globalCall", LuaHighlight.INFERRED_GLOBAL_CALL),
        Pair("color.inferred.class", LuaHighlight.INFERRED_CLASS),
        Pair("color.inferred.field", LuaHighlight.INFERRED_FIELD),
        Pair("color.inferred.method", LuaHighlight.INFERRED_METHOD),
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
        Pair("undefined", LuaHighlight.REF_UNDEFINED),
        Pair("platform", LuaHighlight.VAR_PLATFORM),
        Pair("global", LuaHighlight.VAR_GLOBAL),
        Pair("local", LuaHighlight.VAR_LOCAL),
        Pair("field", LuaHighlight.FIELD),
        Pair("parameter", LuaHighlight.PARAMETER),
        Pair("upval", LuaHighlight.VAR_UP_VALUE),
        Pair("label", LuaHighlight.LABEL),
        Pair("package", LuaHighlight.PACKAGE),
        Pair("shadowed", LuaHighlight.VAR_SHADOWED),
        Pair("func-platform", LuaHighlight.FUNC_PLATFORM),
        Pair("func-global", LuaHighlight.FUNC_GLOBAL),
        Pair("func-local", LuaHighlight.FUNC_LOCAL),
        Pair("call-platform", LuaHighlight.CALL_PLATFORM),
        Pair("call-global", LuaHighlight.CALL_GLOBAL),
        Pair("call-local", LuaHighlight.CALL_LOCAL),
        Pair("attrib-name", LuaHighlight.ATTRIB_NAME),
        Pair("inferred-local-call", LuaHighlight.INFERRED_LOCAL_CALL),
        Pair("inferred-global-call", LuaHighlight.INFERRED_GLOBAL_CALL),
        Pair("inferred-class", LuaHighlight.INFERRED_CLASS),
        Pair("inferred-field", LuaHighlight.INFERRED_FIELD),
        Pair("inferred-method", LuaHighlight.INFERRED_METHOD),
        // Docs
        Pair("documentation", LuaHighlight.DOC_COMMENT),
        Pair("documentation-tag", LuaHighlight.DOC_TAG),
        Pair("documentation-value", LuaHighlight.DOC_VALUE),
        Pair("luacats-tag", LuaCatsHighlight.TAG),
        Pair("luacats-name", LuaCatsHighlight.NAME),
        Pair("luacats-type", LuaCatsHighlight.TYPE),
        Pair("luacats-keyword", LuaCatsHighlight.KEYWORD),
        Pair("luacats-value", LuaCatsHighlight.VALUE),
        Pair("luacats-symbol", LuaCatsHighlight.SYMBOL),
        Pair("string-brackets", LuaHighlight.LONGSTRING_BRACES),
        Pair("comment-brackets", LuaHighlight.LONGCOMMENT_BRACES),
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
        try {
                val classLoader = LuaColorSettingsPage::class.java.getClassLoader()
                classLoader.getResourceAsStream("colorSettings/preview/colorSettings.html")?.use { it ->
                    InputStreamReader(it).use { reader ->
                        return reader.readText()
                    }
                }
        } catch (_ : IOException) { }
        return ""
    }

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> {
        return highlightingTagToDescriptorMap
    }
}
