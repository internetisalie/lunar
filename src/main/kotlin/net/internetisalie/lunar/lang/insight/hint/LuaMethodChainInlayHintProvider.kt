package net.internetisalie.lunar.lang.insight.hint

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypes
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor

/**
 * SYNTAX-07-07: shows the return type of intermediate calls in a multi-line fluent
 * method chain (`obj:setA()\n  :setB()` -> `: A` after each call).
 *
 * A whole `a:m1():m2()` expression is a single [LuaFuncCall] (`varOrExp` + `nameAndArgsList`),
 * so each chain step is one [LuaNameAndArgs]. We walk those steps, carrying the running
 * receiver class name, and emit a hint after each method call whose call begins on a
 * different line than its receiver (REQ-01/05).
 */
class LuaMethodChainInlayHintProvider : InlayHintsProvider {
    companion object {
        const val PROVIDER_ID = "lua.method.chain.hints"
        const val METHOD_CHAIN_OPTION_ID = "lua.method.chain"

        /** REQ-07: never resolve/traverse chains deeper than this many calls. */
        private const val MAX_CHAIN_DEPTH = 10

        private val UNRESOLVED_NAMES = setOf("any", "unknown", "void", "undefined", "nil")

        /** REQ-09: single returns that are usually obvious from the method name. */
        private val TRIVIAL_SINGLE_NAMES = setOf("boolean", "number", "nil")

        /** `function C:m` is indexed as `C:m`; `function C.m` as `C.m`. Try both. */
        private val METHOD_SEPARATORS = listOf(":", ".")
    }

    /** The resolved return type names of one step plus the class name to carry forward. */
    private data class StepType(val returnNames: List<String>, val nextReceiverClass: String?)

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
                if (element !is LuaFuncCall) return

