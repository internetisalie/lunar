package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Semantic annotator that validates Lua code against the configured language level.
 *
 * Flags version-specific syntax (goto, bitwise operators, integer division, attributes)
 * used in projects configured for earlier Lua versions.
 *
 * Does not modify the parser; all Lua 5.1-5.4 syntax is valid at parse time.
 * This annotator enforces the runtime language level setting.
 */
class LuaLanguageLevelAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val project = element.project
        val languageLevel = getLuaLanguageLevel(project)

        // Check all applicable language level constraints
        if (languageLevel < LuaLanguageLevel.LUA52) {
            checkLua52Features(element, holder, languageLevel)
        }
        if (languageLevel < LuaLanguageLevel.LUA53) {
            checkLua53Features(element, holder, languageLevel)
        }
        if (languageLevel < LuaLanguageLevel.LUA54) {
            checkLua54Features(element, holder, languageLevel)
        }
    }

    /**
     * Check for Lua 5.2+ features (goto/label) when project is configured for Lua 5.1.
     */
    private fun checkLua52Features(element: PsiElement, holder: AnnotationHolder, languageLevel: LuaLanguageLevel) {
        when (element) {
            is LuaGotoStatement -> {
                val message = "Goto statements are a Lua 5.2+ feature (project configured for $languageLevel)"
                val annotation = holder.newAnnotation(HighlightSeverity.ERROR, message)
                    .range(element)
                    .withFix(UpgradeLanguageLevelFix(LuaLanguageLevel.LUA52))
                    .withFix(RemoveGotoFix())
                annotation.create()
            }
            is LuaLabel -> {
                val message = "Labels are a Lua 5.2+ feature (project configured for $languageLevel)"
                val annotation = holder.newAnnotation(HighlightSeverity.ERROR, message)
                    .range(element)
                    .withFix(UpgradeLanguageLevelFix(LuaLanguageLevel.LUA52))
                    .withFix(RemoveLabelFix())
                annotation.create()
            }
        }
    }

    /**
     * Check for Lua 5.3+ features (bitwise operators, integer division).
     */
    private fun checkLua53Features(element: PsiElement, holder: AnnotationHolder, languageLevel: LuaLanguageLevel) {
        when (element) {
            is LuaBinOp -> {
                val operator = element.text
                when {
                    operator == "//" -> {
                        val message = "Integer division (//) is a Lua 5.3+ feature (project configured for $languageLevel)"
                        val annotation = holder.newAnnotation(HighlightSeverity.ERROR, message)
                            .range(element)
                            .withFix(UpgradeLanguageLevelFix(LuaLanguageLevel.LUA53))
                            .withFix(ReplaceIntegerDivisionFix())
                        annotation.create()
                    }
                    isBitwiseOperator(operator) -> {
                        val featureName = getBitwiseOperatorName(operator)
                        val message = "$featureName is a Lua 5.3+ feature (project configured for $languageLevel)"
                        val annotation = holder.newAnnotation(HighlightSeverity.ERROR, message)
                            .range(element)
                            .withFix(UpgradeLanguageLevelFix(LuaLanguageLevel.LUA53))
                        annotation.create()
                    }
                }
            }
            is LuaUnOp -> {
                val operator = element.text
                if (operator == "~") {
                    val message = "Bitwise NOT operator (~) is a Lua 5.3+ feature (project configured for $languageLevel)"
                    val annotation = holder.newAnnotation(HighlightSeverity.ERROR, message)
                        .range(element)
                        .withFix(UpgradeLanguageLevelFix(LuaLanguageLevel.LUA53))
                    annotation.create()
                }
            }
        }
    }

    /**
     * Check for Lua 5.4+ features (attributes).
     */
    private fun checkLua54Features(element: PsiElement, holder: AnnotationHolder, languageLevel: LuaLanguageLevel) {
        if (element is LuaAttrib) {
            val message = "Variable attributes are a Lua 5.4 feature (project configured for $languageLevel)"
            val annotation = holder.newAnnotation(HighlightSeverity.ERROR, message)
                .range(element)
                .withFix(UpgradeLanguageLevelFix(LuaLanguageLevel.LUA54))
            annotation.create()
        }
    }

    /**
     * Get the project's configured Lua language level.
     */
    private fun getLuaLanguageLevel(project: Project): LuaLanguageLevel {
        val settings = LuaProjectSettings.getInstance(project)
        return settings.state.languageLevel
    }

    /**
     * Check if an operator is a bitwise operator.
     */
    private fun isBitwiseOperator(operator: String): Boolean =
        operator in setOf("&", "|", "~", "<<", ">>")  // Note: ^ is power, not XOR

    /**
     * Get a human-readable name for a bitwise operator.
     */
    private fun getBitwiseOperatorName(operator: String): String =
        when (operator) {
            "&" -> "Bitwise AND operator (&)"
            "|" -> "Bitwise OR operator (|)"
            "~" -> "Bitwise NOT operator (~)"
            "<<" -> "Left shift operator (<<)"
            ">>" -> "Right shift operator (>>)"
            else -> "Bitwise operator"
        }
}
