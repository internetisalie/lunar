package net.internetisalie.lunar.analysis.luacheck

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.codeInsight.daemon.impl.AnnotationSessionImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class LuaCheckAnnotatorTest : BasePlatformTestCase() {

    @Test
    fun testDeduplicationOfSameLineSameMessage() {
        val file = myFixture.configureByText("test.lua", """
            local x = 1
            local y = 2
            local z = 3
        """.trimIndent())

        val annotator = LuaCheckAnnotator()

        val p1 = Problem(
            lineStart = 0,
            lineEnd = 0,
            columnStart = 0,
            columnEnd = 10,
            message = "unused variable 'x'",
            file = "test.lua",
        )
        val p2 = Problem(
            lineStart = 0,
            lineEnd = 0,
            columnStart = 0,
            columnEnd = 10,
            message = "unused variable 'x'",
            file = "test.lua",
        )
        val p3 = Problem(
            lineStart = 0,
            lineEnd = 0,
            columnStart = 0,
            columnEnd = 10,
            message = "different message on same line",
            file = "test.lua",
        )
        val p4 = Problem(
            lineStart = 1,
            lineEnd = 1,
            columnStart = 0,
            columnEnd = 10,
            message = "unused variable 'x'",
            file = "test.lua",
        )
        val p5 = Problem(
            lineStart = 2,
            lineEnd = 2,
            columnStart = 0,
            columnEnd = 10,
            message = "another message",
            file = "test.lua",
        )

        val results = LuaCheckAnnotator.Results(listOf(p1, p2, p3, p4, p5))

        val holder = AnnotationSessionImpl.computeWithSession(file, false, annotator) { annotationHolder ->
            val holderImpl = annotationHolder as AnnotationHolderImpl
            holderImpl.applyExternalAnnotatorWithContext(file, results)
            holderImpl
        }

        // There should be exactly 4 annotations registered:
        // - "unused variable 'x'" on line 0 (only once!)
        // - "different message on same line" on line 0
        // - "unused variable 'x'" on line 1
        // - "another message" on line 2
        assertEquals(4, holder.size)

        val annotations = holder.map { it.message }
        assertTrue(annotations.contains("unused variable 'x'"))
        assertTrue(annotations.contains("different message on same line"))
        assertTrue(annotations.contains("another message"))

        val unusedCount = annotations.count { it == "unused variable 'x'" }
        assertEquals(2, unusedCount)
    }
}
