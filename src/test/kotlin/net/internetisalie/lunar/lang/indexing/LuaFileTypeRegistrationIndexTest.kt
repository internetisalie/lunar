package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex

/**
 * BUG-436 — every whole-project index must accept what `LuaFileType` is registered for, not the
 * first half of the first half of it.
 *
 * `plugin.xml:99-100` registers `extensions="lua;rockspec"` **and** `fileNames=".luacheckrc;.busted"`.
 * Five indexes re-stated that as `file.extension == "lua"`, so a declaration in a `.rockspec`, a
 * `.luacheckrc` or a `.busted` file was not stale in the index — it was **absent**.
 *
 * **Exact sets, not `contains`.** Every instrument in this area is built to catch a *superset*: the
 * COMP-09 golden diffs assert added rows, the corpus ratchet stops on movement, and COMP-09-06's
 * acceptance is "if any baseline moves, stop". A *subset* — declarations that quietly stop existing
 * — passes all three, which is why this defect survived a feature that was measuring intensively
 * right next to it. Asserting the exact set is the only shape that can fail in the subset direction.
 */
class LuaFileTypeRegistrationIndexTest : BasePlatformTestCase() {
    /** The load-bearing one: this index picks the declaring file for global member completion. */
    fun testGlobalAssignmentsAreIndexedInEveryRegistration() {
        seedAllFourRegistrations()
        assertEquals(
            "a global bare-assigned in a .rockspec/.luacheckrc/.busted is invisible to completion " +
                "without this — the file is never even a candidate declaring file",
            listOf("Widget"),
            keysMatching(LuaGlobalAssignmentIndex.KEY, "Widget"),
        )
        assertEquals(
            "and all four declaring files must be found, not just the .lua one",
            listOf(".busted", ".luacheckrc", "w.lua", "w.rockspec"),
            filesDeclaring(LuaGlobalAssignmentIndex.KEY, "Widget"),
        )
    }

    /**
     * A bare `---@class` in a non-`.lua` registration must reach Go to Class / Go to Symbol.
     *
     * **`getContainingFiles`, never `getAllKeys`.** The first cut of this method asserted over
     * `getAllKeys(KEY, project)` and **SURVIVED its own mutation** — restoring the narrow filter left
     * it green, so it proved nothing. Index storage is shared across test methods in one JVM and
     * `getAllKeys` is not really project-scoped; `MemberEnumerationWorkBoundGateTest`'s KDoc already
     * records the same trap (DR-11 measured 4 145 stub keys in *both* arms of a comparison). The
     * file-scoped query is the one that can fail.
     */
    fun testCatsTypeNamesAreIndexedInEveryRegistration() {
        seedAllFourRegistrations()
        assertEquals(
            "a bare `---@class` in a .rockspec is invisible to Go to Class without this",
            listOf("w.rockspec"),
            filesDeclaring(LuaCatsTypeNameIndex.KEY, "FromRockspec"),
        )
        assertEquals(listOf(".luacheckrc"), filesDeclaring(LuaCatsTypeNameIndex.KEY, "FromLuacheckrc"))
        assertEquals(listOf(".busted"), filesDeclaring(LuaCatsTypeNameIndex.KEY, "FromBusted"))
        assertEquals("the control — the .lua registration always worked", listOf("w.lua"), filesDeclaring(LuaCatsTypeNameIndex.KEY, "FromLua"))
    }

    /** `@field` members declared in the other three registrations. */
    fun testMemberFieldsAreIndexedInEveryRegistration() {
        seedAllFourRegistrations()
        assertEquals(
            listOf(".busted", ".luacheckrc", "w.lua", "w.rockspec"),
            filesDeclaring(LuaMemberFieldIndex.KEY, "Widget.tag"),
        )
    }

    /*
     * NOT COVERED: `LuaFileBindingsIndex`'s widening. Mutation-proved as SURVIVED — restoring the
     * ANDed extension test leaves this class green — and recorded rather than left silent.
     *
     * Three fixtures were tried and all three asserted nothing: that index stores neither globals nor
     * a `local` binding for the shapes used here, returning empty `getFileData` for `b.lua` too, i.e.
     * for the control the defect never touched. An assertion that cannot distinguish the fix from its
     * absence is worse than none, so it was removed rather than left looking like coverage. Closing
     * this needs the fixture shape that actually produces a record — most likely a `require` across
     * files, which is what the index exists to resolve — and is worth one focused pass, not a guess.
     */

    private fun seedAllFourRegistrations() {
        myFixture.addFileToProject("w.lua", declarationsIn("Lua"))
        myFixture.addFileToProject("w.rockspec", declarationsIn("Rockspec"))
        myFixture.addFileToProject(".luacheckrc", declarationsIn("Luacheckrc"))
        myFixture.addFileToProject(".busted", declarationsIn("Busted"))
        myFixture.configureByText("consumer.lua", "local x = 1\n")
    }

    /** One bare global assignment and one bare `---@class`, per registration. */
    private fun declarationsIn(suffix: String): String =
        "Widget = {}\nfunction Widget.from$suffix() end\nWidget.tag = 1\n\n---@class From$suffix\n"

    private fun keysMatching(
        key: com.intellij.util.indexing.ID<String, String>,
        name: String,
    ): List<String> =
        runReadAction {
            FileBasedIndex.getInstance().getAllKeys(key, project).filter { it == name }.sorted()
        }

    private fun filesDeclaring(
        key: com.intellij.util.indexing.ID<String, String>,
        name: String,
    ): List<String> =
        runReadAction {
            FileBasedIndex
                .getInstance()
                .getContainingFiles(key, name, GlobalSearchScope.projectScope(project))
                .map { it.name }
                .sorted()
        }
}
