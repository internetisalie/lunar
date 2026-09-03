package net.internetisalie.lunar.refactoring.rename

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameInputValidator
import com.intellij.util.ProcessingContext
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeDeclarations

/**
 * New-name validation for a LuaCATS `@class`/`@alias` type name, against the LuaCATS `NAME`
 * grammar rather than the Lua identifier grammar (`REFACT-08-08`, design.md §2.7, §3.8).
 *
 * **The pattern must be narrow, and this is a measured constraint rather than a style choice.**
 * `RenameInputValidatorRegistry.getInputValidator` returns a non-null `Condition<String>` as soon
 * as **any** registered validator's pattern accepts the element, and `RenameUtil.isValidName` then
 * returns that condition's answer and never falls through to `LanguageNamesValidation`
 * (`RenameUtil.java:383-406`). A `PlatformPatterns.psiElement()` with an early `return true` for
 * non-cats elements is therefore wrong however harmless it reads: measured, it makes
 * `LuaNamesValidatorTest.testRenameUtilReachesValidatorForLabel` fail — `end` becomes a valid
 * label rename target. The pattern here matches only a LuaCATS `NAME` leaf that is itself a
 * declaration slot ([LuaCatsTypeDeclarations.isDeclarationLeaf]), so every other element —
 * including every Lua-side leaf — is untouched and falls through to
 * [net.internetisalie.lunar.refactoring.LuaNamesValidator] or the platform default as before.
 */
class LuaCatsTypeNameInputValidator : RenameInputValidator {
    override fun getPattern(): ElementPattern<out PsiElement> =
        PlatformPatterns.psiElement(LuaCatsElementTypes.NAME).with(IsDeclarationLeafCondition)

    /**
     * Design §3.8. The builtin-keyword clause is the converse of
     * [LuaCatsTypeRenameProcessor.substituteElementToRename] step 2: renaming a class to `table`
     * would make every future use of it parse as `LuaCatsBuiltinType`, silently unbinding the type.
     */
    override fun isInputValid(
        newName: String,
        element: PsiElement,
        context: ProcessingContext,
    ): Boolean = newName !in LuaCatsTypeDeclarations.BUILTIN_KEYWORDS && CATS_NAME.matches(newName)

    private object IsDeclarationLeafCondition : PatternCondition<PsiElement>("isDeclarationLeaf") {
        override fun accepts(
            element: PsiElement,
            context: ProcessingContext,
        ): Boolean = LuaCatsTypeDeclarations.isDeclarationLeaf(element)
    }

    private companion object {
        /**
         * `luacats.flex:70-73` transcribed:
         * `NAME = ({NAME_LEADING}{NAME_TRAILING}*)|({DIGIT}+{NAME_TRAILING}+)` with
         * `NAME_LEADING = letter|_` and `NAME_TRAILING = letter|digit|_|.|-|*`. The non-capturing
         * group and the anchors outside the alternation are load-bearing — written `^(A)|(B)$` the
         * alternation binds looser than the anchors. Unicode letters are accepted by the flex rule
         * and rejected here, a deliberate narrowing matching [net.internetisalie.lunar.refactoring.LuaNamesValidator]'s
         * own choice for Lua identifiers (`risks-and-gaps.md` Gap 2.6).
         */
        val CATS_NAME = Regex("^(?:[A-Za-z_][A-Za-z0-9_.*\\-]*|[0-9]+[A-Za-z0-9_.*\\-]+)$")
    }
}
