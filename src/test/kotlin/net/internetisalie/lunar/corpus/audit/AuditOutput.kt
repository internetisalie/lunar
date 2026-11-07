package net.internetisalie.lunar.corpus.audit

import com.google.gson.GsonBuilder
import java.io.File

/**
 * MAINT-54 design §4: one member's audit as `build/type-audit/<member>.json`, read by
 * `tooling/corpus/type_audit_summary.py`. `tooling/corpus/testdata/type_audit_sample.json` pins the schema.
 */
object AuditOutput {
    const val SCHEMA = 1

    private val GSON = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    fun render(payload: AuditPayload): String = GSON.toJson(payload) + "\n"

    fun file(
        repoRoot: File,
        member: String,
    ): File = File(repoRoot, "build/type-audit/$member.json")

    fun write(
        repoRoot: File,
        member: String,
        payload: AuditPayload,
    ) {
        val target = file(repoRoot, member)
        target.parentFile.mkdirs()
        target.writeText(render(payload), Charsets.UTF_8)
    }
}
