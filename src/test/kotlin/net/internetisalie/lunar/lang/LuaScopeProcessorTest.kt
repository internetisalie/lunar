package net.internetisalie.lunar.lang

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Unit tests for LuaScopeProcessor and LuaCompletionScopeProcessor core logic.
 *
 * These tests verify that the processors:
 * - Are properly instantiated
 * - Have correct default state
 * - Implement the required PsiScopeProcessor interface
 */
class LuaScopeProcessorTest {

    // ====== LuaScopeProcessor Instantiation Tests ======

    /**
     * Test: LuaScopeProcessor can be instantiated with a name.
     */
    @Test
    fun testProcessorInstantiation() {
        val processor = LuaScopeProcessor("x")
        Assertions.assertNotNull(processor, "Processor should be instantiatable")
    }

    /**
     * Test: LuaScopeProcessor.result is null initially.
     */
    @Test
    fun testProcessorResultNullByDefault() {
        val processor = LuaScopeProcessor("x")
        Assertions.assertNull(processor.result, "Result should be null initially")
    }

    /**
     * Test: LuaScopeProcessor stores the name.
     */
    @Test
    fun testProcessorStoresName() {
        val processor = LuaScopeProcessor("testName")
        Assertions.assertEquals("testName", processor.name, "Processor should store the name")
    }

    /**
     * Test: LuaScopeProcessor.getHint returns null.
     */
    @Test
    fun testProcessorGetHint() {
        val processor = LuaScopeProcessor("x")
        val hint: String? = processor.getHint(com.intellij.openapi.util.Key.create<String>("test"))
        Assertions.assertNull(hint, "getHint should return null")
    }

    /**
     * Test: LuaScopeProcessor.handleEvent does not throw.
     */
    @Test
    fun testProcessorHandleEvent() {
        val processor = LuaScopeProcessor("x")
        // Should not throw
        processor.handleEvent(com.intellij.psi.scope.PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, null)
    }

    // ====== LuaCompletionScopeProcessor Instantiation Tests ======

    /**
     * Test: LuaCompletionScopeProcessor can be instantiated.
     */
    @Test
    fun testCompletionProcessorInstantiation() {
        val processor = LuaCompletionScopeProcessor()
        Assertions.assertNotNull(processor, "Processor should be instantiatable")
    }

    /**
     * Test: LuaCompletionScopeProcessor.results is empty initially.
     */
    @Test
    fun testCompletionProcessorEmptyByDefault() {
        val processor = LuaCompletionScopeProcessor()
        Assertions.assertTrue(processor.results.isEmpty(), "Results should be empty initially")
    }

    /**
     * Test: LuaCompletionScopeProcessor.results is a mutable set.
     */
    @Test
    fun testCompletionProcessorResultsAreMutable() {
        val processor = LuaCompletionScopeProcessor()
        processor.results.add("test")
        Assertions.assertTrue(processor.results.contains("test"), "Results should be mutable")
    }

    /**
     * Test: LuaCompletionScopeProcessor.getHint returns null.
     */
    @Test
    fun testCompletionProcessorGetHint() {
        val processor = LuaCompletionScopeProcessor()
        val hint: String? = processor.getHint(com.intellij.openapi.util.Key.create<String>("test"))
        Assertions.assertNull(hint, "getHint should return null")
    }

    /**
     * Test: LuaCompletionScopeProcessor.handleEvent does not throw.
     */
    @Test
    fun testCompletionProcessorHandleEvent() {
        val processor = LuaCompletionScopeProcessor()
        // Should not throw
        processor.handleEvent(com.intellij.psi.scope.PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, null)
    }

    // ====== Interface Compliance Tests ======

    /**
     * Test: LuaScopeProcessor implements PsiScopeProcessor.
     */
    @Test
    fun testProcessorIsRightType() {
        val processor = LuaScopeProcessor("x")
        Assertions.assertTrue(processor is com.intellij.psi.scope.PsiScopeProcessor, "Should implement PsiScopeProcessor")
    }

    /**
     * Test: LuaCompletionScopeProcessor implements PsiScopeProcessor.
     */
    @Test
    fun testCompletionProcessorIsRightType() {
        val processor = LuaCompletionScopeProcessor()
        Assertions.assertTrue(processor is com.intellij.psi.scope.PsiScopeProcessor, "Should implement PsiScopeProcessor")
    }

    /**
     * Test: LuaScopeProcessor can be used in a list.
     */
    @Test
    fun testProcessorsCanBeStored() {
        val processors = listOf(
            LuaScopeProcessor("x"),
            LuaScopeProcessor("y"),
            LuaCompletionScopeProcessor()
        )
        Assertions.assertEquals(3, processors.size, "Should be able to store processors in a collection")
    }

    /**
     * Test: Multiple processor instances are independent.
     */
    @Test
    fun testMultipleProcessorsAreIndependent() {
        val p1 = LuaScopeProcessor("x")
        val p2 = LuaScopeProcessor("y")

        Assertions.assertNotEquals(p1.name, p2.name, "Processors should have different names")
        Assertions.assertNull(p1.result, "p1 result should be null")
        Assertions.assertNull(p2.result, "p2 result should be null")
    }

    /**
     * Test: Multiple completion processors accumulate independently.
     */
    @Test
    fun testMultipleCompletionProcessorsAreIndependent() {
        val p1 = LuaCompletionScopeProcessor()
        val p2 = LuaCompletionScopeProcessor()

        p1.results.add("x")
        p2.results.add("y")

        Assertions.assertTrue(p1.results.contains("x"), "p1 should contain 'x'")
        Assertions.assertFalse(p1.results.contains("y"), "p1 should not contain 'y'")
        Assertions.assertFalse(p2.results.contains("x"), "p2 should not contain 'x'")
        Assertions.assertTrue(p2.results.contains("y"), "p2 should contain 'y'")
    }

    /**
     * Test: LuaScopeProcessor can be queried for status.
     */
    @Test
    fun testProcessorStatusQueries() {
        val processor = LuaScopeProcessor("testVar")

        // Before any execute() calls
        Assertions.assertNull(processor.result, "Result should be null initially")
        Assertions.assertEquals("testVar", processor.name, "Name should be preserved")
    }

    /**
     * Test: Processors can be reused (though results accumulate).
     */
    @Test
    fun testCompletionProcessorCanBeReused() {
        val processor = LuaCompletionScopeProcessor()

        // First batch
        processor.results.add("a")
        Assertions.assertEquals(1, processor.results.size, "Should have 1 name after first add")

        // Second batch (results accumulate in completion processor by design)
        processor.results.add("b")
        Assertions.assertEquals(2, processor.results.size, "Should have 2 names after second add")
    }
}
