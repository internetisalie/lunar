package net.internetisalie.lunar.corpus.audit

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import net.internetisalie.lunar.corpus.CorpusDefinitionLibraries
import net.internetisalie.lunar.corpus.CorpusEntry
import net.internetisalie.lunar.corpus.CorpusGuards
import net.internetisalie.lunar.corpus.CorpusManifest
import net.internetisalie.lunar.corpus.CorpusSourcePath
import net.internetisalie.lunar.corpus.CorpusTarget
import net.internetisalie.lunar.corpus.SavedTarget
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.io.File
import java.nio.file.Path

/** One member's audit payload and the site count it was selected from. */
private data class MemberAudit(
    val payload: AuditPayload,
    val population: Int,
    val timedOut: Boolean,
)

/**
 * One corpus member's type audit (MAINT-54 design §3.5), shared by [LuaTypeFalseNegativeAuditTest] and
 * [LuaRepresentativeTypeAuditTest] (MAINT-56 design §5.2): pin the member's target, enable its definition
 * libraries when [mode] asks, copy its roots, inject and measure, and write the payloads.
 */
internal class AuditMemberRun(
    private val fixture: CodeInsightTestFixture,
    private val mode: AuditRunMode,
) {
    private var savedTarget: SavedTarget? = null
    private var provisioned: List<Path> = emptyList()
    private var deadline = MemberDeadline(null)

    private val project get() = fixture.project

    fun run(name: String) {
        val startedAt = System.nanoTime()
        deadline = MemberDeadline(mode.memberLimit?.inWholeNanoseconds)
        val repoRoot = File(fixture.testDataPath)
        val entry = CorpusManifest.entry(repoRoot, name, mode.manifestPath)
        CorpusGuards.assertCorpusFetched(repoRoot, entry)
        savedTarget = CorpusTarget.pin(project, CorpusTarget.memberTarget(entry))
        if (mode.enablesLibraries) provisioned = CorpusDefinitionLibraries.provision(project, entry, repoRoot)
        val checkoutDir = CorpusManifest.checkoutDir(repoRoot, name)
        applyModuleRoot(entry, checkoutDir)
        val files = copyRoots(entry)
        val audit = measure(entry, files, checkoutDir)
        AuditOutput.writeTo(File(repoRoot, mode.auditDir), name, audit.payload)
        mode.extrasDir?.let {
            val extras = TypeAuditExtras.payload(name, entry.commit, files).copy(timedOut = audit.timedOut)
            TypeAuditExtras.writeTo(File(repoRoot, it), name, extras)
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        println(
            "[${mode.logTag}:$name] files=${files.size} population=${audit.population} " +
                "sites=${audit.payload.records.size} elapsedMs=$elapsedMs" +
                if (audit.timedOut) " TIMED_OUT limit=${mode.memberLimit}" else "",
        )
    }

    /** `LuaCorpusSweepTest.tearDown`'s order: source path, then libraries, then the target in `finally`. */
    fun restore(savedSourcePath: String) {
        try {
            val restored = CorpusSourcePath.restore(project, savedSourcePath)
            CorpusDefinitionLibraries.release(project, provisioned)
            if (restored && provisioned.isEmpty()) CorpusDefinitionLibraries.refreshRoots(project)
        } finally {
            savedTarget?.let { CorpusTarget.restore(project, it) }
        }
    }

    /** MAINT-54 design §3.2–§3.5: scan, select, measure, and the payload of schema 1. */
    private fun measure(
        entry: CorpusEntry,
        files: List<LuaFile>,
        checkoutDir: File,
    ): MemberAudit {
        val scans = files.map { AuditSiteFinder.scan(it, checkoutDir) }
        val sites = scans.flatMap { it.sites }
        val selected = AuditSelection.select(sites)
        val injector = AuditInjector(fixture)
        val bounded = measureWithin(selected, deadline) { injector.measure(it) }
        val coverage = scans.fold(AuditCoverage()) { total, scan -> total + scan.coverage }
        val payload =
            AuditPayload(
                AuditOutput.SCHEMA,
                entry.name,
                entry.commit,
                AuditSelection.CAP,
                coverage,
                AuditSelection.populations(sites),
                bounded.results,
            )
        return MemberAudit(payload, sites.size, bounded.timedOut)
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
            val copied = fixture.copyDirectoryToProject("${CorpusManifest.CORPUS_DIR}/${entry.name}/$root", root)
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
