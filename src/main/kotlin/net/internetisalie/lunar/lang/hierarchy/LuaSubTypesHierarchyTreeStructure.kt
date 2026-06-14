package net.internetisalie.lunar.lang.hierarchy

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypeReference

/**
 * Subtype tree (NAV-06-01): the children of a `@class` node are the classes that declare it as a
 * (direct) supertype. No reverse index exists, so [buildChildren] scans every indexed class name
 * (design §3.2; a dedicated reverse index is the deferred NAV-06-DR-01) and keeps those whose
 * `superTypes` include the node's class.
 *
 * Mirrors `com.jetbrains.python.hierarchy.treestructures.PySubTypesHierarchyTreeStructure`, which
 * delegates the reverse walk to a search; here the walk is the index scan.
 */
class LuaSubTypesHierarchyTreeStructure(decl: LuaLocalVarDecl, name: String) :
    HierarchyTreeStructure(decl.project, LuaHierarchyNodeDescriptor(null, decl, name, true)) {

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any?> {
        val decl = descriptor.psiElement as? LuaLocalVarDecl ?: return emptyArray()
        val targetName = LuaHierarchyUtil.className(decl) ?: return emptyArray()
        val project = decl.project
        val typeManager = LuaTypeManager.getInstance(project)

        val children = mutableListOf<HierarchyNodeDescriptor>()
        for (candidateName in LuaHierarchyUtil.allClassNames(project)) {
            ProgressManager.checkCanceled()
            if (candidateName == targetName) continue
            val candidate = typeManager.resolveType(candidateName, decl) as? LuaClassType ?: continue
            if (!isDirectSubtypeOf(candidate, targetName)) continue
            val candidateDecl = subtypeDecl(project, candidateName, decl) ?: continue
            children.add(LuaHierarchyNodeDescriptor(descriptor, candidateDecl, candidateName, false))
        }
        return children.toTypedArray()
    }

    /** True when [candidate] lists a supertype that resolves (by name) to [targetName]. */
    private fun isDirectSubtypeOf(candidate: LuaClassType, targetName: String): Boolean =
        candidate.superTypes.any { superTypeName(it) == targetName }

    private fun superTypeName(type: LuaType): String = when (type) {
        is LuaClassType -> type.name
        is LuaTypeReference -> (type.resolveType() as? LuaClassType)?.name ?: type.name
        else -> type.name
    }

    /** The declaring element to wrap for [candidateName]; prefers the in-file decl for navigation. */
    private fun subtypeDecl(project: Project, candidateName: String, context: PsiElement): LuaLocalVarDecl? =
        LuaHierarchyUtil.classDeclarations(project, candidateName)
            .firstOrNull { it.containingFile == context.containingFile }
            ?: LuaHierarchyUtil.classDeclaration(project, candidateName)
}
