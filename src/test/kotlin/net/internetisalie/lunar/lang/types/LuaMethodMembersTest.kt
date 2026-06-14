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
        val usage = myFixture.configureByText(
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
        val usage = myFixture.configureByText(
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
        val usage = myFixture.configureByText(
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
            assertTrue("dot function should resolve as a member", util!!.resolveMember("format")?.type is LuaFunctionType)
        }
    }
}
