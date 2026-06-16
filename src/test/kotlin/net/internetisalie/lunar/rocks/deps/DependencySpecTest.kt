package net.internetisalie.lunar.rocks.deps

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Covers TC-ROCKS-03-04: dependency-spec parsing and constraint satisfaction. */
class DependencySpecTest {
    private fun version(raw: String) = LuaRocksVersion.parse(raw)

    @Test
    fun rangeConstraint() {
        val spec = assertNotNull(DependencySpec.parse("lib >= 2.0, < 4.0"))
        assertEquals("lib", spec.packageName)
        assertTrue(spec.isSatisfiedBy(version("2.5")))
        assertFalse(spec.isSatisfiedBy(version("1.9")))
        assertFalse(spec.isSatisfiedBy(version("4.0")))
    }

    @Test
    fun compatibleConstraint() {
        val spec = assertNotNull(DependencySpec.parse("copas ~> 2.1"))
        assertTrue(spec.isSatisfiedBy(version("2.1.7")))
        assertFalse(spec.isSatisfiedBy(version("2.2")))
        assertFalse(spec.isSatisfiedBy(version("2.0")))
    }

    @Test
    fun noConstraintSatisfiedByAnything() {
        val spec = assertNotNull(DependencySpec.parse("penlight"))
        assertTrue(spec.constraints.isEmpty())
        assertTrue(spec.isSatisfiedBy(version("99.0")))
    }

    @Test
    fun bareVersionIsExact() {
        val spec = assertNotNull(DependencySpec.parse("lua_cliargs = 3.0"))
        assertEquals("lua_cliargs", spec.packageName)
        assertTrue(spec.isSatisfiedBy(version("3.0")))
        assertFalse(spec.isSatisfiedBy(version("3.1")))
    }

    @Test
    fun scopedName() {
        val spec = assertNotNull(DependencySpec.parse("scope/name >= 1.0"))
        assertEquals("scope/name", spec.packageName)
    }

    @Test
    fun emptyInputIsNull() {
        assertEquals(null, DependencySpec.parse("   "))
    }
}
