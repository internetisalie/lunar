package net.internetisalie.lunar.lang.insight.hint

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.psi.types.LuaFunctionType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor

class LuaParameterInlayHintsProvider : InlayHintsProvider {
    companion object {
        const val PROVIDER_ID = "lua.parameter.hints"
        const val PARAMETER_NAME_OPTION_ID = "lua.parameter.name"
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

                if (element is LuaFuncCall) {
                    sink.whenOptionEnabled(PARAMETER_NAME_OPTION_ID) {
                        collectParameterHints(element, sink)
                    }
                }
            }

            private fun collectParameterHints(element: LuaFuncCall, sink: InlayTreeSink) {
                val types = LuaTypesVisitor.getTypes(element)
                val callee = LuaTypeInlayHintProvider.unwrapExpression(element.varOrExp) ?: element.varOrExp

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
                                val declFile = funcDecl.containingFile as? LuaFile
                                if (declFile != null) {
                                    val declTypes = LuaTypesSnapshot.forFile(declFile)
                                    declTypes.graphTypeToLuaType(declTypes.getValueType(funcDecl)) as? LuaFunctionType
                                } else null
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
                            val declFile = decl.containingFile as? LuaFile
                            if (declFile != null) {
                                val declTypes = LuaTypesSnapshot.forFile(declFile)
                                declTypes.graphTypeToLuaType(declTypes.getValueType(decl)) as? LuaFunctionType
                            } else null
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

                        if (LuaTypeInlayHintProvider.shouldShowHint(paramName, argExpr)) {
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
        }
    }
}
