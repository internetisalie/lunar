package net.internetisalie.lunar.lang.psi.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElementTypeHolderEP
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.LuaStubElementTypes
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.lang.reflect.Modifier

/**
 * BUG-531: the platform assigns stub serializer IDs once, from the stub element types that exist when
 * the index initializes. A type created later is logged as SEVERE and its files fail the Stubs index,
 * so every Lua stub type must live in a holder registered through `stubElementTypeHolder`.
 */
@RunWith(JUnit4::class)
class LuaStubElementTypeHolderTest : BasePlatformTestCase() {
    @Test
    fun testHolderIsRegisteredWithoutExternalIdPrefix() {
        val holder = registeredLunarHolders().singleOrNull()

        assertNotNull("LuaStubElementTypes must be registered as a stubElementTypeHolder", holder)
        // A prefix promises an interface whose field names are the external IDs minus the prefix;
        // Lunar's holder is an object whose IDs are `lunar.local.var.decl` etc., so it must be eagerly loaded.
        assertNull(holder?.externalIdPrefix)
    }

    @Test
    fun testEveryLuaStubElementTypeIsAHolderConstant() {
        myFixture.configureByText("holder.lua", "local x = 1\nlocal function f() end\nfunction g() end\n")
        val holderConstants = registeredLunarHolders().flatMap { stubConstantsOf(it.holderClass) }.toSet()

        val createdLuaStubTypes =
            IElementType.enumerate { it is IStubElementType<*, *> && it.language == LuaLanguage }.toSet()

        assertEquals(3, holderConstants.size)
        assertEquals(holderConstants, createdLuaStubTypes)
    }

    private fun registeredLunarHolders(): List<StubElementTypeHolderEP> =
        StubElementTypeHolderEP.EP_NAME.extensionList.filter {
            it.holderClass == LuaStubElementTypes::class.java.name
        }

    private fun stubConstantsOf(holderClassName: String): List<Any> =
        Class
            .forName(holderClassName)
            .declaredFields
            .filter { Modifier.isStatic(it.modifiers) && IStubElementType::class.java.isAssignableFrom(it.type) }
            .mapNotNull { field ->
                field.isAccessible = true
                field.get(null)
            }
}
