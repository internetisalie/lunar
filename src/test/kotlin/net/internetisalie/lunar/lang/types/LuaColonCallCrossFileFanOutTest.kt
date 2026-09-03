package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaMethodExpr
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.RootAccessor
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-13-06, `requirements.md` case 20 — the shipped-code form of `risks-and-gaps.md` DR-04: the
 * cross-file fan-out of design §3.6's **per-file** guard is bounded, and the bound is one extra root
 * resolution per resolved call site, at every project size.
 *
 * Why a separate instrument from `LuaTypeGraphRootResolutionBudgetTest`: `LuaTypeGraph`'s
 * root-resolution counter is per graph, hence per snapshot, so a per-file budget **cannot see a
 * fan-out at all**. This sums it over every file's snapshot instead. No production change is needed
 * for that — `LuaTypesSnapshot.forFile` is `CachedValuesManager`-backed, so re-asking after the
 * resolutions returns the same instances the fan-out charged.
 *
 * **Why a separate class, and it is a harness constraint rather than a choice** (a departure from
 * `implementation-plan.md` Phase 3, which names `LuaTypeGraphRootResolutionBudgetTest`; `design.md`
 * §2.4 adds only the budget method to that class, and this placement keeps that true). Its base,
 * `BaseDocumentTest`, builds `LightTempDirTestFixtureImpl(false)`, which creates added files under
 * `temp:///root` — **outside** any source root, so `LuaTypeManagerImpl`'s
 * `GlobalSearchScope.allScope` lookup never finds a class declared in a sibling file.
 * [BasePlatformTestCase] passes `true` and puts them in the platform source root. Executed, not
 * inferred: under `BaseDocumentTest` this exact ring resolved **0 of 20** sites at `K` = 2 with the
 * receiver typing as `{  }`, and a same-file control in the same harness resolved 1 of 1; under
 * [BasePlatformTestCase] the ring resolves every site. The 0-of-20 run would have been a green test
 * asserting nothing, which is why the anti-vacuity assertion below is not optional.
 *
 * **Measured at commit time** (`7cf880bf` plus this phase, gce-builder, `test --rerun
 * --no-build-cache`), summed over every file's snapshot, baseline taken after all `K` snapshots are
 * built and before any call site is resolved:
 *
 * | `K` | sites | `WRITE` base → after | `READ` base → after |
 * | --: | --: | :-- | :-- |
 * | 2 | 20 | 198 → 218 | 94 → 114 |
 * | 4 | 40 | 396 → 436 | 188 → 228 |
 * | 8 | 80 | 792 → 872 | 376 → 456 |
 * | 16 | 160 | 1 584 → 1 744 | 752 → 912 |
 *
 * The assertion is the **per-site ratio** — one `WRITE` and one `READ` per resolved site — and
 * deliberately not the absolute totals, so an unrelated re-baselining of the engine does not redden
 * it. What that pins is that the per-site cost does not grow with the size of the surrounding
 * project, which is the superlinear blow-up the guard's per-file scope left open (Gap 2.3). It does
 * **not** pin a receiver whose own class resolution crosses several files: this ring puts every
 * receiver's class in exactly one other file, so the hop count per site is one by construction. That
 * limit is DR-04's, stated there rather than papered over here.
 */
@RunWith(JUnit4::class)
class LuaColonCallCrossFileFanOutTest : BasePlatformTestCase() {
    @Test
    fun crossFileFanOutStaysLinearInCallSites() {
        RING_SIZES.forEach { assertRingFanOutIsOnePerResolvedSite(it) }
    }

    /**
     * Builds one ring of [size] files, resolves every call site in it, and asserts the summed cost
     * rose by exactly one `WRITE` and one `READ` per resolved site.
     *
     * Each ring's classes are named for their own `K`, because `LuaTypeManagerImpl` searches
     * `allScope`: a class name shared with an earlier ring in the same project would bind a receiver
     * to the wrong file and manufacture a false result (TYPE-13 requirements case 17).
     */
    private fun assertRingFanOutIsOnePerResolvedSite(size: Int) {
        val files =
            ringFixture(size).mapIndexed { index, text ->
                myFixture.addFileToProject("ring$size/c$index.lua", text)
            }
        val baseWrite = summedRootResolutions(files, RootAccessor.WRITE)
        val baseRead = summedRootResolutions(files, RootAccessor.READ)

        val sites = files.flatMap { colonCallSites(it) }
        assertEquals("ring K=$size call-site count", size * RING_SITES_PER_FILE, sites.size)
        assertEquals(
            "every cross-file call site must resolve, or the fan-out below is measured over nothing",
            sites.size,
            sites.count { it.reference?.resolve() != null },
        )

        val write = summedRootResolutions(files, RootAccessor.WRITE)
        val read = summedRootResolutions(files, RootAccessor.READ)
        val measurement = "K=$size sites=${sites.size} WRITE $baseWrite->$write READ $baseRead->$read"
        assertEquals(
            "summed WRITE fan-out must be one per resolved site — $measurement",
            sites.size.toLong(),
            write - baseWrite,
        )
        assertEquals(
            "summed READ fan-out must be one per resolved site — $measurement",
            sites.size.toLong(),
            read - baseRead,
        )
    }

    /**
     * The summed root-resolution count over every file's snapshot. The first call is also what
     * *builds* those snapshots, so it establishes the baseline the fan-out is measured against.
     */
    private fun summedRootResolutions(
        files: List<PsiFile>,
        accessor: RootAccessor,
    ): Long = files.sumOf { (LuaTypesSnapshot.forFile(it) as LuaTypesSnapshot).rootResolutionCount(accessor) }

    private fun colonCallSites(file: PsiFile): List<LuaNameRef> =
        PsiTreeUtil
            .findChildrenOfType(file, LuaNameRef::class.java)
            .filter { it.parent is LuaMethodExpr }

    /**
     * DR-04's ring: file *i* declares `---@class Ci` with one method, and carries
     * [RING_SITES_PER_FILE] call sites whose receiver is `---@type C(i+1 mod K)` — so **every** site
     * crosses exactly one file, and no site can be served from its own snapshot.
     */
    private fun ringFixture(size: Int): List<String> =
        (0 until size).map { index ->
            val self = ringClassName(size, index)
            val next = ringClassName(size, (index + 1) % size)
            val callSites =
                (0 until RING_SITES_PER_FILE).joinToString("\n") { site ->
                    "---@type $next\nlocal b$site\nb$site:${ringMethodName(next)}()"
                }
            """
            |---@class $self
            |local $self = {}
            |function $self:${ringMethodName(self)}() end
            |
            |$callSites
            |return $self
            """.trimMargin()
        }

    private fun ringClassName(
        size: Int,
        index: Int,
    ): String = "C${size}x$index"

    private fun ringMethodName(className: String): String = "m$className"

    private companion object {
        /** Growing `K` grows the project each resolution happens in, which is the variable DR-04 varies. */
        val RING_SIZES = listOf(2, 4, 8, 16)
        const val RING_SITES_PER_FILE = 10
    }
}
