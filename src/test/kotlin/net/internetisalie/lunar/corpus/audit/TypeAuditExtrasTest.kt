package net.internetisalie.lunar.corpus.audit

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFile
import java.io.File

/** MAINT-56 TC-7 and TC-13: the representative run's payload writer and its error families. */
class TypeAuditExtrasTest : BasePlatformTestCase() {
    fun testTC13BaselineFamiliesClassifyByMessageThenReturnStatement() {
        val file =
            myFixture.configureByText(
                "families.lua",
                "---@param n number\nlocal function f(n) end\nf(1, 2)\nf(\"x\")\n",
            ) as LuaFile

        assertEquals(mapOf("arity.tooMany" to 1, "assign.other" to 2), TypeAuditExtras.baselineFamilies(file))
    }

    fun testTC7WriteToPutsThePayloadInTheNamedDirectory() {
        val directory = File(FileUtil.createTempDirectory("tc7", null, true), "out")
        val payload = AuditPayload(1, "m", "c", 4, AuditCoverage(), emptyList(), emptyList())

        AuditOutput.writeTo(directory, "m", payload)

        val written = File(directory, "m.json")
        assertTrue("$written was not written", written.isFile)
        assertEquals(AuditOutput.render(payload), written.readText(Charsets.UTF_8))
    }
}
