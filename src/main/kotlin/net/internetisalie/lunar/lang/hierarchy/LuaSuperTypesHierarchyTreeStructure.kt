package net.internetisalie.lunar.lang.hierarchy

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypeReference

/**
 * Supertype tree (NAV-06-02): the children of a `@class` node are its declared parent classes.
 *
 * Mirrors `com.jetbrains.python.hierarchy.treestructures.PySuperTypesHierarchyTreeStructure`. Each
 * child is resolved lazily by [buildChildren] (the platform expands one level at a time), so the
 * full transitive chain appears as the user drills down, with cycle safety provided by the type
 * engine's own visited guards plus the natural lazy expansion.
 */
class LuaSuperTypesHierarchyTreeStructure(decl: LuaLocalVarDecl, name: String) :
    HierarchyTreeStructure(decl.project, LuaHierarchyNodeDescriptor(null, decl, name, true)) {

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any?> {
        val decl = descriptor.psiElement as? LuaLocalVarDecl ?: return emptyArray()
        val className = LuaHierarchyUtil.className(decl) ?: return emptyArray()
        val classType = LuaTypeManager.getInstance(decl.project)
            .resolveType(className, decl) as? LuaClassType ?: return emptyArray()

        val children = mutableListOf<HierarchyNodeDescriptor>()
        val seen = mutableSetOf(className)
        for (superType in classType.superTypes) {
            val superName = resolvedName(superType) ?: continue
            if (!seen.add(superName)) continue
            val superDecl = LuaHierarchyUtil.classDeclaration(decl.project, superName) ?: continue
            children.add(LuaHierarchyNodeDescriptor(descriptor, superDecl, superName, false))
        }
        return children.toTypedArray()
    }

    /** The class name of a (possibly lazy) supertype reference, resolved back to a [LuaClassType]. */
    private fun resolvedName(superType: LuaType): String? = when (superType) {
        is LuaClassType -> superType.name
        is LuaTypeReference -> (superType.resolveType() as? LuaClassType)?.name ?: superType.name
        else -> superType.name
    }
}
