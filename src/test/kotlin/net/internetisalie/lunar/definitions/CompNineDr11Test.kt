package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberWork

/**
 * THROWAWAY — COMP-09 DR-11, the last undesigned requirement.
 *
 * COMP-09-09 asks that "**entries traversed** per enumeration is instrumented and asserted
 * proportional to matching entries; adding unrelated indexed content must not increase it". DR-02a's
 * timing probe cannot answer that: a duration is not a count, and a fast run over the whole key space
 * would satisfy a latency gate while violating this one.
 *
 * Two questions, and the second is the one that makes the requirement meaningful:
 *   A  is a traversal count observable at all, and does it equal the declaring files' contribution?
 *   B  does it stay put when a large amount of **unrelated** indexed content is added?
 *
 * B is the mutation proof in reverse: it is the assertion that would go red if anyone reintroduced a
 * `getAllKeys` scan, which is the defect COMP-09-01 exists to remove and which an earlier revision of
 * the design proposed replacing one scan with another form of.
 */
class CompNineDr11Test : LibraryRootTestCase() {
    private fun target(members: Int): String {
        val sb = StringBuilder("---@meta\n\n---@class Target\nTarget = {}\n\n")
        repeat(members) { i -> sb.append("---@return boolean\nfunction Target.m$i() end\n\n") }
        sb.append("return Target\n")
        return sb.toString()
    }

    /**
     * Indexed content that has nothing to do with `Target` — the noise the bound must ignore.
     *
     * Members are **function declarations**, not `Noise.n = nil` assignments. The first cut of this
     * fixture used assignments, which are not stubbed (only `LuaFuncDecl`/`LuaLocalVarDecl`/
     * `LuaLocalFuncDecl` are), so the stub key space stayed at 54 in both arms and the comparison
     * against "what a `getAllKeys` scan would traverse" compared nothing.
     */
    private fun noise(
        receivers: Int,
        membersEach: Int,
    ): Map<String, String> =
        (0 until receivers).associate { r ->
            val sb = StringBuilder("---@meta\n\n---@class Noise$r\nNoise$r = {}\n\n")
            repeat(membersEach) { i -> sb.append("---@return boolean\nfunction Noise$r.n$i() end\n\n") }
            "noise$r.lua" to sb.toString()
        }

    private fun measure(receiver: String): Triple<Int, Int, Int> =
        runReadAction {
            LuaReceiverMemberWork.reset()
            val found =
                LuaReceiverMemberIndex
                    .membersOf(receiver, project, GlobalSearchScope.allScope(project))
                    .size
            Triple(found, LuaReceiverMemberWork.entries, LuaReceiverMemberWork.files)
        }

    /** A: quiet project — 1 declaring file, 50 members. */
    fun testDr11QuietProject() {
        registerLibraryRoot(mapOf("target.lua" to target(50)))
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        val (found, entries, files) = measure("Target")
        println("DR-11 QUIET  found=$found entriesTraversed=$entries filesVisited=$files")
        val keys = runReadAction { StubIndex.getInstance().getAllKeys(LuaGlobalDeclarationIndex.KEY, project).size }
        println("DR-11 QUIET  total stub keys in project = $keys  (what a getAllKeys scan would traverse)")
    }

    /** B: the same receiver, same members, plus 40 unrelated receivers x 100 members. */
    fun testDr11NoisyProject() {
        val files = mutableMapOf("target.lua" to target(50))
        files += noise(receivers = 40, membersEach = 100)
        registerLibraryRoot(files)
        myFixture.configureByText("consumer.lua", "local x = 1\n")

        val (found, entries, visited) = measure("Target")
        println("DR-11 NOISY  found=$found entriesTraversed=$entries filesVisited=$visited")
        val keys = runReadAction { StubIndex.getInstance().getAllKeys(LuaGlobalDeclarationIndex.KEY, project).size }
        println("DR-11 NOISY  total stub keys in project = $keys  (what a getAllKeys scan would traverse)")

        val (nFound, nEntries, nVisited) = measure("Noise7")
        println("DR-11 NOISY  Noise7: found=$nFound entriesTraversed=$nEntries filesVisited=$nVisited")
        println("DR-11 => compare QUIET's entries with NOISY's: equal means the bound holds by construction")
    }
}
