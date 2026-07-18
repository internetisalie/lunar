package net.internetisalie.lunar.lang

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TC-03 (MAINT-30-02): `LuaNameReference.multiResolve` is served from the platform `ResolveCache`.
 * A repeat resolve with no intervening PSI edit must not re-enter the un-cached compute path
 * ([LuaNameReference.doMultiResolve], observed via [LuaNameReference.RESOLVE_INVOCATIONS]); a PSI
 * edit drops the cache and forces a recompute.
 */
@RunWith(JUnit4::class)
class LuaResolveCacheTest : BasePlatformTestCase() {

    @Test
    fun testRepeatResolveServedFromCacheUntilEdit() {
        myFixture.configureByText(
            "cache.lua",
            """
            local x = 1
            print(<caret>x)
            """.trimIndent(),
        )
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val reference = leaf.parent.reference as LuaNameReference

        reference.multiResolve(false)
        val afterFirst = LuaNameReference.RESOLVE_INVOCATIONS.get()

        reference.multiResolve(false)
        assertEquals(
            "A repeat resolve with no PSI edit must be served from ResolveCache (no recompute)",
            afterFirst,
            LuaNameReference.RESOLVE_INVOCATIONS.get(),
        )

        // A PSI edit drops the ResolveCache; a fresh reference over the reparsed tree recomputes.
        // Caret sits immediately before `x`; typing a space advances the caret to land before `x`,
        // so findElementAt(caretOffset) is the reparsed `x` identifier leaf.
        myFixture.type(" ")
        val editedLeaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val editedReference = editedLeaf.parent.reference as LuaNameReference
        editedReference.multiResolve(false)
        assertTrue(
            "A PSI edit must drop the cache and force a recompute",
            LuaNameReference.RESOLVE_INVOCATIONS.get() > afterFirst,
        )
    }
}
