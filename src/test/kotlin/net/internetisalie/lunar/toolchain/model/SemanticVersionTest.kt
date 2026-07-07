package net.internetisalie.lunar.toolchain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SemanticVersionTest {

    @Test
    fun testParseMajorMinorPatch() {
        val v = SemanticVersion.parse("3.9.2")
        assertNotNull(v)
        assertEquals(3, v!!.major)
        assertEquals(9, v.minor)
        assertEquals(2, v.patch)
    }

    @Test
    fun testParseWithBuildSuffix() {
        val v = SemanticVersion.parse("3.11.0-1")
        assertNotNull(v)
        assertEquals(3, v!!.major)
        assertEquals(11, v.minor)
        assertEquals(0, v.patch)
    }

    @Test
    fun testParseMajorMinorOnly() {
        val v = SemanticVersion.parse("3.9")
        assertNotNull(v)
        assertEquals(3, v!!.major)
        assertEquals(9, v.minor)
        assertEquals(0, v.patch)
    }

    @Test
    fun testParseGarbageInput() {
        assertNull(SemanticVersion.parse("not-a-version"))
    }

    @Test
    fun testComparison() {
        val v300 = SemanticVersion(3, 0, 0)
        val v390 = SemanticVersion(3, 9, 0)
        val v400 = SemanticVersion(4, 0, 0)

        assertTrue(v390 > v300)
        assertTrue(v400 > v390)
        assertTrue(v300 < v390)
        assertEquals(v390, SemanticVersion(3, 9, 0))
    }

    @Test
    fun testParseCleanWithSuffix() {
        val v1 = SemanticVersion.parse("3.11.0-1")
        val v2 = SemanticVersion.parse("3.9")
        val v3 = SemanticVersion.parse("not-a-version")

        assertNotNull(v1)
        assertEquals(3, v1!!.major)
        assertEquals(11, v1.minor)
        assertEquals(0, v1.patch)

        assertNotNull(v2)
        assertEquals(3, v2!!.major)
        assertEquals(9, v2.minor)
        assertEquals(0, v2.patch)

        assertNull(v3)

        // Compare: (3,9,2) < (3,11,0)
        val v392 = SemanticVersion(3, 9, 2)
        val v3110 = SemanticVersion(3, 11, 0)
        assertTrue(v392 < v3110)
    }
}
