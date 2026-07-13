package net.internetisalie.lunar.lang.syntax

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaRepeatStatement
import net.internetisalie.lunar.lang.psi.LuaTableConstructor

/**
 * Single source of truth for the Enter handler's opener -> terminator pairs (COMP-08, design §2.3).
 *
 * Note: this is NOT the source for [LuaPairedBraceMatcher], which keys its `if` pair on
 * `IF` -> `END` (span highlighting) rather than `THEN` -> `END`; the matcher keeps its own list.
 */
object LuaBlockPairs {
    /** opener element type at `offset-1` -> terminator element type, used by the balance check (§3.2). */
    val terminatorByOpener: Map<IElementType, IElementType> = mapOf(
        LuaElementTypes.THEN to LuaElementTypes.END,
        LuaElementTypes.DO to LuaElementTypes.END,
        LuaElementTypes.FUNCTION to LuaElementTypes.END,
        LuaElementTypes.REPEAT to LuaElementTypes.UNTIL,
        LuaElementTypes.LCURLY to LuaElementTypes.RCURLY
    )

    /** literal text inserted on the next line for each terminator. */
    val insertTextFor: Map<IElementType, String> = mapOf(
        LuaElementTypes.END to "end",
        LuaElementTypes.UNTIL to "until",
        LuaElementTypes.RCURLY to "}"
    )

    /**
     * OPENER-keyword leaf -> intermediate separator keyword, for Smart Enter (EDITOR-08) scaffolding a
     * bare `if x` / `while c` / `for …` skeleton where no separator leaf exists yet. `function`/`do`/
     * `repeat` have no separator and are absent. Additive; keyed on [LuaElementTypes] like the maps above.
     */
    val separatorByOpenerKeyword: Map<IElementType, IElementType> = mapOf(
        LuaElementTypes.IF to LuaElementTypes.THEN,
        LuaElementTypes.WHILE to LuaElementTypes.DO,
        LuaElementTypes.FOR to LuaElementTypes.DO,
    )

    /** OPENER-keyword leaf -> terminator keyword, for Smart Enter (EDITOR-08). Additive. */
    val terminatorByOpenerKeyword: Map<IElementType, IElementType> = mapOf(
        LuaElementTypes.IF to LuaElementTypes.END,
        LuaElementTypes.WHILE to LuaElementTypes.END,
        LuaElementTypes.FOR to LuaElementTypes.END,
        LuaElementTypes.FUNCTION to LuaElementTypes.END,
        LuaElementTypes.DO to LuaElementTypes.END,
        LuaElementTypes.REPEAT to LuaElementTypes.UNTIL,
    )

    /**
     * owner-NODE-kind -> terminator, used by the between-pair indent (§3.4), which starts from the
     * owner node rather than the opener leaf. Distinct from [terminatorByOpener] (keyed by leaf).
     */
    fun terminatorForOwner(owner: PsiElement): IElementType =
        when (owner) {
            is LuaRepeatStatement -> LuaElementTypes.UNTIL
            is LuaTableConstructor -> LuaElementTypes.RCURLY
            else -> LuaElementTypes.END
        }
}
