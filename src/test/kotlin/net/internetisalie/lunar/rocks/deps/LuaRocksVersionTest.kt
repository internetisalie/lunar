package net.internetisalie.lunar.rocks.deps

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Covers TC-ROCKS-03-03: the LuaRocks version comparator (deltas, revision, zero-pad). */
class LuaRocksVersionTest {
    private fun parse(raw: String) = LuaRocksVersion.parse(raw)

    @Test
    fun revisionTiebreak() {
        assertTrue(parse("3.1-0") < parse("3.1-1"))
    }

    @Test
    fun majorMinorOrder() {
        assertTrue(parse("3.1") < parse("3.2"))
    }

    @Test
    fun scmRanksAboveNumeric() {
        assertTrue(parse("1.0") < parse("scm-1"))
    }

    @Test
    fun devRanksAboveScm() {
        assertTrue(parse("dev-1") > parse("scm-1"))
    }

    @Test
    fun zeroPaddingMakesTrailingZeroEqual() {
        assertEquals(0, parse("1.0.0").compareTo(parse("1.0")))
    }

    @Test
    fun parsesRevisionAndComponents() {
        val version = parse("2.2.0-1")
        assertEquals(listOf(2.0, 2.0, 0.0), version.components)
        assertEquals(1, version.revision)
    }

    @Test
    fun letterAndDigitTokensAreSeparate() {
        // "1.0beta2" -> [1, 0, beta-delta, 2]; LuaRocks does not split "beta2".
        val version = parse("1.0beta2")
        assertEquals(listOf(1.0, 0.0, -100000.0, 2.0), version.components)
    }

    @Test
    fun garbageInputYieldsEmptyComponents() {
        assertTrue(parse("").components.isEmpty())
    }
}
