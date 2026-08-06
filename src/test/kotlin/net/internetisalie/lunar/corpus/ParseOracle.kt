package net.internetisalie.lunar.corpus

import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.ThreadingAssertions
import net.internetisalie.lunar.lang.LuaLanguageLevel
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * PUC Lua as ground truth: does `luac -p` accept the same input Lunar's parser accepts?
 *
 * MAINT-33's sweep counts `parseErrors` but cannot tell a true one from a false one — BUG-392 (valid
 * luarocks code rejected) sat in a baseline as an expected number until a human read the file. This
 * is the missing judge.
 *
 * The binary is **built from a sha256-pinned upstream tarball** by `tooling/corpus/fetch-luac.py`
 * and resolved by table lookup to exactly one path. `PATH` is never consulted: a distro `luac` is
 * unpinned and patched, so two machines would judge by different binaries while the gate looked
 * uniform — see `luac.json` and design §2.0.
 */
internal object ParseOracle {

    sealed interface Verdict {
        /** `luac` parsed it. */
        object Accept : Verdict

        /** `luac` refused it; [message] is diagnostic only and is never baselined. */
        data class Reject(val message: String) : Verdict

        /**
         * Neither — only ever a timeout. A missing binary does **not** land here; it throws, so a
         * sweep can never quietly judge nothing (MAINT-35-03).
         */
        data class NotJudged(val reason: String) : Verdict
    }

    private const val MANIFEST_PATH = "tooling/corpus/luac.json"
    private const val TIMEOUT_SECONDS = 10L

    /**
     * The pinned `luac` for [level], or a thrown error naming the remedy.
     *
     * Throws rather than returning null so that a sweep fails **before** judging any file. An
     * oracle that silently disappears is the one failure a ratchet cannot survive: it would report
     * success while measuring nothing.
     */
    fun requireBinary(repoRoot: File, level: LuaLanguageLevel): File =
        resolved.getOrPut(repoRoot to level) { resolveBinary(repoRoot, level) }

    /**
     * Memoised: [judgeBytes] is called once per corpus file (1 696 of them in the torture member)
     * and the resolution reads and parses `luac.json` — file I/O the sweep would otherwise do once
     * per file to compute a constant. Keyed on the root as well as the level, so a test that points
     * at a different tree is not answered from another tree's cache.
     *
     * Concurrent because this is a shared `object`, not because a race is known: every current
     * caller resolves on its own thread before dispatching to the pool. The first resolution still
     * happens on whatever thread called in — the EDT, for the sweep — which is one small read, not
     * the per-file I/O it replaced.
     */
    private val resolved = ConcurrentHashMap<Pair<File, LuaLanguageLevel>, File>()

    private fun resolveBinary(repoRoot: File, level: LuaLanguageLevel): File {
        val build = pinnedBuild(repoRoot, level)
            ?: error(
                // `level.name`, not `$level`: LuaLanguageLevel overrides toString() to a display
                // form ("Lua 5.0"), and the manifest is keyed on the enum constant. The error must
                // name the thing the reader will grep for.
                "No pinned luac for ${level.name}. The parse oracle is built from source, not " +
                    "installed from a package, and ${level.name} has no entry in $MANIFEST_PATH.",
            )
        val binary = File(repoRoot, "test/luac/${build.version}/luac")
        if (!binary.isFile) {
            error(
                "No luac for ${level.name} at ${binary.path}.\n" +
                    "The parse oracle is built from pinned source, not installed from a package.\n" +
                    "Run:  tooling/corpus/fetch-luac.py",
            )
        }
        assertStamped(binary, build)
        return binary
    }

    /**
     * The binary must carry the digest of the tarball it was built from.
     *
     * Existence was the only check, which left the oracle trusting whatever sat at the resolved
     * path — a stale build from a previous pin, or a hand-copied distro binary. `TortureManifest`
     * already re-checks its corpus pin at test time for the same reason; the judge is the component
     * that can least afford not to.
     */
    private fun assertStamped(binary: File, build: PinnedBuild) {
        val stamp = File(binary.parentFile, ".luac-sha").takeIf { it.isFile }?.readText()?.trim()
        check(stamp == build.sha256) {
            "The luac at ${binary.path} is not the pinned build (stamp ${stamp ?: "absent"}, " +
                "expected ${build.sha256}).\nDelete ${binary.parentFile.path} and re-run: " +
                "tooling/corpus/fetch-luac.py"
        }
    }

