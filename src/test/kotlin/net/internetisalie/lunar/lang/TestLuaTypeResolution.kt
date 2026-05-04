package net.internetisalie.lunar.lang

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaPrimitiveType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TestLuaTypeResolution : BasePlatformTestCase() {

    @Test
    fun testResolveClassCrossFile() {
        val typeManager = LuaTypeManagerImpl(project)

        myFixture.addFileToProject("player.lua", """
            ---@class Player
            ---@field name string
            ---@field score number
            local Player = {}
        """.trimIndent())

        val usage = myFixture.configureByText("usage.lua", "local p")

        val playerType = typeManager.resolveType("Player", usage)

        assertNotNull("Player type should be resolved", playerType)
        assertTrue(playerType is LuaClassType)

        val nameMember = playerType?.resolveMember("name")
        assertNotNull("name member should be resolved", nameMember)
        assertEquals(LuaPrimitiveType.STRING.name, nameMember?.type?.name)
    }

    @Test
    fun testClassMerging() {
        val typeManager = LuaTypeManagerImpl(project)

        myFixture.addFileToProject("part1.lua", "---@class Shared\n---@field f1 string\nlocal s")
        myFixture.addFileToProject("part2.lua", "---@class Shared\n---@field f2 number\nlocal s")
        val usage = myFixture.configureByText("usage_merging.lua", "local s")

        val sharedType = typeManager.resolveType("Shared", usage)

        assertNotNull("Shared type should be resolved", sharedType)
        assertNotNull("f1 should be resolved", sharedType?.resolveMember("f1"))
        assertNotNull("f2 should be resolved", sharedType?.resolveMember("f2"))
    }

    @Test
    fun testInheritanceResolution() {
        val typeManager = LuaTypeManagerImpl(project)

        myFixture.addFileToProject("base.lua", """
            ---@class Base
            ---@field id number
            local Base
        """.trimIndent())

        myFixture.addFileToProject("sub.lua", """
            ---@class Sub : Base
            ---@field name string
            local Sub
        """.trimIndent())

        val usage = myFixture.configureByText("usage_inheritance.lua", "local s")
        val subType = typeManager.resolveType("Sub", usage) as? LuaClassType

        assertNotNull("Sub type should be resolved", subType)
        assertNotNull("name member should be resolved", subType?.resolveMember("name"))
        assertNotNull("id member should be resolved", subType?.resolveMember("id"))

        val baseType = typeManager.resolveType("Base", usage)
        assertNotNull(baseType)
        assertTrue(subType!!.isAssignableTo(baseType!!))
    }
}
