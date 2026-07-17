package net.internetisalie.lunar.lang.insight.hint

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

class LuaTypeInlayHintProvider : InlayHintsProvider {
    companion object {
        const val PROVIDER_ID = "lua.type.hints"
        const val LOCAL_VARIABLE_TYPE_OPTION_ID = "lua.local.variable.type"
        const val RETURN_TYPE_OPTION_ID = "lua.return.type"
        const val RESPECT_ANNOTATIONS_OPTION_ID = "lua.respect.annotations"

        fun unwrapExpression(expr: PsiElement?): PsiElement? {
            var current = expr
            var depth = 0
            while (current != null && depth < 10) {
                depth++
                val children = current.children.filter { it !is PsiWhiteSpace && it !is PsiComment }
                if (children.size == 1) {
                    val child = children[0]
                    if (child is LuaExpr || child is LuaNameRef || child is LuaVar || child is LuaPrefixExpr || child is LuaVarOrExp) {
                        current = child
                        continue
                    }
                }
                break
            }
            return current
        }

        fun shouldShowHint(paramName: String, argExpr: PsiElement): Boolean {
            if (paramName.length <= 1 || paramName == "_" || paramName == "p") return false
            if (argExpr is LuaNameRef && argExpr.text == paramName) return false
            if (argExpr is LuaExpr) {
                val unwrapped = unwrapExpression(argExpr)
                if (unwrapped is LuaNameRef && unwrapped.text == paramName) return false
            }
            return true
        }
    }

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        val settings = LuaInlayHintsSettings.instance.state

        // Check large file threshold
        val document = editor.document
        if (document.lineCount > settings.largeFileThreshold) {
            return null
        }

        return object : SharedBypassCollector {
            override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
                if (file !is LuaFile) return

                val declarativeSettings = DeclarativeInlayHintsSettings.getInstance()
                val respectAnnotations = declarativeSettings.isOptionEnabled(RESPECT_ANNOTATIONS_OPTION_ID, PROVIDER_ID) ?: true

                if (element is LuaNameRef) {
                    sink.whenOptionEnabled(LOCAL_VARIABLE_TYPE_OPTION_ID) {
                        collectLocalVariableHints(element, sink, respectAnnotations)
                    }
                }

                // Return type hints added at RPAREN of the function declaration
                if (element.text == ")") {
                    val func = element.parent
                    if (func is LuaFuncDecl || func is LuaLocalFuncDecl || func is LuaFuncDef) {
                        sink.whenOptionEnabled(RETURN_TYPE_OPTION_ID) {
                            collectReturnTypeHints(func, element, sink, respectAnnotations)
                        }
                    }
                }
            }

            private fun collectLocalVariableHints(
                element: LuaNameRef,
                sink: InlayTreeSink,
                respectAnnotations: Boolean
            ) {
                val parent = element.parent
                val isDecl = parent is LuaAttName || (parent is LuaNameList && parent.parent is LuaParList)
                if (isDecl) {
                    if (respectAnnotations && hasExplicitAnnotation(element)) return

                    val types = LuaTypesSnapshot.forFile(element.containingFile)
                    val type = types.getValueType(element)

                    if (type != LuaGraphType.Any && type != LuaGraphType.Undefined) {
                        val typeName = type.displayName()
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

            private fun collectReturnTypeHints(
                func: PsiElement,
                rparen: PsiElement,
                sink: InlayTreeSink,
                respectAnnotations: Boolean
            ) {
                if (respectAnnotations && hasExplicitReturnAnnotation(func)) return

                val types = LuaTypesSnapshot.forFile(func.containingFile)
                val funcGraphType = types.getValueType(func)

                if (funcGraphType !is LuaGraphType.Function) return

                val returnTypesStrings = funcGraphType.returns.map { node ->
                    val t = if (node.write != LuaGraphType.Undefined) node.write else node.read
                    types.graphTypeToLuaType(t).name
                }

                val lastSignificant = returnTypesStrings.indexOfLast { it != "any" && it != "unknown" && it != "void" }
                val filteredReturns = if (lastSignificant >= 0) returnTypesStrings.take(lastSignificant + 1) else emptyList()

                val hintText = filteredReturns.joinToString(", ")
                if (hintText.isEmpty() || hintText == "unknown" || hintText == "any" || hintText == "void") {
                    return
                }

                sink.addPresentation(
                    InlineInlayPosition(rparen.textRange.endOffset, true),
                    null,
                    null,
                    HintFormat.default
                ) {
                    text(": $hintText")
                }
            }

            private fun hasExplicitReturnAnnotation(element: PsiElement): Boolean {
                var current: PsiElement? = element
                if (current is LuaFuncDef) {
                    while (current != null && current !is LuaLocalVarDecl && current !is LuaStatement) {
                        current = current.parent
                    }
                }

                if (current is LuaCommentOwner) {
                    val cats = current.catsComment
                    if (cats != null) {
                        return cats.getReturnTagList().isNotEmpty()
                    }
                }
                return false
            }

            private fun hasExplicitAnnotation(element: LuaNameRef): Boolean {
                var current: PsiElement? = element
                while (current != null && current !is LuaLocalVarDecl && current !is LuaFuncDecl && current !is LuaFuncDef && current !is LuaLocalFuncDecl) {
                    current = current.parent
                }

                if (current is LuaCommentOwner) {
                    val cats = current.catsComment
                    if (cats != null) {
                        if (element.parent is LuaNameList) {
                            val name = element.text
                            return cats.getParamTagList().any { it.argName?.text == name }
                        }
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
