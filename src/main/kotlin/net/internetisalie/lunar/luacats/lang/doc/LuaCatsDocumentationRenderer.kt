package net.internetisalie.lunar.luacats.lang.doc

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import net.internetisalie.lunar.lang.doc.buildTypeLink
import net.internetisalie.lunar.lang.doc.codeFragment
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.syntax.LuaCatsSummary
import net.internetisalie.lunar.lang.syntax.LuaHighlight
import net.internetisalie.lunar.luacats.lang.psi.*
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

object LuaCatsDocumentationRenderer {
    // Three-block structure constants
    private const val DEFINITION_START = "<div class='definition'>"
    private const val DEFINITION_END = "</div>"

    private const val CONTENT_START = "<div class='content'>"
    private const val CONTENT_END = "</div>"

    private const val SECTIONS_START = "<table class='sections'>"
    private const val SECTIONS_END = "</table>"

    // Section row template
    private const val SECTION_HEADER_CELL = "<tr><td valign='top' class='section'><p>"
    private const val SECTION_SEPARATOR = "</td><td valign='top'>"
    private const val SECTION_END = "</td></tr>"

    /**
     * Renders Quick Documentation for LuaCATS-annotated elements.
     *
     * Returns HTML with three-block structure:
     * 1. Definition block: Syntax-highlighted signature
     * 2. Content block: Markdown-rendered description
     * 3. Sections table: Metadata (params, returns, fields, etc.)
     *
     * @param element The PSI element to document (function, class, or variable)
     * @return HTML string for documentation popup, or null if element is not supported
     */
    fun renderDoc(element: PsiElement): String? {
        val commentOwner = element as? LuaCommentOwner ?: return null
        val comment = commentOwner.catsComment ?: return null

        val definitionBlock = buildDefinitionBlock(element, comment) ?: return null
        val contentBlock = buildContentBlock(comment)
        val sectionsBlock = buildSectionsBlock(element, comment)

        return buildString {
            append(DEFINITION_START)
            append(definitionBlock)
            append(DEFINITION_END)

            if (contentBlock.isNotEmpty()) {
                append(CONTENT_START)
                append(contentBlock)
                append(CONTENT_END)
            }

            if (sectionsBlock.isNotEmpty()) {
                append(SECTIONS_START)
                append(sectionsBlock)
                append(SECTIONS_END)
            }
        }
    }

    // Keep for compatibility with existing caller
    fun render(sb: StringBuilder, element: PsiElement, comment: LuaCatsComment) {
        val doc = renderDoc(element)
        if (doc != null) {
            sb.append(doc)
        }
    }

    private fun buildDefinitionBlock(element: PsiElement, comment: LuaCatsComment): String? {
        return when (element) {
            is LuaFuncDecl -> buildFunctionSignature(element, comment)
            is LuaLocalFuncDecl -> buildLocalFunctionSignature(element, comment)
            is LuaLocalVarDecl -> buildVariableSignature(element, comment)
            else -> null
        }
    }

    private fun buildFunctionSignature(element: LuaFuncDecl, comment: LuaCatsComment): String {
        return buildString {
            append("<pre>")
            val isDeprecated = comment.deprecatedTagList.isNotEmpty()
            if (isDeprecated) append("<s>")

            append(codeFragment(LuaHighlight.KEYWORD, "function"))
            append(" ")
            append(codeFragment(LuaHighlight.VAR_LOCAL, element.funcName.text))

            buildFunctionSignatureTypeParams(comment, this)

            append(codeFragment(LuaHighlight.OPERATORS, "("))
            buildFunctionSignatureParams(comment, this)
            append(codeFragment(LuaHighlight.OPERATORS, ")"))

            append(codeFragment(LuaHighlight.OPERATORS, " : "))
            buildFunctionSignatureReturns(comment, this)

            if (isDeprecated) append("</s>")
            append("</pre>")
        }
    }

