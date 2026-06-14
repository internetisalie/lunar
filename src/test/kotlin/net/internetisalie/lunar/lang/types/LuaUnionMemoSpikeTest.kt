package net.internetisalie.lunar.lang.types

import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-09-P0-02 — memoization soundness (THROWAWAY de-risking spike).
 *
 * Question: is caching compatibility results sound across differing resolution contexts?
 *
 * Today `isCompatible` has no substitution parameter, so we model the future memoized solver
 * here. We compare a naive memo keyed on `(value, use)` against a context-keyed memo on
 * `(value, use, substitutions)`, exercising `Generic("T")` resolved as String then Number.
 *
 * Red-then-green in spirit: the naive cache is asserted to return the WRONG (stale) answer for
 * the second context, while the context-keyed cache returns the correct answer for both.
 */
// Pure-logic spike (models a memo with LuaGraphType + Map only) — no PSI, so it deliberately does
// NOT extend BasePlatformTestCase, avoiding the IntelliJ platform boot per test.
@RunWith(JUnit4::class)
class LuaUnionMemoSpikeTest {

    private val genericT = LuaGraphType.Generic("T")
    private val use = LuaGraphType.String
    private var rawCalls = 0

    /**
     * The "real work": is [value] (after substituting generics) compatible with [use]?
     * `Generic("T")` resolves through [substitutions]; here we model `T <: string`.
     */
    private fun rawCompat(
        value: LuaGraphType,
        useType: LuaGraphType,
        substitutions: Map<String, LuaGraphType>,
    ): Boolean {
        rawCalls++
        val resolved = if (value is LuaGraphType.Generic) substitutions[value.name] ?: value else value
        return resolved == useType
    }

    // -- Naive memo: key omits substitutions (UNSOUND) ----------------------------------------

    private val naiveCache = mutableMapOf<Pair<LuaGraphType, LuaGraphType>, Boolean>()

    private fun naiveCompat(
        value: LuaGraphType,
        useType: LuaGraphType,
        substitutions: Map<String, LuaGraphType>,
    ): Boolean = naiveCache.getOrPut(value to useType) { rawCompat(value, useType, substitutions) }

    // -- Context memo: key includes substitutions (SOUND) -------------------------------------

    private val contextCache = mutableMapOf<Triple<LuaGraphType, LuaGraphType, Map<String, LuaGraphType>>, Boolean>()

    private fun contextCompat(
        value: LuaGraphType,
        useType: LuaGraphType,
        substitutions: Map<String, LuaGraphType>,
    ): Boolean = contextCache.getOrPut(Triple(value, useType, substitutions)) { rawCompat(value, useType, substitutions) }

    @Test
    fun testNaiveMemoIsStaleAcrossContexts() {
        val asString = mapOf("T" to LuaGraphType.String)
        val asNumber = mapOf("T" to LuaGraphType.Number)

        // T=string: T <: string is TRUE.
        val first = naiveCompat(genericT, use, asString)
        assertTrue("T=string should be assignable to string", first)

        // T=number: the CORRECT answer is FALSE (number is not a string)...
        val second = naiveCompat(genericT, use, asNumber)

        // ...but the naive cache, keyed only on (Generic T, string), reuses the stale TRUE.
        assertTrue("Naive cache returns the STALE result (demonstrates the bug)", second)
        assertEquals("Naive cache must have served the second query from the stale entry", 1, rawCalls)
    }

    @Test
    fun testContextMemoIsSoundAndCaches() {
        rawCalls = 0
        val asString = mapOf("T" to LuaGraphType.String)
        val asNumber = mapOf("T" to LuaGraphType.Number)

        // Differing contexts must NOT reuse each other's result.
        val first = contextCompat(genericT, use, asString)
        val second = contextCompat(genericT, use, asNumber)
        assertTrue("T=string is assignable to string", first)
        assertFalse("T=number is NOT assignable to string (context-correct)", second)
        assertEquals("Two distinct contexts => two raw computations", 2, rawCalls)

        // Equal contexts MUST hit the cache (no new raw call).
        val third = contextCompat(genericT, use, asString)
        assertTrue("Repeat of T=string still assignable", third)
        assertEquals("Equal context must be served from cache", 2, rawCalls)
    }
}
