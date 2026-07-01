package net.internetisalie.lunar.analysis.luacheck

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection

class LuaCheckInspection :
    LocalInspectionTool(),
    ExternalAnnotatorBatchInspection {
    override fun getShortName(): String = SHORT_NAME

    companion object {
        const val SHORT_NAME: String = "LuaCheck"
    }
}
