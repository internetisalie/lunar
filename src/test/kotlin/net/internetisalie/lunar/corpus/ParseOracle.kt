package net.internetisalie.lunar.corpus

import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import net.internetisalie.lunar.lang.LuaLanguageLevel
import java.io.File
import java.util.concurrent.TimeUnit

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
    fun requireBinary(repoRoot: File, level: LuaLanguageLevel): File {
        val version = pinnedVersion(repoRoot, level)
            ?: error(
                // `level.name`, not `$level`: LuaLanguageLevel overrides toString() to a display
                // form ("Lua 5.0"), and the manifest is keyed on the enum constant. The error must
                // name the thing the reader will grep for.
                "No pinned luac for ${level.name}. The parse oracle is built from source, not " +
                    "installed from a package, and ${level.name} has no entry in $MANIFEST_PATH.",
            )
        val binary = File(repoRoot, "test/luac/$version/luac")
        if (!binary.isFile) {
            error(
                "No luac for ${level.name} at ${binary.path}.\n" +
                    "The parse oracle is built from pinned source, not installed from a package.\n" +
                    "Run:  tooling/corpus/fetch-luac.py",
            )
        }
        return binary
    }

    /** The version pinned for [level], or null when the level is deliberately unpinned (LUA50). */
    fun pinnedVersion(repoRoot: File, level: LuaLanguageLevel): String? {
        val manifest = File(repoRoot, MANIFEST_PATH)
        require(manifest.isFile) { "Manifest not found: ${manifest.path}" }
        return JsonParser.parseString(manifest.readText())
            .asJsonObject.getAsJsonArray("builds")
            .map { it.asJsonObject }
            .firstOrNull { it.get("level").asString == level.name }
            ?.get("version")?.asString
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
    fun judge(repoRoot: File, source: CharSequence, level: LuaLanguageLevel): Verdict {
        val binary = requireBinary(repoRoot, level)
        return onPooledThread { run(binary, source) }
    }

    private fun <T> onPooledThread(body: () -> T): T =
        ApplicationManager.getApplication()
            .executeOnPooledThread<T>(body)
            .get(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)

    private fun run(binary: File, source: CharSequence): Verdict {
        // `-p` is parse-only and writes no output file; `-` reads the source from stdin, which
        // avoids a temp file and keeps absolute paths out of luac's diagnostics.
        val process = ProcessBuilder(binary.path, "-p", "-").redirectErrorStream(false).start()
        process.outputStream.use { it.write(source.toString().toByteArray()) }

        // luac echoes the offending token back, so its stderr is NOT always valid UTF-8 (risk R5a,
        // hit within five minutes of DR-01). Decode replacing: the message is diagnostic only.
        val stderr = process.errorStream.readBytes().toString(Charsets.UTF_8)
        process.inputStream.readBytes()

        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            // Never Reject: a hung oracle is not evidence about the input.
            return Verdict.NotJudged("timeout after ${TIMEOUT_SECONDS}s")
        }
        return if (process.exitValue() == 0) {
            Verdict.Accept
        } else {
            Verdict.Reject(stderr.trim().lineSequence().firstOrNull().orEmpty())
        }
    }
}
