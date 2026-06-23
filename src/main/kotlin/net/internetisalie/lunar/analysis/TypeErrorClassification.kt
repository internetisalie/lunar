package net.internetisalie.lunar.analysis

import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaFinalStatement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.types.ElementError

/**
 * A type error is "return-related" when it concerns a function's return value or signature:
 * anchored on (or anywhere within) a `return` statement, or on a function definition/declaration.
 *
 * [LuaReturnTypeMismatchInspection] reports the return-related errors; [LuaTypeAssignabilityInspection]
 * reports the rest. The two predicates are exact complements, so every engine error from
 * [net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot.getErrors] surfaces through exactly one
 * inspection — which is what keeps the editor highlight (and the hover tooltip) single, not doubled.
 *
 * The `getParentOfType(..., strict = false)` ancestor walk also matches when the error is anchored on
 * the returned expression itself (e.g. a string literal nested in the return's expression list), which
 * a direct-parent check missed.
 */
internal fun ElementError.isReturnRelated(): Boolean =
    element is LuaFuncDef ||
        element is LuaFuncDecl ||
        element is LuaLocalFuncDecl ||
        PsiTreeUtil.getParentOfType(element, LuaFinalStatement::class.java, false) != null
