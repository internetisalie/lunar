package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaSourceChangeTracker
import net.internetisalie.lunar.lang.psi.types.LuaSourceSetTracker
import net.internetisalie.lunar.lang.psi.types.LuaStampTotal
import net.internetisalie.lunar.lang.psi.types.LuaTypeSourceRecorder
import net.internetisalie.lunar.lang.psi.types.LuaTypes
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * MAINT-63 TC 1 to TC 8 and TC 14 to TC 16: an unpinned snapshot depends on the sources it recorded
 * and on the resolution count (design §3), not on every PSI change. Each method is a falsifier for a
 * row of requirements *Mutation checks*. "Kept" is `assertSame` with the earlier snapshot, "rebuilt"
 * is `assertNotSame`.
 */
class LuaSourceChangeTrackerTest : BasePlatformTestCase() {
    fun testAnEditToAnUnconsumedFileKeepsTheSnapshot_MAINT63_TC1() {
        val a = myFixture.addFileToProject("a.lua", "local M = {}\nfunction M.f() return 1 end\nreturn M\n")
        val b = myFixture.addFileToProject("b.lua", "local a = require(\"a\")\nlocal x = a.f()\nreturn x\n")
        val c = myFixture.addFileToProject("c.lua", "local z = 1\nreturn z\n")
        val before = snapshotOf(b)
        snapshotOf(c)
        val frameUrls = frameOf(b).urls
        assertTrue("b's frame must record a.lua: $frameUrls", a.virtualFile.url in frameUrls)
        assertFalse("b's frame must not record c.lua: $frameUrls", c.virtualFile.url in frameUrls)

        append(c, "\n")

        assertSame("an edit to a file b never consumed must keep b's snapshot", before, snapshotOf(b))
    }

    fun testAnEditToARequiredModuleRetypesTheReader_MAINT63_TC2() {
        val ta = myFixture.addFileToProject("ta.lua", "return { k = 1 }\n")
        val tb = myFixture.addFileToProject("tb.lua", "local a = require(\"ta\")\nlocal x = a.k\nprint(x)\n")
        assertEquals("number", typeOf(tb, "x"))

        replaceText(ta, "k = 1", "k = 'str'")

        assertEquals("string", typeOf(tb, "x"))
    }

    fun testALateGlobalAssignmentRetypesTheReader_MAINT63_TC3() {
        val b2 = myFixture.addFileToProject("b2.lua", "local y = GLOBAL_M63\nreturn y\n")
        val c2 = myFixture.addFileToProject("c2.lua", "local q = 1\nreturn q\n")
        assertEquals("any", typeOf(b2, "y"))

        append(c2, "GLOBAL_M63 = 'str'\n")

        assertEquals("string", typeOf(b2, "y"))
    }

    fun testALateBareClassRetypesTheReader_MAINT63_TC4() {
        val cuse = myFixture.addFileToProject("cuse.lua", "---@type M63Late\nlocal z\nprint(z.n)\n")
        val late = myFixture.addFileToProject("late.lua", "local q = 1\n")
        assertEquals("{ ... }", typeOf(cuse, "z"))

        append(late, "---@class M63Late\n---@field n number\n")

        assertEquals("M63Late", typeOf(cuse, "z"))
    }

    fun testASourceOfAPendingFrameRebuildsTheReader_MAINT63_TC5() {
        val z = myFixture.addFileToProject("z.lua", "return { k = 1 }\n")
        val p2 =
            myFixture.addFileToProject(
                "p2.lua",
                "local z = require(\"z\")\nlocal P = {}\nP.v = z.k\nlocal q = require(\"q2\")\nreturn P\n",
            )
        val q2 = myFixture.addFileToProject("q2.lua", "local p = require(\"p2\")\nlocal w = p.v\nreturn w\n")
        snapshotOf(p2)
        val before = snapshotOf(q2)
        val frame = frameOf(q2)
        assertTrue(
            "q2's frame must hit p2 in progress: ${frame.inProgressHits}",
            p2.virtualFile.url in frame.inProgressHits,
        )
        assertFalse("q2's frame must not record z.lua itself: ${frame.urls}", z.virtualFile.url in frame.urls)

        replaceText(z, "k = 1", "k = 'str'")

        assertNotSame("z.lua reaches q2 only through p2's pending frame", before, snapshotOf(q2))
    }

    fun testAFileLessPsiEventRebuildsTheSnapshot_MAINT63_TC6() {
        val d = myFixture.addFileToProject("d.lua", "local x = 1\nprint(x)\n")
        val before = snapshotOf(d)

        WriteCommandAction.runWriteCommandAction(project) { PsiManager.getInstance(project).dropPsiCaches() }

        assertNotSame("an event that names no file must rebuild every unpinned snapshot", before, snapshotOf(d))
    }

