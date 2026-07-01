package net.internetisalie.lunar.lang.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaFileType

class LuaPsiUtilsTest : BasePlatformTestCase() {

    /** TC3: nodeType on an element with no underlying ASTNode returns null, not an NPE. */
    fun testNodeTypeReturnsNullForNodelessElement() {
        val nodeless: PsiElement =
            object : FakePsiElement() {
                override fun getParent(): PsiElement? = null

                override fun getNode(): ASTNode? = null
            }

        assertNull(LuaPsiUtils.nodeType(nodeless))
    }

    /** TC4: findNextSibling skips the immediate sibling whose element-type matches ignoreType. */
    fun testFindNextSiblingSkipsMatchingType() {
        val file = myFixture.configureByText(LuaFileType, "local a = 1")
        val firstLeaf = PsiTreeUtil.getDeepestFirst(file)
        val secondLeaf = firstLeaf.nextSibling
            ?: error("fixture must yield at least two sibling leaves")

        val skipType = LuaPsiUtils.nodeType(secondLeaf)!!

        var expected: PsiElement? = secondLeaf.nextSibling
        while (expected != null && LuaPsiUtils.nodeType(expected) == skipType) {
            expected = expected.nextSibling
        }

        assertEquals(expected, LuaPsiUtils.findNextSibling(firstLeaf, skipType))
    }
}
