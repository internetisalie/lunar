package net.internetisalie.lunar.lang.insight.hint

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.psi.types.LuaFunctionType
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

                if (element is LuaFuncCall) {
                    collectParameterHints(element, sink)
                }
            }

            private fun collectParameterHints(element: LuaFuncCall, sink: InlayTreeSink) {
                val types = LuaTypesVisitor.getTypes(element)
                val callee = unwrapExpression(element.varOrExp) ?: element.varOrExp

                val nameAndArgs = element.nameAndArgsList.firstOrNull() ?: return
                val methodExpr = nameAndArgs.methodExpr

                val functionType: LuaFunctionType? = if (methodExpr != null) {
                    val receiverGraphType = types.getValueType(callee)
                    val receiverType = types.graphTypeToLuaType(receiverGraphType)
                    val methodName = methodExpr.nameRef?.text
                    if (methodName != null) {
                        val member = receiverType.resolveMember(methodName)
                        if (member != null && member.type is LuaFunctionType) {
                            member.type as LuaFunctionType
                        } else {
                            val resolved = methodExpr.nameRef?.reference?.resolve()
                            val funcDecl = resolved?.parent?.parent as? LuaFuncDecl
                            if (funcDecl != null) {
                                types.graphTypeToLuaType(types.getValueType(funcDecl)) as? LuaFunctionType
                            } else null
                        }
                    } else null
                } else {
                    val calleeGraphType = types.getValueType(callee)
                    val t = types.graphTypeToLuaType(calleeGraphType) as? LuaFunctionType
                    if (t != null) {
                        t
                    } else {
                        val resolved = (callee as? LuaNameRef)?.reference?.resolve()
                        val decl = resolved?.parent as? LuaLocalFuncDecl ?: resolved as? LuaFuncDecl ?: resolved?.parent?.parent as? LuaFuncDecl
                        if (decl != null) {
                            types.graphTypeToLuaType(types.getValueType(decl)) as? LuaFunctionType
                        } else null
                    }
                }

                if (functionType != null) {
                    val args = nameAndArgs.args
                    val argExprs = when {
                        args.string != null -> listOf(args.string!!)
                        args.exprList != null -> args.exprList?.exprList ?: emptyList()
                        args.tableConstructor != null -> listOf(args.tableConstructor!!)
                        else -> emptyList()
                    }

                    if (argExprs.isEmpty()) return

                    val params = functionType.params
                    val isColonCall = methodExpr != null

                    val effectiveParams = if (isColonCall && params.isNotEmpty() && params[0].name == "self") {
                        params.drop(1)
                    } else if (isColonCall && params.size > argExprs.size) {
                        params.drop(1)
                    } else {
                        params
                    }

                    if (effectiveParams.size <= 1) return

                    for (i in 0 until minOf(argExprs.size, effectiveParams.size)) {
                        val argExpr = argExprs[i]
                        val param = effectiveParams[i]
                        val paramName = param.name

                        if (shouldShowHint(paramName, argExpr)) {
                            sink.addPresentation(
                                InlineInlayPosition(argExpr.textRange.startOffset, true),
                                null,
                                null,
                                HintFormat.default
                            ) {
                                text("$paramName:")
                            }
                        }
                    }
                }
            }

            private fun shouldShowHint(paramName: String, argExpr: PsiElement): Boolean {
                // Suppress if non-descriptive
                if (paramName.length <= 1 || paramName == "_" || paramName == "p") return false

                // Suppress if name matches arg
                if (argExpr is LuaNameRef && argExpr.text == paramName) return false
                if (argExpr is LuaExpr) {
                    val unwrapped = unwrapExpression(argExpr)
                    if (unwrapped is LuaNameRef && unwrapped.text == paramName) return false
                }

                return true
            }

            private fun unwrapExpression(expr: PsiElement?): PsiElement? {
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
