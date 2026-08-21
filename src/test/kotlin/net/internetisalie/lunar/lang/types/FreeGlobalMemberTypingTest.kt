package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-397 Phase 2: a member access on a *free global* (a receiver declared in another file, with
 * no binding in any local scope) is typed from the receiver's cross-file declaration —
 * [net.internetisalie.lunar.lang.psi.types.LuaTypeManager.resolveGlobal], the same source member
 * completion uses (BUG-395) — instead of having no node at all.
 *
 * These are the characterization tests for the two failure shapes that reverted both earlier
 * BUG-397 attempts, plus the BUG-359 false positive the missing node manufactured:
 * - the declared return union of `redis.pcall` must survive to the call result **without any
 *   member usage in the consuming file** (the demand-side `if reply.err` pattern in
 *   `RedisAmbientTypingTest` reflects the use constraint back and cannot see a collapsed union);
 * - a declaration-typed callee must NOT be arity-checked (`@overload` table form);
 * - `package.path` concat must not report "nil value is not assignable to string" (BUG-359).
 */
@RunWith(JUnit4::class)
class FreeGlobalMemberTypingTest : IndexedBasePlatformTestCase() {
    // ---------------------------------------------------------------------------------------------
    // BUG-359: the false positive came from the seed-less member access falling into the
    // `graph.nil` operand fallback in visitBinOpExpr — a Nil value meeting the concat's String use.
    // With the member typed from the declaring file, the concat checks for real and stays clean.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testPackagePathConcatAssignReportsNothing() {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.addFileToProject(
            "pkgstub.lua",
            """
            package = {}
            package.path = ""
            """.trimIndent(),
        )
        myFixture.configureByText(
            "test.lua",
            "package.path = package.path .. \";./?/init.lua;./?.lua\"\n",
        )

        val message = "nil value is not assignable to string"
        val matching = myFixture.doHighlighting().filter { it.description == message }
        assertEquals(
            "BUG-359: '$message' must not be reported when the receiver's declaration is resolvable, got ${matching.size}",
            0,
            matching.size,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // The reverted-attempt regression shape: redis.pcall's declared `any|{ err: string }` must
    // reach the call result with both arms intact. No `if reply.err` in this file — the member
    // must come from the declared WRITE, not reflect back from a use constraint.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testDeclaredReturnUnionSurvivesToCallResult() {
        myFixture.addFileToProject(
            "redisstub.lua",
            """
            redis = {}
            ---@param command string
            ---@return any|{ err: string }
            function redis.pcall(command, ...) end
            """.trimIndent(),
        )
        myFixture.configureByText("test.lua", "local reply = redis.pcall(\"GET\")\n")

        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val replyRef =
                PsiTreeUtil
                    .collectElementsOfType(myFixture.file, LuaNameRef::class.java)
                    .first { it.text == "reply" }
            val replyType = snapshot.getValueType(replyRef)

            assertTrue(
                "reply must keep the declared union (got: ${replyType.displayName()})",
                replyType is LuaGraphType.Union,
            )
            val arms = (replyType as LuaGraphType.Union).types
            assertTrue("the any arm must survive (got: ${replyType.displayName()})", LuaGraphType.Any in arms)
            assertTrue(
                "the { err: string } arm's member must be reachable (got: ${replyType.displayName()})",
                replyType.getMembers().containsKey("err"),
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The second reverted-attempt regression shape: a declaration-typed callee contributes its
    // return but is NOT arity/param-checked — the `@overload` table form must not report
    // "Too few arguments" against the primary signature.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testDeclarationTypedCalleeIsNotArityChecked() {
        myFixture.addFileToProject(
            "redisstub.lua",
            """
            redis = {}
            ---@param name string
            ---@param callback fun(keys: string[], args: string[]): any
            ---@overload fun(spec: { function_name: string, callback: fun(keys: string[], args: string[]): any })
            function redis.register_function(name, callback) end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "test.lua",
            """
            redis.register_function{
                function_name = "myfunc",
                callback = function(keys, args) return 1 end,
            }
            """.trimIndent(),
        )

        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val argumentErrors = snapshot.getErrors().filter { it.message.contains("arguments") }
            assertTrue(
                "a declaration-typed callee must not be arity-checked, got: ${argumentErrors.map { it.message }}",
                argumentErrors.isEmpty(),
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // BUG-397 Phase 3: a *bare* reference to a free global is typed for all consumers (hover,
    // inlays, inspections) — the twice-reverted visitNameRef wire-up. The member tests above
    // double as the non-displacement guarantee: with the receiver now bound to a seed, the
    // declared route must still own member typing (this is exactly what regressed both reverts).
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testBareFreeGlobalTypesForConsumers() {
        myFixture.addFileToProject(
            "redisstub.lua",
            """
            redis = {}
            ---@return any|{ err: string }
            function redis.pcall(command, ...) end
            """.trimIndent(),
        )
        myFixture.configureByText("test.lua", "local t = redis\n")

        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val tRef =
                PsiTreeUtil
                    .collectElementsOfType(myFixture.file, LuaNameRef::class.java)
                    .first { it.text == "t" }
            val tType = snapshot.getValueType(tRef)
            assertTrue(
                "a bare free global must carry its declared members (got: ${tType.displayName()})",
                tType.getMembers().containsKey("pcall"),
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Adversarial-review F1: `Config.db.count` must NOT resolve as `Config.count`. The graph path
    // anchors every suffix on the bare receiver, so a later suffix of a declaration-typed chain
    // falling through to it checked the leaf against the GLOBAL's members — four false-positive
    // highlights on a legal write, where the base commit had zero.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testChainedWriteThroughUndeclaredMemberReportsNothing() {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.addFileToProject(
            "config.lua",
            """
            Config = {}
            Config.count = 42
            Config.db = {}
            """.trimIndent(),
        )
        myFixture.configureByText("test.lua", "Config.db.count = \"text\"\n")

        val matching = myFixture.doHighlighting().filter { it.description?.contains("not assignable") == true }
        assertEquals(
            "F1: a chained write through an undeclared member must not be checked against the " +
                "global's own members, got: ${matching.map { it.description }}",
            0,
            matching.size,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Adversarial-review F1 (read form) + F2 (cross-chain pollution): an undeclared chain link
    // leaves the leaf untyped, and a write through one chain must not leak into another chain's
    // read via the seed's shared member nodes.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testChainedReadsStayIsolatedAndUntyped() {
        myFixture.addFileToProject(
            "config.lua",
            """
            Config = {}
            Config.count = 42
            Config.db = {}
            Config.sub = {}
            """.trimIndent(),
        )
        myFixture.configureByText(
            "test.lua",
            """
            Config.db.count = "text"
            local v = Config.sub.count
            local w = Config.db.count
            """.trimIndent(),
        )

        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val refs = PsiTreeUtil.collectElementsOfType(myFixture.file, LuaNameRef::class.java)
            val v = refs.first { it.text == "v" }
            val w = refs.first { it.text == "w" }
            // BUG-441 changed the SPELLING of "the engine does not know", not this property. An
            // unmodellable RHS used to contribute no node at all, so the variable was left at
            // `Undefined`; it now contributes an explicit `Any`, because a vanished write is
            // invisible to union formation and to `checkTypes`' per-definition checks, which is the
            // whole of that bug. Both spellings mean unknown and both absorb every check.
            //
            // Asserted as the property rather than the spelling, and **more** strictly than before
            // on the half that matters: the leak these guards exist for would make these `string`,
            // and that is now named outright instead of being implied by an equality that also
            // happened to pin how the unknown was written down.
            assertTrue(
                "F2: a write through Config.db must not leak into Config.sub's read, got " +
                    snapshot.getValueType(v),
                snapshot.getValueType(v).let { it == LuaGraphType.Undefined || it == LuaGraphType.Any },
            )
            assertNotSame(
                "F2: `v` must never acquire the written string's type",
                LuaGraphType.String,
                snapshot.getValueType(v),
            )
            assertTrue(
                "F1 read form: an undeclared chain member must stay untyped, not inherit the " +
                    "global's, got " + snapshot.getValueType(w),
                snapshot.getValueType(w).let { it == LuaGraphType.Undefined || it == LuaGraphType.Any },
            )
            assertNotSame(
                "F1: `w` must not inherit the written string's type",
                LuaGraphType.String,
                snapshot.getValueType(w),
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The positive chain case: a nested member that IS declared resolves through the previous
    // suffix's declared type, hop by hop.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testDeclaredNestedMemberTypesThroughTheChain() {
        myFixture.addFileToProject(
            "config.lua",
            """
            Config = {}
            Config.db = { name = "lunar" }
            """.trimIndent(),
        )
        myFixture.configureByText("test.lua", "local n = Config.db.name\n")

        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val n =
                PsiTreeUtil
                    .collectElementsOfType(myFixture.file, LuaNameRef::class.java)
                    .first { it.text == "n" }
            assertEquals(
                "a declared nested member must type through the chain",
                LuaGraphType.String,
                snapshot.getValueType(n),
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // A plain declared scalar member read: the smallest end-to-end proof that the member type
    // flows from the declaring file (completion's data source) into this file's expressions.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testDeclaredScalarMemberTypesTheRead() {
        myFixture.addFileToProject(
            "pkgstub.lua",
            """
            package = {}
            package.path = ""
            """.trimIndent(),
        )
        myFixture.configureByText("test.lua", "local p = package.path\n")

        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
            val pRef =
                PsiTreeUtil
                    .collectElementsOfType(myFixture.file, LuaNameRef::class.java)
                    .first { it.text == "p" }
            assertEquals(
                "package.path must read string off the declaring file",
                LuaGraphType.String,
                snapshot.getValueType(pRef),
            )
        }
    }
}
