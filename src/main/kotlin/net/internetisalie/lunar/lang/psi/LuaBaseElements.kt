package net.internetisalie.lunar.lang.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.tree.ILazyParseableElementType
import net.internetisalie.lunar.lang.LuaLabelReference
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.LuaNameReference
import net.internetisalie.lunar.luacats.lang.lexer.LuaCatsLexer
import net.internetisalie.lunar.luacats.lang.parser.LuaCatsParser
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner
import net.internetisalie.lunar.luacats.lang.psi.impl.LuaCatsLazyCommentImpl
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocCommentOwner

open class LuaBaseElement(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun toString(): String {
        return this.node.elementType.toString()
    }
}

// Name Declaration

interface LuaNameDeclElement : PsiNamedElement

abstract class LuaNameDeclElementImpl(node: ASTNode) : LuaBaseElement(node), LuaNameDeclElement {
    override fun getName(): String? {
        return findChildByType<PsiElement?>(LuaElementTypes.IDENTIFIER)!!.getText()
    }

    override fun setName(newName: String): PsiElement {
        val identifierNode = node.findChildByType(LuaElementTypes.IDENTIFIER)
        if (identifierNode != null) {
            val newIdentifier = LuaElementFactory.createIdentifier(project, newName)
            node.replaceChild(identifierNode, newIdentifier.node)
        }
        return this
    }
}

// Name Reference

interface LuaNameRefElement : PsiNamedElement

abstract class LuaNameRefElementImpl(node: ASTNode) : LuaBaseElement(node), LuaNameRefElement {
    override fun getName(): String? {
        return findChildByType<PsiElement?>(LuaElementTypes.IDENTIFIER)!!.text
    }

    override fun setName(newName: String): PsiElement {
        val identifierNode = node.findChildByType(LuaElementTypes.IDENTIFIER)
        if (identifierNode != null) {
            val newIdentifier = LuaElementFactory.createIdentifier(project, newName)
            node.replaceChild(identifierNode, newIdentifier.node)
        }
        return this
    }
}

open class LuaNameRefBaseImpl(node: ASTNode) : LuaNameRefElementImpl(node) {
    override fun getReference(): PsiReference? {
        val value = getName()
        if (value != null) {
            val range = TextRange(0, value.length)
            return LuaNameReference(this, range)
        }
        return null
    }
}

// Label Reference

open class LuaLabelRefBaseImpl(node: ASTNode) : LuaNameRefElementImpl(node) {
    override fun getReference(): PsiReference? {
        val value = name ?: return null
        val range = TextRange(0, value.length)
        return LuaLabelReference(this, range)
    }
}

// Comment Owner

interface LuaCommentOwner : LuaCatsCommentOwner, LuaDocCommentOwner {
    fun getComment() : PsiComment?
}

// Lazy Elements

object LuaLazyElementTypes {
    /**
     * LuaCats comment
     */
    var LUACATS_COMMENT: ILazyParseableElementType = object : ILazyParseableElementType("LAZY_COMMENT") {
        override fun getLanguage(): Language {
            return LuaLanguage
        }

        override fun parseContents(chameleon: ASTNode): ASTNode? {
            val parentElement = checkNotNull(chameleon.getTreeParent().getPsi())
            val project = parentElement.getProject()
            val parser: PsiParser = LuaCatsParser()
            val lexer: Lexer = LuaCatsLexer()

            val builder = PsiBuilderFactory.getInstance()
                .createBuilder(project, chameleon, lexer, getLanguage(), chameleon.getText())
            return parser.parse(this, builder).getFirstChildNode()
        }

        override fun createNode(text: CharSequence?): ASTNode {
            return LuaCatsLazyCommentImpl(text)
        }
    }
}


// Block Owner

interface LuaBlockParent : PsiElement {
    fun getBlockList() : List<LuaBlock>
}