    private fun buildLocalFunctionSignature(element: LuaLocalFuncDecl, comment: LuaCatsComment): String {
        return buildString {
            append("<pre>")
            val isDeprecated = comment.deprecatedTagList.isNotEmpty()
            if (isDeprecated) append("<s>")

            append(codeFragment(LuaHighlight.KEYWORD, "local function"))
            append(" ")
            append(codeFragment(LuaHighlight.VAR_LOCAL, element.nameRef.text))

            append(codeFragment(LuaHighlight.OPERATORS, "("))
            buildFunctionSignatureParams(comment, this)
            append(codeFragment(LuaHighlight.OPERATORS, ")"))

            append(codeFragment(LuaHighlight.OPERATORS, " : "))
            buildFunctionSignatureReturns(comment, this)

            if (isDeprecated) append("</s>")
            append("</pre>")
        }
    }

    private fun buildVariableSignature(element: LuaLocalVarDecl, comment: LuaCatsComment): String {
        val classTag = comment.classTagList.firstOrNull()
        val typeTag = comment.typeTagList.firstOrNull()
        val enumTag = comment.enumTagList.firstOrNull()

        return buildString {
            append("<pre>")
            val isDeprecated = comment.deprecatedTagList.isNotEmpty()
            if (isDeprecated) append("<s>")

            if (enumTag != null) {
                append(codeFragment(LuaHighlight.KEYWORD, "enum"))
                append(" ")
                append(buildTypeLink(enumTag.argName.text))
            } else if (classTag != null || typeTag != null) {
                append(codeFragment(LuaHighlight.KEYWORD, "class"))
                append(" ")
                val className = classTag?.argType?.text ?: typeTag?.argType?.text ?: element.attNameList.firstOrNull()?.text ?: "Unknown"
                append(buildTypeLink(className))

                // Check for parent types
                if (classTag?.parentTypes != null) {
                    append(" ")
                    append(codeFragment(LuaHighlight.OPERATORS, ":"))
                    append(" ")
                    append(buildTypeLink(classTag.parentTypes!!.text))
                }
            } else {
                val name = element.attNameList.firstOrNull()?.text ?: "variable"
                append("<b>").append(name).append("</b>")
            }

            if (isDeprecated) append("</s>")
            append("</pre>")
        }
    }

    private fun buildFunctionSignatureTypeParams(comment: LuaCatsComment, sb: StringBuilder) {
        if (comment.genericTagList.isEmpty()) return
        sb.append(codeFragment(LuaHighlight.OPERATORS, "<"))
        var first = true
        comment.genericTagList.forEach { tag ->
            val typeParamList = tag.genericTypeParams?.genericTypeParamList ?: return@forEach
            typeParamList.forEach { param ->
                if (!first) {
                    sb.append(codeFragment(LuaHighlight.OPERATORS, ","))
                        .append(" ")
                } else {
                    first = false
                }
                sb.append(buildTypeLink(param.argName.text))

                val argType = param.argType ?: return@forEach
                sb
                    .append(" ")
                    .append(codeFragment(LuaHighlight.OPERATORS, ":"))
                    .append(" ")
                    .append(buildTypeLink(argType.text))
            }
        }
        sb.append(codeFragment(LuaHighlight.OPERATORS, ">"))
    }

    private fun buildFunctionSignatureParams(comment: LuaCatsComment, sb: StringBuilder) {
        var paramCount = 0
        comment.paramTagList.forEach {
            if (paramCount > 0) {
                sb.append(codeFragment(LuaHighlight.OPERATORS, ", "))
            }
            paramCount++

            if (it.argName != null) {
                sb.append(codeFragment(LuaHighlight.VAR_LOCAL, it.argName!!.text))
            } else {
                sb.append(codeFragment(LuaHighlight.OPERATORS, "..."))
            }
            sb.append(codeFragment(LuaHighlight.OPERATORS, ": "))
            sb.append(buildTypeLink(it.argType.text))
        }
    }

