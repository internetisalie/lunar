package net.internetisalie.lunar.lang.hierarchy

import com.intellij.ide.hierarchy.HierarchyBrowserManager
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaPsiImplUtil

/**
 * Shared primitives for the Lua type-hierarchy views (NAV-06): mapping a `@class` declaration to its
 * LuaCATS class name and back, plus the node comparator. Kept separate from the tree structures so
 * both the supertype and subtype scans share one source of truth for class identity.
 */
object LuaHierarchyUtil {

    /**
     * The LuaCATS class name declared by [decl] (the `Name` in `---@class Name`), read from the stub
     * when available and falling back to the cats comment for AST-backed (in-edit) declarations.
     */
    fun className(decl: LuaLocalVarDecl): String? {
        decl.stub?.luacatsClassName?.let { return it }
        val cats = LuaPsiImplUtil.getCatsComment(decl) ?: return null
        return cats.classTagList.firstOrNull()?.argType?.text
    }

    /**
     * Every `@class` declaration named [name] in the project, via [LuaClassNameIndex] (keyed on the
     * class name). Usually one; multiple partial declarations are possible.
     */
    fun classDeclarations(project: Project, name: String): Collection<LuaLocalVarDecl> =
        StubIndex.getElements(
            LuaClassNameIndex.KEY,
            name,
            project,
            GlobalSearchScope.projectScope(project),
            LuaLocalVarDecl::class.java,
        )

    /** The first declaring [LuaLocalVarDecl] for class [name], or null if none is indexed. */
    fun classDeclaration(project: Project, name: String): LuaLocalVarDecl? =
        classDeclarations(project, name).firstOrNull()

    /** All class names indexed in the project (the subtype-scan domain, per design §3.2). */
    fun allClassNames(project: Project): Collection<String> =
        StubIndex.getInstance().getAllKeys(LuaClassNameIndex.KEY, project)

    private val BY_INDEX: Comparator<NodeDescriptor<*>?> = Comparator.comparingInt { it?.index ?: 0 }

    fun comparator(project: Project): Comparator<NodeDescriptor<*>?> =
        if (HierarchyBrowserManager.getInstance(project).state?.SORT_ALPHABETICALLY == true) {
            AlphaComparator.getInstance()
        } else {
            BY_INDEX
        }
}
