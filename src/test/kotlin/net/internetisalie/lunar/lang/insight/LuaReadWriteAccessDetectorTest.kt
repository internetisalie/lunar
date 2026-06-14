package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector.Access
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaAttName
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaVar
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [LuaReadWriteAccessDetector].
 *
 * Covers:
 *   TC-NAV-10-01 — write detection: assignment LHS and declaration bindings
 *   TC-NAV-10-02 — read detection: argument usage and index-base usage
 *
 * TC-NAV-10-03 (highlight colors under caret) requires a live IDE and cannot
 * be verified in a unit fixture — validate manually by selecting a variable in
 * the sandbox IDE and confirming distinct read/write highlight colors.
 */
@RunWith(JUnit4::class)
class LuaReadWriteAccessDetectorTest : BasePlatformTestCase() {

    private val detector = LuaReadWriteAccessDetector()

    // -------------------------------------------------------------------------
    // TC-NAV-10-01: write detection
    // -------------------------------------------------------------------------

    @Test
    fun testGlobalAssignmentLhsIsWrite() {
        // x = 1  — bare global assignment: x is a write target
        val file = myFixture.configureByText("test.lua", "x = 1")
        val vars = PsiTreeUtil.findChildrenOfType(file, LuaVar::class.java)
        val xVar = vars.firstOrNull { it.nameRef?.identifier?.text == "x" && it.varSuffixList.isEmpty() }
        assertNotNull("Expected a bare LuaVar for 'x'", xVar)
        val nameRef = xVar!!.nameRef as LuaNameRef
        assertEquals("Global assignment LHS should be Write", Access.Write, detector.getExpressionAccess(nameRef))
    }

    @Test
    fun testLocalDeclarationIsDeclarationWrite() {
        // local y = 2  — binding site: isDeclarationWriteAccess must return true
        val file = myFixture.configureByText("test.lua", "local y = 2")
        val attName = PsiTreeUtil.findChildOfType(file, LuaAttName::class.java)
        assertNotNull("Expected LuaAttName for local declaration", attName)
        // element passed is the nameRef child of LuaAttName (the binding element)
        val nameRef = attName!!.nameRef
        assertTrue("Local binding should be a declaration write", detector.isDeclarationWriteAccess(nameRef))
    }

    @Test
    fun testParameterBindingIsDeclarationWrite() {
        // function f(a) end  — parameter 'a' is a binding write
        val file = myFixture.configureByText("test.lua", "function f(a) end")
        // The parameter nameRef is a child of LuaNameList under LuaParList
        val nameRefs = PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java)
        val paramRef = nameRefs.firstOrNull { it.identifier.text == "a" }
        assertNotNull("Expected a LuaNameRef for parameter 'a'", paramRef)
        assertTrue("Parameter binding should be a declaration write", detector.isDeclarationWriteAccess(paramRef!!))
    }

    @Test
    fun testMultiAssignmentLhsVarsAreWrite() {
        // a, b = f()  — both LHS vars are write targets
        val file = myFixture.configureByText("test.lua", "a, b = f()")
        val vars = PsiTreeUtil.findChildrenOfType(file, LuaVar::class.java)
        val aVar = vars.firstOrNull { it.nameRef?.identifier?.text == "a" && it.varSuffixList.isEmpty() }
        val bVar = vars.firstOrNull { it.nameRef?.identifier?.text == "b" && it.varSuffixList.isEmpty() }
        assertNotNull("Expected LuaVar for 'a'", aVar)
        assertNotNull("Expected LuaVar for 'b'", bVar)
        assertEquals("'a' in multi-assign should be Write", Access.Write, detector.getExpressionAccess(aVar!!.nameRef!!))
        assertEquals("'b' in multi-assign should be Write", Access.Write, detector.getExpressionAccess(bVar!!.nameRef!!))
    }

    // -------------------------------------------------------------------------
    // TC-NAV-10-02: read detection
    // -------------------------------------------------------------------------

    @Test
    fun testArgumentUsageIsRead() {
        // print(x)  — x is passed as an argument, not an assignment target
        val file = myFixture.configureByText("test.lua", "print(x)")
        val nameRefs = PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java)
        val xRef = nameRefs.firstOrNull { it.identifier.text == "x" }
        assertNotNull("Expected a LuaNameRef for 'x'", xRef)
        assertEquals("Argument 'x' should be Read", Access.Read, detector.getExpressionAccess(xRef!!))
    }

    @Test
    fun testIndexBaseInSuffixedAssignIsRead() {
        // t.k = 1  — 't' is the base of a suffixed var; it is Read, not Write
        val file = myFixture.configureByText("test.lua", "t.k = 1")
        val vars = PsiTreeUtil.findChildrenOfType(file, LuaVar::class.java)
        val tVar = vars.firstOrNull { it.nameRef?.identifier?.text == "t" && it.varSuffixList.isNotEmpty() }
        assertNotNull("Expected a suffixed LuaVar for 't.k'", tVar)
        val nameRef = tVar!!.nameRef as LuaNameRef
        assertEquals("Index-base 't' in 't.k = 1' should be Read", Access.Read, detector.getExpressionAccess(nameRef))
    }

    // -------------------------------------------------------------------------
    // isReadWriteAccessible — accepts LuaNameRef and IDENTIFIER leaf
    // -------------------------------------------------------------------------

    @Test
    fun testIsReadWriteAccessibleAcceptsNameRef() {
        val file = myFixture.configureByText("test.lua", "local x = 1")
        val attName = PsiTreeUtil.findChildOfType(file, LuaAttName::class.java)!!
        val nameRef = attName.nameRef
        assertTrue("LuaNameRef should be read-write accessible", detector.isReadWriteAccessible(nameRef))
    }

    @Test
    fun testIsReadWriteAccessibleAcceptsIdentifierLeaf() {
        val file = myFixture.configureByText("test.lua", "local x = 1")
        val attName = PsiTreeUtil.findChildOfType(file, LuaAttName::class.java)!!
        val identifier = attName.nameRef.identifier
        assertTrue("IDENTIFIER leaf should be read-write accessible", detector.isReadWriteAccessible(identifier))
    }
}
