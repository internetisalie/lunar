package net.internetisalie.lunar.luacats.lang.parser

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsLdocParamTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Every type form reads the same in every tag that takes a type (BUG-559, BUG-562, BUG-563). */
@RunWith(JUnit4::class)
class LuaCatsTypeFormsTest : BasePlatformTestCase() {
    private val forms =
        listOf(
            "string",
            "Some.Class",
            "\"a\"|\"b\"",
            "'a'|'b'",
            "1|2",
            "1.5",
            "`T`",
            "string[]",
            "string|nil",
            "Box<string>",
            "fun()",
            "fun(a: string): number",
            "fun(...)",
            "fun(a?: string, ...): number",
            "{a: string}",
            "{[string]: number}",
        )

    private val tags =
        listOf(
            "---@param p %s\nlocal function f(p) end\n",
            "---@return %s\nlocal function f() end\n",
            "---@type %s\nlocal x\n",
            "---@alias A %s\nlocal x\n",
            "---@class C\n---@field f %s\nlocal C = {}\n",
            "local x\n---@cast x %s\n",
        )

    private fun problemWith(
        source: String,
        form: String,
    ): String? {
        myFixture.configureByText(LuaFileType, source)
        return runReadAction {
            val file = myFixture.file
            val type = PsiTreeUtil.findChildrenOfType(file, LuaCatsType::class.java).firstOrNull()?.text
            when {
                PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null -> "parse error"
                PsiTreeUtil.findChildOfType(file, LuaCatsLdocParamTag::class.java) != null -> "fell back to prose"
                type != form -> "read as <$type>"
                else -> null
            }
        }
    }

    @Test
    fun testEveryFormInEveryTag() {
        val failures =
            tags.flatMap { tag ->
                forms.mapNotNull { form ->
                    val source = tag.replace("%s", form)
                    problemWith(source, form)?.let { "${source.lines().first { "---@" in it }}: $it" }
                }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun testProseAfterATypeStaysProse() {
        val samples =
            listOf(
                "---@param p string the `name` of 2 'things'\nlocal function f(p) end\n",
                "---@return string the `name` of 2 'things'\nlocal function f() end\n",
                "---@param fun string a parameter named fun\nlocal function f(fun) end\n",
            )
        samples.forEach { assertNull(it, problemWith(it, "string")) }
    }
}
