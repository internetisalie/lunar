package net.internetisalie.lunar.corpus

import com.google.gson.Gson
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryFetcher
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import java.io.File
import java.nio.file.Path

/**
 * MAINT-65-02: reads each candidate file first, in a fresh fixture, and records its findings digest in
 * `build/alone-digest/digests.tsv` (design §2.2, §3.2, §3.3). A candidate is a file whose cold digest differs
 * between two sweeps. Opt-in (`build.gradle.kts`): `test -PwithAloneDigest -PaloneDigestBase=<sweep.jsonl>
 * -PaloneDigestChange=<sweep.jsonl> --rerun --no-build-cache --tests '*LuaAloneDigestTest*'`.
 */
@RunWith(JUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LuaAloneDigestTest : BasePlatformTestCase() {
    private var savedSourcePath: String = ""
    private var savedTarget: SavedTarget? = null
    private var provisioned: List<Path> = emptyList()

    private val repoRoot: File get() = File(testDataPath)
    private val outputDir: File get() = File(repoRoot, OUTPUT_DIR).also { it.mkdirs() }

    override fun getTestDataPath(): String = System.getProperty("user.dir")

    override fun setUp() {
        super.setUp()
        CorpusDefinitionLibraries.requireSettingsSubscriber(project)
        VfsRootAccess.allowRootAccess(testRootDisposable, File(repoRoot, CorpusManifest.CORPUS_DIR).canonicalPath)
        VfsRootAccess.allowRootAccess(testRootDisposable, LuaDefinitionLibraryFetcher.defaultCacheRoot().toString())
        LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf()
        savedSourcePath = CorpusSourcePath.snapshot(project)
    }

    override fun tearDown() {
        try {
            restoreMember()
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testSlot00() = readSlot(0)

    @Test
    fun testSlot01() = readSlot(1)

    @Test
    fun testSlot02() = readSlot(2)

    @Test
    fun testSlot03() = readSlot(3)

    @Test
    fun testSlot04() = readSlot(4)

    @Test
    fun testSlot05() = readSlot(5)

    @Test
    fun testSlot06() = readSlot(6)

    @Test
    fun testSlot07() = readSlot(7)

    @Test
    fun testSlot08() = readSlot(8)

    @Test
    fun testSlot09() = readSlot(9)

    @Test
    fun testSlot10() = readSlot(10)

    @Test
    fun testSlot11() = readSlot(11)

    @Test
    fun testSlot12() = readSlot(12)

    @Test
    fun testSlot13() = readSlot(13)

    @Test
    fun testSlot14() = readSlot(14)

    @Test
    fun testSlot15() = readSlot(15)

    @Test
    fun testSlot16() = readSlot(16)

    @Test
    fun testSlot17() = readSlot(17)

    @Test
    fun testSlot18() = readSlot(18)

    @Test
    fun testSlot19() = readSlot(19)

    @Test
    fun testSlot20() = readSlot(20)

    @Test
    fun testSlot21() = readSlot(21)

    @Test
    fun testSlot22() = readSlot(22)

    @Test
    fun testSlot23() = readSlot(23)

    @Test
    fun testSlot24() = readSlot(24)

    @Test
    fun testSlot25() = readSlot(25)

    @Test
    fun testSlot26() = readSlot(26)

    @Test
    fun testSlot27() = readSlot(27)

    @Test
    fun testSlot28() = readSlot(28)

    @Test
    fun testSlot29() = readSlot(29)

    @Test
    fun testSlot30() = readSlot(30)

    @Test
    fun testSlot31() = readSlot(31)

    @Test
    fun testSlot32() = readSlot(32)

    @Test
    fun testSlot33() = readSlot(33)

    @Test
    fun testSlot34() = readSlot(34)

    @Test
    fun testSlot35() = readSlot(35)

    @Test
    fun testSlot36() = readSlot(36)

    @Test
    fun testSlot37() = readSlot(37)

    @Test
    fun testSlot38() = readSlot(38)

    @Test
    fun testSlot39() = readSlot(39)

    private fun candidates(): List<Pair<String, String>> {
        val base = rowsByKey(sweepPath(BASE_PROPERTY))
        val change = rowsByKey(sweepPath(CHANGE_PROPERTY))
        return base.keys
            .filter { key -> differs(base[key], change[key]) }
            .sortedWith(compareBy({ it.first }, { it.second }))
    }

    private fun differs(
        base: SweepRow?,
        change: SweepRow?,
    ): Boolean =
        base != null &&
            change != null &&
            base.coldStatus == SnapshotCostVerdict.OK &&
            change.coldStatus == SnapshotCostVerdict.OK &&
            base.digest != change.digest

    private fun sweepPath(property: String): String {
        val path = System.getProperty(property).orEmpty()
        check(path.isNotBlank() && System.getProperty(other(property)).orEmpty().isNotBlank()) {
            "aloneDigest needs -PaloneDigestBase and -PaloneDigestChange"
        }
        return path
    }

    private fun other(property: String): String = if (property == BASE_PROPERTY) CHANGE_PROPERTY else BASE_PROPERTY

    private fun rowsByKey(path: String): Map<Pair<String, String>, SweepRow> {
        val gson = Gson()
        return File(path)
            .readLines()
            .filter { it.isNotBlank() }
            .map { gson.fromJson(it, SweepRow::class.java) }
            .associateBy { it.member to it.file }
    }

    private fun readSlot(slot: Int) {
        val list = candidates()
        val digests = File(outputDir, DIGESTS_FILE)
        if (slot == 0) {
            digests.writeText("")
            assertTrue("${list.size} candidates exceed $SLOTS slots", list.size <= SLOTS)
        }
        if (slot >= list.size) return
        val (member, file) = list[slot]
        val luaFile = openAlone(member, file)
        val (status, errors) = CorpusSnapshotReads.readErrors(luaFile, SnapshotCostVerdict.COLD_DEADLINE_MS)
        val digest = errors?.let { CorpusSnapshotReads.errorDigest(it) } ?: 0
        digests.appendText("$member\t$file\t$status\t$digest\n")
    }

    private fun openAlone(
        member: String,
        file: String,
    ): LuaFile {
        val entry = CorpusManifest.entry(repoRoot, member, CorpusManifest.TYPE_MANIFEST_PATH)
        CorpusGuards.assertCorpusFetched(repoRoot, entry)
        savedTarget = CorpusTarget.pin(project, CorpusTarget.memberTarget(entry))
        provisioned = CorpusDefinitionLibraries.provision(project, entry, repoRoot)
        applyModuleRoot(entry)
        entry.roots.forEach { root ->
            myFixture.copyDirectoryToProject("${CorpusManifest.CORPUS_DIR}/${entry.name}/$root", root)
        }
        val copied = myFixture.findFileInTempDir(file) ?: error("$member/$file was not copied")
        return PsiManager.getInstance(project).findFile(copied) as? LuaFile ?: error("$member/$file is not a Lua file")
    }

    /** As `AuditMemberRun.applyModuleRoot`: module names resolve against the on-disk checkout. */
    private fun applyModuleRoot(entry: CorpusEntry) {
        val moduleRoot = entry.moduleRoot ?: return
        val base = File(CorpusManifest.checkoutDir(repoRoot, entry.name), moduleRoot).canonicalPath
        LuaProjectSettings.getInstance(project).state.sourcePath = "$base/?.lua;$base/?/init.lua"
    }

    private fun restoreMember() {
        try {
            val restored = CorpusSourcePath.restore(project, savedSourcePath)
            CorpusDefinitionLibraries.release(project, provisioned)
            if (restored && provisioned.isEmpty()) CorpusDefinitionLibraries.refreshRoots(project)
        } finally {
            savedTarget?.let { CorpusTarget.restore(project, it) }
        }
    }

    private companion object {
        const val OUTPUT_DIR = "build/alone-digest"
        const val DIGESTS_FILE = "digests.tsv"
        const val BASE_PROPERTY = "lunar.aloneDigest.baseSweep"
        const val CHANGE_PROPERTY = "lunar.aloneDigest.changeSweep"
        const val SLOTS = 40
    }
}
