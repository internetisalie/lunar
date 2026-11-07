package net.internetisalie.lunar.corpus.audit

import com.intellij.openapi.util.TextRange
import net.internetisalie.lunar.lang.psi.LuaExpr

/** What every site of one call shares: its cell coordinates, file, range and declaring file. */
internal data class CallBase(
    val path: CalleePath,
    val form: CalleeForm,
    val file: String,
    val site: TextRange,
    val declFile: String?,
    val declRange: TextRange?,
) {
    fun site(
        shape: String,
        injection: Injection,
        slot: ArgSlot?,
    ): AuditSite =
        AuditSite(
            cell = AuditCell(shape, path, form),
            file = file,
            edit = injection.edit,
            wrong = injection.wrong,
            control = injection.control,
            site = site,
            paramRange = slot?.paramRange,
            declRange = declRange,
            declFile = declFile,
            declaredType = slot?.kind?.text ?: injection.declaredType,
        )
}

/** One argument against its declared parameter; [paramRange] is set only for SAME_FILE callees. */
internal data class ArgSlot(
    val arg: LuaExpr,
    val kind: DeclaredKind,
    val paramRange: TextRange?,
)

/** An edit and its two replacement texts; a null [control] means "original". */
internal data class Injection(
    val edit: TextRange,
    val wrong: String,
    val control: String?,
    val declaredType: String = "",
)

/** The argument list of one call, for the arity shapes of design §3.2 step 8. */
internal data class CallArgs(
    val args: List<LuaExpr>,
    val argsRange: TextRange,
    val lastRequired: Boolean,
    val signature: String,
)

/** Design §3.1's table: which shapes a declared kind emits, with their wrong and control texts. */
internal object AuditShapes {
    private val NUMBER_LITERAL = Regex("^(-?\\d|0x)")

    /** Kinds whose shapes the §3.1 table restricts to "not optional". */
    private val REQUIRED_ONLY = setOf(Kind.TABLE, Kind.RECORD, Kind.CLASS, Kind.ARRAY, Kind.FUNCTION)

    fun argSites(
        base: CallBase,
        slot: ArgSlot,
    ): List<AuditSite> =
        argShapes(slot).map { (shape, wrong, control) ->
            base.site(shape, Injection(slot.arg.textRange, wrong, control), slot)
        }

    fun aritySites(
        base: CallBase,
        call: CallArgs,
    ): List<AuditSite> {
        val insertAt = TextRange.from(call.argsRange.endOffset - 1, 0)
        val tooMany = Injection(insertAt, if (call.args.isEmpty()) "true" else ", true", null, call.signature)
        val sites = mutableListOf(base.site("arity.tooMany", tooMany, null))
        if (call.args.isNotEmpty() && call.lastRequired) {
            val previous = call.args.getOrNull(call.args.size - 2)
            val start = previous?.textRange?.endOffset ?: call.args[0].textRange.startOffset
            val tooFew =
                Injection(
                    TextRange(
                        start,
                        call.args
                            .last()
                            .textRange.endOffset,
                    ),
                    "",
                    null,
                    call.signature,
                )
            sites += base.site("arity.tooFew", tooFew, null)
        }
        return sites
    }

    private fun argShapes(slot: ArgSlot): List<Triple<String, String, String?>> {
        val kind = slot.kind
        if (kind.optional && kind.kind in REQUIRED_ONLY) return emptyList()
        return when (kind.kind) {
            Kind.PRIMITIVE -> listOf(Triple(primitiveShape(kind), wrongOf(kind), AuditKinds.control(kind)))
            Kind.TABLE, Kind.RECORD, Kind.CLASS -> tableShapes(kind)
            Kind.ARRAY -> listOf(Triple("arg.array", wrongOf(kind), AuditKinds.control(kind)))
            Kind.UNION -> unionShape(slot)
            Kind.FUNCTION -> listOf(Triple("arg.function", wrongOf(kind), AuditKinds.control(kind)))
            Kind.GENERIC -> genericShape(kind)
        }
    }

    private fun primitiveShape(kind: DeclaredKind): String = if (kind.optional) "arg.optional" else "arg.primitive"

    private fun wrongOf(kind: DeclaredKind): String = AuditKinds.wrong(kind) ?: error("no wrong literal for $kind")

    private fun tableShapes(kind: DeclaredKind): List<Triple<String, String, String?>> =
        listOf(
            Triple("arg.${kind.kind.name.lowercase()}", wrongOf(kind), AuditKinds.control(kind)),
            Triple("lenient.primitiveForTable", "true", null),
        )

    private fun genericShape(kind: DeclaredKind): List<Triple<String, String, String?>> {
        val wrong = AuditKinds.wrong(kind) ?: return emptyList()
        return listOf(Triple("arg.generic", wrong, AuditKinds.control(kind)))
    }

    private fun unionShape(slot: ArgSlot): List<Triple<String, String, String?>> {
        val wrong = AuditKinds.wrong(slot.kind) ?: return emptyList()
        val matching = literalArm(slot.arg.text, slot.kind.arms)
        val control = matching?.let { AuditKinds.primitiveControl(it) } ?: AuditKinds.control(slot.kind)
        return listOf(Triple("arg.union", wrong, control))
    }

    /** The union arm matching the argument's literal kind (design §3.2 step 7), or null. */
    private fun literalArm(
        text: String,
        arms: List<String>,
    ): String? {
        val wanted =
            when {
                text.startsWith(
                    "\"",
                ) ||
                    text.startsWith("'") ||
                    text.startsWith("[[") ||
                    text.startsWith("[=") -> listOf("string")
                NUMBER_LITERAL.containsMatchIn(text) -> listOf("number", "integer")
                text == "true" || text == "false" -> listOf("boolean")
                else -> emptyList()
            }
        return arms.firstOrNull { it in wanted }
    }
}
