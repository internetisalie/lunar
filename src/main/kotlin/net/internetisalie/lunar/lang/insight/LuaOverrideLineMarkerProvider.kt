package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypeMember
import net.internetisalie.lunar.lang.psi.types.LuaTypeReference

/**
 * Adds an overriding/implementing gutter icon to a `function Class:method` (or `function Class.fn`)
 * declaration whose name also exists on a supertype of `Class`, navigating to the super definition.
 *
 * Implements NAV-05 (method override / implement markers). The hierarchy walk lives in
 * [findSuperMembers], a reusable primitive that NAV-06 (type hierarchy) shares.
 */
class LuaOverrideLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        val identifier = methodNameIdentifier(element) ?: return
        val decl = PsiTreeUtil.getParentOfType(identifier, LuaFuncDecl::class.java) ?: return
        val receiverName = decl.funcName.nameRef.text
        val superMembers = findSuperMembers(receiverName, identifier.text, decl)
        if (superMembers.isEmpty()) return
        result.add(markerFor(identifier, superMembers))
    }

    /**
     * The method-name `IDENTIFIER` leaf when [element] is that leaf of a `function Class:method`
     * (a `:` method) or `function Class.fn` (a dotted member); else null. Attaching to the leaf
     * keeps one marker per method and satisfies the platform's leaf-only line-marker rule.
     */
    private fun methodNameIdentifier(element: PsiElement): PsiElement? {
        if (element.node?.elementType != LuaElementTypes.IDENTIFIER) return null
        val decl = PsiTreeUtil.getParentOfType(element, LuaFuncDecl::class.java) ?: return null
        return if (memberNameLeaf(decl.funcName) === element) element else null
    }

    /** The leaf naming the member: the `:method` leaf, else the last `.property` leaf; null for a bare `function f()`. */
    private fun memberNameLeaf(funcName: LuaFuncName): PsiElement? {
        funcName.funcNameMethod?.let { return it.nameRef.identifier }
        return funcName.funcNamePropertyList.lastOrNull()?.nameRef?.identifier
    }

    private fun markerFor(identifier: PsiElement, superMembers: List<LuaTypeMember>): RelatedItemLineMarkerInfo<*> {
        val implements = superMembers.any { isAbstractMember(it) }
        val icon = if (implements) AllIcons.Gutter.ImplementingMethod else AllIcons.Gutter.OverridingMethod
        val tooltip = LuaBundle.message(
            if (implements) "gutter.implementing.method" else "gutter.overriding.method",
        )
        return NavigationGutterIconBuilder.create(icon)
            .setTargets(superMembers.mapNotNull { it.sourceElement })
            .setTooltipText(tooltip)
            .createLineMarkerInfo(identifier)
    }

    /**
     * A super member is "abstract" (→ Implement) when it is a `@field`-declared function signature
     * (declaration only); a concrete `function Super:method() end` (→ Override) has a [LuaFuncDecl]
     * as its `sourceElement`. [LuaTypeMember] carries no `isAbstract`, so we derive it from the
     * source PSI: method declarations materialize with a [LuaFuncDecl] source, `@field`s do not.
     */
    private fun isAbstractMember(member: LuaTypeMember): Boolean = member.sourceElement !is LuaFuncDecl

    companion object {
        /**
         * Resolve [className] to a class type and collect every member named [methodName] declared on
         * a *supertype* (not on [className] itself), walking the inheritance graph with a visited-set
         * cycle guard. Each returned [LuaTypeMember.sourceElement] is a navigation target (the super
         * `function …` decl or `@field` tag).
         *
         * Reusable primitive: NAV-05 uses it for override/implement markers; NAV-06 reuses it to walk
         * the super hierarchy from a method. [context] supplies the resolution scope.
         */
        fun findSuperMembers(className: String, methodName: String, context: PsiElement): List<LuaTypeMember> {
            val classType = LuaTypeManager.getInstance(context.project)
                .resolveType(className, context) as? LuaClassType ?: return emptyList()
            val members = mutableListOf<LuaTypeMember>()
            val visited = mutableSetOf(className)
            for (superType in classType.superTypes) {
                collectMember(superType, methodName, context, visited, members)
            }
            return members
        }

        private fun collectMember(
            superType: LuaType,
            methodName: String,
            context: PsiElement,
            visited: MutableSet<String>,
            sink: MutableList<LuaTypeMember>,
        ) {
            val resolved = resolveClass(superType, context) ?: return
            if (!visited.add(resolved.name)) return
            resolved.localMembers[methodName]?.let { sink.add(it) }
            for (grandSuper in resolved.superTypes) {
                collectMember(grandSuper, methodName, context, visited, sink)
            }
        }

        private fun resolveClass(type: LuaType, context: PsiElement): LuaClassType? = when (type) {
            is LuaClassType -> type
            is LuaTypeReference -> type.resolveType() as? LuaClassType
            else -> LuaTypeManager.getInstance(context.project).resolveType(type.name, context) as? LuaClassType
        }
    }
}
