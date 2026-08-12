package net.internetisalie.lunar.type

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * TC-17 — **did a new way of reading another file appear?**
 *
 * Design §3.5 enumerates every place `LuaTypeManagerImpl` turns another file's content into type
 * information, and `LuaTypesVisitor.seedAmbientGlobals` is the counter-example that proves the
 * enumeration is per-file rather than global. Missing a site produces a snapshot judged pinnable
 * while depending on an unrecorded file — a silent stale-type defect, not a crash — so this counts
 * the door calls and fails the build when the count moves. It does not decide whether a new site
 * needs a `reportFile`; it forces the author to.
 *
 * **The matcher is the measured one, not the obvious one** (§1.9 B3). Counting qualified chains was
 * measured against the real file at `1 / 3 / 0`: ktlint wraps both
 * `FileBasedIndex.getInstance().getContainingFiles(…)` sites across lines so the literal appears
 * nowhere, and `PsiManager.getInstance(project).findFile` under-counts because one of the two sites
 * calls through a `psiManager` local. A guard whose count drops when a formatter re-wraps a line is
 * worse than no guard. So: strip comments, remove **all** whitespace, count the bare member name
 * with its opening paren.
 *
 * Comment stripping is prophylactic rather than load-bearing — measured, the counts are identical
 * with and without it, because no comment writes one of these members followed by `(`. It is kept so
 * that a future KDoc writing `.findFile(…)` in prose cannot fail a build that added no call site.
 *
 * Headless by construction: this reads Kotlin as **text**, never as PSI, so it needs no fixture.
 */
class LuaTypeSourceRecorderCoverageTest {
    /**
     * `.membersIn(` joined this list in COMP-09 Phase 3, which is the case the guard was written
     * for: `addMethodsOf` stopped reading `StubIndex.getAllKeys` and started reading
     * `LuaReceiverMemberIndex.membersIn`, a **new kind** of cross-file read that none of the five
     * original members would have counted. Left off the list, the two `.getAllKeys(` removals would
     * have shown up as a count *drop* — trivially re-baselined — while an unrecognised door came in
     * unremarked, which is precisely the silent case this test exists to prevent.
     */
    private val doorMembers =
        listOf(
            ".findFile(",
            ".getElements(",
            ".getContainingFiles(",
            ".getAllKeys(",
            ".getLibraryFiles(",
            ".membersIn(",
        )

    /**
     * Stated mutation: inject one `PsiManager.getInstance(project).findFile(…)` into
     * `LuaTypeManagerImpl` → its `.findFile(` count goes `2 → 3` and this fails. Second check, that
     * the guard is not fooled by formatting alone: re-wrapping an existing `StubIndex.getElements(`
     * across lines leaves `.getElements(` at `3`.
     *
     * **COMP-09 Phase 3 re-earned this count** — `.getAllKeys(` went `2 → 0` and `.membersIn(`
     * `0 → 1`, as `addMethodsOf` swapped a scan of every global-declaration key for a receiver-keyed
     * lookup. The accounting the message demands: **the new door owes no additional `reportFile`,
     * and the two removed ones freed none.** `membersIn` supplies candidate *names*; a name only
     * becomes type information once `declaredMethod` finds a `LuaFuncDecl` behind it, and that
     * branch still reports `decl.containingFile` exactly as the scan did. A candidate with no
     * declaration contributes no member, so no file's content entered the type through it. The
     * `reportFile` call sites in this class are unchanged in number and in argument.
     */
    @Test
    fun `every cross-file door in the type manager is accounted for`() {
        assertEquals(
            "a door call was added to or removed from LuaTypeManagerImpl. Design §3.5 lists every " +
                "place this class turns another file's content into type information; each one owes " +
                "a LuaTypeSourceRecorder.reportFile, or a library snapshot is pinned while " +
                "depending on a file nothing recorded",
            listOf(2, 3, 2, 0, 0, 1),
            countsIn("LuaTypeManagerImpl.kt"),
        )
    }

    /**
     * `LuaTypesVisitor` is here because §3.5's *premise* — "cross-file consumption means
     * `LuaTypeManagerImpl`" — is false: `seedAmbientGlobals` calls
     * `RuntimeLibraryProvider.getLibraryFiles(target)` → `psiManager.findFile` → walks that file's
     * AST, as `buildSnapshot`'s first statement. It is harmless **today** only because it reads
     * `global.lua` under the runtime root, which is provisioned by construction.
     */
    @Test
    fun `the snapshot builder reads exactly one other file`() {
        assertEquals(
            "LuaTypesVisitor gained or lost a cross-file read. It runs INSIDE the recording frame, " +
                "so anything it consumes that is not provisioned by construction must report " +
                "(design §3.5's ⚠, DR-16)",
            listOf(1, 0, 0, 0, 1, 0),
            countsIn("LuaTypesVisitor.kt"),
        )
    }

    /**
     * TYPE-11-DR-21 — the invariant Phase 2 left implicit: **every path that stores a `null` answer
     * reports the absence into the frame it stores.**
     *
     * `LuaTypeManagerRecordingTest.testAMemoizedAbsenceReplaysAsAnAbsence` asserts that the one
     * storing path obeys it; nothing asserted that it is the *only* one, and a future path that
     * writes a `CachedAnswer` with a `null` answer and no absence re-creates §1.8 B1 through the
     * cache — silently, because every behavioural test in the suite would stay green. The
     * enforceable form of "only one path" is the count of construction sites: one declaration plus
     * one `recordInto` write.
     *
     * Stated mutation: add a second `CachedAnswer(…)` construction anywhere in the file → red, and
     * the message tells the author what the new site owes.
     */
    @Test
    fun `only one path stores an answer beside its frame`() {
        assertEquals(
            "a second CachedAnswer construction site appeared. Design §3.6: a stored null answer " +
                "whose frame carries no absence replays as 'no sources at all', which is §1.8 B1 " +
                "re-created through the cache. A new storing path must report the absence into the " +
                "frame it stores — re-earn this count once it does",
            2,
            normalizedSourceOf("LuaTypeManagerImpl.kt").split("CachedAnswer(").size - 1,
        )
    }

    private fun countsIn(fileName: String): List<Int> {
        val normalized = normalizedSourceOf(fileName)
        return doorMembers.map { normalized.split(it).size - 1 }
    }

    private fun normalizedSourceOf(fileName: String): String {
        val sourceFile =
            File(System.getProperty("user.dir"), "src/main/kotlin/net/internetisalie/lunar/lang/psi/types/$fileName")
        val withoutBlocks = BLOCK_COMMENT.replace(sourceFile.readText(), "")
        return withoutBlocks.lines().joinToString("") { withoutLineComment(it) }.filterNot { it.isWhitespace() }
    }

    /**
     * A `//` inside a string literal is not a comment — `LuaTypesVisitor` writes exactly that in its
     * binary-operator table — so the cut is taken only where an even number of unescaped quotes
     * precedes it.
     */
    private fun withoutLineComment(sourceLine: String): String {
        var quotes = 0
        var index = 0
        while (index < sourceLine.length - 1) {
            val character = sourceLine[index]
            if (character == '\\') {
                index += 2
                continue
            }
            if (character == '"') quotes++
            if (character == '/' && sourceLine[index + 1] == '/' && quotes % 2 == 0) return sourceLine.take(index)
            index++
        }
        return sourceLine
    }

    private companion object {
        private val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    }
}
