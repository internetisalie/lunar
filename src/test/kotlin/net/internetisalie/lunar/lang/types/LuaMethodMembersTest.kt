package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaFunctionType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Method-aware class members: `function Class:method` / `function Class.fn` declarations are not
 * `@field`/implicit members, so they must be enumerated from the global-declaration index and
 * added to the class so [LuaClassType.resolveMember] finds them (needed by NAV-05/06 and parameter
 * hints). See `materializeClass` / `collectMethodMembers` in `LuaTypeManagerImpl`.
 */
@RunWith(JUnit4::class)
class LuaMethodMembersTest : BasePlatformTestCase() {
    @Test
    fun testColonMethodIsResolvableAsMember() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage =
            myFixture.configureByText(
                "builder.lua",
                """
                ---@class Builder
                local Builder = {}

                ---@return Builder
                function Builder:setName(n) end
                """.trimIndent(),
            )

        runReadAction {
            val builder = typeManager.resolveType("Builder", usage)
            assertTrue("Builder should be a class type", builder is LuaClassType)
            val member = (builder as LuaClassType).resolveMember("setName")
            assertNotNull("setName method should resolve as a member", member)
            assertTrue("setName should be a function type", member!!.type is LuaFunctionType)
            assertEquals("Builder", (member.type as LuaFunctionType).returnType.name)
        }
    }

    @Test
    fun testSelfReturnResolvesToReceiverClass() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage =
            myFixture.configureByText(
                "chain.lua",
                """
                ---@class Chain
                local Chain = {}

                ---@return self
                function Chain:step() end
                """.trimIndent(),
            )

        runReadAction {
            val chain = typeManager.resolveType("Chain", usage) as? LuaClassType
            assertNotNull("Chain should be a class type", chain)
            val step = chain!!.resolveMember("step")?.type as? LuaFunctionType
            assertNotNull("step method should resolve", step)
            assertEquals("`---@return self` should resolve to the receiver class", "Chain", step!!.returnType.name)
        }
    }

    @Test
    fun testDotFunctionIsResolvableAsMember() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage =
            myFixture.configureByText(
                "util.lua",
                """
                ---@class Util
                local Util = {}

                function Util.format(s) end
                """.trimIndent(),
            )

        runReadAction {
            val util = typeManager.resolveType("Util", usage) as? LuaClassType
            assertNotNull("Util should be a class type", util)
            assertTrue(
                "dot function should resolve as a member",
                util!!.resolveMember("format")?.type is LuaFunctionType,
            )
        }
    }

    /**
     * BUG-398: the class name and the local holding it need not match. LuaCATS libraries routinely
     * separate them — luassert writes `---@class luassert.internal` on `local internal = {}` — and
     * the methods are then declared against the *variable*, so a class-name-only scan of the
     * declaration index found nothing and the class materialized with no members at all.
     */
    @Test
    fun testMethodsDeclaredAgainstTheLocalRatherThanTheClassName() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage =
            myFixture.configureByText(
                "luassert.lua",
                """
                ---@class luassert.internal
                local internal = {}

                function internal.True(value) end

                internal.are = internal
                """.trimIndent(),
            )

        runReadAction {
            val internal = typeManager.resolveType("luassert.internal", usage) as? LuaClassType
            assertNotNull("luassert.internal should be a class type", internal)
            assertTrue(
                "a method declared as `function internal.True` must be a member of the class the " +
                    "local carries. Found: ${internal!!.getMembers().keys}",
                internal.resolveMember("True")?.type is LuaFunctionType,
            )
            assertNotNull(
                "an implicit field assigned as `internal.are = …` must be a member too",
                internal.resolveMember("are"),
            )
        }
    }

    /** Inheriting from such a class must inherit its members — what BUG-398 actually cost. */
    @Test
    fun testMembersOfAParentNamedApartFromItsLocalAreInherited() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage =
            myFixture.configureByText(
                "luassert.lua",
                """
                ---@class luassert.internal
                local internal = {}
                function internal.True(value) end

                ---@class luassert : luassert.internal
                local luassert = {}
                function luassert.unregister(namespace) end
                """.trimIndent(),
            )

        runReadAction {
            val luassert = typeManager.resolveType("luassert", usage) as? LuaClassType
            assertNotNull("luassert should be a class type", luassert)
            assertEquals(
                setOf("True", "unregister"),
                luassert!!.getMembers().keys,
            )
        }
    }

    /** The confinement that keeps the widened match honest: a same-named local elsewhere is not mine. */
    @Test
    fun testALocalOfTheSameNameInAnotherFileDoesNotContributeMembers() {
        val typeManager = LuaTypeManagerImpl(project)
        myFixture.addFileToProject("other.lua", "local internal = {}\nfunction internal.NotMine() end\n")
        val usage =
            myFixture.configureByText(
                "mine.lua",
                """
                ---@class Mine
                local internal = {}
                function internal.Mine() end
                """.trimIndent(),
            )

        runReadAction {
            val mine = typeManager.resolveType("Mine", usage) as? LuaClassType
            assertNotNull("Mine should be a class type", mine)
            assertEquals(
                "only the declaring file's `internal` contributes. Found: ${mine!!.getMembers().keys}",
                setOf("Mine"),
                mine.getMembers().keys,
            )
        }
    }
}
