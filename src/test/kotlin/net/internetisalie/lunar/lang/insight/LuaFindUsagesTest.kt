package net.internetisalie.lunar.lang.insight

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usages.impl.rules.UsageType
import net.internetisalie.lunar.lang.psi.LuaAttName
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaVar
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for [LuaFindUsagesProvider] and [LuaReadWriteUsageTypeProvider].
 *
 * Covers:
 *   TC-NAV-02-01 — local variable usages (count + declaration excluded)
 *   TC-NAV-02-02 — cross-file global usages via stub index (see [LuaFindUsagesCrossFileTest])
 *   TC-NAV-02-03 — label usages (regression: LuaLabelName branch preserved)
 *   TC-NAV-02-04 — scope isolation (two locals with same name)
 *   TC-NAV-02-05 — read/write classification via [LuaReadWriteUsageTypeProvider]
 */
@RunWith(JUnit4::class)
class LuaFindUsagesTest : BasePlatformTestCase() {

    private val provider = LuaFindUsagesProvider()
    private val usageTypeProvider = LuaReadWriteUsageTypeProvider()

    // -------------------------------------------------------------------------
    // canFindUsagesFor — declaration-kind coverage
    // -------------------------------------------------------------------------

    @Test
    fun testCanFindUsagesForLocalVar() {
        val file = myFixture.configureByText("test.lua", "local x = 1")
        val attName = PsiTreeUtil.findChildOfType(file, LuaAttName::class.java)!!
        val identifier = attName.nameRef.identifier
        assertTrue("Should accept local var IDENTIFIER", provider.canFindUsagesFor(identifier))
        assertEquals("local variable", provider.getType(identifier))
    }

    @Test
    fun testCanFindUsagesForGlobalFunction() {
        val file = myFixture.configureByText("test.lua", "function greet() end")
        val funcDecl = PsiTreeUtil.findChildOfType(file, LuaFuncDecl::class.java)!!
        val identifier = funcDecl.funcName.nameRef.identifier
        assertTrue("Should accept global function IDENTIFIER", provider.canFindUsagesFor(identifier))
        assertEquals("global function", provider.getType(identifier))
    }

    @Test
    fun testCanFindUsagesForLabel() {
        val file = myFixture.configureByText("test.lua", "::done:: goto done")
        val labelName = PsiTreeUtil.findChildOfType(file, LuaLabelName::class.java)!!
        assertTrue("Should accept LuaLabelName for labels", provider.canFindUsagesFor(labelName))
        assertEquals("label", provider.getType(labelName))
    }

