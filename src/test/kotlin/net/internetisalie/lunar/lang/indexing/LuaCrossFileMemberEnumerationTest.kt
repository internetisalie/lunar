package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.definitions.LibraryRootTestCase

/**
 * BUG-439 — a global's members declared in a **sibling file** must be offered.
 *
 * Found by COMP-09's live IDE verification against `LuaCATS/love2d`, where `love.` offered the 40
 * members declared in `love.lua` and **none of the 19 submodules**: `love.graphics = {}` and its
 * siblings are plain member assignments in other files, so the whole 100-function `love.graphics`
 * API was unreachable by completion. No fixture in the feature could have shown it — every one of
 * them declares a receiver and its members in the same file, the same uniformity that hid BUG-436.
 *
 * The reason it survived so long is worth stating where the fix is tested: the completion door
 * selected declaring files from `LuaGlobalAssignmentIndex`, which lists files that assign the
 * **global**. A file that only extends one never appears there at all, so unioning declaring files
 * was a no-op — there was only ever one. Both halves are needed, and
 * [testASiblingFileIsNotADeclaringFileByTheAssignmentIndexAlone] pins the half that is easy to omit.
 */
class LuaCrossFileMemberEnumerationTest : LibraryRootTestCase() {
    /** The report's own reproduction, verbatim. */
    fun testMembersDeclaredInASiblingFileAreOffered() {
        myFixture.addFileToProject(
            "probe_a.lua",
            "---@class Probe\nProbe = {}\nfunction Probe.sameFileFn() end\nProbe.sameFileVal = 1\n",
        )
        myFixture.addFileToProject(
            "probe_b.lua",
            "Probe.otherFileVal = 2\nfunction Probe.otherFileFn() end\nProbe.nested = {}\n",
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        assertEquals(
            "the three members declared in probe_b.lua are the bug",
            listOf("nested", "otherFileFn", "otherFileVal", "sameFileFn", "sameFileVal"),
            globalNamesOf("Probe"),
        )
    }

    /**
     * The control that made the bug attributable to the **file boundary** rather than to the
     * assignment form: `ngx.X = …` in the declaring file always enumerated fine.
     */
    fun testMembersDeclaredInTheDeclaringFileAreStillOffered() {
        myFixture.addFileToProject("solo.lua", "Solo = {}\nSolo.a = 1\nfunction Solo.b() end\n")
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        assertEquals(listOf("a", "b"), globalNamesOf("Solo"))
    }

    /**
     * The half that is a no-op on its own. `sibling.lua` assigns a *member* of `Ext`, never `Ext`
     * itself, so `LuaGlobalAssignmentIndex` does not list it and no union over that index's files
     * can ever reach it — which is why the fix has to consult `LuaReceiverMemberIndex`'s own key
     * space, and in turn why the file-local sentinel had to exist.
     */
    fun testASiblingFileIsNotADeclaringFileByTheAssignmentIndexAlone() {
        myFixture.addFileToProject("root.lua", "Ext = {}\n")
        val sibling = myFixture.addFileToProject("sibling.lua", "Ext.fromSibling = 1\n")
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        val assigning =
            runReadAction {
                FileBasedIndex
                    .getInstance()
                    .getContainingFiles(LuaGlobalAssignmentIndex.KEY, "Ext", GlobalSearchScope.projectScope(project))
            }
        assertFalse(
            "if this ever lists the sibling, the second half of the fix is redundant and should go",
            assigning.contains(sibling.virtualFile),
        )
        assertEquals("and yet its member is offered", listOf("fromSibling"), globalNamesOf("Ext"))
    }

    /**
     * A sibling that binds the receiver opaquely still costs the receiver its authority, now that
     * such a file can be a candidate at all. Before BUG-439 only the first declaring file could
     * raise the flag; DR-19c's rule is that authority is a property of the **receiver**.
     */
    fun testAnOpaqueSiblingBindingCostsTheReceiverItsAuthority() {
        myFixture.addFileToProject("opaque_a.lua", "OM = {}\nfunction OM.direct() end\n")
        myFixture.addFileToProject("opaque_b.lua", "OM = require(\"elsewhere\")\nfunction OM.extra() end\n")
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        val membership = runReadAction { LuaReceiverMemberIndex.globalMembership("OM", project, null) }
        assertFalse("a sibling binds OM to something the index cannot see through", membership.authoritative)
        assertEquals(listOf("direct", "extra"), membership.members.map { it.name }.sorted())
    }

    /**
     * **A hazard BUG-439's own fix introduced, caught before it shipped.** The scope chain was
     * `projectScope` then `allScope`, and that was safe only while a project file entered the
     * candidate set by *bare-assigning* the global — a non-empty project tier meant a project
     * declaration. Admitting files that merely extend the receiver breaks the implication: one
     * `Lib.myHelper = …` in your own code makes the project tier non-empty, `allScope` is never
     * consulted, and the library's members disappear behind your one addition.
     *
     * Which is a strictly worse bug than the one being fixed — it takes members away from a case
     * that worked. `scopeChain` now asks `LuaGlobalAssignmentIndex` directly whether the *project*
     * declares the receiver, rather than inferring it from a candidate set that no longer means that.
     */
    fun testExtendingALibraryGlobalDoesNotHideTheLibrarysMembers() {
        registerLibraryRoot(
            mapOf("lib.lua" to "---@meta\n\nLib = {}\n\nfunction Lib.fromLibrary() end\n\nreturn Lib\n"),
        )
        myFixture.addFileToProject("mine.lua", "Lib.myHelper = 1\n")
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        assertEquals(
            "extending a library global from the project must ADD to its members, not replace them",
            listOf("fromLibrary", "myHelper"),
            globalNamesOf("Lib"),
        )
    }

    /**
     * **The indexer must survive malformed source, and `@NotNull` getters do not tell you that.**
     *
     * Grammar-Kit generates required children as `findNotNullChildByClass`, and `notNullChild` does
     * not return null when the child is missing — `PsiElementBase:293` calls `LOG.error`, which is a
     * reported IDE exception in production and a hard suite failure under `TestLoggerFactory`. An
     * indexer sees every file in the project, and a file being typed into is malformed most of the
     * time, so this is the normal case rather than the edge.
     *
     * The fixture is the one that caught it: `repeat` is a Lua keyword, so
     * `local function repeat(count)` parses to a `LuaLocalFuncDecl` with **no name node at all**.
     * BUG-439's first cut called `decl.nameRef.text` on it and turned
     * `TestLuaTypeEnginePhase1.testComplexPhase1File` red — a test in an unrelated package — while
     * every test of this index stayed green.
     *
     * **The real assertion is that this method completes**, since `LOG.error` fails the test by
     * itself; the member check is there so the method cannot pass by never reaching the indexer. It
     * deliberately reads a receiver from a *different* file: `repeat` opens a repeat-block, so the
     * malformed declaration swallows everything after it, and asserting on `broken.lua`'s own
     * contents would be asserting about parser recovery rather than about the indexer.
     */
    fun testAMalformedLocalDeclarationDoesNotLogAnError() {
        myFixture.addFileToProject(
            "broken.lua",
            "---@type number\nlocal globalNum\n\nlocal function repeat(count)\n    return count\nend\n",
        )
        myFixture.addFileToProject("ok.lua", "Broken = {}\nfunction Broken.stillIndexed() end\n")
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        assertEquals(
            "a malformed file must not stop the project being indexed",
            listOf("stillIndexed"),
            globalNamesOf("Broken"),
        )
    }

    private fun globalNamesOf(receiver: String): List<String> =
        runReadAction {
            LuaReceiverMemberIndex.membersOfGlobal(receiver, project, null).map { it.name }.sorted()
        }
}
