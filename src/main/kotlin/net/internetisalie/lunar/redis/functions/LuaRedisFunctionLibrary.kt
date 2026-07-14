package net.internetisalie.lunar.redis.functions

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.analysis.redis.RedisCallSiteMatcher
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaField
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr

/** The registered-names result from a static scan of a library file (design §2.2). */
data class RegisteredNames(val names: Set<String>, val hasDynamic: Boolean)

/**
 * Paired cache entry for registration scan results: names + per-function flag sets.
 * Stored together to avoid a double walk per file (design §3.7).
 */
private data class RegistrationCache(
    val registered: RegisteredNames,
    val flags: Map<String, Set<String>>,
)

/**
 * Static model for a Redis Function library file (design §2.2).
 *
 * Detects the `#!lua name=<lib>` shebang and statically collects `register_function`
 * call names. Both results are cached per-file via [CachedValuesManager] and invalidated
 * on any PSI modification.
 *
 * Pure PSI reads; call from a read action. No retained heavy refs.
 */
object LuaRedisFunctionLibrary {

    /** Matches the `#!lua name=<identifier>` shebang prefix (design §3.2). */
    val SHEBANG_NAME = Regex("""^#!\s*lua\s+name=([A-Za-z0-9_]+)""")

    private val REGISTER_NAMESPACES = setOf("redis", "server")

    /**
     * Returns the library name declared in the `#!lua name=<lib>` shebang, or `null`
     * if the file is not a Lua Function library (design §3.2).
     */
    fun detect(file: PsiFile): String? =
        CachedValuesManager.getManager(file.project).getCachedValue(file) {
            CachedValueProvider.Result.create(
                detectUncached(file),
                PsiModificationTracker.MODIFICATION_COUNT,
            )
        }

    /** Returns true when [detect] yields a non-null library name. */
    fun isLibrary(file: PsiFile): Boolean = detect(file) != null

    /**
     * Statically scans all `redis.register_function` / `server.register_function` calls
     * in [file] and returns the set of literal-string names plus a flag for any dynamic
     * (non-literal) registration (design §3.7).
     */
    fun registeredNames(file: PsiFile): RegisteredNames = registrationCache(file).registered

    /**
     * Returns the `flags` string set for the named function from the most recent static
     * scan, or an empty set when the function was not found or used a dynamic name
     * (design §3.7 step 4).
     */
    fun registeredFlags(file: PsiFile, name: String): Set<String> =
        registrationCache(file).flags[name] ?: emptySet()

    // -------------------------------------------------------------------------
    // Detect helpers (§3.2)
    // -------------------------------------------------------------------------

    private fun detectUncached(file: PsiFile): String? {
        if (file !is LuaFile) return null
        val shebangLeaf = findLeadingShebang(file) ?: return null
        val shebangLine = buildShebangLine(shebangLeaf)
        return SHEBANG_NAME.find(shebangLine)?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
    }

    private fun findLeadingShebang(file: LuaFile): PsiElement? {
        var leaf: PsiElement = PsiTreeUtil.getDeepestFirst(file)
        while (leaf !== file) {
            when {
                leaf.elementType == LuaElementTypes.SHEBANG -> return leaf
                !isHeaderLeaf(leaf) -> return null
            }
            leaf = PsiTreeUtil.nextLeaf(leaf) ?: return null
        }
        return null
    }

    private fun isHeaderLeaf(leaf: PsiElement): Boolean =
        leaf is PsiWhiteSpace ||
            leaf.elementType == LuaElementTypes.SHEBANG ||
            leaf.elementType == LuaElementTypes.SHORTCOMMENT ||
            leaf.elementType == LuaElementTypes.LONGCOMMENT

    private fun buildShebangLine(shebangLeaf: PsiElement): String {
        val shebangText = shebangLeaf.text
        val nextLeaf = PsiTreeUtil.nextLeaf(shebangLeaf) ?: return shebangText
        val commentText = if (nextLeaf.elementType == LuaElementTypes.SHORTCOMMENT) nextLeaf.text else ""
        return shebangText + commentText
    }

