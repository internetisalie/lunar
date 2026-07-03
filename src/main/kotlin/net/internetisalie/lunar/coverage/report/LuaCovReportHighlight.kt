package net.internetisalie.lunar.coverage.report

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Color

object LuaCovReportHighlight {
    private val COVERED_GREEN = JBColor(Color(0x59A869), Color(0x6A8759))
    private val UNCOVERED_RED = JBColor(Color(0xD32F2F), Color(0xFF6B68))

    val HEADER: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUACOV_HEADER", DefaultLanguageHighlighterColors.METADATA
    )
    val FILE_PATH: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUACOV_FILE_PATH", DefaultLanguageHighlighterColors.STRING
    )

    // Covered (green) and uncovered (red) foregrounds are pinned with explicit default
    // TextAttributes on the key itself so they render correctly under ANY active editor
    // scheme (including modern themes such as "Islands Dark" that do not inherit the
    // Default/Darcula <additionalTextAttributes> deltas registered in plugin.xml).
    // MAINT-03: createTextAttributesKey(String, TextAttributes) is deprecated but has no
    // non-deprecated overload taking raw TextAttributes (only (String) / (String, Key)); the
    // hard-coded colors above are intentional, so suppress rather than force-migrate.
    @Suppress("DEPRECATION")
    val COVERED: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUACOV_COVERED", TextAttributes().apply { foregroundColor = COVERED_GREEN }
    )

    @Suppress("DEPRECATION")
    val UNCOVERED: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUACOV_UNCOVERED", TextAttributes().apply { foregroundColor = UNCOVERED_RED }
    )
}
