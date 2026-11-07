package net.internetisalie.lunar.corpus.audit

import com.intellij.openapi.util.TextRange

/** The declared-type kinds of MAINT-54 design §3.1, classified from tag text only. */
enum class Kind { PRIMITIVE, TABLE, RECORD, CLASS, ARRAY, UNION, FUNCTION, GENERIC }

data class DeclaredKind(
    val kind: Kind,
    val text: String,
    val optional: Boolean = false,
    val bound: DeclaredKind? = null,
    val arms: List<String> = emptyList(),
)

/** Where the callee's declaration lives, relative to the call (design §3.2 step 5). */
enum class CalleePath { SAME_FILE, PROJECT_FILE, CHECKOUT_FILE, LIBRARY }

/** How the callee is declared (design §3.2 step 4); FIELD is a `@field` assignment. */
enum class CalleeForm { LOCAL, GLOBAL, MEMBER, METHOD, FIELD }

/** Where a new error lands relative to the injected site (design §3.4 step 5). */
enum class Anchor { AT_SITE, AT_PARAM, AT_DECL, BODY, DECLARING_FILE }

enum class AuditOutcome { REPORTED, REPORTED_OTHER_RULE, SILENT, LENIENT_BY_DESIGN, NOISY, SKIPPED_PREEXISTING, FAILED }

data class AuditCell(
    val shape: String,
    val path: CalleePath,
    val form: CalleeForm,
)

/**
 * One injection point. [edit] is replaced by [wrong], then by [control] (null: the site's own text,
 * so the control run is skipped). [file] and [declFile] are paths relative to the fixture's temp root.
 */
data class AuditSite(
    val cell: AuditCell,
    val file: String,
    val edit: TextRange,
    val wrong: String,
    val control: String?,
    val site: TextRange,
    val paramRange: TextRange?,
    val declRange: TextRange?,
    val declFile: String?,
    val declaredType: String,
)

data class AuditEntry(
    val severity: String,
    val rule: String,
    val message: String,
    val anchor: Anchor,
)

data class AuditRecord(
    val cell: AuditCell,
    val file: String,
    val line: Int,
    val declaredType: String,
    val wrong: String,
    val outcome: AuditOutcome,
    val newErrors: List<AuditEntry>,
)

/** Design §3.2: the call counters sum to [calls]; the other two count fields and LDoc lines. */
data class AuditCoverage(
    val calls: Int = 0,
    val stringOrTableArgs: Int = 0,
    val unresolvedCallee: Int = 0,
    val unresolvedMethod: Int = 0,
    val formMismatch: Int = 0,
    val noTag: Int = 0,
    val arityGuard: Int = 0,
    val eligible: Int = 0,
    val fieldUntagged: Int = 0,
    val ldocParam: Int = 0,
) {
    operator fun plus(other: AuditCoverage): AuditCoverage =
        AuditCoverage(
            calls + other.calls,
            stringOrTableArgs + other.stringOrTableArgs,
            unresolvedCallee + other.unresolvedCallee,
            unresolvedMethod + other.unresolvedMethod,
            formMismatch + other.formMismatch,
            noTag + other.noTag,
            arityGuard + other.arityGuard,
            eligible + other.eligible,
            fieldUntagged + other.fieldUntagged,
            ldocParam + other.ldocParam,
        )
}

data class AuditPopulation(
    val cell: AuditCell,
    val population: Int,
)

/** The per-member JSON document of design §4. */
data class AuditPayload(
    val schema: Int,
    val member: String,
    val commit: String,
    val cap: Int,
    val coverage: AuditCoverage,
    val cells: List<AuditPopulation>,
    val records: List<AuditRecord>,
)
