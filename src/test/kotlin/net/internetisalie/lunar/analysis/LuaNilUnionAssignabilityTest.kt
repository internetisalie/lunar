package net.internetisalie.lunar.analysis

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * BUG-416. A possibly-nil value reaching a typed slot is optionality, not a type error.
 *
 * Measured on the zerobrane corpus member: 1 801 of 2 491 assignability warnings (72%) were the
 * three shapes below. In Lua an absent field read *is* `nil`, so every declare-now-fill-later
 * pattern — `ide = { frame = nil }` at `main.lua:64` of a shipped IDE — produced errors on ordinary
 * code. Caused by BUG-397 typing free globals: the comparison never used to happen at all.
 *
 * Each fixture is the minimal form of a shape the corpus measured, not an invented case.
 */
class LuaNilUnionAssignabilityTest : BasePlatformTestCase() {
    private fun errorsIn(source: String): List<String> {
        var messages: List<String> = emptyList()
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText("use.lua", source)
                messages =
                    LuaTypesSnapshot
                        .forFile(myFixture.file)
                        .getErrors()
                        .map { "${it.message}  @[${it.element.text.take(40)}]" }
            }
        }
        return messages
    }

    private fun assertNoAssignabilityError(source: String) {
        val complaints = errorsIn(source).filter { it.contains("is not assignable") }
        assertTrue("expected no assignability error, got:\n${complaints.joinToString("\n")}", complaints.isEmpty())
    }

    /**
     * Shapes 2 and 3 (`nil | { ... }` / `{ ... } | nil`, 421 each): a branch-dependent table.
     * The union's nil arm records that the branch may not have run — it is not evidence of misuse.
     */
    fun testBranchDependentTableIsNotAnError() {
        assertNoAssignabilityError(
            """
            --- @param t table
            local function use(t) return t end

            local maybe = nil
            if os.time() > 0 then
                maybe = { ready = true }
            end
            use(maybe)
            """.trimIndent(),
        )
    }

    /**
     * Shape 1 (`nil value is not assignable to { ... }`, ×959): the declare-now-fill-later global.
     * `frame = nil` in a declaration is a placeholder, not a commitment that the field is always
     * nil — zerobrane fills it at startup and calls through it 959 times.
     */
    fun testDeclaredNilPlaceholderMemberIsNotAnError() {
        myFixture.addFileToProject(
            "main.lua",
            """
            ide = {
              frame = nil, -- gui related
              editors = {},
            }
            """.trimIndent(),
        )
        assertNoAssignabilityError(
            """
            --- @param f table
            local function layout(f) return f end

            layout(ide.frame)
            ide.frame:SetStatusText("ready")
            """.trimIndent(),
        )
    }

    /**
     * A MATERIALIZED `{ ... } | nil` union on a direct edge — `and`/`or` builds one — exercises the
     * union branch of `checkCompatibility` rather than the per-edge flow, so this is the test that
     * makes the informative-arms rule load-bearing on its own: the per-edge certainty rule cannot
     * catch it (a single union-typed write is "certain").
     */
    fun testMaterializedNilUnionIsNotAnError() {
        assertNoAssignabilityError(
            """
            --- @param t table
            local function use(t) return t end

            use(os.time() > 0 and { ready = true } or nil)
            """.trimIndent(),
        )
    }

    /** The declared-table member that IS typed must keep flowing — the fix must not widen to it. */
    fun testDeclaredTypedMemberStillChecks() {
        myFixture.addFileToProject(
            "main.lua",
            """
            ide = {
              config = { path = {} },
            }
            """.trimIndent(),
        )
        val complaints =
            errorsIn(
                """
                --- @param n number
                local function count(n) return n end

                count(ide.config)
                """.trimIndent(),
            ).filter { it.contains("is not assignable") }
        assertTrue(
            "a declared table passed where a number is required must still error, got none",
            complaints.isNotEmpty(),
        )
    }

    /** A union whose non-nil arm genuinely mismatches must still error — nil forgives only itself. */
    fun testNonNilArmStillChecks() {
        val complaints =
            errorsIn(
                """
                --- @param n number
                local function count(n) return n end

                local v = nil
                if os.time() > 0 then
                    v = "text"
                end
                count(v)
                """.trimIndent(),
            ).filter { it.contains("is not assignable") }
        assertTrue(
            "string | nil into a number slot must still error on the string arm, got none",
            complaints.isNotEmpty(),
        )
    }

    /** An explicit, unconditional nil is not the placeholder pattern and stays an error. */
    fun testUnconditionalNilLocalStillChecks() {
        val complaints =
            errorsIn(
                """
                --- @param n number
                local function count(n) return n end

                local nothing = nil
                count(nothing)
                """.trimIndent(),
            ).filter { it.contains("nil value is not assignable") }
        assertTrue(
            "a local that is only ever nil must still error at a number slot, got none",
            complaints.isNotEmpty(),
        )
    }
}
