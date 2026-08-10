package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager

/**
 * TYPE-11 DR-12 — the **incomplete** residual (Step 9 blocker B4), as an assertion rather than a
 * paragraph.
 *
 * Design §3.6 argues, correctly, that without replay the feature is unsound: `resolveGlobal` is
 * memoized project-wide, so a later build "gets the type with no sources". `LuaTypesSnapshot.forFile`
 * is memoized too and has **no** analogous replay — a snapshot's recorded source set is used for the
 * pin decision and then discarded.
 *
 * The interleaving needs no roots tick, only an ordering inside one modification epoch:
 *
 * 1. `b.lua` (library) is `bGlobal = projectSeed`, and `projectSeed` is declared in a **project**
 *    file, so `forFile(b.lua)` records `{p.lua}` and is correctly NOT pinnable.
 * 2. Something asks for a different global `b.lua` declares, so `forFile(b.lua)` is built and warm.
 * 3. `a.lua` (library) is `aAlias = bGlobal`. Building it calls `resolveGlobal("bGlobal")` →
 *    `typeOfGlobalIn` reports `b.lua` → `globalTypeIn` → `forFile(b.lua)` **cache hit, no frame
 *    opened**. `a.lua` records `{b.lua}` only — every source provisioned — and is pinned while
 *    transitively embedding `p.lua`'s type through `freeGlobalSeed`.
 *
 * Asserts today's correct answer; green on `main`, and the measurement is whether it goes red under
 * the §3 conditional rule as written.
 */
class TypeElevenDr12WarmInnerSnapshotTest : TypeElevenDefinitionLibraryTestCase() {
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

    fun testALibraryWhoseInnerLibrarySnapshotWasServedWarmStillTracksTheProjectFile() {
        installDefinitionLibrary(
            "luassert",
            mapOf(
                "b.lua" to "---@meta\n\nbOther = { fromB = 1 }\nbGlobal = projectSeed\n",
                "a.lua" to "---@meta\n\naAlias = bGlobal\n",
            ),
        )
        val projectDeclaration =
            myFixture.addFileToProject("p.lua", "projectSeed = { beforeEdit = 1 }\n")
        announceRootsChange()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        // Step 2 — warm b.lua's snapshot through a global that does NOT lead to a.lua.
        val other = membersOfGlobal("bOther", consumer)
        println("DR-12 warm-up: bOther = $other")
        assertEquals("the warm-up must actually build b.lua's snapshot", setOf("fromB"), other)

        // Step 3 — a.lua is built now, and its inner forFile(b.lua) is a cache hit.
        val before = membersOfGlobal("aAlias", consumer)
        println("DR-12 before edit: aAlias = $before")
        assertEquals(
            "the library alias must carry the project declaration's members through b.lua",
            setOf("beforeEdit"),
            before,
        )

        rewriteAssertingRootsAreStill(projectDeclaration, "projectSeed = { afterEdit = 1 }\n")

        val after = membersOfGlobal("aAlias", consumer)
        println("DR-12 after edit: aAlias = $after")
        assertEquals(
            "editing the project file must be reflected through the two-library chain, even though " +
                "the inner library snapshot was served warm when the outer one was built",
            setOf("afterEdit"),
            after,
        )
    }
}
