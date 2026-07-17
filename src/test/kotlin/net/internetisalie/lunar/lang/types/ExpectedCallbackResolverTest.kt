package net.internetisalie.lunar.lang.types

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.types.LuaExpectedCallbackResolver
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-10 Phase 1 (design §2.2/§3.2): [LuaExpectedCallbackResolver] resolves a call's callee
 * `LuaFunctionType` and its per-slot callback type, for a bundled external stub reached via
 * reference resolution (`redis.register_function`, `table.sort`). Feeds TC 1 and TC 3.
 */
@RunWith(JUnit4::class)
class ExpectedCallbackResolverTest : IndexedBasePlatformTestCase() {

    private fun redis7() = Target(LuaPlatform.REDIS, VersionEntry("7+", "redis-7"))
    private fun standard54() = Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4"))

    // `LuaProjectSettings` is a project-level service and `BasePlatformTestCase` reuses one light
    // project per module run; restore the STANDARD default so a Redis target set here does not leak
    // into alphabetically-later tests.
    override fun tearDown() {
        try {
            EdtTestUtil.runInEdtAndWait<RuntimeException> {
                LuaProjectSettings.getInstance(project).setTargetAndNotify(standard54())
                PlatformLibraryIndex.reload()
            }
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testRegisterFunctionCallbackSlotResolves() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            LuaProjectSettings.getInstance(project).setTargetAndNotify(redis7())
            PlatformLibraryIndex.reload()
        }
        val file = myFixture.configureByText(
            "test.lua",
            "redis.register_function('f', function(keys, args) return keys end)",
        )
        val call = PsiTreeUtil.findChildrenOfType(file, LuaFuncCall::class.java)
            .first { it.text.startsWith("redis.register_function") }
        val resolver = LuaExpectedCallbackResolver(call, requireNotNull(call.varOrExp.`var`))

        val calleeType = requireNotNull(resolver.resolveCalleeType()) {
            "redis.register_function must resolve to a LuaFunctionType"
        }
        val callback = requireNotNull(resolver.expectedCallbackAt(1, calleeType, 0)) {
            "params[1] of register_function must be a callback fun(...)"
        }
        assertEquals("string[]", callback.params.getOrNull(0)?.type?.name)
        assertEquals("string[]", callback.params.getOrNull(1)?.type?.name)
    }

    @Test
    fun testTableSortComparatorSlotResolves() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local t = {}
            table.sort(t, function(a, b) return a end)
            """.trimIndent(),
        )
        val call = PsiTreeUtil.findChildrenOfType(file, LuaFuncCall::class.java)
            .first { it.text.startsWith("table.sort") }
        val resolver = LuaExpectedCallbackResolver(call, requireNotNull(call.varOrExp.`var`))

        val calleeType = requireNotNull(resolver.resolveCalleeType()) {
            "table.sort must resolve to a LuaFunctionType"
        }
        val comparator = requireNotNull(resolver.expectedCallbackAt(1, calleeType, 0)) {
            "params[1] of table.sort must be a comparator fun(...)"
        }
        assertEquals("any", comparator.params.getOrNull(0)?.type?.name)
        assertEquals("any", comparator.params.getOrNull(1)?.type?.name)
        assertEquals("boolean", comparator.returnType.name)
    }
}
