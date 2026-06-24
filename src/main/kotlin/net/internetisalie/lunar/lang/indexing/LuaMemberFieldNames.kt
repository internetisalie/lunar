package net.internetisalie.lunar.lang.indexing

import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaVar

/**
 * The dotted qualified name of an assignable `LuaVar` of the form `a.b(.c…)` — e.g. `package.path`.
 * Returns null for a bare name (`a`) or any bracket/method access in the path (`a[i]`, `a:m`), which
 * have no stable qualified key. Used by [LuaMemberFieldIndex] and
 * [net.internetisalie.lunar.lang.navigation.LuaMemberFieldNavigation] so indexing and resolution agree.
 */
internal fun dottedMemberName(target: LuaVar): String? {
    val base = target.nameRef?.text ?: return null
    if (target.varSuffixList.isEmpty()) return null
    val builder = StringBuilder(base)
    for (suffix in target.varSuffixList) {
        val field = suffix.indexExpr?.nameRef?.text ?: return null
        builder.append('.').append(field)
    }
    return builder.toString()
}

/** The field-name identifier of the last dotted segment (the navigation target), or null. */
internal fun memberFieldIdentifier(target: LuaVar): PsiElement? =
    target.varSuffixList.lastOrNull()?.indexExpr?.nameRef
