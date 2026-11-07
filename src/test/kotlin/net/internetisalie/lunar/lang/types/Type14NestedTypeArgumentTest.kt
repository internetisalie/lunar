package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.types.LuaArrayType
import net.internetisalie.lunar.lang.psi.types.LuaGenericType
import net.internetisalie.lunar.lang.psi.types.LuaParameterizedType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.TypeParser
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-14 Phase 4: a type argument is a full type, so a generic name bound through a parameterized
 * table reads the nested argument it sits against. Requirements case 7, with a nested argument.
 */
@RunWith(JUnit4::class)
class Type14NestedTypeArgumentTest : IndexedBasePlatformTestCase() {
    private fun typeOf(
        file: PsiFile,
        variable: String,
    ): String {
        val nameRef =
            PsiTreeUtil
                .findChildrenOfType(file, LuaLocalVarDecl::class.java)
                .first { it.text.startsWith("local $variable ") }
                .attNameList
                .first()
                .nameRef
        return LuaTypesSnapshot.forFile(file).getValueType(nameRef).displayName()
    }

    @Test
    fun testCase7BindsANestedTypeArgument() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@class Widget
                ---@field id number

                ---@generic K, V
                ---@param t table<K, V>
                ---@return V
                local function get(t) end
                ---@type table<string, Widget|nil>
                local t
                local v = get(t)
                """.trimIndent(),
            )
        assertEquals("Widget | nil", typeOf(file, "v"))
    }

    /** TYPE-14-01 through a nested argument: `T` inside `T[]` inside `<…>` is still the generic. */
    @Test
    fun testGenericNameInsideANestedTypeArgument() {
        val file = myFixture.configureByText("test.lua", "local x = 1")
        val parsed = TypeParser.parse("table<string, T[]>", file, mapOf("T" to null))
        val nested = (parsed as LuaParameterizedType).arguments[1]
        assertTrue((nested as LuaArrayType).elementType is LuaGenericType)
    }
}
