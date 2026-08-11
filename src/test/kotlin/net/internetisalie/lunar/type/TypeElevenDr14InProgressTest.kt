package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * TYPE-11 DR-14 — Step 9 blocker V1: is the `LuaTypesVisitor.inProgressSnapshot` early return
 * (`LuaTypes.kt:214`) ever served for a file **other** than the one whose own re-entrant call is
 * directly on the stack, and if so, does it ship a stale type?
 *
 * `design.md` §3.7's last bullet: "it is the same file's own in-flight build, whose frame is the
 * very frame currently open." The guard is a map keyed on the *requested* file
 * (`LuaTypesVisitor.kt:1483-1487`) and `buildSnapshot` adds an entry for every file whose build is
 * on the current thread's call stack (`:1507-1518`) — nothing limits that to one entry.
 *
 * Fixture: two **library** files forming a genuine mutual-reference cycle, with the depended-upon
 * value seeded from a **project** file:
 *
 * ```
 * outer.lua:  OuterSeed = projectSeed        -- statement 1, fully resolves before statement 2
 *             OuterGlobal = InnerSeed        -- statement 2, nests into inner.lua
 * inner.lua:  InnerSeed = OuterSeed          -- resolves back into outer.lua, mid-build
 * ```
 *
 * Resolving `OuterGlobal` starts `forFile(outer.lua)`; while outer.lua's traversal is blocked on
 * `InnerSeed`, `inner.lua`'s traversal (nested one level in) resolves `OuterSeed` and calls
 * `forFile(outer.lua)` again — for the file that is genuinely still on the stack, but **not** the
 * file inner.lua's own build is for. That is "several files legitimately in progress on one
 * thread," not the trivial single-file self-loop the design's wording suggests.
 */
class TypeElevenDr14InProgressTest : TypeElevenDefinitionLibraryTestCase() {
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

    private fun captureStdout(body: () -> Unit): String {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer))
        try {
            body()
        } finally {
            System.setOut(original)
        }
        return buffer.toString()
    }

    private fun installCycle(): PsiFile {
        installDefinitionLibrary(
            "luassert",
            mapOf(
                "outer.lua" to "---@meta\n\nOuterSeed = projectSeed\nOuterGlobal = InnerSeed\n",
                "inner.lua" to "---@meta\n\nInnerSeed = OuterSeed\n",
            ),
        )
        return myFixture.addFileToProject("p.lua", "projectSeed = { beforeEdit = 1 }\n")
    }

    /**
     * Q14a — is the interleaving reachable? A trace println at `LuaTypes.kt:214` is the cheapest
     * instrument (per the de-risking round's scaffold); this test only needs the *shape* of the
     * cycle to exist and to be exercised, and is otherwise a plain correctness check so it stays
     * green whether or not the scaffold's trace line is present.
     */
    fun testMutualReferenceCycleBetweenTwoLibraryFilesResolvesWithoutRecursing() {
        installCycle()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        val output =
            captureStdout {
                val outer = membersOfGlobal("OuterGlobal", consumer)
                println("DR-14 OuterGlobal = $outer")
            }
        print(output)

        val innerAlone = membersOfGlobal("InnerSeed", consumer)
        assertEquals(
            "InnerSeed must carry the project seed's members through the mutual-reference cycle",
            setOf("beforeEdit"),
            innerAlone,
        )
    }

    /**
     * Q14b — if reachable, does it ship a stale type? `inner.lua`'s own build is a **normal**,
     * top-level `forFile(inner.lua)` call (nested inside outer.lua's build, not itself a re-entrant
     * hit) — so it is recorded and cached exactly like any other library file. What it records is
     * the defect: `typeOfGlobalIn` reports `outer.lua` as visited (a provisioned file), but the
     * project dependency `outer.lua` itself carries (`p.lua`, recorded only into *outer's* frame,
     * pushed onto the stack before inner's frame existed) never reaches inner's frame, because the
     * re-entrant `forFile(outer.lua)` call inner.lua makes returns via `inProgressSnapshot` and
     * skips the whole recording/replay mechanism (§3.7's own rule, applied to itself).
     *
     * Asserts today's correct answer; green on `main`. The measurement is whether it goes red under
     * the §3 conditional rule as written (no DR-14 guard) and green under the candidate rule.
     */
    fun testALibraryTransitivelyEmbeddingAProjectTypeThroughAReentrantCycleStillTracksIt() {
        val projectFile = installCycle()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        // Drive the cycle: outer.lua's build nests into inner.lua's, which reenters outer.lua.
        val outerBefore = membersOfGlobal("OuterGlobal", consumer)
        println("DR-14 before edit: OuterGlobal = $outerBefore")
        val innerBefore = membersOfGlobal("InnerSeed", consumer)
        println("DR-14 before edit: InnerSeed = $innerBefore")
        assertEquals(
            "InnerSeed must carry the project seed's members before the edit",
            setOf("beforeEdit"),
            innerBefore,
        )

        rewriteAssertingRootsAreStill(projectFile, "projectSeed = { afterEdit = 1 }\n")

        val innerAfter = membersOfGlobal("InnerSeed", consumer)
        println("DR-14 after edit: InnerSeed = $innerAfter")
        assertEquals(
            "editing the project file must be reflected in InnerSeed's type, even though InnerSeed " +
                "was built while outer.lua's build was still in progress on the same thread",
            setOf("afterEdit"),
            innerAfter,
        )
    }
}
