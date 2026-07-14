package net.internetisalie.lunar.redis.functions

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.lang.psi.LuaIndexExpr
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
 * REDIS-05 Phase 1 — `redis.register_function` stub typing tests (AC-2).
 *
 * Covers TC-STUB-1 and TC-STUB-2 from requirements.md: verifies that the bundled
 * `redis-7/redis.lua` stub makes `register_function` accessible under a Redis 7+ target,
 * and that the `string[]` subscript mechanism works.
 *
 * SCOPE (see design §3.3 + risks Gap 2.4, decided 2026-07-14). Two AC-2 clauses are NOT
 * fixture-assertable and are intentionally out of scope here:
 *  - `redis.register_function` reference resolution (no unresolved-ref highlight) rides the
 *    jar-stub `FileBasedIndex` path, which a `BasePlatformTestCase` never populates — same
 *    documented constraint as `RedisAmbientTypingTest`. Verified via human-verification §1.
 *  - Callback-param auto-typing (`keys`/`args` → `string[]` from the stub `fun(...)`
 *    annotation) is a genuine ENGINE limitation, not a fixture one: the type engine has no
 *    expected-type → lambda-parameter propagation (empirically proven — direct `---@param`
 *    works, expected-type does not). Descoped for this Could-priority feature; general
 *    inference is deferred to a TYPE-epic enhancement. Users annotate callback params.
 * What IS tested here: the type engine runs without crash / no type errors on both call
 * forms; and the `string[]`-subscript path (`ArraySubscriptTypeTest` regression pin) infers
 * `string` under a Redis 7+ target.
 */
@RunWith(JUnit4::class)
class LuaRedisRegisterFunctionStubTest : IndexedBasePlatformTestCase() {

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

    private inline fun <reified T : PsiElement> firstExprOfType(): T? =
        PsiTreeUtil.findChildrenOfType(myFixture.file, T::class.java).firstOrNull()

    // -------------------------------------------------------------------------
    // TC-STUB-1 (part A): register_function file parses and type engine does not crash.
    // The file type-checks without errors under a Redis 7+ target.
    // See class doc for fixture limitation on full stub-resolution + callback param typing.
    // -------------------------------------------------------------------------
    @Test
    fun testRegisterFunctionPositionalTypeEngineNoCrash_TC_STUB_1() {
        setRedisTarget("7+")
        myFixture.configureByText(
            "lib.lua",
            "#!lua name=lib\nredis.register_function('f', function(keys, args) return keys[1] end)",
        )
        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val errors = snapshot.getErrors()
            assertTrue(
                "register_function file must not produce type errors, got: ${errors.map { it.message }}",
                errors.isEmpty(),
            )
        }
    }

    // -------------------------------------------------------------------------
    // TC-STUB-1 (part B): the `string[]` subscript path that the stub callback annotation
    // would exercise infers `string`. Uses a locally-annotated array under Redis 7+ to
    // confirm no target-based regression on the ArraySubscript path (design §3.1b).
    // -------------------------------------------------------------------------
    @Test
    fun testStringArraySubscriptInfersStringUnderRedis7_TC_STUB_1b() {
        setRedisTarget("7+")
        myFixture.configureByText(
            "keys-test.lua",
            "---@type string[]\nlocal keys = {}\nlocal x = keys[1]",
        )
        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val subscript = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaIndexExpr::class.java)
                .firstOrNull { it.expr != null }
            assertNotNull("keys[1] subscript must exist", subscript)
            assertEquals(
                "keys[1] with @type string[] annotation must infer string under Redis 7+ target",
                LuaGraphType.String,
                snapshot.getValueType(subscript!!),
            )
        }
    }

    // -------------------------------------------------------------------------
    // TC-STUB-2: register_function table form — type engine does not crash or error.
    // -------------------------------------------------------------------------
    @Test
    fun testRegisterFunctionTableFormNoCrash_TC_STUB_2() {
        setRedisTarget("7+")
        myFixture.configureByText(
            "lib.lua",
            """
            #!lua name=lib
            redis.register_function{ function_name='f', callback=function(keys, args) end, flags={'no-writes'}, description='d' }
            """.trimIndent(),
        )
        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val errors = snapshot.getErrors()
            assertTrue(
                "Table-form register_function must not produce type errors, got: ${errors.map { it.message }}",
                errors.isEmpty(),
            )
        }
    }
}