    // -------------------------------------------------------------------------
    // Registration scan helpers (§3.7)
    // -------------------------------------------------------------------------

    private fun registrationCache(file: PsiFile): RegistrationCache =
        CachedValuesManager.getManager(file.project).getCachedValue(file) {
            CachedValueProvider.Result.create(
                buildRegistrationCache(file),
                PsiModificationTracker.MODIFICATION_COUNT,
            )
        }

    private fun buildRegistrationCache(file: PsiFile): RegistrationCache {
        val names = mutableSetOf<String>()
        val flagsMap = mutableMapOf<String, Set<String>>()
        var hasDynamic = false

        val funcCalls = PsiTreeUtil.findChildrenOfType(file, LuaFuncCall::class.java)
        for (fc in funcCalls) {
            val site = RedisCallSiteMatcher.match(fc) ?: continue
            if (site.namespace !in REGISTER_NAMESPACES) continue
            if (site.member != "register_function") continue

            val args = fc.nameAndArgsList.lastOrNull()?.args ?: continue
            val tableConstructor = args.tableConstructor
            if (tableConstructor != null) {
                val (name, flags, dynamic) = extractTableForm(tableConstructor)
                if (name != null) {
                    names.add(name)
                    flagsMap[name] = flags
                } else if (dynamic) {
                    hasDynamic = true
                }
            } else {
                val (name, dynamic) = extractPositionalForm(args.exprList?.exprList)
                if (name != null) {
                    names.add(name)
                } else if (dynamic) {
                    hasDynamic = true
                }
            }
        }

        return RegistrationCache(RegisteredNames(names, hasDynamic), flagsMap)
    }

    /** Extracts (name, flags, isDynamic) from a table-form `register_function{ ... }` call. */
    private fun extractTableForm(table: LuaTableConstructor): Triple<String?, Set<String>, Boolean> {
        val fields = table.fieldList?.fieldList ?: return Triple(null, emptySet(), false)
        var name: String? = null
        var flags: Set<String> = emptySet()
        var isDynamic = false
        for (field in fields) {
            when (field.identifier?.text) {
                "function_name" -> {
                    val literal = fieldStringLiteral(field)
                    if (literal != null) name = literal else isDynamic = true
                }
                "flags" -> flags = extractFlagsField(field)
            }
        }
        return Triple(name, flags, isDynamic)
    }

    /** Extracts (name, isDynamic) from a positional `register_function('name', cb)` call. */
    private fun extractPositionalForm(args: List<LuaExpr>?): Pair<String?, Boolean> {
        val firstArg = args?.firstOrNull() ?: return null to false
        val terminal = firstArg as? LuaTerminalExpr ?: return null to true
        val stringEl = terminal.string ?: return null to true
        val name = stripQuotes(stringEl.text)
        return if (name.isNotEmpty()) name to false else null to true
    }

    /** Reads the string literal value of a table field, or null if non-literal. */
    private fun fieldStringLiteral(field: LuaField): String? {
        val valueExpr = field.exprList.firstOrNull() ?: return null
        val terminal = valueExpr as? LuaTerminalExpr ?: return null
        val stringEl = terminal.string ?: return null
        val stripped = stripQuotes(stringEl.text)
        return stripped.takeIf { it.isNotEmpty() }
    }

    /** Reads flag string literals from a `flags = { 'no-writes', ... }` table field. */
    private fun extractFlagsField(field: LuaField): Set<String> {
        val flagsExpr = field.exprList.firstOrNull() as? LuaTableConstructor ?: return emptySet()
        val flagFields = flagsExpr.fieldList?.fieldList ?: return emptySet()
        return flagFields.mapNotNull { f ->
            val expr = f.exprList.firstOrNull() as? LuaTerminalExpr ?: return@mapNotNull null
            val stringEl = expr.string ?: return@mapNotNull null
            stripQuotes(stringEl.text).takeIf { it.isNotEmpty() }
        }.toSet()
    }

    /** Strips surrounding quotes exactly as in LuaRequireReferenceContributor (line 39). */
    private fun stripQuotes(raw: String): String = raw.trim('"', '\'', '[', ']', '=')
}
