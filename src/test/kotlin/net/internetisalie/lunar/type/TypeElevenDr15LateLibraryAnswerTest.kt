package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager

/**
 * TYPE-11 DR-15 — Step 9 blocker V2: a successful global resolution that is later out-ranked by a
 * project declaration.
 *
 * `doResolveGlobal` is `typeOfGlobalIn(projectScope) ?: typeOfGlobalIn(allScope)`
 * (`LuaTypeManagerImpl.kt:162-163`) — project scope wins **when it answers**. Library `alpha.lua`
 * references a global that library `lib.lua` declares, and no project file declares it yet: the
 * project-scope pass finds zero candidate files (nothing to iterate, nothing to report), the
 * all-scope fallback finds `lib.lua` and answers, so `doResolveGlobal` **succeeds overall** — no
 * absence is recorded (§3.1 step 5 only fires when the whole call returns null, and this one
 * doesn't). `alpha.lua`'s frame records `{lib.lua}` only, entirely provisioned, so it is pinned.
 *
 * The user then declares the same global in a project file. Project scope now out-ranks `lib.lua`
 * for every *unpinned* caller, but `alpha.lua`'s pinned snapshot never rebuilds — §3.3's own rule is
 * "a pin must be correct at the moment it is taken; there is no second chance."
 *
 * This is `TypeElevenDr11LateDeclarationTest`'s fixture with one change: instead of *nothing*
 * declaring the global, a **library** file declares it. The project declaration arrives via
 * [rewriteAssertingRootsAreStill] on a project file that starts out **not** declaring the name, so
 * the base case's "an unearned green fails loudly" guarantee applies here too.
 */
class TypeElevenDr15LateLibraryAnswerTest : TypeElevenDefinitionLibraryTestCase() {
    private fun membersOfGlobal(
        name: String,
        context: PsiFile,
    ): Set<String> =
        runReadAction {
            LuaTypeManager
                .getInstance(project)
                .resolveGlobal(name, context)
                ?.getMembers()
                ?.keys
                ?.toSet()
                .orEmpty()
        }

    private fun filesDeclaringInProjectScope(name: String): List<String> =
        runReadAction {
            FileBasedIndex
                .getInstance()
                .getContainingFiles(LuaGlobalAssignmentIndex.KEY, name, GlobalSearchScope.projectScope(project))
                .map { it.name }
        }

    fun testAProjectDeclarationWrittenAfterALibraryAnsweredStillOutranksIt() {
        installDefinitionLibrary(
            "luassert",
            mapOf(
                "alpha.lua" to "---@meta\n\nlibAlias = sharedByLibrary\n",
                "lib.lua" to "---@meta\n\nsharedByLibrary = { beforeEdit = 1 }\n",
            ),
        )
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")
        // Starts out declaring nothing relevant, so project scope is genuinely empty for the shared
        // name at build time — the rewrite below is what introduces the first project declaration.
        val projectFile = myFixture.addFileToProject("shared.lua", "local unrelated = 1\n")

        assertEquals(
            "the premise of this test is that NO project file declares the global when the " +
                "library snapshot is built",
            emptyList<String>(),
            filesDeclaringInProjectScope("sharedByLibrary"),
        )
        val before = membersOfGlobal("libAlias", consumer)
        println(
            "DR-15 before: libAlias = $before declaringInProject = " +
                filesDeclaringInProjectScope("sharedByLibrary"),
        )
        assertEquals(
            "libAlias must take the library's declaration when nothing in the project answers yet",
            setOf("beforeEdit"),
            before,
        )

        rewriteAssertingRootsAreStill(projectFile, "sharedByLibrary = { afterProject = 1 }\n")

        val after = membersOfGlobal("libAlias", consumer)
        println(
            "DR-15 after: libAlias = $after declaringInProject = " +
                filesDeclaringInProjectScope("sharedByLibrary"),
        )
        assertEquals(
            "a project declaration written AFTER a library snapshot resolved via the all-scope " +
                "fallback must out-rank that library's answer, exactly as it would for a fresh build",
            setOf("afterProject"),
            after,
        )
    }
}
