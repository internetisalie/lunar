package net.internetisalie.lunar.corpus.audit

import net.internetisalie.lunar.corpus.CorpusManifest

/**
 * The two audits [AuditMemberRun] performs (MAINT-56 design §5.2): MAINT-54's false-negative audit over
 * the sweep corpus, bare; and MAINT-56's representative audit over the type corpus, each member with its
 * definition libraries enabled and its baseline error families written beside the audit payload.
 */
enum class AuditRunMode(
    val manifestPath: String,
    val auditDir: String,
    val extrasDir: String?,
    val logTag: String,
    val enablesLibraries: Boolean,
) {
    FALSE_NEGATIVE(CorpusManifest.MANIFEST_PATH, "build/type-audit", null, "type-audit", false),
    REPRESENTATIVE(
        CorpusManifest.TYPE_MANIFEST_PATH,
        "build/type-corpus/audit",
        "build/type-corpus/extras",
        "type-corpus",
        true,
    ),
}
