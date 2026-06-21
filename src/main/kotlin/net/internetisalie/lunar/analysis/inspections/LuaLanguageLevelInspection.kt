package net.internetisalie.lunar.analysis.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.psi.LuaAttrib
import net.internetisalie.lunar.lang.psi.LuaBinOp
import net.internetisalie.lunar.lang.psi.LuaGlobalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGlobalModeDecl
import net.internetisalie.lunar.lang.psi.LuaGlobalVarDecl
import net.internetisalie.lunar.lang.psi.LuaGotoStatement
import net.internetisalie.lunar.lang.psi.LuaLabel
import net.internetisalie.lunar.lang.psi.LuaUnOp
import net.internetisalie.lunar.lang.psi.LuaVisitor
import net.internetisalie.lunar.lang.syntax.RemoveGotoFix
import net.internetisalie.lunar.lang.syntax.RemoveLabelFix
import net.internetisalie.lunar.lang.syntax.ReplaceIntegerDivisionFix
import net.internetisalie.lunar.lang.syntax.UpgradeLanguageLevelFix
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Flags version-specific Lua syntax used in a project configured for an earlier language level
 * (INSP-09). Replaces the former `LuaLanguageLevelAnnotator`: every annotator check is migrated here
 * so the feature is toggleable as an inspection, with the same messages and the same quick fixes
 * (reused via [IntentionWrapper]). All Lua 5.1–5.4 syntax parses; this enforces the runtime level.
 */
class LuaLanguageLevelInspection : LocalInspectionTool() {

    override fun getShortName(): String = "LuaLanguageLevel"

    override fun getGroupDisplayName(): String = "Lua"

    override fun getDisplayName(): String = "Language level compliance"

    override fun isEnabledByDefault(): Boolean = true

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.ERROR

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : LuaVisitor() {
            override fun visitGotoStatement(o: LuaGotoStatement) {
                if (level(o) < LuaLanguageLevel.LUA52) {
                    register(
                        holder, o,
                        "Goto statements are a Lua 5.2+ feature (project configured for ${level(o)})",
                        UpgradeLanguageLevelFix(LuaLanguageLevel.LUA52), RemoveGotoFix(),
                    )
                }
            }

            override fun visitLabel(o: LuaLabel) {
                if (level(o) < LuaLanguageLevel.LUA52) {
                    register(
                        holder, o,
                        "Labels are a Lua 5.2+ feature (project configured for ${level(o)})",
                        UpgradeLanguageLevelFix(LuaLanguageLevel.LUA52), RemoveLabelFix(),
                    )
                }
            }

            override fun visitBinOp(o: LuaBinOp) {
                if (level(o) >= LuaLanguageLevel.LUA53) return
                when (val operator = o.text) {
                    "//" -> register(
                        holder, o,
                        "Integer division (//) is a Lua 5.3+ feature (project configured for ${level(o)})",
                        UpgradeLanguageLevelFix(LuaLanguageLevel.LUA53), ReplaceIntegerDivisionFix(),
                    )
                    in BITWISE_OPERATORS -> register(
                        holder, o,
                        "${bitwiseOperatorName(operator)} is a Lua 5.3+ feature (project configured for ${level(o)})",
                        UpgradeLanguageLevelFix(LuaLanguageLevel.LUA53),
                    )
                }
            }

            override fun visitUnOp(o: LuaUnOp) {
                if (level(o) < LuaLanguageLevel.LUA53 && o.text == "~") {
                    register(
                        holder, o,
                        "Bitwise NOT operator (~) is a Lua 5.3+ feature (project configured for ${level(o)})",
                        UpgradeLanguageLevelFix(LuaLanguageLevel.LUA53),
                    )
                }
            }

            override fun visitAttrib(o: LuaAttrib) {
                if (level(o) < LuaLanguageLevel.LUA54) {
                    register(
                        holder, o,
                        "Variable attributes are a Lua 5.4 feature (project configured for ${level(o)})",
                        UpgradeLanguageLevelFix(LuaLanguageLevel.LUA54),
                    )
                }
            }

            override fun visitGlobalVarDecl(o: LuaGlobalVarDecl) {
                super.visitGlobalVarDecl(o)
                if (level(o) < LuaLanguageLevel.LUA55) {
                    register(
                        holder, o.firstChild,
                        "Global variable declarations are only available in Lua 5.5+",
                        UpgradeLanguageLevelFix(LuaLanguageLevel.LUA55)
                    )
                }
            }

            override fun visitGlobalFuncDecl(o: LuaGlobalFuncDecl) {
                super.visitGlobalFuncDecl(o)
                if (level(o) < LuaLanguageLevel.LUA55) {
                    register(
                        holder, o.firstChild,
                        "Global function declarations are only available in Lua 5.5+",
                        UpgradeLanguageLevelFix(LuaLanguageLevel.LUA55)
                    )
                }
            }

            override fun visitGlobalModeDecl(o: LuaGlobalModeDecl) {
                super.visitGlobalModeDecl(o)
                if (level(o) < LuaLanguageLevel.LUA55) {
                    register(
                        holder, o.firstChild,
                        "Global mode declarations are only available in Lua 5.5+",
                        UpgradeLanguageLevelFix(LuaLanguageLevel.LUA55)
                    )
                }
            }
        }

    private fun level(element: PsiElement): LuaLanguageLevel =
        LuaProjectSettings.getInstance(element.project).state.languageLevel

    private fun register(holder: ProblemsHolder, element: PsiElement, message: String, vararg fixes: com.intellij.codeInsight.intention.IntentionAction) {
        val quickFixes: Array<LocalQuickFix> = fixes.map { IntentionWrapper(it) }.toTypedArray()
        holder.registerProblem(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, *quickFixes)
    }

    private fun bitwiseOperatorName(operator: String): String =
        when (operator) {
            "&" -> "Bitwise AND operator (&)"
            "|" -> "Bitwise OR operator (|)"
            "~" -> "Bitwise NOT operator (~)"
            "<<" -> "Left shift operator (<<)"
            ">>" -> "Right shift operator (>>)"
            else -> "Bitwise operator"
        }

    companion object {
        // Note: ^ is power, not XOR — never matched.
        private val BITWISE_OPERATORS = setOf("&", "|", "~", "<<", ">>")
    }
}
