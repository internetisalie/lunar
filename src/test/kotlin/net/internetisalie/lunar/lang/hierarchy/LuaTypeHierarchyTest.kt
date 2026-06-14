package net.internetisalie.lunar.lang.hierarchy

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.IndexedDocumentTest
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers NAV-06 type hierarchy at the tree-structure layer (the robust layer per the spec test
 * cases): build the supertype / subtype [com.intellij.ide.hierarchy.HierarchyTreeStructure] for a
 * target class and assert the first level of children contains the expected class declarations.
 *
 * Uses [IndexedDocumentTest] because class resolution and the subtype scan go through the stub index
 * ([net.internetisalie.lunar.lang.indexing.LuaClassNameIndex]).
 */
class LuaTypeHierarchyTest : IndexedDocumentTest() {

    private val source =
        """
        ---@class Base
        local Base = {}

        ---@class Derived : Base
        local Derived = {}
        """.trimIndent()

    /** TC-NAV-06-02: caret on `Derived` → the supertype tree contains `Base`. */
    @Test
    fun testSupertypesContainBase() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText("super.lua", source)
                val derived = classDecl("Derived")
                val structure = LuaSuperTypesHierarchyTreeStructure(derived, "Derived")
                assertTrue(
                    childClassNames(structure).contains("Base"),
                    "Supertype tree of Derived should contain Base",
                )
            }
        }
    }

    /** TC-NAV-06-01: caret on `Base` → the subtype tree contains `Derived`. */
    @Test
    fun testSubtypesContainDerived() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText("sub.lua", source)
                val base = classDecl("Base")
                val structure = LuaSubTypesHierarchyTreeStructure(base, "Base")
                assertTrue(
                    childClassNames(structure).contains("Derived"),
                    "Subtype tree of Base should contain Derived",
                )
            }
        }
    }

    /** A leaf class (no supertypes) yields no supertype children. */
    @Test
    fun testSupertypesOfRootIsEmpty() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText("root.lua", source)
                val base = classDecl("Base")
                val structure = LuaSuperTypesHierarchyTreeStructure(base, "Base")
                assertEquals(emptyList(), childClassNames(structure))
            }
        }
    }

    private fun classDecl(name: String): LuaLocalVarDecl =
        LuaHierarchyUtil.classDeclaration(myFixture.project, name)
            ?: error("No @class declaration indexed for $name")

    private fun childClassNames(structure: com.intellij.ide.hierarchy.HierarchyTreeStructure): List<String> =
        structure.getChildElements(structure.rootElement)
            .filterIsInstance<HierarchyNodeDescriptor>()
            .mapNotNull { it.psiElement as? LuaLocalVarDecl }
            .mapNotNull { LuaHierarchyUtil.className(it) }
}
