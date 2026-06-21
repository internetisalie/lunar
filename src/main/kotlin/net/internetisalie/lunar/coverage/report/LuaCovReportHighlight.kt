package net.internetisalie.lunar.coverage.report

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors

object LuaCovReportHighlight {
    val HEADER: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUACOV_HEADER", DefaultLanguageHighlighterColors.METADATA
    )
    val FILE_PATH: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUACOV_FILE_PATH", DefaultLanguageHighlighterColors.STRING
    )

    // Covered (green) and uncovered (red) foregrounds are supplied by the bundled
    // color schemes registered via <additionalTextAttributes> in plugin.xml
    // (resources/colorSchemes/LuaCovReport*.xml). The STRING fallback keeps covered
    // lines green even when no scheme override is present.
    val COVERED: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUACOV_COVERED", DefaultLanguageHighlighterColors.STRING
    )
    val UNCOVERED: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUACOV_UNCOVERED"
    )
}
