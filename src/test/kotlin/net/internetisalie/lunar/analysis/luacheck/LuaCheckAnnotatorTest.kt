package net.internetisalie.lunar.analysis.luacheck

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.codeInsight.daemon.impl.AnnotationSessionImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.TextRange
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.util.UUID

/**
 * MAINT-26-03 / MAINT-26-06 — annotator apply-phase behavior driven against a real
 * `configureByText` document (the DR-02 pure-apply seam): clamped offset math (TC3/TC4),
 * same-line/same-message de-dup, and the launch-failure WARNING banner (TC5) via the real
 * `capture` path pointed at a non-existent binary.
 */
class LuaCheckAnnotatorTest : ToolchainSettingsTestCase() {

    private fun applyOutcome(outcome: LuaCheckOutcome): AnnotationHolderImpl {
        val file = myFixture.file
        val annotator = LuaCheckAnnotator()
        return AnnotationSessionImpl.computeWithSession(file, false, annotator) { annotationHolder ->
            val holderImpl = annotationHolder as AnnotationHolderImpl
            holderImpl.applyExternalAnnotatorWithContext(file, outcome)
            holderImpl
        }
    }

    fun `test TC3 unused local range lands on the never-saved buffer`() {
        myFixture.configureByText("test.lua", "local x = 1\n")
        val problem = Problem(lineStart = 0, lineEnd = 0, columnStart = 6, columnEnd = 6, message = "unused 'x'", file = "test.lua")

        val holder = applyOutcome(LuaCheckOutcome.Problems(listOf(problem)))

        assertEquals(1, holder.size)
        assertEquals(TextRange(6, 7), holder.single().let { TextRange(it.startOffset, it.endOffset) })
    }

    fun `test TC4 problem beyond the buffer is clamped without IOOBE`() {
        myFixture.configureByText("test.lua", "local x = 1")
        val problem = Problem(lineStart = 5, lineEnd = 5, columnStart = 0, columnEnd = 3, message = "stale line", file = "test.lua")

        val holder = applyOutcome(LuaCheckOutcome.Problems(listOf(problem)))

        assertEquals(1, holder.size)
        val range = holder.single().let { TextRange(it.startOffset, it.endOffset) }
        assertTrue("range within document", range.endOffset <= myFixture.file.textLength)
    }

    fun `test same line same message is deduplicated`() {
        myFixture.configureByText("test.lua", "local x = 1\nlocal y = 2\nlocal z = 3\n")
        fun problem(line: Int, message: String) =
            Problem(lineStart = line, lineEnd = line, columnStart = 0, columnEnd = 4, message = message, file = "test.lua")
        val outcome = LuaCheckOutcome.Problems(
            listOf(
                problem(0, "unused 'x'"),
                problem(0, "unused 'x'"),
                problem(0, "other"),
                problem(1, "unused 'x'"),
                problem(2, "another"),
            ),
        )

        val holder = applyOutcome(outcome)

        assertEquals(4, holder.size)
        assertEquals(1, holder.count { it.message == "unused 'x'" && it.startOffset in 0..11 })
    }

    fun `test TC5 missing binary surfaces one warning banner`() {
        val luaCheck = seedToolAt("luacheck", "/nonexistent/luacheck-does-not-exist")
        settings.setBinding("luacheck", luaCheck.id)
        myFixture.configureByText("test.lua", "local x = 1\n")

        val info = requireNotNull(LuaCheckAnnotator().collectInformation(myFixture.file))
        val outcome = ApplicationManager.getApplication()
            .executeOnPooledThread<LuaCheckOutcome> { LuaCheckInvoker.invoke(info) }.get()

        val failure = outcome as LuaCheckOutcome.Failure
        assertEquals(FailureKind.LAUNCH_FAILED, failure.kind)
        assertEquals("Could not execute luacheck", failure.detail)

        val holder = applyOutcome(outcome)
        assertEquals(1, holder.size)
        assertEquals("Could not execute luacheck", holder.single().message)
    }

    private fun seedToolAt(kindId: String, path: String): LuaRegisteredTool {
        val model = LuaRegisteredTool(
            id = UUID.randomUUID().toString(),
            kindId = kindId,
            path = path,
            version = "1.0.0",
            luaVersion = null,
            runtime = null,
            origin = Origin.MANUAL,
            environmentId = null,
            health = LuaToolHealth(fileExists = true, executable = true, probeOk = true, probedAtMtime = 1L, reason = null),
        )
        registry.registerProvisioned(model)
        return model
    }
}
