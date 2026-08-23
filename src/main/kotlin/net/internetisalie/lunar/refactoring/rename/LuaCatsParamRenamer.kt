package net.internetisalie.lunar.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaPsiImplUtil
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner

/**
 * Moves a `---@param <old>` tag when its parameter is renamed (REFACT-01-16, design §2.8/§3.6).
 *
 * A LuaCATS name is unlinked text — no `PsiReference` exists anywhere under `luacats/` — so the
 * tag cannot be reached as a usage and is propagated structurally instead, from the renamed
 * parameter back up to its declaration's attached comment.
 *
 * **[oldName] is a declared parameter because it cannot be recovered here.** By the time
 * `LuaRenameProcessor.renameElement` reaches design §3.3 step 5 the declaration leaf has already
 * been swapped, so the old spelling exists nowhere in the tree; §3.3 step 1 captures it before the
 * first edit and passes it in.
 *
 * **This has no failure outcome, which is what keeps `renameElement` atomic.** It edits comment
 * text rather than a `LuaNameRef`, so it does not funnel through
 * [net.internetisalie.lunar.lang.psi.LuaElementFactory] and is not covered by the replacement that
 * §3.3 step 2 resolves up front — `risks-and-gaps.md` Gap 2.13 names it as the first candidate to
 * restore a visible half-apply. It cannot, because *every* way out of [rename] short of the
 * rewrite means "there is no `@param` tag spelled [oldName] to move", which is a correct no-op
 * (TC-20b, TC-20c, TC-20g), and the rewrite itself is total: `ArgName ::= <<child>>`
 * (`luacats.bnf:43`) gives an `ARG_NAME` node exactly one child, a `NAME` [LeafElement] — measured,
 * not read — so selecting the tag BY that leaf leaves no branch in which a matching tag is found
 * and cannot be rewritten. [LeafElement.replaceWithText] then only interns text and calls
 * `replaceChild` (`LeafElement.java:137-141`); it neither parses nor validates, so a [newName] that
 * `LuaElementFactory.createIdentifier` already accepted cannot be rejected here.
 */
object LuaCatsParamRenamer {
    /**
     * Rewrites the first `---@param [oldName]` tag on [parameterIdentifier]'s declaration to
     * [newName], or does nothing.
     *
     * Selecting by the `ARG_NAME` leaf rather than by `LuaCatsParamTag.getArgName()?.text` collapses
     * design §3.6 steps 3 and 4 into one choice, and that is deliberate: as two steps, step 4's
     * `?: return` is unreachable for any tag step 3 can match, so it would read as a silent failure
     * branch while being nothing of the kind. The variadic form
     * `@param ... <type>` has a null `argName` (`luacats.bnf:143`) and is skipped by the same
     * selection. Duplicate `@param x` tags are already a LuaCATS error, so only the first moves.
     *
     * **The tag scan carries no `ProgressManager.checkCanceled`, deliberately.** It is not a search:
     * it walks one comment's `@param` tags — single digits — with no index or VFS read, and it runs
     * after `renameElement` has already rewritten the declaration and every usage. A cancellation
     * point here would be the one thing able to abandon the rename between its code edits and its
     * annotation edit, which is precisely the half-apply `risks-and-gaps.md` Gap 2.13 predicts for
     * this path. The cancellation check belongs to the usage loop upstream, where the work is
     * unbounded and nothing has been written yet.
     */
    fun rename(
        parameterIdentifier: PsiElement,
        oldName: String,
        newName: String,
    ) {
        val owner =
            PsiTreeUtil.getParentOfType(parameterIdentifier, LuaCatsCommentOwner::class.java, /* strict = */ false)
                ?: return
        val comment = LuaPsiImplUtil.getCatsComment(owner) ?: return
        val taggedName =
            comment.paramTagList
                .asSequence()
                .mapNotNull { it.argName?.node?.firstChildNode as? LeafElement }
                .firstOrNull { it.textMatches(oldName) }
                ?: return
        taggedName.replaceWithText(newName)
    }
}
