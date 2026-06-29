package net.internetisalie.lunar.lang.schema

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.*

class PrintChildrenTest : BasePlatformTestCase() {
    fun testPrintChildren() {
        val file = myFixture.configureByText("test.lua", "a = 1\nb = 2") as LuaFile
        println("File children: ${file.children.map { it.javaClass.simpleName }}")
    }
}
