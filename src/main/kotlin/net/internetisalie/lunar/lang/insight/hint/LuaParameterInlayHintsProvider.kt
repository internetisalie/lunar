package net.internetisalie.lunar.lang.insight.hint

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.psi.types.LuaFunctionType
import net.internetisalie.lunar.lang.psi.types.LuaType
import net.internetisalie.lunar.lang.psi.types.LuaTypeMember
import net.internetisalie.lunar.lang.psi.types.LuaTypes
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor
import net.internetisalie.lunar.lang.psi.types.LuaUnionType
import net.internetisalie.lunar.project.PlatformLibraryIndex

class LuaParameterInlayHintsProvider : InlayHintsProvider {
    companion object {
        const val PROVIDER_ID = "lua.parameter.hints"
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
                    collectParameterHints(element, sink)
                }
            }

            private fun collectParameterHints(element: LuaFuncCall, sink: InlayTreeSink) {
                val nameAndArgs = element.nameAndArgsList.firstOrNull() ?: return
                if (isStdlibCall(element, nameAndArgs)) return

                val types = LuaTypesVisitor.getTypes(element)
                val functionType = resolveFunctionType(element, types) ?: return

                val args = nameAndArgs.args
                val argExprs = when {
                    args.string != null -> listOf(args.string!!)
                    args.exprList != null -> args.exprList?.exprList ?: emptyList()
                    args.tableConstructor != null -> listOf(args.tableConstructor!!)
                    else -> emptyList()
                }

                if (argExprs.isEmpty()) return

                val params = functionType.params
                val isColonCall = nameAndArgs.methodExpr != null

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

            private fun resolveFunctionType(element: LuaFuncCall, types: LuaTypes): LuaFunctionType? {
                val callee = LuaTypeInlayHintProvider.unwrapExpression(element.varOrExp) ?: element.varOrExp
                val nameAndArgs = element.nameAndArgsList.firstOrNull() ?: return null
                val methodExpr = nameAndArgs.methodExpr

                return if (methodExpr != null) {
                    resolveMethodCall(callee, methodExpr, types)
                } else {
                    resolveStandardCall(callee, types)
                }
            }

            private fun resolveMethodCall(callee: PsiElement, methodExpr: LuaMethodExpr, types: LuaTypes): LuaFunctionType? {
                val methodName = methodExpr.nameRef.text
                val receiverGraphType = types.getValueType(callee)
                val receiverType = types.graphTypeToLuaType(receiverGraphType)
                val member = resolveMemberFromType(receiverType, methodName)
                if (member != null) {
                    val extracted = extractFunctionType(member.type)
                    if (extracted != null) return extracted
                }

                val resolved = methodExpr.nameRef.reference?.resolve()
                val funcDecl = resolved?.parent?.parent as? LuaFuncDecl ?: return null
                val declFile = funcDecl.containingFile as? LuaFile ?: return null
                val declTypes = LuaTypesSnapshot.forFile(declFile)
                return extractFunctionType(declTypes.graphTypeToLuaType(declTypes.getValueType(funcDecl)))
            }

            private fun resolveStandardCall(callee: PsiElement, types: LuaTypes): LuaFunctionType? {
                val calleeGraphType = types.getValueType(callee)
                val calleeType = types.graphTypeToLuaType(calleeGraphType)
                val extracted = extractFunctionType(calleeType)
                if (extracted != null) return extracted

                val resolved = (callee as? LuaNameRef)?.reference?.resolve()
                val decl = resolved?.parent as? LuaLocalFuncDecl ?: resolved as? LuaFuncDecl ?: resolved?.parent?.parent as? LuaFuncDecl
                if (decl != null) {
                    val declFile = decl.containingFile as? LuaFile ?: return null
                    val declTypes = LuaTypesSnapshot.forFile(declFile)
                    return extractFunctionType(declTypes.graphTypeToLuaType(declTypes.getValueType(decl)))
                }
                return null
            }

            private fun resolveMemberFromType(type: LuaType, methodName: String): LuaTypeMember? {
                if (type is LuaUnionType) {
                    val resolved = type.resolveMember(methodName)
                    if (resolved != null) return resolved

                    for (variant in type.types) {
                        val member = resolveMemberFromType(variant, methodName)
                        if (member != null) return member
                    }
                    return null
                }
                return type.resolveMember(methodName)
            }

            private fun extractFunctionType(type: LuaType): LuaFunctionType? {
                if (type is LuaFunctionType) return type
                if (type is LuaUnionType) {
                    for (variant in type.types) {
                        val funcType = extractFunctionType(variant)
                        if (funcType != null) return funcType
                    }
                }
                return null
            }

            private fun isStdlibCall(element: LuaFuncCall, nameAndArgs: LuaNameAndArgs): Boolean {
                val methodExpr = nameAndArgs.methodExpr
                val callee = LuaTypeInlayHintProvider.unwrapExpression(element.varOrExp) ?: element.varOrExp
                val decl = if (methodExpr != null) {
                    methodExpr.nameRef.reference?.resolve()
                } else {
                    getDeclaration(callee)
                }
                return decl != null && isStdlibElement(decl)
            }

            private fun getDeclaration(callee: PsiElement): PsiElement? {
                if (callee is LuaNameRefElement) {
                    return callee.reference?.resolve()
                }
                if (callee is LuaVar) {
                    val suffixes = callee.varSuffixList
                    if (suffixes.isNotEmpty()) {
                        val lastSuffix = suffixes.last()
                        val nameRef = lastSuffix.indexExpr.nameRef
                        if (nameRef != null) {
                            return nameRef.reference?.resolve()
                        }
                    } else {
                        val nameRef = callee.nameRef
                        if (nameRef != null) {
                            return nameRef.reference?.resolve()
                        }
                    }
                }
                return null
            }

            private fun isStdlibElement(decl: PsiElement): Boolean {
                val file = decl.containingFile ?: return false
                val virtualFile = file.virtualFile ?: return false
                val platformLibraryFolder = PlatformLibraryIndex.getPlatformLibraryFolder(decl.project) ?: return false
                return com.intellij.openapi.vfs.VfsUtil.isAncestor(platformLibraryFolder, virtualFile, false)
            }
        }
    }
}