    @Test
    fun testCannotFindUsagesForArbitraryIdentifier() {
        myFixture.configureByText("test.lua", "print(x)")
        val nameRefs = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java)
        val printRef = nameRefs.firstOrNull { it.identifier.text == "print" }
        assertNotNull("Expected a nameRef for 'print'", printRef)
        assertFalse(
            "Should NOT accept a usage-site identifier",
            provider.canFindUsagesFor(printRef!!.identifier),
        )
    }

    // -------------------------------------------------------------------------
    // TC-NAV-02-01: local variable usages — count + declaration excluded
    // -------------------------------------------------------------------------

    @Test
    fun testLocalVariableUsagesCount() {
        // local x = 1; print(x); x = 2   →   2 usages (not including declaration)
        val file = myFixture.configureByText("test.lua", "local x = 1\nprint(x)\nx = 2")
        val attName = PsiTreeUtil.findChildOfType(file, LuaAttName::class.java)!!
        val declIdentifier = attName.nameRef.identifier

        // Exercise the real Find Usages action: it drives ReferencesSearch over the
        // declaration leaf, which LuaNameReferenceSearcher now turns into a word scan.
        val usages = myFixture.findUsages(declIdentifier)
        assertEquals("Expected 2 usages of local 'x'", 2, usages.size)

        // Reverse search must also resolve directly (the searcher is what the action uses).
        val refs = ReferencesSearch.search(declIdentifier).findAll()
        assertEquals("Expected 2 references to local 'x'", 2, refs.size)
    }

    // -------------------------------------------------------------------------
    // TC-NAV-02-03: label usages — regression guard
    // -------------------------------------------------------------------------

    @Test
    fun testLabelUsagesCount() {
        // ::done:: goto done   →   1 usage
        val file = myFixture.configureByText("test.lua", "::done::\ngoto done")
        val labelName = PsiTreeUtil.findChildOfType(file, LuaLabelName::class.java)!!

        val usages = myFixture.findUsages(labelName)
        assertEquals("Expected 1 label usage", 1, usages.size)

        val refs = ReferencesSearch.search(labelName).findAll()
        assertEquals("Expected 1 label reference", 1, refs.size)
    }

    // -------------------------------------------------------------------------
    // TC-NAV-02-04: scope isolation — two locals with same name
    // -------------------------------------------------------------------------

    @Test
    fun testScopeIsolation() {
        val code = """
            local function f()
                local x = 1
                print(x)
            end
            local function g()
                local x = 2
                print(x)
            end
        """.trimIndent()
        val file = myFixture.configureByText("test.lua", code)

        // Find first attName (x inside f)
        val attNames = PsiTreeUtil.findChildrenOfType(file, LuaAttName::class.java)
        val firstX = attNames.first { it.nameRef.identifier.text == "x" }
        val usages = myFixture.findUsages(firstX.nameRef.identifier)

        // Only the x in f should be found, not x in g. The searcher scans every "x"
        // occurrence, but isReferenceTo (resolve() === f's leaf) rejects g's usage.
        assertEquals("Expected 1 usage of 'x' in f, not g's x", 1, usages.size)
    }

    // -------------------------------------------------------------------------
    // TC-NAV-02-05: read/write classification
    // -------------------------------------------------------------------------

    @Test
    fun testWriteClassification() {
        // x = 2  →  nameRef inside LuaVar with empty varSuffixList → WRITE
        val file = myFixture.configureByText("test.lua", "local x = 0\nx = 2")
        // Find the "x" nameRef on the assignment left-hand side
        val vars = PsiTreeUtil.findChildrenOfType(file, LuaVar::class.java)
        val assignedVar = vars.firstOrNull { v -> v.nameRef?.identifier?.text == "x" && v.varSuffixList.isEmpty() }
        assertNotNull("Expected a LuaVar for 'x' on lhs", assignedVar)

        val nameRef = assignedVar!!.nameRef!!
        val usageType = usageTypeProvider.getUsageType(nameRef.identifier)
        assertEquals("Assignment target should be WRITE", UsageType.WRITE, usageType)
    }

    @Test
    fun testReadClassification() {
        // print(x)  →  nameRef not in var-list lhs → READ
        val file = myFixture.configureByText("test.lua", "local x = 0\nprint(x)")
        // Find the "x" nameRef that's inside a function call argument
        val nameRefs = PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java)
        val readRef = nameRefs.firstOrNull { ref ->
            ref.identifier.text == "x" && ref.parent !is LuaVar
        }
        assertNotNull("Expected a read-site nameRef for 'x'", readRef)

        val usageType = usageTypeProvider.getUsageType(readRef!!.identifier)
        assertEquals("Read usage should be READ", UsageType.READ, usageType)
    }

    @Test
    fun testIndexBaseIsRead() {
        // t.k = 1  →  `t` is the base of a suffixed var; varSuffixList non-empty → READ
        val file = myFixture.configureByText("test.lua", "local t = {}\nt.k = 1")
        val vars = PsiTreeUtil.findChildrenOfType(file, LuaVar::class.java)
        // The var `t.k` has a non-empty varSuffixList; its base nameRef is `t`
        val suffixedVar = vars.firstOrNull { it.nameRef?.identifier?.text == "t" && it.varSuffixList.isNotEmpty() }
        assertNotNull("Expected a suffixed LuaVar for 't.k'", suffixedVar)

        val nameRef = suffixedVar!!.nameRef!!
        val usageType = usageTypeProvider.getUsageType(nameRef.identifier)
        assertEquals("Index base t in t.k=1 should be READ", UsageType.READ, usageType)
    }

    // -------------------------------------------------------------------------
    // isWriteTarget companion helper
    // -------------------------------------------------------------------------

    @Test
    fun testIsWriteTargetPredicate() {
        val file = myFixture.configureByText("test.lua", "local x = 0\nx = 99")
        val vars = PsiTreeUtil.findChildrenOfType(file, LuaVar::class.java)
        val assignedVar = vars.firstOrNull { it.nameRef?.identifier?.text == "x" && it.varSuffixList.isEmpty() }
        assertNotNull(assignedVar)
        val nameRef = assignedVar!!.nameRef as LuaNameRef
        assertTrue("isWriteTarget should be true for bare lhs", LuaReadWriteUsageTypeProvider.isWriteTarget(nameRef))
    }
}
