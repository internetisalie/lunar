package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaLabelScopes
import net.internetisalie.lunar.lang.psi.LuaPsiUtils
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * The declaration being renamed and the name it is becoming (design §2.3). Folded into one type so
 * [LuaLabelConflictDetector.collisions] takes a single argument.
 */
internal data class LuaLabelRenameTarget(
    val label: LuaLabelName,
    val newName: String,
)

/**
 * The duplicate/shadowing check `REFACT-04-07` needs and the platform's default
 * `RenamePsiElementProcessorBase` does not provide (design §2.3, §3.2, §3.3).
 *
 * **Why the anchor is always the colliding [LuaLabelName], never a `goto`**: it is the element the
 * user must look at — the rival declaration, not one of the jumps that would be rebound. Anchoring
 * on a usage would not skip rewriting it either way (`LuaRenameCollisionUsageInfo`'s KDoc corrects
 * the opposite, previously-held claim), but a label declaration is never a usage of another label,
 * so the question does not arise here.
 *
 * **Threading**: called only from [LuaLabelRenameProcessor.findCollisions], i.e. inside the
 * background read action `BaseRefactoringProcessor` wraps around `findUsages`. Never the EDT.
 * `ProgressManager.checkCanceled()` guards the one loop that can grow with the size of the
 * function being renamed in.
 */
internal object LuaLabelConflictDetector {
    /**
     * Design §3.3. Empty list when the rename is safe. Never returns a collision anchored on a
     * `goto` — every candidate this method compares [target] against is itself a [LuaLabelName].
     */
    fun collisions(target: LuaLabelRenameTarget): List<LuaRenameCollisionUsageInfo> {
        if (target.label.name == target.newName) return emptyList()
        val scope = LuaLabelScopes.functionScopeOf(target.label) ?: return emptyList()
        if (LuaLabelScopes.blockOf(target.label) == null) return emptyList()
        val found =
            LuaLabelScopes.labelsInFunctionScope(scope).mapNotNull { other ->
                ProgressManager.checkCanceled()
                collisionWith(target, other)
            }
        return found.distinctBy { it.element }
    }

    /** One candidate, checked against [target] and wrapped if it collides — or `null` if it does not. */
    private fun collisionWith(
        target: LuaLabelRenameTarget,
        other: LuaLabelName,
    ): LuaRenameCollisionUsageInfo? {
        if (other === target.label) return null
        if (other.name != target.newName) return null
        if (!collides(target, other)) return null
        return LuaRenameCollisionUsageInfo(other, target.label, messageFor(target, other))
    }

    /**
     * The rule of design §3.2, corrected from `REFACT-04-07`'s stated wording
     * (`risks-and-gaps.md` RD-1): two labels of the same name collide iff one is declared in an
     * enclosing-or-same block of the other **and the outer one comes first in source order**. The
     * "descendant" direction alone — an outer label declared AFTER an inner one closes — is legal
     * Lua on every version tested (design §1 rows P-b, P-e) and must not be reported.
     */
    private fun collides(
        target: LuaLabelRenameTarget,
        other: LuaLabelName,
    ): Boolean {
        val renamedBlock = LuaLabelScopes.blockOf(target.label) ?: return false
        val otherBlock = LuaLabelScopes.blockOf(other) ?: return false
        val otherEnclosesAndComesFirst =
            PsiTreeUtil.isAncestor(otherBlock, renamedBlock, false) && before(other, target.label)
        val renamedEnclosesAndComesFirst =
            PsiTreeUtil.isAncestor(renamedBlock, otherBlock, false) && before(target.label, other)
        return otherEnclosesAndComesFirst || renamedEnclosesAndComesFirst
    }

    private fun before(
        first: PsiElement,
        second: PsiElement,
    ): Boolean = first.textRange.startOffset < second.textRange.startOffset

    /**
     * Design §3.4 — one conflicts-dialog mechanism, tiered by message. At `LUA54` and above the
     * renamed file will not load (executed, design §1 row P-a); below it the rename silently
     * rebinds a `goto` instead. `>=` is the fail-safe direction for `LUA55`, whose own behaviour is
     * unverified (`risks-and-gaps.md` DR-01): a stricter message on a permissive version is a false
     * alarm, the reverse silently ships a broken file.
     */
    private fun messageFor(
        target: LuaLabelRenameTarget,
        other: LuaLabelName,
    ): String {
        val level = LuaProjectSettings.getInstance(other.project).state.languageLevel
        val line = LuaPsiUtils.getElementLineNumber(other)
        val key =
            if (level >= LuaLanguageLevel.LUA54) {
                "refactoring.rename.label.conflict.duplicate"
            } else {
                "refactoring.rename.label.conflict.rebind"
            }
        return LuaBundle.message(key, target.newName, line, level.toString())
    }
}
