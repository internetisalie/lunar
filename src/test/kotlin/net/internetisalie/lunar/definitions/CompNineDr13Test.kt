package net.internetisalie.lunar.definitions

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager

/**
 * THROWAWAY — COMP-09 DR-02c (redone with medians) and DR-07, together, because they are the same
 * measurement read two ways.
 *
 * **DR-02c** was marked REOPENED: Step 9 re-ran the original harness and it printed the *opposite*
 * verdict to the one design §1.2 quotes, because both runs were single-shot. §1.2's "76 % repaid
 * after one keystroke" is therefore an unsupported figure sitting in a design document.
 *
 * **DR-07** asks whether narrowing cache invalidation would beat indexing — the cheaper alternative
 * this feature has never priced. The numbers that answer it are the same three: cold, warm, and
 * warm-after-an-unrelated-edit. If narrowing removed the third cost entirely, what remains is the
 * first, and that is what decides whether the two are alternatives or complements.
 *
 * Uses DR-02a's first-element probe, so all figures are time-to-**first**, the quantity the NFR
 * names — not time-to-exhaustive, which is what §1.2 measured.
 */
class CompNineDr13Test : TimedCompletionTestCase() {
    private fun timeToFirstOnWx(): Long = timeToFirstUs("wx.<caret>\n")

    /** One keystroke in the CONSUMER file — an edit that touches nothing the library declares. */
    private fun keystrokeInConsumer() {
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(myFixture.editor.document.textLength, "-- k\n")
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
    }

    private fun bigLibrary(): String {
        val root = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(3400) { i -> root.append("---@type number\nwx.wxC_$i = nil\n\n") }
        root.append("return wx\n")
        return root.toString()
    }

    fun testDr02cAndDr07() {
        installFirstElementProbe()
        registerLibraryRoot(mapOf("wx.lua" to bigLibrary()))

        val cold = timeToFirstOnWx()
        val warm = (1..5).map { timeToFirstOnWx() }.sorted()[2]

        // Five INDEPENDENT post-edit samples: edit, then complete, five times over.
        val afterEdit =
            (1..5)
                .map {
                    keystrokeInConsumer()
                    timeToFirstOnWx()
                }.sorted()

        println("DR-02c time-to-FIRST cold          = ${cold}us")
        println("DR-02c time-to-FIRST warm (med/5)  = ${warm}us")
        println("DR-02c time-to-FIRST after one keystroke in CONSUMER, 5 samples = $afterEdit")
        println("DR-02c median after edit = ${afterEdit[2]}us")
        val repaid = if (cold > 0) afterEdit[2] * 100 / cold else -1
        println("DR-02c => $repaid% of the cold cost is repaid after an unrelated edit")
        println(
            "DR-02c VERDICT: " +
                if (afterEdit[2] > cold / 2) {
                    "PER-KEYSTROKE — an unrelated edit repays most of the cost"
                } else {
                    "NOT per-keystroke — the library snapshot survives an unrelated edit"
                },
        )
        println(
            "DR-07 => narrowing invalidation could at best take the post-edit figure (${afterEdit[2]}us) " +
                "down to the warm figure (${warm}us). It cannot touch the COLD figure (${cold}us), " +
                "which is the first completion of every session and is what the 100ms budget is missed by.",
        )
    }
}
