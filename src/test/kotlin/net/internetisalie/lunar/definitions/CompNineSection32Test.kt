package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaCatsTypeNameIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsClassTag
import kotlin.system.measureTimeMillis

/**
 * THROWAWAY — COMP-09 design §3.2: is the `@class` door (`resolveType` → `materializeClass`)
 * dominated by `LuaTypesSnapshot.forFile`, or by something else?
 *
 * It matters because §1.5 established the `resolveGlobal` door is entirely `forFile`, and §1.4 showed
 * `resolveType` is a *second* door to the same enumeration. If the two have different bottlenecks,
 * one fix does not cover both — and COMP-09-01's site list describes only the first.
 *
 * Reading `materializeClass` suggests it may never call `forFile` at all: it goes through
 * `catsClassTags` (getContainingFiles then a whole-file `findChildrenOfType`), `LuaImplicitFields`
 * (an assignment walk) and `addMethodsOf` (getAllKeys then getElements per matching key). That is a
 * hypothesis from the call shape — the same kind that was refuted twice already — so it is measured.
 */
class CompNineSection32Test : LibraryRootTestCase() {
    /** A large library file holding several classes, each with many colon-declared methods. */
    private fun bigLibrary(
        classes: Int,
        methodsEach: Int,
    ): String {
        val sb = StringBuilder("---@meta\n\n")
        repeat(classes) { c ->
            sb.append("---@class Big$c\nlocal Big$c = {}\n\n")
            repeat(methodsEach) { m ->
                sb.append("---@param a number\n---@return boolean\nfunction Big$c:M$m(a) end\n\n")
            }
        }
        return sb.toString()
    }

    fun testSection32ClassDoorBuckets() {
        val text = bigLibrary(classes = 8, methodsEach = 500)
        // A second, identical-shaped file nothing will touch until the cold-AST measurement below.
        val untouched = bigLibrary(classes = 8, methodsEach = 500).replace("Big", "Cold")
        val libRoot = registerLibraryRoot(mapOf("big.lua" to text, "cold.lua" to untouched))
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        println("§3.2 fixture: ${text.length / 1024} KiB, 8 classes x 500 colon methods = 4000 methods")

        runReadAction {
            val manager = LuaTypeManager.getInstance(project)
            val context = myFixture.file
            val scope = GlobalSearchScope.allScope(project)

            // --- the whole @class door, cold (Big0 has never been resolved)
            var members0 = 0
            val resolveTypeMs =
                measureTimeMillis {
                    members0 =
                        manager
                            .resolveType("Big0", context)
                            ?.let {
                                net.internetisalie.lunar.lang.psi.types.LuaGraphType
                                    .materialize(it, context)
                                    .getMembers()
                                    .size
                            } ?: -1
                }
            println("§3.2 resolveType(\"Big0\") + materialize + getMembers = ${resolveTypeMs}ms  members=$members0")

            // --- a SECOND class in the same file: file-level work is now warm, class work is not
            var members1 = 0
            val secondClassMs =
                measureTimeMillis {
                    members1 =
                        manager
                            .resolveType("Big1", context)
                            ?.let {
                                net.internetisalie.lunar.lang.psi.types.LuaGraphType
                                    .materialize(it, context)
                                    .getMembers()
                                    .size
                            } ?: -1
                }
            println(
                "§3.2 resolveType(\"Big1\") — same file, warm file / cold class = ${secondClassMs}ms  members=$members1",
            )

            // --- candidate A: does this door touch forFile at all? Time it separately.
            val libVf = libRoot.findChild("big.lua")!!
            val libPsi = PsiManager.getInstance(project).findFile(libVf)!!
            val forFileMs = measureTimeMillis { LuaTypesSnapshot.forFile(libPsi) }
            println("§3.2 candidate A — LuaTypesSnapshot.forFile(big.lua), measured AFTER the above = ${forFileMs}ms")
            println("§3.2   (if this is large while resolveType was small, the @class door never built the graph)")

            // --- candidate B: catsClassTags' narrowed-then-walk. Medians of five, because the
            //     single-shot 22 ms this once reported is the numerator design §4.11 disavows. A
            //     proper re-measurement against the RIGHT DOOR for each of the three walk sites is
            //     still owed and is DR-16; this only retires the medians half of the complaint.
            val catsRuns =
                (1..5).map {
                    measureTimeMillis {
                        FileBasedIndex
                            .getInstance()
                            .getContainingFiles(LuaCatsTypeNameIndex.KEY, "Big2", scope)
                            .mapNotNull { PsiManager.getInstance(project).findFile(it) }
                            .flatMap { PsiTreeUtil.findChildrenOfType(it, LuaCatsClassTag::class.java) }
                            .count { it.argType?.text?.trim() == "Big2" }
                    }
                }
            Medians.report("§3.2 candidate B — catsClassTags-shaped walk for one class", catsRuns)
            val catsMs = Medians.of(catsRuns)

            // --- candidate C: addMethodsOf's getAllKeys + getElements per matching key
            var keyCount = 0
            var elementCount = 0
            val scanRuns =
                (1..5).map {
                    measureTimeMillis {
                        val allKeys = StubIndex.getInstance().getAllKeys(LuaGlobalDeclarationIndex.KEY, project)
                        keyCount = allKeys.size
                        elementCount = 0
                        for (key in allKeys) {
                            if (!key.startsWith("Big3.") && !key.startsWith("Big3:")) continue
                            elementCount +=
                                StubIndex
                                    .getElements<String, LuaFuncDecl>(
                                        LuaGlobalDeclarationIndex.KEY,
                                        key,
                                        project,
                                        scope,
                                        LuaFuncDecl::class.java,
                                    ).size
                        }
                    }
                }
            Medians.report("§3.2 candidate C — getAllKeys($keyCount) + getElements per match ($elementCount)", scanRuns)
            val scanMs = Medians.of(scanRuns)
            // --- candidate D: the unaccounted remainder. Is it the AST parse of the declaring file?
            //     `cold.lua` has never been touched, so this pays the parse that resolveType paid.
            val coldVf = libRoot.findChild("cold.lua")!!
            var tagCount = 0
            val coldParseMs =
                measureTimeMillis {
                    val coldPsi = PsiManager.getInstance(project).findFile(coldVf)!!
                    tagCount = PsiTreeUtil.findChildrenOfType(coldPsi, LuaCatsClassTag::class.java).size
                }
            println(
                "§3.2 candidate D — first AST walk of an UNTOUCHED 253 KiB file = ${coldParseMs}ms ($tagCount tags)",
            )
            val warmWalkMs =
                measureTimeMillis {
                    PsiTreeUtil
                        .findChildrenOfType(
                            PsiManager.getInstance(project).findFile(coldVf)!!,
                            LuaCatsClassTag::class.java,
                        ).size
                }
            println(
                "§3.2 candidate D — same walk, AST now warm = ${warmWalkMs}ms  => parse cost ~${coldParseMs - warmWalkMs}ms",
            )
            println(
                "§3.2 VERDICT: resolveType cold=${resolveTypeMs}ms warm-file=${secondClassMs}ms | A(forFile)=$forFileMs B(walk)=$catsMs C(scan)=$scanMs D(parse)=$coldParseMs",
            )
        }
    }
}
