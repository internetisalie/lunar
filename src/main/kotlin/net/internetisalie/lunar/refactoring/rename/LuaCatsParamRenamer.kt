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
 * **Finding the tag and rewriting it are separated, and that separation is the whole shape of this
 * object.** Finding it PARSES: `LuaPsiImplUtil.getCatsComment` reaches `prev.firstChild`
 * (`LuaPsiImplUtil.kt:29`) and `comment.paramTagList` reaches
 * `LuaCatsLazyCommentImpl.getParamTagList` → `innerComment()` → `LazyParseablePsiElement.getFirstChild()`
 * (`:88-89`) → `LazyParseableElement.getFirstChildNode()` (`:233-235`) → `ensureParsed()` (`:156`),
 * expanding a `LuaCatsLazyCommentImpl` chameleon that nothing earlier in the rename path has
 * expanded — on an input sized by the user's doc-comment block rather than by the rename. Rewriting
 * it does not parse. So [preparedRename] hands back a closure instead of applying the edit: the
 * lookup runs in design §3.3 step 3a, before the first edit and outside the non-cancelable section,
 * and the closure runs inside it (§3.3 step 4). The scan still performs **no index and no VFS
 * read**; the chameleon expansion is the one cost it carries, and it is why the split exists.
 *
 * **[oldName] is a declared parameter, and NOT because the old spelling is unrecoverable.** It was,
 * while the lookup ran after the declaration swap; since the hoist to step 3a it runs before, so
 * `parameterIdentifier.text` is the old name and a two-argument form would work. It stays explicit
 * because it makes tag selection independent of *which* leaf the caller passes — both the pre-swap
 * `element` and a post-swap `replacement` select the correct tag with it supplied (design §3.6).
 *
 * **This has no failure outcome, which is what keeps `renameElement` atomic.** It edits comment
 * text rather than a `LuaNameRef`, so it does not funnel through
 * [net.internetisalie.lunar.lang.psi.LuaElementFactory] and is not covered by the replacement that
 * §3.3 step 2 resolves up front — `risks-and-gaps.md` Gap 2.13 names it as the first candidate to
 * restore a visible half-apply. It cannot, because *every* way out of [preparedRename] short of
 * returning a closure means "there is no `@param` tag spelled [oldName] to move", which is a correct
 * no-op (TC-20b, TC-20c, TC-20g), and the closure itself is total: `ArgName ::= <<child>>`
 * (`luacats.bnf:43`) gives an `ARG_NAME` node exactly one child, a `NAME` [LeafElement] — measured,
 * not read — so selecting the tag BY that leaf leaves no branch in which a matching tag is found
 * and cannot be rewritten. [LeafElement.replaceWithText] then only interns text and calls
 * `replaceChild` (`LeafElement.java:137-141`); it neither parses nor validates, so a [newName] that
 * `LuaElementFactory.createIdentifier` already accepted cannot be rejected here.
 */
object LuaCatsParamRenamer {
    /**
     * The rewrite of the first `---@param [oldName]` tag on [parameterIdentifier]'s declaration to
     * [newName], resolved but NOT applied — or `null` when there is no such tag, which is a correct
     * no-op rather than a failure.
     *
     * Selecting by the `ARG_NAME` leaf rather than by `LuaCatsParamTag.getArgName()?.text` collapses
     * design §3.6 steps 3 and 4 into one choice, and that is deliberate: as two steps, step 4's
     * `?: return null` is unreachable for any tag step 3 can match, so it would read as a silent
     * failure branch while being nothing of the kind. The variadic form
     * `@param ... <type>` has a null `argName` (`luacats.bnf:143`) and is skipped by the same
     * selection. Duplicate `@param x` tags are already a LuaCATS error, so only the first moves.
     *
     * **The tag scan carries no `ProgressManager.checkCanceled`, deliberately, and the reason is no
     * longer the one Phase 6 gave.** It is not a search: it walks one comment's `@param` tags —
     * single digits — with no index or VFS read. Phase 6 added that the scan ran *after*
     * `renameElement` had already rewritten the declaration and every usage, so a cancellation point
     * here would be the one thing able to abandon a rename between its code edits and its annotation
     * edit; that clause is now false, because the scan runs before the first edit. The point stands
     * on the first reason alone, and the hazard it guarded against is gone rather than relocated:
     * every rewrite this object contributes is applied inside design §3.3 step 4's non-cancelable
     * section, so `risks-and-gaps.md` Gap 2.17 is CLOSED by Phase 8 and is not a standing hazard on
     * this path. The one cancellation point on the write path is design §3.3 step 3's per-usage
     * check, which runs before anything is written.
     */
    fun preparedRename(
        parameterIdentifier: PsiElement,
        oldName: String,
        newName: String,
    ): (() -> Unit)? {
        val owner =
            PsiTreeUtil.getParentOfType(parameterIdentifier, LuaCatsCommentOwner::class.java, /* strict = */ false)
                ?: return null
        val comment = LuaPsiImplUtil.getCatsComment(owner) ?: return null
        val taggedName =
            comment.paramTagList
                .asSequence()
                .mapNotNull { it.argName?.node?.firstChildNode as? LeafElement }
                .firstOrNull { it.textMatches(oldName) }
                ?: return null
        return { taggedName.replaceWithText(newName) }
    }
}
