package net.internetisalie.lunar.analysis

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.controlflow.*
import net.internetisalie.lunar.lang.psi.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaControlFlowTest : BasePlatformTestCase() {

    @Test
    fun testSimpleBranchingReachability() {
        val file = myFixture.configureByText("test.lua", """
            function test(x)
                if x then return 1 else return 2 end
                print("unreachable")
            end
        """.trimIndent())
        val func = PsiTreeUtil.findChildOfType(file, LuaFuncDecl::class.java)
        assertNotNull("Expected LuaFuncDecl", func)
        
        val flow = ControlFlowCache.getControlFlow(func!!)
        
        // Find the print call
        val printCall = PsiTreeUtil.findChildrenOfType(func, LuaFuncCall::class.java).firstOrNull { it.text.startsWith("print") }
        assertNotNull("Expected print call", printCall)
        
        // Find the instruction matching printCall
        val printInst = flow.instructions.firstOrNull { it.element == printCall || it.element == printCall?.parent }
        assertNotNull("Expected instruction for print call", printInst)
        
        // The print instruction should be unreachable
        assertFalse("Print call should be unreachable", flow.isReachable(printInst!!))
        
        // The return instructions should be reachable
        val returnStats = PsiTreeUtil.findChildrenOfType(func, LuaFinalStatement::class.java)
        assertEquals(2, returnStats.size)
        for (ret in returnStats) {
            val retInst = flow.instructions.firstOrNull { it.element == ret }
            assertNotNull("Expected instruction for return", retInst)
            assertTrue("Return statement should be reachable", flow.isReachable(retInst!!))
        }
    }

    @Test
    fun testReadWriteAccesses() {
        val file = myFixture.configureByText("test.lua", """
            local a = 1
            print(a)
        """.trimIndent())
        
        val flow = ControlFlowCache.getControlFlow(file)
        
        val rwInstructions = flow.instructions.filterIsInstance<LuaReadWriteInstruction>().filter { it.variableName == "a" }
        assertEquals(2, rwInstructions.size)
        
        val writeInst = rwInstructions[0]
        assertEquals("a", writeInst.variableName)
        assertEquals(AccessType.WRITE, writeInst.accessType)
        
        val readInst = rwInstructions[1]
        assertEquals("a", readInst.variableName)
        assertEquals(AccessType.READ, readInst.accessType)
    }

    @Test
    fun testNumericForLoopVariableWrite() {
        val file = myFixture.configureByText("test.lua", """
            for i = 1, 10 do
                print(i)
            end
        """.trimIndent())
        
        val flow = ControlFlowCache.getControlFlow(file)
        
        // Verify WRITE to loop variable i
        val writeInst = flow.instructions.filterIsInstance<LuaReadWriteInstruction>()
            .firstOrNull { it.variableName == "i" && it.accessType == AccessType.WRITE }
        assertNotNull("Expected loop variable WRITE instruction", writeInst)
        
        // Verify READ of loop variable i
        val readInst = flow.instructions.filterIsInstance<LuaReadWriteInstruction>()
            .firstOrNull { it.variableName == "i" && it.accessType == AccessType.READ }
        assertNotNull("Expected loop variable READ instruction", readInst)
    }

    @Test
    fun testWhileLoopWithBreak() {
        val file = myFixture.configureByText("test.lua", """
            while true do
                if cond then
                    break
                    print("unreachable after break")
                end
            end
            print("reachable after loop")
        """.trimIndent())
        
        val flow = ControlFlowCache.getControlFlow(file)
        
        // Find unreachable print
        val unreachablePrint = PsiTreeUtil.findChildrenOfType(file, LuaFuncCall::class.java)
            .firstOrNull { it.text.contains("unreachable after break") }
        assertNotNull(unreachablePrint)
        val unreachableInst = flow.instructions.firstOrNull { it.element == unreachablePrint || it.element == unreachablePrint?.parent }
        assertNotNull(unreachableInst)
        assertFalse("Print after break should be unreachable", flow.isReachable(unreachableInst!!))
        
        // Find reachable print
        val reachablePrint = PsiTreeUtil.findChildrenOfType(file, LuaFuncCall::class.java)
            .firstOrNull { it.text.contains("reachable after loop") }
        assertNotNull(reachablePrint)
        val reachableInst = flow.instructions.firstOrNull { it.element == reachablePrint || it.element == reachablePrint?.parent }
        assertNotNull(reachableInst)
        assertTrue("Print after loop should be reachable", flow.isReachable(reachableInst!!))
    }

    @Test
    fun testGotoAndLabel() {
        val file = myFixture.configureByText("test.lua", """
            goto target
            print("skipped")
            ::target::
            print("reached")
        """.trimIndent())
        
        val flow = ControlFlowCache.getControlFlow(file)
        
        // print("skipped") should be unreachable
        val skippedPrint = PsiTreeUtil.findChildrenOfType(file, LuaFuncCall::class.java)
            .firstOrNull { it.text.contains("skipped") }
        assertNotNull(skippedPrint)
        val skippedInst = flow.instructions.firstOrNull { it.element == skippedPrint || it.element == skippedPrint?.parent }
        assertNotNull(skippedInst)
        assertFalse("Print after goto should be unreachable", flow.isReachable(skippedInst!!))
        
        // print("reached") should be reachable (via the goto edge)
        val reachedPrint = PsiTreeUtil.findChildrenOfType(file, LuaFuncCall::class.java)
            .firstOrNull { it.text.contains("reached") }
        assertNotNull(reachedPrint)
        val reachedInst = flow.instructions.firstOrNull { it.element == reachedPrint || it.element == reachedPrint?.parent }
        assertNotNull(reachedInst)
        assertTrue("Label target print should be reachable", flow.isReachable(reachedInst!!))
    }
}
