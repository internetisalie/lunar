package net.internetisalie.lunar.lang.psi

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.LuaScopeProcessor
import net.internetisalie.lunar.lang.indexing.memberFieldIdentifier
import net.internetisalie.lunar.lang.path.resolveModuleCandidates
import net.internetisalie.lunar.lang.psi.types.LuaMemberDeclarations
import net.internetisalie.lunar.lang.psi.types.LuaPrimitiveType
import net.internetisalie.lunar.lang.psi.types.LuaType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypeMember
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor
import net.internetisalie.lunar.lang.psi.types.LuaUnionType
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicInteger

/**
 * MAINT-53: resolves a member name — `b` in `a.b`, `m` in `a.b:m()` — through the inferred type of
 * its receiver rather than the receiver's text.
 *
 * Every shape the chain walk does not model returns null or empty, so the caller falls back to text
 * (MAINT-53-08). Bare-receiver colon calls (`t:m()`) stay with [LuaColonCallResolution].
 *
 * Threading: a read action, inherited from `PsiReference` resolution. Stateless, so no `Project`,
 * `PsiFile` or `Editor` is retained; `LuaTypesSnapshot.forFile` is the only cache.
 *
 * See `docs/features/maint/53-member-resolution-by-receiver-type/design.md` §2.1, §3.4 to §3.6.
 */
object LuaMemberResolution {
    private data class MemberChain(
        val head: LuaNameRef,
        val steps: List<String>,
    )

    /** MAINT-53-09 observation seam: counts [typedTargets] calls made while the file's snapshot is being built. */
    @VisibleForTesting
    internal val TYPED_DURING_BUILD = AtomicInteger()

    /** True for the member name of an index expression or a colon call. */
    fun isMemberName(element: PsiElement): Boolean =
        element is LuaNameRef && (element.parent is LuaIndexExpr || element.parent is LuaMethodExpr)

    /** True for the `m` of `a.b:m()`: a colon call whose receiver is suffixed. */
    fun isSuffixedColonCall(nameRef: LuaNameRef): Boolean =
        nameRef.parent is LuaMethodExpr && chainOf(nameRef)?.steps?.isNotEmpty() == true

    /** The declarations of [nameRef] in its receiver's type, or empty when the type does not declare it. */
    fun typedTargets(nameRef: LuaNameRef): List<PsiElement> {
        if (LuaTypesVisitor.isSnapshotUnderConstruction(nameRef.containingFile)) {
            TYPED_DURING_BUILD.incrementAndGet()
        }
        val chain = chainOf(nameRef) ?: return emptyList()
        val receiver = receiverType(chain) ?: return emptyList()
        return membersNamed(receiver, nameRef.text).mapNotNull(::declarationTarget).distinct()
    }

    /** True when the receiver chain of [nameRef] starts at a local initialised from a table constructor. */
    fun isConstructedLocalHead(nameRef: LuaNameRef): Boolean {
        val chain = chainOf(nameRef) ?: return false
        val binding = bindingOf(chain.head) ?: return false
        return initializerOf(binding)?.text?.startsWith("{") == true
    }

    private fun chainOf(nameRef: LuaNameRef): MemberChain? =
        when (val parent = nameRef.parent) {
            is LuaIndexExpr -> dotChainOf(parent)
            is LuaMethodExpr -> colonChainOf(parent)
            else -> null
        }

    private fun dotChainOf(indexExpr: LuaIndexExpr): MemberChain? {
        val suffix = indexExpr.parent as? LuaVarSuffix ?: return null
        if (suffix.nameAndArgsList.isNotEmpty()) return null
        val luaVar = suffix.parent as? LuaVar ?: return null
        val head = luaVar.nameRef ?: return null
        val before = luaVar.varSuffixList.takeWhile { it !== suffix }
        return stepsOf(before)?.let { MemberChain(head, it) }
    }

    private fun colonChainOf(methodExpr: LuaMethodExpr): MemberChain? {
        val nameAndArgs = methodExpr.parent as? LuaNameAndArgs ?: return null
        val call = nameAndArgs.parent as? LuaFuncCall ?: return null
        if (call.nameAndArgsList.firstOrNull() !== nameAndArgs) return null
        val luaVar = call.varOrExp.`var` ?: return null
        val head = luaVar.nameRef ?: return null
        return stepsOf(luaVar.varSuffixList)?.let { MemberChain(head, it) }
    }

