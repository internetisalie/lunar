package net.internetisalie.lunar.coverage.report

import com.intellij.openapi.fileTypes.LanguageFileType
import net.internetisalie.lunar.lang.LuaIcons
import javax.swing.Icon

object LuaCovReportFileType : LanguageFileType(LuaCovReportLanguage) {
    override fun getName(): String = "LuaCov Report"
    override fun getDescription(): String = "LuaCov code coverage report"
    override fun getDefaultExtension(): String = ""
    override fun getIcon(): Icon = LuaIcons.COVERAGE_REPORT
}
