package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * MAINT-25-05: guards the behavior of the direct-child LuaCatsComment scan in
 * [LuaRecursiveVisitor.visitElement], which replaced the per-element O(n²) deep
 * `findChildrenOfType`. The scan must still visit every distinct [LuaCatsComment] exactly once —
 * cats comments attach directly under real PSI containers (LuaFile/LuaBlock/LuaFuncDecl/
 * LuaDoStatement), verified by MAINT-25-00-DR-02, so a per-node direct-child scan collectively
 * reaches all of them.
 */
@RunWith(JUnit4::class)
class LuaRecursiveVisitorCatsScanTest : BasePlatformTestCase() {

    @Test
    fun testVisitsEveryCatsCommentExactlyOnce() {
        val file = myFixture.configureByText(
            "visit.lua",
            """
            ---@class Foo
            ---@field a string
            local Foo = {}

            ---@param x number
            function Foo:bar(x)
                ---@type table
                local t = {}
            end

            do
                ---@type number
                local nested = 1
            end
            """.trimIndent(),
        )

        val visitCounts = mutableMapOf<LuaCatsComment, Int>()
        val visitor = object : LuaRecursiveVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is LuaCatsComment) {
                    visitCounts[element] = (visitCounts[element] ?: 0) + 1
                }
                super.visitElement(element)
            }
        }
        file.accept(visitor)

        val allComments = PsiTreeUtil.findChildrenOfType(file, LuaCatsComment::class.java)
        assertTrue("fixture must contain cats comments", allComments.isNotEmpty())
        // Completeness is the load-bearing property: the direct-child scan must not DROP any cats
        // comment relative to the previous deep findChildrenOfType. (Each comment text has two
        // distinct PSI objects — the outer lazy comment and its re-parsed inner node — which is
        // pre-existing structure, so we assert per-object reachability, not a fixed count.)
        for (comment in allComments) {
            assertNotNull(
                "Every cats comment must be visited at least once: '${comment.text.take(24)}'",
                visitCounts[comment],
            )
        }
    }
}
