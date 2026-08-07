package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-400: a `---@class` with no stubbed host must still resolve.
 *
 * LuaCATS tags are not stubbed — they ride a host declaration's stub — and only `LuaFuncDecl`,
 * `LuaLocalVarDecl` and `LuaLocalFuncDecl` are stubbed at all. The bundled stdlib stubs declare
 * their classes above a bare **global assignment** (`---@class package` over `package = {}`), which
 * is none of those, so `LuaClassNameIndex` never saw them and every stdlib library class resolved to
 * nothing.
 *
 * `string` and `table` hid this: `resolveType` checks `LuaPrimitiveType.PRIMITIVES` first and those
 * names collide with primitives, so they appeared to work while never reaching the class at all.
 * The classes asserted here are deliberately ones with no primitive of the same name.
 */
@RunWith(JUnit4::class)
class LuaUnhostedClassResolutionTest : BasePlatformTestCase() {
    /** The bundled stdlib shape, which is what actually regressed. */
    @Test
    fun testStdlibClassResolvesWithItsMembers() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage = myFixture.configureByText("consumer.lua", "local x = 1\n")

        runReadAction {
            val packageClass = typeManager.resolveType("package", usage) as? LuaClassType
            assertNotNull("`package` is declared `---@class package` in the bundled stub", packageClass)
            val members = packageClass!!.getMembers().keys
            assertTrue(
                "an implicit field (`package.path = \"\"`) must be a member. Found: $members",
                members.contains("path"),
            )
            assertTrue(
                "a declared function (`function package.loadlib(...)`) must be a member. Found: $members",
                members.contains("loadlib"),
            )
        }
    }

    /** Every stdlib library class, not just the one that surfaced the bug. */
    @Test
    fun testEveryStdlibLibraryClassResolves() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage = myFixture.configureByText("consumer.lua", "local x = 1\n")

        runReadAction {
            listOf("package", "io", "os", "debug", "coroutine", "utf8").forEach { name ->
                assertNotNull("stdlib class '$name' must resolve", typeManager.resolveType(name, usage))
            }
        }
    }

    /** The general un-hosted form: a bare `---@class` with `@field`s and no declaration under it. */
    @Test
    fun testBareClassWithFieldsResolves() {
        val typeManager = LuaTypeManagerImpl(project)
        // Explicit newlines and NO trailing declaration: a `local` after the comment would give the
        // tag a stub host, routing resolution through LuaClassNameIndex and never exercising the
        // un-hosted path this test exists for.
        myFixture.addFileToProject(
            "defs.lua",
            "---@meta\n\n---@class Bare\n---@field alpha string\n---@field beta? number\n",
        )
        val usage = myFixture.configureByText("consumer.lua", "local x = 1\n")

        runReadAction {
            val bare = typeManager.resolveType("Bare", usage) as? LuaClassType
            assertNotNull("a bare `---@class` with no host declaration must resolve", bare)
            assertEquals(setOf("alpha", "beta"), bare!!.getMembers().keys)
        }
    }

    /** Inheritance still works when the parent is itself un-hosted. */
    @Test
    fun testBareClassInheritsFromAnotherBareClass() {
        val typeManager = LuaTypeManagerImpl(project)
        myFixture.addFileToProject(
            "defs.lua",
            "---@meta\n\n---@class BareParent\n---@field inherited string\n\n" +
                "---@class BareChild : BareParent\n---@field own number\n",
        )
        val usage = myFixture.configureByText("consumer.lua", "local x = 1\n")

        runReadAction {
            val child = typeManager.resolveType("BareChild", usage) as? LuaClassType
            assertNotNull("BareChild must resolve", child)
            assertEquals(setOf("inherited", "own"), child!!.getMembers().keys)
        }
    }

    /** A name nothing declares still resolves to nothing — the fallback must not invent classes. */
    @Test
    fun testUnknownNameStillResolvesToNothing() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage = myFixture.configureByText("consumer.lua", "local x = 1\n")
        runReadAction {
            assertNull(typeManager.resolveType("NoSuchClassAnywhere", usage))
        }
    }

    /** The user-visible payoff: annotating with a stdlib class name now completes its members. */
    @Test
    fun testTypeAnnotationWithAStdlibClassCompletes() {
        myFixture.configureByText("consumer.lua", "---@type package\nlocal p\np.<caret>\n")
        myFixture.completeBasic()
        val found = myFixture.lookupElementStrings.orEmpty()
        assertTrue("`---@type package` must complete the class's members. Found: $found", found.contains("path"))
    }
}
