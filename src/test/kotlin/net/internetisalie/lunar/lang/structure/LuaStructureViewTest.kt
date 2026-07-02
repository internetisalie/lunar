package net.internetisalie.lunar.lang.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFinalStatement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLabel
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaStructureViewTest : BasePlatformTestCase() {

    private fun luaFile(text: String): LuaFile =
        myFixture.configureByText("test.lua", text.trimIndent()) as LuaFile

    private fun rootChildren(file: LuaFile): List<TreeElement> =
        LuaFileStructureViewTreeElement(file).children.toList()

    // Phase 1: Factory & Model wiring — MAINT-11-01

    @Test
    fun testFactoryProducesLuaStructureViewModel() {
        val luaSource = luaFile("x = 1")
        val structureBuilder = LuaStructureViewFactory().getStructureViewBuilder(luaSource)
        assertInstanceOf(structureBuilder, TreeBasedStructureViewBuilder::class.java)
        val structureModel = (structureBuilder as TreeBasedStructureViewBuilder).createStructureViewModel(null)
        assertInstanceOf(structureModel, LuaStructureViewModel::class.java)
    }

    @Test
    fun testModelRootSortersAndSuitableClasses() {
        val luaSource = luaFile("x = 1")
        val structureModel = LuaStructureViewModel(luaSource)
        assertInstanceOf(structureModel.root, LuaFileStructureViewTreeElement::class.java)
        assertTrue(structureModel.sorters.any { it === Sorter.ALPHA_SORTER })
        assertSameElements(
            structureModel.SUITABLE_CLASSES.toList(),
            LuaFile::class.java,
            LuaLabel::class.java,
            LuaFuncDecl::class.java,
            LuaLocalFuncDecl::class.java,
            LuaFinalStatement::class.java,
        )
    }

    // Phase 2: File-level outline mapping — MAINT-11-02

    @Test
    fun testFilePresentableTextIsFileName() {
        val luaSource = luaFile("x = 1")
        val fileElement = LuaFileStructureViewTreeElement(luaSource)
        assertEquals("test.lua", fileElement.presentation.presentableText)
    }

    @Test
    fun testTopLevelStatementsMapToNodeTypesInOrder() {
        val luaSource = luaFile(
            """
            function foo() end
            local function bar() end
            local v = 1
            ::done::
            return 1
            """,
        )
        val children = rootChildren(luaSource)
        assertEquals(5, children.size)
        assertInstanceOf(children[0], LuaFunctionStructureViewTreeElement::class.java)
        assertInstanceOf(children[1], LuaLocalFunctionStructureViewTreeElement::class.java)
        assertInstanceOf(children[2], LuaLocalVariableStructureViewTreeElement::class.java)
        assertInstanceOf(children[3], LuaLabelStructureViewTreeElement::class.java)
        assertInstanceOf(children[4], LuaReturnStructureViewTreeElement::class.java)
    }

    @Test
    fun testMultipleLocalVarsBecomeSeparateNodes() {
        val luaSource = luaFile("local a, b = 1, 2")
        val variableNodes = rootChildren(luaSource)
            .filterIsInstance<LuaLocalVariableStructureViewTreeElement>()
        assertEquals(2, variableNodes.size)
        assertEquals("a", variableNodes[0].presentation.presentableText)
        assertEquals("b", variableNodes[1].presentation.presentableText)
    }
}
