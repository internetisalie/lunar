package net.internetisalie.lunar.corpus

import com.google.gson.Gson
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.EXCEPTION_PREFIX
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.NOT_RUN
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.OK
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.SKIPPED
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.TIMEOUT
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.UNMEASURED
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryFetcher
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.ElementError
import net.internetisalie.lunar.lang.psi.types.LuaSnapshotBuildMeter
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** One read of a file's snapshot errors (MAINT-60 design §2.1): its time, status, findings digest and builds. */
private data class Read(
    val ms: Long,
    val status: String,
    val errorDigest: Int,
    val builds: List<LuaSnapshotBuildMeter.BuildRecord>,
)

/** A selected file of the member being swept, and its PSI in the fixture's temp tree. */
private data class SweptFile(
    val path: String,
    val file: LuaFile,
)

/** A cold read, and whether the file's snapshot was already built before it (design §3.1 step 4). */
private data class ColdRead(
    val prebuilt: Boolean,
    val read: Read,
)

/** Both passes of one selected file, before they become a [SweepRow]. */
private data class FileResult(
    val path: String,
    val lines: Int,
    val cold: ColdRead,
    val edit: Read,
)

/** The `rootSelfMs`/`topDep`/`topDepSelfMs`/`builds` fields of one pass (design §3.1 step 6). */
private data class PassSummary(
    val rootSelfMs: Long,
    val topDep: String,
    val topDepSelfMs: Long,
    val builds: Int,
)

/**
 * MAINT-60-05: the per-file snapshot cost sweep over `tooling/corpus/type-corpus.json` (design §2.1, §3.1,
 * §3.4). Each member is set up as MAINT-56 DR-16's probe did; each selected file is read cold, then after
 * `"\n"` is appended, and one row per file goes to `build/snapshot-cost/sweep.jsonl`. [testZzVerdict]
 * writes `ranked.tsv` and `verdict.txt` and asserts the verdict (design §3.2).
 *
 * Opt-in only (`build.gradle.kts`): `test -PwithSnapshotSweep --rerun --no-build-cache --tests
 * '*SnapshotCostSweep*'`, after `tooling/corpus/fetch-corpus.py --manifest tooling/corpus/type-corpus.json`.
 */
