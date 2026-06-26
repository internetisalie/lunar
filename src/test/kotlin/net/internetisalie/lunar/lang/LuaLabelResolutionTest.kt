package net.internetisalie.lunar.lang

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaLabel
import net.internetisalie.lunar.lang.psi.LuaLabelName
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaLabelResolutionTest : BasePlatformTestCase() {

    @Test
    fun testBackwardLabelResolution() {
        myFixture.configureByText("test.lua", """
            ::done::
            print(1)
            goto don<caret>e
        """.trimIndent())

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("Reference should not be null", reference)
        val resolved = reference!!.resolve()
        assertNotNull("Should resolve to a label name", resolved)
        assertTrue("Should be an instance of LuaLabelName", resolved is LuaLabelName)
        val labelName = resolved as LuaLabelName
        assertEquals("done", labelName.identifier.text)
        assertTrue("Parent should be LuaLabel", labelName.parent is LuaLabel)
    }

    @Test
    fun testForwardLabelResolution() {
        myFixture.configureByText("test.lua", """
            goto don<caret>e
            ::done::
        """.trimIndent())

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("Reference should not be null", reference)
        val resolved = reference!!.resolve()
        assertNotNull("Should resolve to a label name", resolved)
        assertTrue("Should be an instance of LuaLabelName", resolved is LuaLabelName)
        val labelName = resolved as LuaLabelName
        assertEquals("done", labelName.identifier.text)
        assertTrue("Parent should be LuaLabel", labelName.parent is LuaLabel)
    }

    @Test
    fun testEnclosingBlockResolution() {
        myFixture.configureByText("test.lua", """
            ::top::
            do
                goto to<caret>p
            end
        """.trimIndent())

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("Reference should not be null", reference)
        val resolved = reference!!.resolve()
        assertNotNull("Should resolve to a label name", resolved)
        assertTrue("Should be an instance of LuaLabelName", resolved is LuaLabelName)
        val labelName = resolved as LuaLabelName
        assertEquals("top", labelName.identifier.text)
        assertTrue("Parent should be LuaLabel", labelName.parent is LuaLabel)
    }

    @Test
    fun testFunctionBoundaryResolution() {
        myFixture.configureByText("test.lua", """
            ::outer::
            local f = function()
                goto out<caret>er
            end
        """.trimIndent())

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("Reference should not be null", reference)
        val polyRef = reference as? PsiPolyVariantReference
        assertNotNull("Reference should be a PsiPolyVariantReference", polyRef)
        val results = polyRef!!.multiResolve(false)
        assertEquals("Should not resolve across function boundary", 0, results.size)

        val resolved = reference.resolve()
        assertNull("Should not resolve across function boundary", resolved)
    }

    @Test
    fun testSiblingBlockResolution() {
        myFixture.configureByText("test.lua", """
            do
                ::inner::
            end
            goto inn<caret>er
        """.trimIndent())

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("Reference should not be null", reference)
        val polyRef = reference as? PsiPolyVariantReference
        assertNotNull("Reference should be a PsiPolyVariantReference", polyRef)
        val results = polyRef!!.multiResolve(false)
        assertEquals("Should not resolve sibling block label", 0, results.size)

        val resolved = reference.resolve()
        assertNull("Should not resolve sibling block label", resolved)
    }
}
