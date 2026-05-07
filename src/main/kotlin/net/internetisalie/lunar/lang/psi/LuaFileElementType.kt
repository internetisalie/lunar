package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.psi.stubs.LuaFileStub
import net.internetisalie.lunar.lang.psi.stubs.impl.LuaFileStubImpl
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment

class LuaFileElementType : IStubFileElementType<LuaFileStub>("Lua", LuaLanguage) {
    override fun getStubVersion(): Int = 2

    override fun getBuilder(): StubBuilder {
        return object : com.intellij.psi.stubs.DefaultStubBuilder() {
            override fun createStubForFile(file: com.intellij.psi.PsiFile): StubElement<*> {
                if (file !is LuaFile) return super.createStubForFile(file)

                val exportedTypeString = extractExportedType(file)
                return LuaFileStubImpl(file, exportedTypeString)
            }
        }
    }

    private fun extractExportedType(file: LuaFile): String? {
        val rootReturns = PsiTreeUtil.findChildrenOfType(file, LuaFinalStatement::class.java).filter { fs ->
            fs.text.startsWith("return") && isAtRoot(fs)
        }

        if (rootReturns.isEmpty()) return null

        val types = mutableSetOf<String>()
        for (lastReturn in rootReturns) {
            val returnCats = getCatsComment(lastReturn)
            if (returnCats != null) {
                val typeStr = returnCats.getTypeTagList().firstOrNull()?.argType?.text
                if (typeStr != null) {
                    types.add(typeStr)
                    continue
                }
            }

            val exprList = lastReturn.exprList?.exprList
            if (exprList != null && exprList.isNotEmpty()) {
                val lastExpr = exprList.last()
                if (lastExpr is LuaNameRef) {
                    val name = lastExpr.text
                    var current: PsiElement? = lastReturn.prevSibling
                    while (current != null) {
                        if (current is LuaLocalVarDecl) {
                            val names = current.attNameList.map { it.nameRef.text }
                            if (name in names) {
                                val catsComment = getCatsComment(current)
                                if (catsComment != null) {
                                    val typeStr = catsComment.getTypeTagList().firstOrNull()?.argType?.text
                                        ?: catsComment.getClassTagList().firstOrNull()?.argType?.text
                                    if (typeStr != null) types.add(typeStr)
                                }
                            }
                        }
                        current = current.prevSibling
                    }
                } else if (lastExpr is LuaTableConstructor) {
                    val catsComment = getCatsComment(lastExpr)
                    if (catsComment != null) {
                        val typeStr = catsComment.getTypeTagList().firstOrNull()?.argType?.text
                        if (typeStr != null) types.add(typeStr)
                    }
                }
            }
        }

        return if (types.isEmpty()) null else types.joinToString(" | ")
    }

    private fun isAtRoot(element: PsiElement): Boolean {
        var current: PsiElement? = element.parent
        while (current != null && current !is LuaFile) {
            if (current is LuaFuncDef || current is LuaFuncDecl || current is LuaLocalFuncDecl) return false
            current = current.parent
        }
        return current is LuaFile
    }

    private fun getCatsComment(element: PsiElement): LuaCatsComment? {
        var current: PsiElement? = element.prevSibling
        while (current != null) {
            if (current is LuaCatsComment) return current
            if (current is com.intellij.psi.PsiWhiteSpace || current is com.intellij.psi.PsiComment) {
                // skip
            } else {
                break
            }
            current = current.prevSibling
        }
        return null
    }

    override fun serialize(stub: LuaFileStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.exportedTypeString)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): LuaFileStub {
        val exportedTypeString = dataStream.readName()?.string
        return LuaFileStubImpl(null, exportedTypeString)
    }

    override fun getExternalId(): String = "lunar.file"
}