@RunWith(JUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LuaSnapshotCostSweepTest : BasePlatformTestCase() {
    private var savedSourcePath: String = ""
    private var savedTarget: SavedTarget? = null
    private var provisioned: List<Path> = emptyList()

    private val repoRoot: File get() = File(testDataPath)
    private val outputDir: File get() = File(repoRoot, OUTPUT_DIR).also { it.mkdirs() }

    override fun getTestDataPath(): String = System.getProperty("user.dir")

    override fun setUp() {
        super.setUp()
        println("[snapshot-cost] run started at $RUN_START_MS")
        CorpusDefinitionLibraries.requireSettingsSubscriber(project)
        VfsRootAccess.allowRootAccess(testRootDisposable, File(repoRoot, CorpusManifest.CORPUS_DIR).canonicalPath)
        VfsRootAccess.allowRootAccess(testRootDisposable, LuaDefinitionLibraryFetcher.defaultCacheRoot().toString())
        LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf()
        savedSourcePath = CorpusSourcePath.snapshot(project)
    }

    override fun tearDown() {
        try {
            LuaSnapshotBuildMeter.sink = null
            restoreMember()
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testM00aLuals() = sweepMember("luals")

    @Test
    fun testM00bFallo() = sweepMember("fallo")

    @Test
    fun testM01Telescope() = sweepMember("telescope")

    @Test
    fun testM02Gitsigns() = sweepMember("gitsigns")

    @Test
    fun testM03Blinkcmp() = sweepMember("blinkcmp")

    @Test
    fun testM04Differ() = sweepMember("differ")

    @Test
    fun testM05Weakauras() = sweepMember("weakauras")

    @Test
    fun testM06Ouf() = sweepMember("ouf")

    @Test
    fun testM07Omniinv() = sweepMember("omniinv")

    @Test
    fun testM08Kristal() = sweepMember("kristal")

    @Test
    fun testM09Prism() = sweepMember("prism")

    @Test
    fun testM10Opensmce() = sweepMember("opensmce")

    @Test
    fun testM11Factoryplanner() = sweepMember("factoryplanner")

    @Test
    fun testM12Rampant() = sweepMember("rampant")

    @Test
    fun testM13Facc() = sweepMember("facc")

    @Test
    fun testM14Awesome() = sweepMember("awesome")

    @Test
    fun testM15Lain() = sweepMember("lain")

    @Test
    fun testM16Somewm() = sweepMember("somewm")

    @Test
    fun testM17Bullmq() = sweepMember("bullmq")

    @Test
    fun testM18Inngest() = sweepMember("inngest")

    @Test
    fun testM19Beequeue() = sweepMember("beequeue")

    @Test
    fun testM20Kong() = sweepMember("kong")

    @Test
    fun testM21Apisix() = sweepMember("apisix")

    @Test
    fun testM22Restyhttp() = sweepMember("restyhttp")

    @Test
    fun testM23Tntmetrics() = sweepMember("tntmetrics")

    @Test
    fun testM24Tntcrud() = sweepMember("tntcrud")

    @Test
    fun testM25Tntqueue() = sweepMember("tntqueue")

    @Test
    fun testM26Pokisample() = sweepMember("pokisample")

    @Test
    fun testM27Druid() = sweepMember("druid")

    @Test
    fun testM28Monarch() = sweepMember("monarch")

    @Test
    fun testM29Ldoc() = sweepMember("ldoc")

    @Test
    fun testM30Lnko() = sweepMember("lnko")

    @Test
    fun testM31Lde() = sweepMember("lde")

    @Test
    fun testM32Snabb() = sweepMember("snabb")

    @Test
    fun testM33Ljtk() = sweepMember("ljtk")

    @Test
    fun testZzVerdict() {
        val rows = readRows()
        val checks =
            SnapshotCostVerdict.coverage(selectedByMember(), rows) +
                SnapshotCostVerdict.sweepErrors(rows) +
                SnapshotCostVerdict.runTimeFailures(System.currentTimeMillis() - RUN_START_MS)
        File(outputDir, "ranked.tsv").writeText(SnapshotCostVerdict.rankedLines(rows).joinToString("\n"))
        val release = SnapshotCostVerdict.releaseLines(rows, checks.size)
        File(outputDir, "verdict.txt").writeText(release.joinToString("\n"))
        (checks + release).forEach { println("[snapshot-cost] $it") }
        assertTrue(
            "${checks.size} snapshot cost check(s) failed; first ${FAILURES_SHOWN}:\n" +
                checks.take(FAILURES_SHOWN).joinToString("\n"),
            checks.isEmpty(),
        )
    }

    private fun sweepMember(name: String) {
        val entry = CorpusManifest.entry(repoRoot, name, CorpusManifest.TYPE_MANIFEST_PATH)
        val selected = SnapshotCostVerdict.selectedFiles(repoRoot, entry)
        val results =
            if (budgetLeft()) {
                measureMember(entry, selected)
            } else {
                selected.map { path ->
                    FileResult(path, lineCount(entry, path), ColdRead(false, notRead(SKIPPED)), notRead(SKIPPED))
                }
            }
        val rows = results.map { it.toRow(name) }
        File(outputDir, "sweep.jsonl").appendText(rows.joinToString("") { it.toJson() + "\n" })
        val summedMs = "coldMs=${rows.sumOf { it.coldMs }} editMs=${rows.sumOf { it.editMs }}"
        println("[snapshot-cost:$name] files=${rows.size} $summedMs")
    }

    private fun measureMember(
        entry: CorpusEntry,
        selected: List<String>,
    ): List<FileResult> {
        CorpusGuards.assertCorpusFetched(repoRoot, entry)
        savedTarget = CorpusTarget.pin(project, CorpusTarget.memberTarget(entry))
        provisioned = CorpusDefinitionLibraries.provision(project, entry, repoRoot)
        applyModuleRoot(entry)
        val files = copyRoots(entry, selected)
        val colds = coldPass(files)
        val edits = editPass(files, colds)
        return files.indices.map { FileResult(files[it].path, lineCount(entry, files[it].path), colds[it], edits[it]) }
    }

    private fun coldPass(files: List<SweptFile>): List<ColdRead> {
        var stopped = false
        return files.map { swept ->
            when {
                !budgetLeft() -> ColdRead(false, notRead(SKIPPED))
                stopped -> ColdRead(false, notRead(UNMEASURED))
                else -> {
                    val prebuilt = isPrebuilt(swept.file)
                    val read = readTimed(swept.file, SnapshotCostVerdict.COLD_DEADLINE_MS)
                    if (read.status == TIMEOUT) stopped = true
                    ColdRead(prebuilt, read)
                }
            }
        }
    }

    /** `CachedValuesManager.getCachedValue(PsiElement, …)` stores a `ParameterizedCachedValue` under the key. */
    private fun isPrebuilt(file: LuaFile): Boolean {
        val snapshotKey: Key<*> = LuaTypesVisitor.KEY
        return (file.getUserData(snapshotKey) as? ParameterizedCachedValue<*, *>)?.hasUpToDateValue() == true
    }

    private fun editPass(
        files: List<SweptFile>,
        colds: List<ColdRead>,
    ): List<Read> =
        files.zip(colds).map { (swept, cold) ->
            when (cold.read.status) {
                OK -> if (budgetLeft()) editedRead(swept.file) else notRead(SKIPPED)
                SKIPPED, UNMEASURED -> notRead(cold.read.status)
                else -> notRead(NOT_RUN)
            }
        }

    private fun editedRead(file: LuaFile): Read {
        appendNewline(file)
        return readTimed(file, SnapshotCostVerdict.EDIT_DEADLINE_MS)
    }

    private fun readTimed(
        file: LuaFile,
        deadlineMs: Long,
    ): Read {
        val records = mutableListOf<LuaSnapshotBuildMeter.BuildRecord>()
        val reader = Thread.currentThread()
        LuaSnapshotBuildMeter.sink = { record -> if (Thread.currentThread() === reader) records += record }
        val startNs = System.nanoTime()
        val outcome =
            try {
                readErrors(file, deadlineMs)
            } finally {
                LuaSnapshotBuildMeter.sink = null
            }
        val ms = (System.nanoTime() - startNs) / 1_000_000L
        val digest = outcome.second?.let { errorDigest(it) } ?: 0
        return Read(ms, outcome.first, digest, records.toList())
    }

    private fun readErrors(
        file: LuaFile,
        deadlineMs: Long,
    ): Pair<String, List<ElementError>?> {
        val indicator = EmptyProgressIndicator()
        val cancel =
            AppExecutorUtil
                .getAppScheduledExecutorService()
                .schedule({ indicator.cancel() }, deadlineMs, TimeUnit.MILLISECONDS)
        return try {
            val errors =
                ProgressManager.getInstance().runProcess(
                    Computable { runReadAction { LuaTypesSnapshot.forFile(file).getErrors() } },
                    indicator,
                )
            OK to errors
        } catch (canceled: ProcessCanceledException) {
            TIMEOUT to null
        } catch (failure: Exception) {
            "$EXCEPTION_PREFIX${failure.javaClass.simpleName}" to null
        } finally {
            cancel.cancel(false)
        }
    }

    private fun errorDigest(errors: List<ElementError>): Int =
        runReadAction {
            errors.map { "${it.element.textOffset}:${it.severity}:${it.message}" }.sorted().hashCode()
        }

    private fun appendNewline(file: LuaFile) {
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(file) ?: error("No document for ${file.virtualFile.path}")
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.textLength, "\n") }
        documentManager.commitDocument(document)
    }

    private fun budgetLeft(): Boolean = System.currentTimeMillis() - RUN_START_MS < SnapshotCostVerdict.RUN_BUDGET_MS

    private fun notRead(status: String): Read = Read(0, status, 0, emptyList())

    /** As `AuditMemberRun.applyModuleRoot`: module names resolve against the on-disk checkout. */
    private fun applyModuleRoot(entry: CorpusEntry) {
        val moduleRoot = entry.moduleRoot ?: return
        val base = File(CorpusManifest.checkoutDir(repoRoot, entry.name), moduleRoot).canonicalPath
        LuaProjectSettings.getInstance(project).state.sourcePath = "$base/?.lua;$base/?/init.lua"
    }

    private fun copyRoots(
        entry: CorpusEntry,
        selected: List<String>,
    ): List<SweptFile> {
        entry.roots.forEach { root ->
            myFixture.copyDirectoryToProject("${CorpusManifest.CORPUS_DIR}/${entry.name}/$root", root)
        }
        val psiManager = PsiManager.getInstance(project)
        return selected.map { path ->
            val copied = myFixture.findFileInTempDir(path) ?: error("${entry.name}/$path was not copied")
            SweptFile(path, psiManager.findFile(copied) as? LuaFile ?: error("${entry.name}/$path is not a Lua file"))
        }
    }

    private fun lineCount(
        entry: CorpusEntry,
        path: String,
    ): Int = File(CorpusManifest.checkoutDir(repoRoot, entry.name), path).useLines { it.count() }

    /** `LuaCorpusSweepTest.tearDown`'s order: source path, then libraries, then the target in `finally`. */
    private fun restoreMember() {
        try {
            val restored = CorpusSourcePath.restore(project, savedSourcePath)
            CorpusDefinitionLibraries.release(project, provisioned)
            if (restored && provisioned.isEmpty()) CorpusDefinitionLibraries.refreshRoots(project)
        } finally {
            savedTarget?.let { CorpusTarget.restore(project, it) }
        }
    }

    private fun readRows(): List<SweepRow> {
        val sweep = File(outputDir, "sweep.jsonl")
        if (!sweep.isFile) return emptyList()
        val gson = Gson()
        return sweep.readLines().filter { it.isNotBlank() }.map { gson.fromJson(it, SweepRow::class.java) }
    }

    private fun selectedByMember(): Map<String, List<String>> =
        CorpusManifest
            .load(repoRoot, CorpusManifest.TYPE_MANIFEST_PATH)
            .associate { it.name to SnapshotCostVerdict.selectedFiles(repoRoot, it) }

    private companion object {
        val RUN_START_MS: Long = System.currentTimeMillis()

        const val OUTPUT_DIR = "build/snapshot-cost"
        const val FAILURES_SHOWN = 40
    }
}

private fun Read.summary(): PassSummary {
    val root = builds.firstOrNull { it.depth == 0 }
    val top = builds.filter { it.depth >= 1 }.maxByOrNull { it.selfMs }
    return PassSummary(root?.selfMs ?: -1, top?.path ?: "", top?.selfMs ?: -1, builds.size)
}

private fun FileResult.toRow(member: String): SweepRow {
    val coldSummary = cold.read.summary()
    val editSummary = edit.summary()
    return SweepRow(
        member = member,
        file = path,
        lines = lines,
        prebuilt = cold.prebuilt,
        coldMs = cold.read.ms,
        coldStatus = cold.read.status,
        rootSelfMs = coldSummary.rootSelfMs,
        topDep = coldSummary.topDep,
        topDepSelfMs = coldSummary.topDepSelfMs,
        builds = coldSummary.builds,
        editMs = edit.ms,
        editStatus = edit.status,
        editRootSelfMs = editSummary.rootSelfMs,
        editTopDep = editSummary.topDep,
        editTopDepSelfMs = editSummary.topDepSelfMs,
        editBuilds = editSummary.builds,
        digest = cold.read.errorDigest,
    )
}

private fun jsonString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private fun SweepRow.toJson(): String =
    listOf(
        "member" to jsonString(member),
        "file" to jsonString(file),
        "lines" to "$lines",
        "prebuilt" to "$prebuilt",
        "coldMs" to "$coldMs",
        "coldStatus" to jsonString(coldStatus),
        "rootSelfMs" to "$rootSelfMs",
        "topDep" to jsonString(topDep),
        "topDepSelfMs" to "$topDepSelfMs",
        "builds" to "$builds",
        "editMs" to "$editMs",
        "editStatus" to jsonString(editStatus),
        "editRootSelfMs" to "$editRootSelfMs",
        "editTopDep" to jsonString(editTopDep),
        "editTopDepSelfMs" to "$editTopDepSelfMs",
        "editBuilds" to "$editBuilds",
        "digest" to "$digest",
    ).joinToString(",", "{", "}") { (key, value) -> "\"$key\":$value" }