    /** One `luac.json` entry: which upstream release answers for a level, and its tarball digest. */
    private data class PinnedBuild(val version: String, val sha256: String)

    /** The version pinned for [level], or null when the level is deliberately unpinned (LUA50). */
    fun pinnedVersion(repoRoot: File, level: LuaLanguageLevel): String? =
        pinnedBuild(repoRoot, level)?.version

    private fun pinnedBuild(repoRoot: File, level: LuaLanguageLevel): PinnedBuild? {
        val manifest = File(repoRoot, MANIFEST_PATH)
        require(manifest.isFile) { "Manifest not found: ${manifest.path}" }
        return JsonParser.parseString(manifest.readText())
            .asJsonObject.getAsJsonArray("builds")
            .map { it.asJsonObject }
            .firstOrNull { it.get("level").asString == level.name }
            ?.let { PinnedBuild(it.get("version").asString, it.get("sha256").asString) }
    }

    /**
     * Judges [source] with the `luac` pinned for [level].
     *
     * Runs **off the EDT**. `LuaCorpusSweepTest` extends `BasePlatformTestCase`, whose
     * `runInDispatchThread()` defaults to true, so the sweep body is on the EDT and spawning a
     * process there would be blocking I/O on the EDT — the thing the engineering contract forbids
     * and `ThreadingAssertions` exists to catch. The pooled task only spawns a process and takes no
     * read lock, so blocking on it cannot deadlock.
     */
    fun judge(repoRoot: File, source: CharSequence, level: LuaLanguageLevel): Verdict =
        judgeBytes(repoRoot, source.toString().toByteArray(), level)

