package net.internetisalie.lunar.lang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.run.LuaCodeFragment

object LuaElementFactory {
    fun createIdentifier(project: Project, name: String?): PsiElement? {
        val luaLabelRef = createLabelRef(project, name)
        return luaLabelRef.identifier ?: luaLabelRef.firstChild
    }

    fun createLabelRef(project: Project, name: String?): LuaLabelRef {
        val luaGotoStatement = createGotoStatement(project, name)
        return luaGotoStatement.getLabelRef()
    }

    fun createGotoStatement(project: Project, name: String?): LuaGotoStatement {
        val luaFile = createFile(project, "goto " + name)
        return PsiTreeUtil.findChildOfType(luaFile, LuaGotoStatement::class.java)!!
    }

    fun createLabel(project: Project, name: String?): LuaLabel? {
        val luaFile = createFile(project, "%%" + name + "%%")
        return PsiTreeUtil.findChildOfType(luaFile, LuaLabel::class.java)
    }

    fun createExpression(project: Project, value : String) : LuaExpr? {
        val luaFile = createFile(project, "local _ = $value")
        return PsiTreeUtil.findChildOfType(luaFile, LuaExpr::class.java)
    }

    fun createNewLine(project: Project): PsiElement {
        return PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n")
    }

    fun createFile(project: Project, text: String): LuaFile {
        val name = "dummy.lua"
        return PsiFileFactory.getInstance(project).createFileFromText(name, LuaFileType, text) as LuaFile
    }

    fun createExpressionCodeFragment(project : Project, text: String, context : PsiElement?, isPhysical : Boolean) : LuaCodeFragment {
        return LuaCodeFragment(
            project,
            LuaExpressionFragmentElementType(),
            isPhysical,
            "fragment.lua",
            text,
            context,
        )
    }
}