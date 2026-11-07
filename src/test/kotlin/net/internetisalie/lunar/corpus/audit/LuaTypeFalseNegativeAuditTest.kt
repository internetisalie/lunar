package net.internetisalie.lunar.corpus.audit

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.corpus.CorpusDefinitionLibraries
import net.internetisalie.lunar.corpus.CorpusEntry
import net.internetisalie.lunar.corpus.CorpusGuards
import net.internetisalie.lunar.corpus.CorpusManifest
import net.internetisalie.lunar.corpus.CorpusSourcePath
import net.internetisalie.lunar.corpus.CorpusTarget
import net.internetisalie.lunar.corpus.SavedTarget
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * MAINT-54: injects known type errors into copies of each corpus member's declared-demand sites and
 * writes what the checker reported to `build/type-audit/<member>.json` (design §3.5).
 *
 * Opt-in only, with `-PwithTypeAudit` (never `-PwithCorpus`, BUG-418):
 * `test -PwithTypeAudit --rerun --no-build-cache --tests '*FalseNegativeAudit*'`. No definition
 * library is enabled, so library calls count as unresolved.
 */
@RunWith(JUnit4::class)
class LuaTypeFalseNegativeAuditTest : BasePlatformTestCase() {
    private var savedSourcePath: String = ""
    private var savedTarget: SavedTarget? = null

    override fun getTestDataPath(): String = System.getProperty("user.dir")

    override fun setUp() {
        super.setUp()
        CorpusDefinitionLibraries.requireSettingsSubscriber(project)
        VfsRootAccess.allowRootAccess(
            testRootDisposable,
            File(System.getProperty("user.dir"), CorpusManifest.CORPUS_DIR).canonicalPath,
        )
        LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf()
        savedSourcePath = CorpusSourcePath.snapshot(project)
    }

    override fun tearDown() {
        try {
            try {
                if (CorpusSourcePath.restore(project, savedSourcePath)) CorpusDefinitionLibraries.refreshRoots(project)
            } finally {
                savedTarget?.let { CorpusTarget.restore(project, it) }
            }
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testLuacheckMember() = audit("luacheck")

    @Test
    fun testLuarocksMember() = audit("luarocks")

    @Test
    fun testZerobraneMember() = audit("zerobrane")

    @Test
    fun testPenlightMember() = audit("penlight")

    @Test
    fun testLazynvimMember() = audit("lazynvim")

    private fun audit(name: String) {
        val startedAt = System.nanoTime()
        val repoRoot = File(testDataPath)
        val entry = CorpusManifest.entry(repoRoot, name)
        CorpusGuards.assertCorpusFetched(repoRoot, entry)
        savedTarget = CorpusTarget.pin(project, CorpusTarget.memberTarget(entry))
        val checkoutDir = CorpusManifest.checkoutDir(repoRoot, name)
        applyModuleRoot(entry, checkoutDir)
        val files = copyRoots(entry)
        val scans = files.map { AuditSiteFinder.scan(it, checkoutDir) }
        val sites = scans.flatMap { it.sites }
        val selected = AuditSelection.select(sites)
        val injector = AuditInjector(myFixture)
        val records = selected.map { injector.measure(it) }
        val coverage = scans.fold(AuditCoverage()) { total, scan -> total + scan.coverage }
        val payload =
            AuditPayload(
                AuditOutput.SCHEMA,
                name,
                entry.commit,
                AuditSelection.CAP,
                coverage,
                AuditSelection.populations(sites),
                records,
            )
        AuditOutput.write(repoRoot, name, payload)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        println(
            "[type-audit:$name] files=${files.size} population=${sites.size} sites=${selected.size} elapsedMs=$elapsedMs",
        )
    }

    /** As `CorpusSweep.applyModuleRoot`: module names resolve against the on-disk checkout. */
    private fun applyModuleRoot(
        entry: CorpusEntry,
        checkoutDir: File,
    ) {
        val moduleRoot = entry.moduleRoot ?: return
        val base = File(checkoutDir, moduleRoot).canonicalPath
        LuaProjectSettings.getInstance(project).state.sourcePath = "$base/?.lua;$base/?/init.lua"
    }

    /** Copies each manifest root into the fixture's temp tree, as `CorpusSweep.sweepRoot` does. */
    private fun copyRoots(entry: CorpusEntry): List<LuaFile> {
        val psiManager = PsiManager.getInstance(project)
        return entry.roots.flatMap { root ->
            val copied = myFixture.copyDirectoryToProject("${CorpusManifest.CORPUS_DIR}/${entry.name}/$root", root)
            luaFiles(copied).mapNotNull { psiManager.findFile(it) as? LuaFile }
        }
    }

    private fun luaFiles(root: VirtualFile): List<VirtualFile> {
        val found = mutableListOf<VirtualFile>()
        VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
            if (!file.isDirectory && file.extension == "lua") found += file
            true
        }
        return found.sortedBy { it.path }
    }
}
