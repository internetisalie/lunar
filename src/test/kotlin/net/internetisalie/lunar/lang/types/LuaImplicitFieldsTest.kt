package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaPrimitiveType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaImplicitFieldsTest : BasePlatformTestCase() {

    @Test
    fun testImplicitFieldsFromSelfAndDirectAssignment() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage = myFixture.configureByText(
            "player.lua",
            """
            ---@class Player
            local Player = {}
            function Player:heal() self.hp = 1 end
            Player.maxHp = 100
            """.trimIndent(),
        )

        runReadAction {
            val player = typeManager.resolveType("Player", usage)
            assertTrue("Player should be a class type", player is LuaClassType)
            val members = player!!.getMembers()
            assertTrue("hp should be an implicit member (self.hp in a Player method)", members.containsKey("hp"))
            assertTrue("maxHp should be an implicit member (Player.maxHp)", members.containsKey("maxHp"))
        }
    }

    @Test
    fun testExplicitFieldTakesPrecedence() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage = myFixture.configureByText(
            "player_field.lua",
            """
            ---@class Player
            ---@field hp string
            local Player = {}
            function Player:heal() self.hp = 1 end
            """.trimIndent(),
        )

        runReadAction {
            val player = typeManager.resolveType("Player", usage)
            val hp = player!!.resolveMember("hp")
            assertNotNull("hp member should resolve", hp)
            // Explicit @field hp string wins over the implicit self.hp = 1 (number).
            assertEquals("string", hp!!.type.name)
        }
    }

    @Test
    fun testSelfOutsideMethodContributesNothing() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage = myFixture.configureByText(
            "player_self.lua",
            """
            ---@class Player
            local Player = {}
            self.x = 1
            """.trimIndent(),
        )

        runReadAction {
            val player = typeManager.resolveType("Player", usage)
            assertTrue("Player should be a class type", player is LuaClassType)
            assertFalse(
                "self.x outside a Player method should not become a member",
                player!!.getMembers().containsKey("x"),
            )
        }
    }

    @Test
    fun testInheritanceRegression() {
        val typeManager = LuaTypeManagerImpl(project)
        myFixture.addFileToProject(
            "base.lua",
            """
            ---@class Base
            ---@field id number
            local Base
            """.trimIndent(),
        )
        val usage = myFixture.configureByText(
            "sub.lua",
            """
            ---@class Sub : Base
            ---@field name string
            local Sub
            """.trimIndent(),
        )

        runReadAction {
            val sub = typeManager.resolveType("Sub", usage) as? LuaClassType
            assertNotNull("Sub should resolve", sub)
            assertNotNull("inherited id member should resolve", sub!!.resolveMember("id"))
            assertNotNull("own name member should resolve", sub.resolveMember("name"))
        }
    }

    @Test
    fun testAliasRegression() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage = myFixture.configureByText(
            "alias.lua",
            """
            ---@alias Id number
            local x
            """.trimIndent(),
        )

        runReadAction {
            val id = typeManager.resolveType("Id", usage)
            assertNotNull("alias Id should resolve", id)
            assertTrue("alias Id should resolve to its number target", id!!.isAssignableTo(LuaPrimitiveType.NUMBER))
        }
    }
}
