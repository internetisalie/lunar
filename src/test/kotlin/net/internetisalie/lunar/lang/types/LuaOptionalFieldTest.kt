package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-401: `---@field name? T` declares a member called `name`, not `name?`.
 *
 * The stub path — the one a *hosted* class actually takes, and so the common case — carried the
 * optional marker straight into the member key, producing a member no lookup could match. Every
 * optional field on a stubbed class was silently unreachable: no completion, no resolution, no hover.
 * `materializeClass`'s AST branch had always stripped it, which is why this survived unnoticed.
 */
@RunWith(JUnit4::class)
class LuaOptionalFieldTest : BasePlatformTestCase() {
    @Test
    fun testOptionalFieldIsNamedWithoutTheMarker() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage =
            myFixture.configureByText(
                "opts.lua",
                "---@class Opts\n---@field required string\n---@field optional? number\nlocal Opts = {}\n",
            )

        runReadAction {
            val opts = typeManager.resolveType("Opts", usage) as? LuaClassType
            assertNotNull("Opts must resolve", opts)
            assertEquals(setOf("required", "optional"), opts!!.getMembers().keys)
        }
    }

    /** The marker is not merely dropped — it widens the field's type with nil. */
    @Test
    fun testOptionalFieldTypeAdmitsNil() {
        val typeManager = LuaTypeManagerImpl(project)
        val usage =
            myFixture.configureByText(
                "opts.lua",
                "---@class Opts\n---@field optional? number\nlocal Opts = {}\n",
            )

        runReadAction {
            val opts = typeManager.resolveType("Opts", usage) as LuaClassType
            val optional = requireNotNull(opts.resolveMember("optional")) { "optional must resolve" }
            assertTrue(
                "an optional field's type must admit nil, was: ${optional.type.name}",
                optional.type.name.contains("nil"),
            )
        }
    }

    /** The user-visible payoff. */
    @Test
    fun testOptionalFieldCompletes() {
        myFixture.addFileToProject(
            "opts.lua",
            "---@class Opts\n---@field optional? number\nlocal Opts = {}\nreturn Opts\n",
        )
        myFixture.configureByText("consumer.lua", "---@type Opts\nlocal o\no.<caret>\n")
        myFixture.completeBasic()
        val found = myFixture.lookupElementStrings.orEmpty()
        assertTrue("an optional field must complete under its own name. Found: $found", found.contains("optional"))
    }
}
