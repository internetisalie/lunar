package net.internetisalie.lunar.analysis.redis

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaUnOpExpr
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REDIS-04 Phase 1 — ambient `KEYS`/`ARGV` typing (AC-1) and `redis.pcall` union narrowing
 * (AC-6), exercised through the *real* type engine (`LuaTypesSnapshot.forFile` →
 * `getValueType`) against the **bundled** `runtime/redis` stubs under a real
 * `Target(REDIS, ...)`. A green assertion proves what a user gets: the stub-declared globals
 * and the `redis.pcall` return union are consumed by the existing engine with no REDIS-04
 * engine code.
 *
 * Covers TC-KEYS-1, TC-KEYS-2, TC-KEYS-3, TC-PCALL-1.
 */
@RunWith(JUnit4::class)
class RedisAmbientTypingTest : IndexedBasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaUndeclaredVariableInspection())
    }

    // `LuaProjectSettings` is a project-level service and `BasePlatformTestCase` reuses one light
    // project across every test in the module run, so a Redis target set here would leak into the
    // alphabetically-later `lang.indexing`/`lang.types` tests that (correctly) assume the STANDARD
    // default — polluting their library roots (redis stubs indexed) and breaking `require("math")`
    // resolution + the dotted `cjson.decode` index count. Restore the default target on teardown so
    // ambient-typing coverage stays isolated.
    override fun tearDown() {
        try {
            setStandardTarget("5.4")
        } finally {
            super.tearDown()
        }
    }

    private fun setRedisTarget(label: String) {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.REDIS, label))
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            LuaProjectSettings.getInstance(project).setTargetAndNotify(Target(LuaPlatform.REDIS, version))
            PlatformLibraryIndex.reload()
        }
    }

    private fun setStandardTarget(label: String) {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, label))
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            LuaProjectSettings.getInstance(project).setTargetAndNotify(Target(LuaPlatform.STANDARD, version))
            PlatformLibraryIndex.reload()
        }
    }

    private inline fun <reified T : PsiElement> exprByText(text: String): T? =
        PsiTreeUtil.collectElementsOfType(myFixture.file, T::class.java).firstOrNull { it.text == text }

    // -------------------------------------------------------------------------
    // TC-KEYS-1: KEYS[1] infers `string` under a Redis target (via bundled global.lua).
    //
    // NOTE (fixture limitation, tracked in risks-and-gaps §Gap 2.3): the requirements
    // TC-KEYS-1 also asserts "no undeclared-variable highlight on KEYS/ARGV". That sub-
    // assertion rides the *reference-resolution* path (LuaUndeclaredNames → multiResolve →
    // the platform library-bindings FileBasedIndex over the stub global.lua), NOT the
    // type-inference path exercised here. In a BasePlatformTestCase the stub global.lua is
    // served from inside the plugin jar (`jar:` URL) and is therefore never FileBasedIndex-
    // indexed, so bare-global KEYS never resolves and the warning fires regardless of target.
    // This is a light-fixture/packaging constraint, not a REDIS-04 resolution gap (a real IDE
    // indexes library roots). The live "no undeclared on KEYS/ARGV" behavior is covered by
    // human-verification-checklists Scenario 4.1.
    // -------------------------------------------------------------------------
    @Test
    fun testKeysIndexInfersString() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "local x = KEYS[1]")
        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val keysAccess = requireNotNull(exprByText<LuaVar>("KEYS[1]")) { "KEYS[1] var access must exist" }
            assertEquals(
                "KEYS[1] must infer string via the bundled global.lua @type string[] stub",
                LuaGraphType.String,
                snapshot.getValueType(keysAccess),
            )
        }
    }

    // -------------------------------------------------------------------------
    // TC-KEYS-1 (redis-5 coverage): the trimmed redis-5 stub still declares KEYS/ARGV in
    // global.lua, so ambient typing holds under a Redis 5 target too (AC-1 across versions).
    // -------------------------------------------------------------------------
    @Test
    fun testKeysIndexInfersStringUnderRedis5() {
        setRedisTarget("5")
        myFixture.configureByText("test.lua", "local x = KEYS[1]")
        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val keysAccess = requireNotNull(exprByText<LuaVar>("KEYS[1]")) { "KEYS[1] var access must exist" }
            assertEquals(
                "KEYS[1] must infer string under the redis-5 stub too",
                LuaGraphType.String,
                snapshot.getValueType(keysAccess),
            )
        }
    }

    // -------------------------------------------------------------------------
    // Chained subscript regression (1a reviewer non-blocking flag): documents the *bounded*
    // behavior of the seam-(b) element inference on a chained subscript `KEYS[1][2]`. The
    // subscript inference is receiver-anchored on the innermost `LuaVar` name-ref (KEYS,
    // typed `string[]`), so every bracket in the chain resolves its element against that same
    // array type — i.e. `KEYS[1][2]` stably infers `string` (the array element type) rather
    // than crashing or thrashing the graph. This pins the chained-index contract: no crash,
    // deterministic single-level element type. (Nested `T[][]` typing is out of Phase-1 scope.)
    // -------------------------------------------------------------------------
    @Test
    fun testChainedSubscriptIsBoundedToElementType() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "local x = KEYS[1][2]")
        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val outer = requireNotNull(exprByText<LuaVar>("KEYS[1][2]")) { "KEYS[1][2] var access must exist" }
            assertEquals(
                "KEYS[1][2] must stably infer string (receiver-anchored element type) without crashing",
                LuaGraphType.String,
                snapshot.getValueType(outer),
            )
        }
    }

    // -------------------------------------------------------------------------
    // TC-KEYS-2: #ARGV infers `number` (length over string[]) under a Redis target.
    // -------------------------------------------------------------------------
    @Test
    fun testArgvLengthInfersNumber() {
        setRedisTarget("7+")
        myFixture.configureByText("test.lua", "local n = #ARGV")
        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val lengthExpr = requireNotNull(exprByText<LuaUnOpExpr>("#ARGV")) { "#ARGV expr must exist" }
            assertEquals(
                "#ARGV must infer number (length operator over string[])",
                LuaGraphType.Number,
                snapshot.getValueType(lengthExpr),
            )
        }
    }

    // -------------------------------------------------------------------------
    // TC-KEYS-3: off a Redis target the stub is not on scope, so KEYS is undeclared —
    //            proves no leakage of the Redis ambient globals.
    // -------------------------------------------------------------------------
    @Test
    fun testKeysUndeclaredOffRedisTarget() {
        setStandardTarget("5.4")
        myFixture.configureByText("test.lua", "local x = KEYS[1]")
        val warnings = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Undeclared variable") }
        assertTrue(
            "KEYS must be flagged undeclared off a Redis target (no ambient-stub leakage), got: $warnings",
            warnings.contains("Undeclared variable 'KEYS'"),
        )
    }

    // -------------------------------------------------------------------------
    // TC-PCALL-1: redis.pcall narrows to the `{ err: string }` union arm — the inferred
    //            reply type surfaces an `err` member (proves the union stub, AC-6).
    // -------------------------------------------------------------------------
    @Test
    fun testPcallReturnSurfacesErrMember() {
        setRedisTarget("7+")
        myFixture.configureByText(
            "test.lua",
            """
            local reply = redis.pcall("GET", KEYS[1])
            if reply.err then end
            """.trimIndent(),
        )
        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val replyRef = requireNotNull(exprByText<LuaNameRef>("reply")) { "reply name-ref must exist" }
            val replyType = snapshot.getValueType(replyRef)
            assertTrue(
                "redis.pcall reply must expose the { err: string } arm member 'err', got: ${replyType.displayName()}",
                replyType.getMembers().containsKey("err"),
            )
        }
    }
}
