package net.internetisalie.lunar.lang.insight.hint

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor

class LuaTypeInlayHintProvider : InlayHintsProvider {
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        return object : SharedBypassCollector {
            override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
                if (file !is LuaFile) return

                if (element is LuaNameRef) {
                    val parent = element.parent
                    // Check if it's a declaration (inside LuaAttName or LuaNameList from LuaParList)
                    val isDecl = parent is LuaAttName || (parent is LuaNameList && parent.parent is LuaParList)
                    if (isDecl) {
                        // Check if it already has an explicit annotation
                        if (hasExplicitAnnotation(element)) return

                        val types = LuaTypesVisitor.getTypes(element)
                        val type = types.getValueType(element)

                        if (type != LuaGraphType.Any && type != LuaGraphType.Undefined) {
                            val typeName = type.displayName()
                            // Only show if not trivial (e.g. number literal is often obvious, but for now we follow spec)
                            sink.addPresentation(
                                InlineInlayPosition(element.textRange.endOffset, true),
                                null,
                                null,
                                HintFormat.default
                            ) {
                                text(": $typeName")
                            }
                        }
                    }
                }
            }

            private fun hasExplicitAnnotation(element: LuaNameRef): Boolean {
                // Find containing declaration
                var current: PsiElement? = element
                while (current != null && current !is LuaLocalVarDecl && current !is LuaFuncDecl && current !is LuaFuncDef && current !is LuaLocalFuncDecl) {
                    current = current.parent
                }

                if (current is LuaCommentOwner) {
                    val cats = current.catsComment
                    if (cats != null) {
                        // If it's a param, check for @param with matching name
                        if (element.parent is LuaNameList) {
                            val name = element.text
                            return cats.getParamTagList().any { it.argName?.text == name }
                        }
                        // If it's a local var, check for @type
                        if (current is LuaLocalVarDecl) {
                            val hasType = cats.getTypeTagList().isNotEmpty()
                            val hasClass = cats.getClassTagList().isNotEmpty()
                            val hasAlias = cats.getAliasTagList().isNotEmpty()
                            return hasType || hasClass || hasAlias
                        }
                    }
                }
                return false
            }
        }
    }
}
