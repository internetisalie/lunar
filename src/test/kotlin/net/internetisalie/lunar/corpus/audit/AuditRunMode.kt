package net.internetisalie.lunar.corpus.audit

import net.internetisalie.lunar.corpus.CorpusManifest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The two audits [AuditMemberRun] performs (MAINT-56 design §5.2): MAINT-54's false-negative audit over
 * the sweep corpus, bare; and MAINT-56's representative audit over the type corpus, each member with its
 * definition libraries enabled and its baseline error families written beside the audit payload.
 * [memberLimit] bounds the representative run per member (design §5.2, BUG-96, BUG-97); MAINT-54's stays unbounded.
 */
enum class AuditRunMode(
    val manifestPath: String,
    val auditDir: String,
    val extrasDir: String?,
    val logTag: String,
    val enablesLibraries: Boolean,
    val memberLimit: Duration?,
) {
    FALSE_NEGATIVE(CorpusManifest.MANIFEST_PATH, "build/type-audit", null, "type-audit", false, null),
    REPRESENTATIVE(
        CorpusManifest.TYPE_MANIFEST_PATH,
        "build/type-corpus/audit",
        "build/type-corpus/extras",
        "type-corpus",
        true,
        30.minutes,
    ),
}