    private fun buildFunctionSignatureReturns(comment: LuaCatsComment, sb: StringBuilder) {
        if (comment.returnTagList.isEmpty()) {
            sb.append("any")
            return
        }
        var returnCount = 0
        comment.returnTagList.forEach {
            if (returnCount > 0) {
                sb.append(codeFragment(LuaHighlight.OPERATORS, ", "))
            }
            returnCount++
            sb.append(buildTypeLink(it.argType.text))
        }
    }

    private fun buildContentBlock(comment: LuaCatsComment): String {
        val summary = LuaCatsSummary.getText(comment) ?: ""
        return if (summary.isNotEmpty()) {
            markdownDescription(summary)
        } else {
            ""
        }
    }

    private fun buildSectionsBlock(element: PsiElement, comment: LuaCatsComment): String {
        val sb = StringBuilder()

        when (element) {
            is LuaFuncDecl, is LuaLocalFuncDecl -> {
                buildParamSection(comment, sb)
                buildReturnSection(comment, sb)
            }
            is LuaLocalVarDecl -> {
                if (comment.enumTagList.isNotEmpty()) {
                    buildEnumValuesSection(comment, sb)
                } else {
                    buildFieldsSection(element, comment, sb)
                }
            }
        }

        buildSeeSection(comment, sb)
        buildDeprecatedSection(comment, sb)

        return sb.toString()
    }

    private fun buildSectionHeader(title: String, sb: StringBuilder) {
        sb.append(SECTION_HEADER_CELL)
        sb.append(title)
        sb.append(SECTION_SEPARATOR)
    }

    private fun buildParamSection(comment: LuaCatsComment, sb: StringBuilder) {
        val tags = comment.paramTagList
        if (tags.isEmpty()) return

        buildSectionHeader("Parameters:", sb)
        tags.forEach { tag ->
            val name = if (tag.argName != null) tag.argName!!.text else "..."
            val type = tag.argType.text
            val desc = tag.description?.text ?: ""

            sb.append("<p><code>").append(name).append("</code>")
            sb.append(" <span style='color: #808080;'>(").append(buildTypeLink(type)).append(")</span>")
            if (desc.isNotEmpty()) {
                sb.append(" - ").append(markdownDescription(desc))
            }
            sb.append("</p>")
        }
        sb.append(SECTION_END)
    }

    private fun buildReturnSection(comment: LuaCatsComment, sb: StringBuilder) {
        val tags = comment.returnTagList
        if (tags.isEmpty()) return

        buildSectionHeader("Returns:", sb)
        tags.forEach { tag ->
            val type = tag.argType.text
            val name = tag.argName?.text
            val desc = tag.description?.text ?: ""

            sb.append("<p><span style='color: #808080;'>").append(buildTypeLink(type)).append("</span>")
            if (name != null) {
                sb.append(" <code>").append(name).append("</code>")
            }
            if (desc.isNotEmpty()) {
                sb.append(" - ").append(markdownDescription(desc))
            }
            sb.append("</p>")
        }
        sb.append(SECTION_END)
    }

    private fun buildFieldsSection(element: LuaLocalVarDecl, comment: LuaCatsComment, sb: StringBuilder) {
        val hasDirectFields = comment.fieldTagList.isNotEmpty()
        val classTag = comment.classTagList.firstOrNull()
        val parentTypeName = classTag?.parentTypes?.text
        val parentComment = parentTypeName?.let { lookupParentComment(element.project, it) }
        val hasInheritedFields = parentComment != null && parentComment.fieldTagList.isNotEmpty()

        if (!hasDirectFields && !hasInheritedFields) return

        if (hasDirectFields) {
            buildSectionHeader("Fields:", sb)
            comment.fieldTagList.forEach { tag ->
                buildFieldTag(tag, sb)
            }
            sb.append(SECTION_END)
        }

        if (hasInheritedFields) {
            buildSectionHeader("Inherited Fields:", sb)
            parentComment!!.fieldTagList.forEach { tag ->
                buildFieldTag(tag, sb)
            }
            sb.append(SECTION_END)
        }
    }

