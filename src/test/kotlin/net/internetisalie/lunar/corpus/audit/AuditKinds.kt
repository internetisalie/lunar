package net.internetisalie.lunar.corpus.audit

import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations

/**
 * MAINT-54 design §3.1: one declared kind per tag type text, read from the text and never from the
 * engine's inferred signature (DR-01), plus the wrong and control literals each kind is injected with.
 */
object AuditKinds {
    private val PRIMITIVES = setOf("string", "number", "integer", "boolean")
    private val UNION_ARMS = PRIMITIVES + "nil"
    private val OPENERS = setOf('(', '{', '<', '[')
    private val CLOSERS = setOf(')', '}', '>', ']')

    fun classify(
        typeText: String,
        comment: LuaCatsComment,
    ): DeclaredKind? {
        val text = typeText.trim()
        val generic = LuaCatsDeclarations.genericParameters(comment).firstOrNull { it.name == text }
        if (generic != null) {
            val bound = generic.boundText?.takeIf { it != text }?.let { classify(it, comment) }
            return DeclaredKind(Kind.GENERIC, text, bound = bound)
        }
        if (text.endsWith("?")) return classify(text.dropLast(1), comment)?.copy(optional = true)
        val arms = splitTopLevel(text, '|')
        if (arms.size > 1) return classifyArms(text, arms, comment)
        return classifyShape(text) ?: classifyClass(text, comment)
    }

    /** The wrong literal for [kind]'s main shape (design §3.1 table); null when the kind has none. */
    fun wrong(kind: DeclaredKind): String? =
        when (kind.kind) {
            Kind.PRIMITIVE -> primitiveWrong(kind.text)
            Kind.TABLE, Kind.RECORD, Kind.CLASS -> "function() end"
            Kind.ARRAY -> "true"
            Kind.UNION -> unionWrong(kind)
            Kind.FUNCTION -> "42"
            Kind.GENERIC -> kind.bound?.takeIf { it.kind == Kind.PRIMITIVE }?.let { primitiveWrong(it.text) }
        }

    /** The control literal for [kind]'s main shape; null means "original" (the control run is skipped). */
    fun control(kind: DeclaredKind): String? =
        when (kind.kind) {
            Kind.PRIMITIVE -> primitiveControl(kind.text)
            Kind.TABLE, Kind.ARRAY -> "{}"
            Kind.RECORD, Kind.CLASS -> null
            Kind.UNION -> kind.arms.firstOrNull { it != "nil" }?.let { primitiveControl(it) }
            Kind.FUNCTION -> functionControl(kind.text)
            Kind.GENERIC -> kind.bound?.takeIf { it.kind == Kind.PRIMITIVE }?.let { primitiveControl(it.text) }
        }

    /** True when every arm of a UNION is a primitive or `nil` — the only unions `arg.union` injects. */
    fun isPrimitiveUnion(kind: DeclaredKind): Boolean = kind.kind == Kind.UNION && kind.arms.all { it in UNION_ARMS }

    fun primitiveWrong(primitive: String): String = if (primitive == "boolean") "\"audit\"" else "true"

    fun primitiveControl(primitive: String): String =
        when (primitive) {
            "string" -> "\"ctl\""
            "boolean" -> "false"
            else -> "7"
        }

    /** Splits [text] on [separator] at bracket depth 0, trimming each part. */
    fun splitTopLevel(
        text: String,
        separator: Char,
    ): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        text.forEachIndexed { index, ch ->
            when {
                ch in OPENERS -> depth++
                ch in CLOSERS -> depth--
                ch == separator && depth == 0 -> {
                    parts += text.substring(start, index).trim()
                    start = index + 1
                }
            }
        }
        parts += text.substring(start).trim()
        return parts
    }

    private fun classifyArms(
        text: String,
        arms: List<String>,
        comment: LuaCatsComment,
    ): DeclaredKind? {
        val others = arms.filter { it != "nil" }
        if ("nil" in arms && others.size == 1) return classify(others.single(), comment)?.copy(optional = true)
        return DeclaredKind(Kind.UNION, text, arms = arms)
    }

    private fun classifyShape(text: String): DeclaredKind? {
        val kind =
            when {
                text.startsWith("fun(") || text == "function" -> Kind.FUNCTION
                text.endsWith("[]") -> Kind.ARRAY
                text.startsWith("{") -> Kind.RECORD
                text == "table" || text.startsWith("table<") -> Kind.TABLE
                text in PRIMITIVES -> Kind.PRIMITIVE
                else -> return null
            }
        return DeclaredKind(kind, text)
    }

    private fun classifyClass(
        text: String,
        comment: LuaCatsComment,
    ): DeclaredKind? {
        val resolved = LuaTypeManager.getInstance(comment.project).resolveType(text, comment)
        return if (resolved is LuaClassType) DeclaredKind(Kind.CLASS, text) else null
    }

    private fun unionWrong(kind: DeclaredKind): String? =
        when {
            !isPrimitiveUnion(kind) -> null
            "boolean" !in kind.arms -> "true"
            "string" !in kind.arms -> "\"audit\""
            else -> null
        }

    /** `function(p1, …, pn) end` with the declared arity; an entry starting `...` stays `...`. */
    private fun functionControl(text: String): String {
        val inner = if (text.startsWith("fun(")) text.substring(4, closingParen(text, 3)) else ""
        val entries = if (inner.isBlank()) emptyList() else splitTopLevel(inner, ',')
        val params = entries.mapIndexed { index, entry -> if (entry.startsWith("...")) "..." else "p${index + 1}" }
        return "function(${params.joinToString(", ")}) end"
    }

    private fun closingParen(
        text: String,
        open: Int,
    ): Int {
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                in OPENERS -> depth++
                in CLOSERS -> if (--depth == 0) return index
            }
        }
        return text.length
    }
}