    private fun stepsOf(suffixes: List<LuaVarSuffix>): List<String>? =
        suffixes.map { suffix ->
            if (suffix.nameAndArgsList.isNotEmpty()) return null
            suffix.indexExpr.nameRef?.text ?: return null
        }

    private fun receiverType(chain: MemberChain): LuaType? {
        var current = headType(chain.head) ?: return null
        for (step in chain.steps) {
            ProgressManager.checkCanceled()
            val member = membersNamed(current, step).firstOrNull { it.sourceElement != null } ?: return null
            current = member.type.takeIf(::isKnown) ?: return null
        }
        return current
    }

    private fun membersNamed(
        type: LuaType,
        name: String,
    ): List<LuaTypeMember> {
        val members = mutableListOf<LuaTypeMember>()
        type.resolveMember(name)?.let { members += it }
        if (type is LuaUnionType) type.types.forEach { arm -> arm.resolveMember(name)?.let { members += it } }
        return members
    }

    private fun isKnown(type: LuaType?): Boolean =
        type != null && type != LuaPrimitiveType.UNKNOWN && type != LuaPrimitiveType.ANY

    private fun declarationTarget(member: LuaTypeMember): PsiElement? {
        val anchor = member.sourceElement ?: return null
        return when (val declaration = LuaMemberDeclarations.declarationOf(member)) {
            is LuaFuncDecl -> declaration
            is LuaAssignmentStatement ->
                PsiTreeUtil.getParentOfType(anchor as? LuaIndexExpr, LuaVar::class.java)?.let(::memberFieldIdentifier)
            is LuaVar -> memberFieldIdentifier(declaration)
            else -> null
        }
    }

    private fun bindingOf(head: LuaNameRef): PsiElement? {
        val processor = LuaScopeProcessor(head.text)
        LuaResolveUtil.scopeCrawlUp(processor, head)
        return processor.result
    }

    private fun headType(head: LuaNameRef): LuaType? {
        val binding = bindingOf(head)
        if (binding == null) {
            val global = LuaTypeManager.getInstance(head.project).resolveGlobal(head.text, head)
            if (isKnown(global)) return global
        } else {
            requiredModuleType(binding)?.let { return it }
        }
        val types = LuaTypesSnapshot.forFile(head.containingFile)
        return types.graphTypeToLuaType(types.getValueType(head)).takeIf(::isKnown)
    }

    private fun initializerOf(binding: PsiElement): LuaExpr? {
        val declaration = PsiTreeUtil.getParentOfType(binding, LuaLocalVarDecl::class.java) ?: return null
        val index = declaration.attNameList.indexOfFirst { it.nameRef.identifier === binding }
        if (index < 0) return null
        return declaration.exprList?.exprList?.getOrNull(index)
    }

    private fun requiredModuleName(binding: PsiElement): String? {
        val call = initializerOf(binding) as? LuaFuncCall ?: return null
        if (call.varOrExp.text != "require") return null
        val args = call.nameAndArgsList.firstOrNull()?.args ?: return null
        val text =
            args.string?.text ?: args.exprList
                ?.exprList
                ?.singleOrNull()
                ?.text ?: return null
        return text.trim('"', '\'')
    }

    private fun requiredModuleType(binding: PsiElement): LuaType? {
        val moduleName = requiredModuleName(binding) ?: return null
        val module = resolveModuleCandidates(binding.project, moduleName).firstOrNull() ?: return null
        if (LuaTypesVisitor.isSnapshotUnderConstruction(module)) return null
        val returned = topLevelReturnOf(module) ?: return null
        val types = LuaTypesSnapshot.forFile(module)
        val anchor =
            PsiTreeUtil
                .findChildOfType(returned, LuaNameRef::class.java)
                ?.takeIf { returned.text.all { c -> c.isLetterOrDigit() || c == '_' } } ?: returned
        return types.graphTypeToLuaType(types.getValueType(anchor)).takeIf(::isKnown)
    }

    private fun topLevelReturnOf(module: LuaFile): LuaExpr? =
        PsiTreeUtil
            .findChildrenOfType(module, LuaFinalStatement::class.java)
            .lastOrNull { statement ->
                PsiTreeUtil.getParentOfType(
                    statement,
                    LuaFuncDecl::class.java,
                    LuaFuncDef::class.java,
                    LuaLocalFuncDecl::class.java,
                ) == null
            }?.exprList
            ?.exprList
            ?.firstOrNull()
}