    private fun buildFieldTag(tag: LuaCatsFieldTag, sb: StringBuilder) {
        val fieldDescriptor = tag.fieldDescriptor
        val name = fieldDescriptor.argName?.text ?: "Unknown"
        val type = tag.argType.text

        sb.append("<p><code>").append(name).append("</code>")
        sb.append(" <span style='color: #808080;'>(").append(buildTypeLink(type)).append(")</span>")
        if (tag.description != null) {
            sb.append(" - ").append(markdownDescription(tag.description!!.text))
        }
        sb.append("</p>")
    }

    private fun buildEnumValuesSection(comment: LuaCatsComment, sb: StringBuilder) {
        // Enums in LuaCATS often use ---| "value" # description
        // This is handled by the parser but we need to find where those values are stored.
        // Looking at LuaCatsComment.java, it has getTypeOptionList().

        val options = comment.typeOptionList
        if (options.isEmpty()) return

        buildSectionHeader("Values:", sb)
        options.forEach { option ->
            val type = option.argValue.text
            val desc = option.description?.text ?: ""

            sb.append("<p><code>").append(type).append("</code>")
            if (desc.isNotEmpty()) {
                sb.append(" - ").append(markdownDescription(desc))
            }
            sb.append("</p>")
        }
        sb.append(SECTION_END)
    }

    private fun buildSeeSection(comment: LuaCatsComment, sb: StringBuilder) {
        val tags = comment.seeTagList
        if (tags.isEmpty()) return

        buildSectionHeader("See Also:", sb)
        tags.forEach { tag ->
            val reference = tag.argName.text ?: ""
            sb.append("<p><code>").append(reference).append("</code></p>")
        }
        sb.append(SECTION_END)
    }
    private fun buildDeprecatedSection(comment: LuaCatsComment, sb: StringBuilder) {
        val tag = comment.deprecatedTagList.firstOrNull() ?: return

        buildSectionHeader("<span style='color: #FF6B68;'>⚠ Deprecated:</span>", sb)
        val desc = tag.description?.text ?: "This item is deprecated"
        sb.append("<p>").append(markdownDescription(desc)).append("</p>")
        sb.append(SECTION_END)
    }

    private fun lookupParentComment(project: Project, parentTypeName: String): LuaCatsComment? {
        val scope = GlobalSearchScope.projectScope(project)
        val parentDecl = StubIndex.getElements(LuaClassNameIndex.KEY, parentTypeName, project, scope, LuaLocalVarDecl::class.java).firstOrNull()
        return parentDecl?.catsComment
    }

    private fun highlightLuaCode(code: String): String {
        val lexer = net.internetisalie.lunar.lang.lexer.LuaLexer()
        lexer.start(code)
        val highlighter = net.internetisalie.lunar.lang.syntax.LuaSyntaxHighlighter()
        val sb = StringBuilder()
        while (lexer.tokenType != null) {
            val tokenType = lexer.tokenType!!
            val tokenText = code.substring(lexer.tokenStart, lexer.tokenEnd)
            val highlights = highlighter.getTokenHighlights(tokenType)
            if (highlights.isNotEmpty()) {
                sb.append(codeFragment(highlights[0], tokenText))
            } else {
                sb.append(com.intellij.openapi.util.text.HtmlChunk.text(tokenText))
            }
            lexer.advance()
        }
        return sb.toString()
    }

    private fun markdownDescription(markdown: String): String {
        val flavour = CommonMarkFlavourDescriptor()
        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        var html = HtmlGenerator(markdown, parsedTree, flavour).generateHtml()

        // Post-process to highlight Lua code blocks
        val regex = Regex("<pre><code class=\"language-lua\">([\\s\\S]*?)</code></pre>")
        html = regex.replace(html) { matchResult ->
            val escapedCode = matchResult.groupValues[1]
            // Basic unescape (since Markdown library might have escaped it)
            val code = escapedCode.replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")

            "<pre><code>${highlightLuaCode(code)}</code></pre>"
        }

        // Strip <body> and <html> wrappers if present
        return html.removePrefix("<body>").removeSuffix("</body>")
            .removePrefix("<html>").removeSuffix("</html>")
            .trim()
    }
}
