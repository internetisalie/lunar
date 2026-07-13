package net.internetisalie.lunar.lang.smartenter

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaElementTypes

/**
 * Raw-PSI/token helpers for the Smart Enter fixers (EDITOR-08), so the fixers hold only orchestration.
 * Everything keys off tokens (not well-formed PSI classes) because a half-written skeleton like `if x`
 * parses to an ERROR tree (Lua's grammar has no `pin` on the block rules). Stateless `object`.
 */
object LuaSmartEnterUtil {

    /** Block opener keyword leaves that Smart Enter completes. */
    val OPENERS: Set<IElementType> = setOf(
        LuaElementTypes.IF, LuaElementTypes.WHILE, LuaElementTypes.FOR,
        LuaElementTypes.FUNCTION, LuaElementTypes.DO, LuaElementTypes.REPEAT,
    )

    private val OPEN_BRACKETS = mapOf(
        LuaElementTypes.LPAREN to LuaElementTypes.RPAREN,
        LuaElementTypes.LCURLY to LuaElementTypes.RCURLY,
        LuaElementTypes.LBRACK to LuaElementTypes.RBRACK,
    )
    private val CLOSE_BRACKETS = OPEN_BRACKETS.values.toSet()

    /** First non-whitespace leaf at or after [from] (bounded by [limit]), else the leaf at [from]. */
    fun firstLeafFrom(root: PsiElement, from: Int, limit: Int): PsiElement? {
        var offset = from
        while (offset < limit) {
            val leaf = root.containingFile.findElementAt(offset) ?: return null
            if (leaf !is PsiWhiteSpace) return leaf
            offset = leaf.textRange.endOffset
        }
        return root.containingFile.findElementAt(from)
    }

    fun separatorText(separator: IElementType): String = if (separator == LuaElementTypes.DO) "do" else "then"

    fun terminatorText(terminator: IElementType): String =
        if (terminator == LuaElementTypes.UNTIL) "until" else "end"

    /** True if [opener] already has its matching terminator token somewhere after it (idempotent guard). */
    fun alreadyTerminated(opener: PsiElement, terminator: IElementType): Boolean {
        var leaf = PsiTreeUtil.nextLeaf(opener)
        while (leaf != null) {
            if (leaf.node.elementType == terminator) return true
            leaf = PsiTreeUtil.nextLeaf(leaf)
        }
        return false
    }

    /** Closers to append (in order) to balance the brackets opened within [tokens]; empty if balanced. */
    fun unbalancedClosers(tokens: List<PsiElement>): List<String> {
        val stack = ArrayDeque<IElementType>()
        for (token in tokens) {
            val type = token.node.elementType
            if (type in OPEN_BRACKETS) {
                stack.addLast(OPEN_BRACKETS.getValue(type))
            } else if (type in CLOSE_BRACKETS) {
                if (stack.isNotEmpty() && stack.last() == type) stack.removeLast() else return emptyList()
            }
        }
        return stack.reversed().map { closerText(it) }
    }

    private fun closerText(closer: IElementType): String = when (closer) {
        LuaElementTypes.RPAREN -> ")"
        LuaElementTypes.RCURLY -> "}"
        else -> "]"
    }

    /** Leaf tokens under [element], skipping whitespace. */
    fun leafTokens(element: PsiElement): List<PsiElement> =
        PsiTreeUtil.collectElementsOfType(element, PsiElement::class.java)
            .filter { it.firstChild == null && it !is PsiWhiteSpace }

    /** True if a [token] leaf appears after [opener] but before [beforeOffset] (same logical header). */
    fun hasTokenBefore(opener: PsiElement, token: IElementType, beforeOffset: Int): Boolean {
        var leaf = PsiTreeUtil.nextLeaf(opener)
        while (leaf != null && leaf.textRange.startOffset < beforeOffset) {
            if (leaf.node.elementType == token) return true
            leaf = PsiTreeUtil.nextLeaf(leaf)
        }
        return false
    }
}
