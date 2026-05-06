package net.internetisalie.lunar.lang.insight.hint

import com.intellij.lang.parameterInfo.*
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.insight.LuaBindingsVisitor
import net.internetisalie.lunar.lang.psi.*

class LuaParameterInfoHandler : ParameterInfoHandler<LuaArgs, LuaParameterInfoHandler.LuaParameterInfoCandidate> {

    data class LuaParameterInfoCandidate(
        val name: String,
        val params: List<String>,
        val types: List<String?>,
        val isMethod: Boolean = false
    )

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): LuaArgs? {
        val file = context.file
        val offset = context.offset

        // Find LuaArgs at or before offset
        var element = file.findElementAt(offset)
        if (element == null && offset > 0) element = file.findElementAt(offset - 1)
        if (element == null) return null

        var args = PsiTreeUtil.getParentOfType(element, LuaArgs::class.java, false)
        if (args == null && element.parent is com.intellij.psi.PsiErrorElement) {
            val funcCall = PsiTreeUtil.getParentOfType(element, LuaFuncCall::class.java)
            if (funcCall != null) {
                args = PsiTreeUtil.findChildOfType(funcCall, LuaArgs::class.java)
            }
        }
        if (args == null) return null

        val nameAndArgs = args.parent as? LuaNameAndArgs ?: return null
        val funcCall = nameAndArgs.parent as? LuaFuncCall ?: return null

        val candidates = resolveCandidates(funcCall, nameAndArgs)
        if (candidates.isEmpty()) return null

        context.itemsToShow = candidates.toTypedArray()
        return args
    }

    private fun resolveCandidates(funcCall: LuaFuncCall, nameAndArgs: LuaNameAndArgs): List<LuaParameterInfoCandidate> {
        val methodExpr = nameAndArgs.methodExpr
        val target = if (methodExpr != null) {
            methodExpr.nameRef
        } else {
            val index = funcCall.nameAndArgsList.indexOf(nameAndArgs)
            if (index == 0) funcCall.varOrExp else null
        } ?: return emptyList()

        val identifier = findIdentifier(target) ?: return emptyList()

        val bindings = LuaBindingsVisitor.getBindingsWithImports(funcCall)
        val reference = bindings.lookup(identifier)
        val boundElement = reference?.binding?.element ?: (identifier.parent as? com.intellij.psi.PsiReference)?.resolve() ?: return emptyList()

        // Find the actual declaration containing this identifier
        val definition = PsiTreeUtil.getParentOfType(boundElement, LuaFuncDecl::class.java, false)
            ?: PsiTreeUtil.getParentOfType(boundElement, LuaLocalFuncDecl::class.java, false)
            ?: return emptyList()

        val candidates = mutableListOf<LuaParameterInfoCandidate>()

        when (definition) {
            is LuaFuncDecl -> {
                val comment = definition.catsComment
                val name = definition.funcName.text
                val isMethod = definition.funcName.funcNameMethod != null

                if (comment != null) {
                    // Add main signature
                    if (comment.paramTagList.isNotEmpty()) {
                        val params = mutableListOf<String>()
                        val types = mutableListOf<String?>()
                        if (isMethod) {
                            params.add("self")
                            types.add(null)
                        }
                        params.addAll(comment.paramTagList.map { it.argName?.text ?: "..." })
                        types.addAll(comment.paramTagList.map { it.argType.text })
                        candidates.add(LuaParameterInfoCandidate(name, params, types, isMethod))
                    } else {
                        val params = mutableListOf<String>()
                        if (isMethod) params.add("self")
                        params.addAll(definition.parList?.nameList?.nameRefList?.map { it.text } ?: emptyList())
                        if (definition.parList?.node?.findChildByType(LuaElementTypes.ELLIPSIS) != null) {
                            params.add("...")
                        }
                        candidates.add(LuaParameterInfoCandidate(name, params, List(params.size) { null }, isMethod))
                    }

                    // Add overloads
                    for (overload in comment.overloadTagList) {
                        val signature = overload.overloadFunctionSignature
                        val params = mutableListOf<String>()
                        val types = mutableListOf<String?>()
                        if (isMethod) {
                            params.add("self")
                            types.add(null)
                        }
                        params.addAll(signature.functionSignatureArgumentList.map { it.argName.text })
                        types.addAll(signature.functionSignatureArgumentList.map { it.argType.text })
                        candidates.add(LuaParameterInfoCandidate(name, params, types, isMethod))
                    }
                } else {
                    val params = mutableListOf<String>()
                    if (isMethod) params.add("self")
                    params.addAll(definition.parList?.nameList?.nameRefList?.map { it.text } ?: emptyList())
                    if (definition.parList?.node?.findChildByType(LuaElementTypes.ELLIPSIS) != null) {
                        params.add("...")
                    }
                    candidates.add(LuaParameterInfoCandidate(name, params, List(params.size) { null }, isMethod))
                }
            }
            is LuaLocalFuncDecl -> {
                val comment = definition.catsComment
                val name = definition.nameRef.text

                if (comment != null) {
                    // Add main signature
                    if (comment.paramTagList.isNotEmpty()) {
                        val params = comment.paramTagList.map { it.argName?.text ?: "..." }
                        val types = comment.paramTagList.map { it.argType.text }
                        candidates.add(LuaParameterInfoCandidate(name, params, types))
                    } else {
                        val params = mutableListOf<String>()
                        params.addAll(definition.parList?.nameList?.nameRefList?.map { it.text } ?: emptyList())
                        if (definition.parList?.node?.findChildByType(LuaElementTypes.ELLIPSIS) != null) {
                            params.add("...")
                        }
                        candidates.add(LuaParameterInfoCandidate(name, params, List(params.size) { null }))
                    }

                    // Add overloads
                    for (overload in comment.overloadTagList) {
                        val signature = overload.overloadFunctionSignature
                        val params = signature.functionSignatureArgumentList.map { it.argName.text }
                        val types = signature.functionSignatureArgumentList.map { it.argType.text }
                        candidates.add(LuaParameterInfoCandidate(name, params, types))
                    }
                } else {
                    val params = mutableListOf<String>()
                    params.addAll(definition.parList?.nameList?.nameRefList?.map { it.text } ?: emptyList())
                    if (definition.parList?.node?.findChildByType(LuaElementTypes.ELLIPSIS) != null) {
                        params.add("...")
                    }
                    candidates.add(LuaParameterInfoCandidate(name, params, List(params.size) { null }))
                }
            }
        }

        return candidates
    }

    private fun findIdentifier(element: PsiElement): PsiElement? {
        if (element.elementType == LuaElementTypes.IDENTIFIER) return element
        if (element is LuaNameRef) return element.identifier
        return PsiTreeUtil.findChildOfType(element, LuaNameRef::class.java)?.identifier
    }

    override fun showParameterInfo(element: LuaArgs, context: CreateParameterInfoContext) {
        context.showHint(element, element.textOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): LuaArgs? {
        val file = context.file
        val offset = context.offset
        var element = file.findElementAt(offset)
        if (element == null && offset > 0) element = file.findElementAt(offset - 1)
        if (element == null) return null

        return PsiTreeUtil.getParentOfType(element, LuaArgs::class.java, false)
    }

    override fun updateParameterInfo(parameterOwner: LuaArgs, context: UpdateParameterInfoContext) {
        val offset = context.offset
        val exprList = parameterOwner.exprList

        if (exprList == null) {
            context.setCurrentParameter(0)
            return
        }

        val exprs = exprList.exprList
        var index = 0
        for (expr in exprs) {
            if (offset > expr.textRange.endOffset) {
                index++
            } else {
                break
            }
        }

        context.setCurrentParameter(index)
    }

    override fun updateUI(p: LuaParameterInfoCandidate, context: ParameterInfoUIContext) {
        val sb = StringBuilder()
        sb.append(p.name).append("(")

        var start = -1
        var end = -1

        val args = context.parameterOwner as? LuaArgs
        val funcCall = PsiTreeUtil.getParentOfType(args, LuaFuncCall::class.java)
        val isColonCall = funcCall?.nameAndArgsList?.any { it.methodExpr != null } ?: false

        var paramIndex = 0
        p.params.forEachIndexed { index, name ->
            // If the definition is a method (Obj:method), it has 'self'.
            // If the call is a colon call (Obj:method()), 'self' is passed implicitly.
            if (p.isMethod && name == "self" && isColonCall) return@forEachIndexed

            if (paramIndex > 0) sb.append(", ")

            if (paramIndex == context.currentParameterIndex) {
                start = sb.length
            }

            sb.append(name)
            val type = p.types.getOrNull(index)
            if (type != null) {
                sb.append(": ").append(type)
            }

            if (paramIndex == context.currentParameterIndex) {
                end = sb.length
            }
            paramIndex++
        }
        sb.append(")")

        context.setupUIComponentPresentation(
            sb.toString(),
            start,
            end,
            false,
            false,
            false,
            context.defaultParameterColor
        )
    }
}