    /**
     * Judges the **exact bytes** [source], for callers that hold them.
     *
     * Not a convenience overload — a correctness one. [judge] encodes UTF-8, which is right for the
     * project sweep (its input came from the platform already decoded) and wrong for the torture
     * sweep, whose inputs are decoded ISO-8859-1 precisely because they are not valid UTF-8. Going
     * through `judge` there re-encoded 657 of 1 696 inputs, so PUC was judging different bytes from
     * the ones Lunar lexed. Measured on those 657 plus a 4 000-case randomised probe: zero verdict
     * flips today — latent, not live, but the comparison was not the one the gate claims to make.
     */
    fun judgeBytes(repoRoot: File, source: ByteArray, level: LuaLanguageLevel): Verdict {
        val binary = requireBinary(repoRoot, level)
        val pending = ApplicationManager.getApplication().executeOnPooledThread<Verdict> { run(binary, source) }
        // The in-process wait is the backstop for the in-`run` one, not a duplicate of it: if `run`
        // itself ever wedges, an uncaught TimeoutException here would abort the whole sweep and
        // leak the thread. Degrade to NotJudged instead — the same shape as any other non-verdict.
        return try {
            pending.get(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
        } catch (expired: TimeoutException) {
            // `cancel(true)` interrupts the pooled thread; it does not kill the child. What kills
            // the child is `run`'s own finally block, which the interrupt unwinds `waitFor` into.
            // An interrupt cannot reach a thread blocked in the stdin write, so state that limit
            // rather than implying the cancel is sufficient on every path.
            pending.cancel(true)
            Verdict.NotJudged("oracle did not return within ${TIMEOUT_SECONDS * 2}s: ${expired.javaClass.simpleName}")
        }
    }

    /** Valid at every pinned level; rejected at every pinned level. Neither uses level-specific syntax. */
    private const val KNOWN_GOOD = "local sanity = 1\n"
    private const val KNOWN_BAD = "local sanity = = 1\n"

    /**
     * Proves the pinned binary **discriminates** before a sweep trusts a single one of its verdicts.
     *
     * `requireBinary` alone only proves a file exists at the resolved path. The failure it cannot
     * see is an oracle that answers `Accept` to everything: `oracleDisagreements` would then be
     * structurally 0, the ratchet would stay green, and the gate would be measuring nothing while
     * reporting success — the one failure mode a ratchet cannot survive (MAINT-35-03). Two spawns
     * per corpus member is the whole cost.
     */
    fun assertDiscriminates(repoRoot: File, level: LuaLanguageLevel) {
        val onValid = judge(repoRoot, KNOWN_GOOD, level)
        check(onValid == Verdict.Accept) {
            "The ${level.name} oracle rejected valid Lua ($onValid). It cannot be used as ground truth."
        }
        val onInvalid = judge(repoRoot, KNOWN_BAD, level)
        check(onInvalid is Verdict.Reject) {
            "The ${level.name} oracle accepted malformed Lua ($onInvalid). An oracle that accepts " +
                "everything makes oracleDisagreements structurally zero and the gate vacuous."
        }
    }

    /**
     * Diagnostics are redirected to a **file**, never read from a pipe, and stdin is written before
     * any wait. Both were defects here:
     *
     * - Reading `errorStream` to EOF *before* `waitFor` made the timeout unreachable — a hung child
     *   never closes the stream, so the read blocked forever and `destroyForcibly` was dead code.
     * - Writing the whole source into a pipe assumed luac reads all of it. It does not: it aborts at
     *   the first syntax error, and the write then fails with EPIPE. Measured against the shipped
     *   5.1.5 binary: fine at 50 kB, **broken pipe at 100 kB and 900 kB**. Latent only because every
     *   current corpus reject is under 1 kB.
     *
     * A failed write is therefore swallowed, not propagated: the child exiting early *is* the
     * verdict, and the exit code still carries it.
     *
     * Cleanup is unconditional, in a `finally`, covering the interrupt path as well as the timeout
     * one: `judge`'s `cancel(true)` unwinds `waitFor` with an `InterruptedException`, and without
     * this the child and the temp file would both outlive the sweep. `deleteOnExit` is the backstop
     * for the one path an interrupt cannot reach — a thread blocked writing stdin.
     */
    private fun run(binary: File, source: ByteArray): Verdict {
        ThreadingAssertions.softAssertBackgroundThread()
        val diagnostics = File.createTempFile("lunar-luac", ".err")
        diagnostics.deleteOnExit()
        // `-p` is parse-only and writes no output file; `-` reads the source from stdin, which
        // avoids a temp source file and keeps absolute paths out of luac's diagnostics.
        val process = ProcessBuilder(binary.path, "-p", "-")
            .redirectErrorStream(true)
            .redirectOutput(diagnostics)
            .start()
        try {
            runCatching { process.outputStream.use { it.write(source) } }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                // Never Reject: a hung oracle is not evidence about the input.
                return Verdict.NotJudged("timeout after ${TIMEOUT_SECONDS}s")
            }
            return if (process.exitValue() == 0) Verdict.Accept else Verdict.Reject(firstLineOf(diagnostics))
        } finally {
            process.destroyForcibly()
            diagnostics.delete()
        }
    }

    /**
     * luac echoes the offending token back, so its diagnostics are NOT always valid UTF-8 (risk R5a,
     * hit within five minutes of DR-01). Decoding replaces rather than throws: the text is
     * diagnostic only and is never baselined.
     */
    private fun firstLineOf(diagnostics: File): String {
        val line = diagnostics.readBytes().toString(Charsets.UTF_8).trim().lineSequence().firstOrNull().orEmpty()
        // luac prefixes its own absolute path ("/home/builder/lunar/test/luac/5.1.5/luac: stdin:1: …").
        // That is an artefact of where the binary happens to live, not part of the verdict, and it
        // would churn any recorded message across machines — BUG-409's allowlist records these.
        val marker = line.indexOf("stdin:")
        return if (marker >= 0) line.substring(marker) else line
    }
}