    fun testDumbModeKeepsTheSnapshotWhileDumbAndRebuildsOnExit_MAINT63_TC7() {
        val e = myFixture.addFileToProject("e.lua", "local x = 1\nprint(x)\n")
        var s1: LuaTypes? = null
        var s2: LuaTypes? = null

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            s1 = snapshotOf(e)
            s2 = snapshotOf(e)
            typeOf(e, "x")
        }
        val afterDumb = snapshotOf(e)

        assertNotNull("the dumb-mode reads must have run", s1)
        assertSame("a snapshot read twice in dumb mode is kept", s1, s2)
        assertNotSame("the first read after dumb mode rebuilds", s1, afterDumb)
    }

    fun testAnUnidentifiableFrameKeepsTheModificationCount_MAINT63_TC8() {
        val f = myFixture.addFileToProject("f.lua", "local x = 1\n")
        val warm = LuaTypeSourceRecorder.SourceFrame().apply { unreplayedWarm += "file:///w.lua" }
        val unidentified = LuaTypeSourceRecorder.SourceFrame().apply { urls += "unidentified:consumed-file" }
        val empty = LuaTypeSourceRecorder.SourceFrame()

        val dependencies =
            runReadAction { listOf(warm, unidentified, empty).map { LuaTypesSnapshot.churnDependencyFor(f, it) } }

        assertSame("a warm unreplayed frame", PsiModificationTracker.MODIFICATION_COUNT, dependencies[0])
        assertSame("an unidentified source", PsiModificationTracker.MODIFICATION_COUNT, dependencies[1])
        assertTrue("an empty frame gets a tracker: ${dependencies[2]}", dependencies[2] is ModificationTracker)
        assertNotSame(PsiModificationTracker.MODIFICATION_COUNT, dependencies[2])
    }

    fun testAGlobalTableDeclaredLater_MAINT63_TC14_gtab() {
        val gc = myFixture.addFileToProject("gc14.lua", "local q = 1\nreturn q\n")
        val gb = myFixture.addFileToProject("gb14.lua", "local y = G14\nprint(y)\n")
        assertSettledEqualsRebuilt(Probe(gb, "y", "any" to "{ ... }")) { append(gc, "G14 = {}\n") }
    }

    fun testABareClassDeclaredLater_MAINT63_TC14_lclass0() {
        val kc = myFixture.addFileToProject("kc14.lua", "local q = 1\n")
        val kb = myFixture.addFileToProject("kb14.lua", "---@type K14\nlocal z\nprint(z)\n")
        assertSettledEqualsRebuilt(Probe(kb, "z", "undefined" to "K14")) { append(kc, "---@class K14\n") }
    }

    fun testAGlobalFunctionDeclaredLater_MAINT63_TC14_gfunc() {
        val fc = myFixture.addFileToProject("fc14.lua", "local q = 1\nreturn q\n")
        val fb = myFixture.addFileToProject("fb14.lua", "local y = F14()\nprint(y)\n")
        assertSettledEqualsRebuilt(Probe(fb, "y", "undefined" to "string")) {
            append(fc, "function F14() return 'str' end\n")
        }
    }

    fun testAMethodDeclaredLaterInAnotherFile_MAINT63_TC14_method() {
        myFixture.addFileToProject("mk14.lua", "---@class M14\nlocal M14 = {}\nreturn M14\n")
        val mj = myFixture.addFileToProject("mj14.lua", "local q = 1\n")
        val mb = myFixture.addFileToProject("mb14.lua", "---@type M14\nlocal o\nlocal y = o:run()\nprint(y)\n")
        assertSettledEqualsRebuilt(Probe(mb, "y", "undefined" to "string")) {
            append(mj, "---@return string\nfunction M14:run() return 'str' end\n")
        }
    }

    fun testAMemberFieldAssignedLater_MAINT63_TC14_mfield2() {
        myFixture.addFileToProject("od14.lua", "---@param cb fun(x: string)\nfunction O14.f(cb) end\n")
        val oe = myFixture.addFileToProject("oe14.lua", "local q = 1\n")
        val ob = myFixture.addFileToProject("ob14.lua", "O14.f(function(x) print(x) end)\n")
        assertSettledEqualsRebuilt(Probe(ob, "x", "string" to "undefined")) { append(oe, "O14.f = O14.f\n") }
    }

    fun testALocalFunctionParamChanged_MAINT63_TC14_localfn() {
        val lm =
            myFixture.addFileToProject(
                "lm14.lua",
                "---@param cb fun(x: string)\nlocal function f(cb) end\nreturn { f = f }\n",
            )
        val lu = myFixture.addFileToProject("lu14.lua", "local m = require(\"lm14\")\nm.f(function(x) print(x) end)\n")
        assertSettledEqualsRebuilt(Probe(lu, "x", "string" to "number")) { replaceText(lm, "x: string", "x: number") }
    }

    fun testARequiredModuleCreatedLater_MAINT63_TC14_newfile() {
        val nr = myFixture.addFileToProject("nr14.lua", "local m = require(\"nf14\")\nlocal y = m.k\nprint(y)\n")
        assertSettledEqualsRebuilt(Probe(nr, "y", "undefined" to "string")) {
            myFixture.addFileToProject("nf14.lua", "return { k = 'str' }\n")
        }
    }

    fun testADegradedPendingFrameMovesTheCount_MAINT63_TC15() {
        val f = myFixture.addFileToProject("f.lua", "local x = 1\n")
        val g = myFixture.addFileToProject("g.lua", "local y = 1\n")
        val warm = LuaTypeSourceRecorder.SourceFrame().apply { unreplayedWarm += "file:///w.lua" }
        val unidentified = LuaTypeSourceRecorder.SourceFrame().apply { urls += "unidentified:consumed-file" }
        val nested = LuaTypeSourceRecorder.SourceFrame().apply { pendingFrames += warm }
        val clean = LuaTypeSourceRecorder.SourceFrame().apply { urls += "file:///clean.lua" }
        val owner = LuaSourceChangeTracker.getInstance(project)
        val sources = setOf(f.virtualFile.url)
        val trackers = listOf(warm, unidentified, nested, clean).map { LuaSourceSetTracker(owner, sources, listOf(it)) }

        val before = runReadAction { trackers.map { it.modificationCount } }
        append(g, "\n")
        val after = runReadAction { trackers.map { it.modificationCount } }

        listOf("warm", "unidentified", "nested").forEachIndexed { index, name ->
            assertTrue(
                "the $name pending frame must move the count: ${before[index]} -> ${after[index]}",
                after[index] != before[index],
            )
        }
        assertEquals("a clean pending frame must not move on an unrelated edit", before[3], after[3])
    }

    fun testTheStampTotalNeverRepeatsAValue_MAINT63_TC16() {
        val total = LuaStampTotal()
        val sequence =
            listOf(
                total.advance(longArrayOf(5, 5, 5, 5, 5), 0),
                total.advance(longArrayOf(5, 5, 5, 5, 5), 0),
                total.advance(longArrayOf(5, 5, 5, 5, 6), 0),
                total.advance(longArrayOf(4, 5, 5, 5, 6), 0),
                total.advance(longArrayOf(4, 5, 5, 5, 6), 0),
                total.advance(longArrayOf(4, 5, 5, 5, 6), 1),
            )

        assertEquals(listOf(25L, 25L, 26L, 27L, 27L, 28L), sequence)
    }

    /** The local [name] read in [reader], whose type goes from `types.first` to `types.second`. */
    private class Probe(
        val reader: PsiFile,
        val name: String,
        val types: Pair<String, String>,
    )

    private fun assertSettledEqualsRebuilt(
        probe: Probe,
        edit: () -> Unit,
    ) {
        assertEquals("before the edit", probe.types.first, typeOf(probe.reader, probe.name))
        edit()
        val settled = typeOf(probe.reader, probe.name)
        forceFullRebuild()
        val rebuilt = typeOf(probe.reader, probe.name)
        assertEquals("the settled type must equal a full rebuild's", rebuilt, settled)
        assertEquals("after the edit", probe.types.second, settled)
    }

    private fun snapshotOf(file: PsiFile): LuaTypes = runReadAction { LuaTypesSnapshot.forFile(file) }

    private fun frameOf(file: PsiFile): LuaTypeSourceRecorder.SourceFrame =
        checkNotNull(LuaTypeSourceRecorder.snapshotFrames[snapshotOf(file)])

    private fun typeOf(
        file: PsiFile,
        name: String,
    ): String =
        runReadAction {
            val ref = PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java).last { it.text == name }
            LuaTypesSnapshot.forFile(file).getValueType(ref).displayName()
        }

    private fun append(
        file: PsiFile,
        text: String,
    ) {
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = checkNotNull(documentManager.getDocument(file))
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.textLength, text) }
        documentManager.commitDocument(document)
    }

    private fun replaceText(
        file: PsiFile,
        old: String,
        new: String,
    ) {
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = checkNotNull(documentManager.getDocument(file))
        check(old in document.text) { "fixture text lacks '$old'" }
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace(old, new)) }
        documentManager.commitDocument(document)
    }

    private fun forceFullRebuild() {
        (LuaProjectSettings.getInstance(project).state.targetModificationTracker as SimpleModificationTracker)
            .incModificationCount()
        WriteCommandAction.runWriteCommandAction(project) { PsiManager.getInstance(project).dropPsiCaches() }
    }
}