                sink.whenOptionEnabled(METHOD_CHAIN_OPTION_ID) {
                    collectChainHints(element, document, sink)
                }
            }
        }
    }

    private fun collectChainHints(call: LuaFuncCall, document: Document, sink: InlayTreeSink) {
        val steps = call.nameAndArgsList
        if (steps.size < 2) return

        val types = LuaTypesVisitor.getTypes(call)
        val receiver = LuaTypeInlayHintProvider.unwrapExpression(call.varOrExp) ?: call.varOrExp
        var receiverClass = className(types.getValueType(receiver))
        var receiverStart = receiver.textRange.startOffset

        steps.take(MAX_CHAIN_DEPTH).forEach { step ->
            val methodName = step.methodExpr?.nameRef?.text
            if (methodName == null) {
                receiverStart = step.textRange.startOffset
                return@forEach
            }

            val stepType = resolveStepType(call.project, receiverClass, methodName)
            if (isMultiLineStep(step, receiverStart, document)) {
                emitHint(step, stepType.returnNames, receiverClass, sink)
            }

            receiverClass = stepType.nextReceiverClass ?: receiverClass
            receiverStart = step.textRange.startOffset
        }
    }

    private fun emitHint(step: LuaNameAndArgs, returnNames: List<String>, receiverClass: String?, sink: InlayTreeSink) {
        val hintText = formatReturnNames(returnNames, receiverClass) ?: return
        sink.addPresentation(
            InlineInlayPosition(step.textRange.endOffset, true),
            null,
            null,
            HintFormat.default,
        ) {
            text(": $hintText")
        }
    }

    /** REQ-01/05: a step qualifies only when its call starts on a line after its receiver's start. */
    private fun isMultiLineStep(step: LuaNameAndArgs, receiverStart: Int, document: Document): Boolean {
        val stepLine = document.getLineNumber(step.textRange.startOffset)
        val receiverLine = document.getLineNumber(receiverStart)
        return stepLine > receiverLine
    }

    /**
     * REQ-02/06/08: resolve a method's return type from the RECEIVER'S class, not the call site.
     * Method references resolve by qualified name from the receiver *text* (`b:m` -> key `b.m`),
     * so a copied receiver (`local b = Builder`) never resolves. Instead we look the method up by
     * its declared key `<class>:<method>` in the global-declaration stub index and read `---@return`.
     */
    private fun resolveStepType(project: Project, receiverClass: String?, methodName: String): StepType {
        if (receiverClass == null) return StepType(emptyList(), null)
        val funcDecl = findMethodDecl(project, receiverClass, methodName)
            ?: return StepType(emptyList(), receiverClass)

        // The `---@return` annotation keeps `self` and class names verbatim (the type graph
        // collapses or drops them when the name is not a resolvable class).
        val rawNames = annotatedReturnNames(funcDecl) ?: inferredReturnNames(funcDecl)
        val resolvedNames = rawNames.map { if (it == "self") receiverClass else it }
        val nextClass = resolvedNames.firstOrNull { it !in UNRESOLVED_NAMES } ?: receiverClass
        return StepType(resolvedNames, nextClass)
    }

    /** Look up `function <class>:<method>` (or `.<method>`) via the global-declaration stub index. */
    private fun findMethodDecl(project: Project, receiverClass: String, methodName: String): LuaFuncDecl? {
        val scope = GlobalSearchScope.allScope(project)
        for (separator in METHOD_SEPARATORS) {
            val key = "$receiverClass$separator$methodName"
            StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, key, project, scope, LuaFuncDecl::class.java)
                .firstOrNull()?.let { return it }
        }
        return null
    }

    /** Raw `---@return` type names, or null when the method has no return annotation. */
    private fun annotatedReturnNames(funcDecl: LuaFuncDecl): List<String>? {
        // Stub fast-path: index-loaded decls expose the first return type without de-stubbing.
        funcDecl.stub?.luacatsReturnType?.takeIf { it.isNotBlank() }?.let { return listOf(it.trim()) }
        val cats = funcDecl.catsComment ?: return null
        val tags = cats.getReturnTagList()
        if (tags.isEmpty()) return null
        return tags.flatMap { it.returnTypeDescriptorList }.map { it.argType.text.trim() }
    }

    /** Fallback: read the method's inferred graph return type when no annotation is present. */
    private fun inferredReturnNames(funcDecl: LuaFuncDecl): List<String> {
        val declFile = funcDecl.containingFile as? LuaFile ?: return emptyList()
        val declTypes = LuaTypesSnapshot.forFile(declFile)
        val funcType = declTypes.getValueType(funcDecl) as? LuaGraphType.Function ?: return emptyList()
        return returnTypeNames(funcType, declTypes)
    }

    /** Mirror [LuaTypeInlayHintProvider]'s graph-return mapping so multi-returns are preserved. */
    private fun returnTypeNames(funcType: LuaGraphType.Function, declTypes: LuaTypes): List<String> {
        val names = funcType.returns.map { node ->
            val t = if (node.write != LuaGraphType.Undefined) node.write else node.read
            declTypes.graphTypeToLuaType(t).name
        }
        val lastSignificant = names.indexOfLast { it !in UNRESOLVED_NAMES }
        return if (lastSignificant >= 0) names.take(lastSignificant + 1) else emptyList()
    }

    /** REQ-03/04/09: join meaningful names, dropping a lone trivial primitive. */
    private fun formatReturnNames(returnNames: List<String>, receiverClass: String?): String? {
        val resolved = returnNames.map { if (it == "self") receiverClass ?: it else it }
        val meaningful = resolved.filterNot { it in UNRESOLVED_NAMES }
        if (meaningful.isEmpty()) return null
        if (meaningful.size == 1 && meaningful.first() in TRIVIAL_SINGLE_NAMES) return null
        return meaningful.joinToString(", ")
    }

    /**
     * The receiver's class name. A `@class` local infers as a union of the table literal and the
     * class (`{ ... } | Builder`), so dig the first named class out of a union rather than using
     * the union's display text.
     */
    private fun className(type: LuaGraphType): String? = when (type) {
        is LuaGraphType.Table -> type.className
        is LuaGraphType.Generic -> type.name
        is LuaGraphType.Union -> type.types.firstNotNullOfOrNull { className(it) }
        else -> type.displayName().takeIf { it !in UNRESOLVED_NAMES }
    }
}
