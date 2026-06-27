package net.internetisalie.lunar.lang.psi.stubs

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.SerializationManagerEx
import com.intellij.psi.stubs.Stub
import com.intellij.psi.stubs.StubTree
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(JUnit4::class)
class LuaStubSerializationTest : BasePlatformTestCase() {

    private fun buildStubTree(text: String): StubTree {
        val file = myFixture.configureByText("test.lua", text.trimIndent()) as PsiFileImpl
        return runReadAction {
            file.calcStubTree() ?: error("Failed to build stub tree")
        }
    }

    private fun roundTrip(root: Stub): Stub {
        val out = ByteArrayOutputStream()
        SerializationManagerEx.getInstanceEx().serialize(root, out)
        val bytes = out.toByteArray()
        val restored = SerializationManagerEx.getInstanceEx().deserialize(ByteArrayInputStream(bytes))
        return restored ?: error("Failed to deserialize stub tree")
    }

    private inline fun <reified T> Stub.collect(): List<T> {
        val result = mutableListOf<T>()
        val queue = ArrayDeque<Stub>()
        queue.add(this)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current is T) {
                result.add(current)
            }
            queue.addAll(current.childrenStubs)
        }
        return result
    }

    @Test
    fun testLocalVarStubHoistsClassAndExtends() {
        val stubTree = buildStubTree("""
            ---@class Builder: Base
            local Builder = {}
        """)
        val localVars = stubTree.root.collect<LuaLocalVarStub>()
        assertTrue("Local variables list should not be empty", localVars.isNotEmpty())
        val first = localVars.first()
        assertEquals("Builder", first.luacatsClassName)
        assertEquals("Base", first.luacatsExtends)
    }

    @Test
    fun testLocalVarStubSerializationRoundTrip() {
        val stubTree = buildStubTree("""
            ---@class Builder: Base
            ---@field name string
            ---@field id number
            local Builder = {}
        """)
        val restoredRoot = roundTrip(stubTree.root)
        val localVars = restoredRoot.collect<LuaLocalVarStub>()
        assertTrue("Local variables list should not be empty", localVars.isNotEmpty())
        val first = localVars.first()
        assertEquals("Builder", first.luacatsClassName)
        assertEquals("Base", first.luacatsExtends)
        assertEquals("string", first.luacatsFields["name"])
        assertEquals("number", first.luacatsFields["id"])
    }

    @Test
    fun testFuncStubReturnTypeRoundTrip() {
        val stubTree = buildStubTree("""
            ---@return string
            function f() end
        """)
        val restoredRoot = roundTrip(stubTree.root)
        val funcs = restoredRoot.collect<LuaFuncStub>()
        assertTrue("Functions list should not be empty", funcs.isNotEmpty())
        val first = funcs.first()
        assertEquals("f", first.name)
        assertEquals("string", first.luacatsReturnType)
    }

    @Test
    fun testLocalFuncStubRoundTrip() {
        val stubTree = buildStubTree("""
            local function g() end
        """)
        val restoredRoot = roundTrip(stubTree.root)
        val localFuncs = restoredRoot.collect<LuaLocalFuncStub>()
        assertTrue("Local functions list should not be empty", localFuncs.isNotEmpty())
        val first = localFuncs.first()
        assertEquals("g", first.name)
    }
}
