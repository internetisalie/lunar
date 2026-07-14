package net.internetisalie.lunar.analysis.redis

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaArgs
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.lang.psi.LuaVar

/**
 * A resolved `redis`/`server` call site (design §2.10).
 *
 * The single source of truth for the `redis.call` / `redis.pcall` (and `server.*`)
 * call shape, shared by the completion contributor (§2.3), the command inspection
 * (§2.4), the quick-doc provider (§3.6), and REDIS-05's function-registration scan.
 *
 * The PSI elements ([funcCall], [nameLiteral]) are meant for **synchronous** use by
 * the caller inside the same read action; the matcher never retains them.
 *
 * @param funcCall The enclosing `LuaFuncCall` (the call anchor).
 * @param nameLiteral The command-name string-literal expression, or `null` when the
 *   first argument is not a string literal (a dynamic command).
 * @param commandName The upper-cased, quote-stripped command name, or `null` when the
 *   first argument is dynamic (never flagged/completed downstream).
 * @param argCount The number of value arguments, **including** the command name.
 * @param namespace The receiver namespace — `redis` or `server`.
 * @param member The member invoked — typically `call` or `pcall`; carried verbatim so
 *   downstream consumers filter on it (REDIS-05 also matches `register_function`).
 * @see RedisCallSiteMatcher
 */
data class RedisCallSite(
    val funcCall: LuaFuncCall,
    val nameLiteral: LuaTerminalExpr?,
    val commandName: String?,
    val argCount: Int,
    val namespace: String,
    val member: String,
)

/**
 * Recognizes `redis.call`/`redis.pcall` (and `server.*`) call sites and exposes the
 * command-name literal + argument count (design §2.10).
 *
 * Pure PSI reads; call from a read action. The call-shape walk mirrors
 * `LuaRequireReferenceContributor` (`funcCall.varOrExp.var.nameRef`) and grounds on the
 * grammar (`lua.bnf:270-281`: `var ::= nameRef varSuffix*`,
 * `varSuffix ::= nameAndArgs* indexExpr`, `indexExpr ::= ('[' expr ']') | ('.' nameRef)`,
 * `funcCall ::= varOrExp nameAndArgs+`, `args ::= '(' [exprList] ')' | … | STRING`).
 */
object RedisCallSiteMatcher {

    private val NAMESPACES = setOf("redis", "server")

    /**
     * Matches [anchor] — a `LuaFuncCall`, a command-name string literal, or any element
     * inside a `redis`/`server` member call — to its [RedisCallSite].
     *
     * @return the resolved site, or `null` when [anchor] is not a `redis`/`server`
     *   member call.
     */
    fun match(anchor: PsiElement): RedisCallSite? {
        val funcCall = anchor as? LuaFuncCall
            ?: PsiTreeUtil.getParentOfType(anchor, LuaFuncCall::class.java)
            ?: return null
        val receiver = funcCall.varOrExp.`var` ?: return null
        val namespace = namespaceOf(receiver) ?: return null
        val member = memberOf(receiver) ?: return null

        val args = funcCall.nameAndArgsList.firstOrNull()?.args ?: return null
        val nameLiteral = firstStringLiteral(args)
        return RedisCallSite(
            funcCall = funcCall,
            nameLiteral = nameLiteral,
            commandName = nameLiteral?.string?.text?.trim('"', '\'')?.uppercase(),
            argCount = argCountOf(args),
            namespace = namespace,
            member = member,
        )
    }

    /** The root name-ref of a `redis`/`server` member access (`redis` in `redis.call`). */
    private fun namespaceOf(receiver: LuaVar): String? {
        val root = receiver.nameRef?.identifier?.text ?: return null
        return root.takeIf { it in NAMESPACES }
    }

    /** The invoked member (`call` in `redis.call`), from the sole dotted `varSuffix`. */
    private fun memberOf(receiver: LuaVar): String? {
        val suffix = receiver.varSuffixList.singleOrNull() ?: return null
        return suffix.indexExpr.nameRef?.identifier?.text
    }

    /**
     * The first parenthesized argument as a string literal, or `null` when it is dynamic
     * or absent. Mirrors `LuaTypesVisitor.extractModuleName` (`LuaTypesVisitor.kt:66-73`).
     */
    private fun firstStringLiteral(args: LuaArgs): LuaTerminalExpr? {
        val firstArg = args.exprList?.exprList?.firstOrNull() ?: return null
        return (firstArg as? LuaTerminalExpr)?.takeIf { it.string != null }
    }

    /** The count of value arguments (including the command name). */
    private fun argCountOf(args: LuaArgs): Int {
        args.exprList?.let { return it.exprList.size }
        return if (args.string != null) 1 else 0
    }
}
