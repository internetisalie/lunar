package net.internetisalie.lunar.lang

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import net.internetisalie.lunar.lang.path.resolveModuleCandidates
import net.internetisalie.lunar.lang.psi.LuaArgs
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr

class LuaRequireReference(
    element: PsiElement,
    textRange: TextRange,
    private val moduleName: String,
) : PsiReferenceBase<PsiElement>(element, textRange) {
    // MAINT-30-03 (§2.5): the module→file resolution now lives in the single canonical
    // resolveModuleCandidates helper; this caller has no type gate, so it takes the first found file.
    override fun resolve(): PsiElement? = resolveModuleCandidates(element.project, moduleName).firstOrNull()

    /**
     * Rewrites the module string when the file it names is renamed (REFACT-01-18, design §3.7).
     *
     * The override is not an optimisation — without it a file rename **fails outright**. No
     * `elementManipulator` is registered for `TERMINAL_EXPR` or `ARGS`, and
     * [PsiReferenceBase.handleElementRename] delegates to `ElementManipulators.handleContentChange`,
     * so renaming any file some `require` names threw
     * `PluginException: No ElementManipulator instance registered` and abandoned the refactoring
     * (measured on the parent commit, in all three call shapes).
     *
     * [newElementName] is the full new **file name**, extension included — `RenamePsiFileProcessor`
     * does not override `renameElement`, so `RenameUtilBase.doRenameGenericNamedElement` hands the
     * raw new name to every non-bindable reference (`RenameUtilBase.java:44-50`).
     *
     * The platform already holds the write action here; each of the three ways this can decline
     * returns [element] untouched rather than writing a partial literal.
     */
    override fun handleElementRename(newElementName: String): PsiElement {
        val moduleString = moduleStringOf(element) ?: return element
        val newLiteral = renamedLiteral(moduleString.text, newElementName) ?: return element
        val replacement = LuaElementFactory.createStringLiteral(element.project, newLiteral) ?: return element
        moduleString.node.treeParent.replaceChild(moduleString.node, replacement.node)
        return element
    }

    companion object {
        /**
         * The string carrying the module name, for whichever call shape [element] hosts — or null
         * when [element] is not a `require` argument position at all.
         *
         * Both hosts are **composite** elements. The paren-less form's module name is a bare STRING
         * leaf, but a reference cannot be hung there: `LeafPsiElement.getReferences()` returns empty
         * without ever consulting the provider registry, so a contributor never reaches it. The
         * enclosing [LuaArgs] is the anchor instead, with the range narrowed to the string.
         *
         * The two branches are mutually exclusive: in the parenthesized form [LuaArgs.getString] is
         * null (the string lives inside `exprList`), so exactly one reference is contributed per
         * call — `exactlyOneReferenceIsContributedPerCall` locks that.
         */
        fun moduleStringOf(element: PsiElement): PsiElement? =
            when (element) {
                is LuaTerminalExpr -> element.string
                is LuaArgs -> element.string
                else -> null
            }

        /**
         * [oldLiteral] with its module name repointed at [newFileName], delimiters and package
         * prefix preserved — or null when [oldLiteral] is not a delimited literal with a body.
         *
         * Delimiters are located by [LuaStringLiteralText.bodyRange], which parses them as a
         * grammar; measuring them as a run of characters drawn from `"'[=` mis-slices a body that
         * itself starts or ends with one of those (BUG-467). They are re-emitted verbatim, so
         * `require 'app.util'` never becomes `require "app.helpers"`. `.` is the only module
         * separator this plugin understands
         * (`resolveModuleCandidates` maps it to `/`), so only the last dotted segment is replaced:
         * renaming `app/util.lua` moves `app.util` to `app.helpers`, not to `helpers`.
         */
        private fun renamedLiteral(
            oldLiteral: String,
            newFileName: String,
        ): String? {
            val (openIndex, closeIndex) = LuaStringLiteralText.bodyRange(oldLiteral) ?: return null
            if (closeIndex <= openIndex) return null
            val oldModule = oldLiteral.substring(openIndex, closeIndex)
            val newBase = newFileName.removeSuffix(".lua")
            val newModule =
                if ('.' in oldModule) "${oldModule.substringBeforeLast('.')}.$newBase" else newBase
            return oldLiteral.take(openIndex) + newModule + oldLiteral.drop(closeIndex)
        }
    }
}
