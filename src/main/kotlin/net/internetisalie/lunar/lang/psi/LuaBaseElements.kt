package net.internetisalie.lunar.lang.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.ILazyParseableElementType
import net.internetisalie.lunar.lang.LuaLabelReference
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.LuaNameReference
import net.internetisalie.lunar.lang.psi.LuaVisitor
import net.internetisalie.lunar.luacats.lang.lexer.LuaCatsLexer
import net.internetisalie.lunar.luacats.lang.parser.LuaCatsParser
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner
import net.internetisalie.lunar.luacats.lang.psi.impl.LuaCatsLazyCommentImpl

open class LuaBaseElement(
    node: ASTNode,
) : ASTWrapperPsiElement(node) {
    override fun toString(): String = this.node.elementType.toString()

    override fun getReferences(): Array<PsiReference> {
        // Include this element's own reference (e.g. LuaNameReference from getReference()) alongside
        // contributed ones. The platform default does this; without it, findReferenceAt() —
        // and therefore Go to Declaration on locals — never sees the name reference, which lives
        // on the LuaNameRef composite rather than a registered contributor.
        val contributed =
            com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
                .getReferencesFromProviders(
                    this,
                )
        val own = getReference()
        return if (own == null) contributed else arrayOf(own) + contributed
    }
}

// Name Declaration

interface LuaNameDeclElement : PsiNameIdentifierOwner

abstract class LuaNameDeclElementImpl(
    node: ASTNode,
) : LuaBaseElement(node),
    LuaNameDeclElement {
    override fun getName(): String? = getNameIdentifier()?.text

    override fun getNameIdentifier(): PsiElement? = findChildByType<PsiElement?>(LuaElementTypes.IDENTIFIER)

    override fun setName(newName: String): PsiElement {
        val identifierNode = node.findChildByType(LuaElementTypes.IDENTIFIER)
        if (identifierNode != null) {
            val newIdentifier = LuaElementFactory.createIdentifier(project, newName)
            if (newIdentifier != null) {
                node.replaceChild(identifierNode, newIdentifier.node)
            }
        }
        return this
    }

    // A label is invisible outside its own function (REFACT-04-04, -11), so its use scope is
    // exactly that function rather than the platform's default module-wide scope. The gate is
    // unreachable today — `labelName` is the only grammar rule using this mixin (`lua.bnf:251-254`)
    // — but the scope is a property of Lua's label rule, not of `LuaNameDeclElement` in general; a
    // future rule sharing this mixin must not silently inherit it.
    override fun getUseScope(): SearchScope {
        if (this !is LuaLabelName) return super.getUseScope()
        val boundary = LuaLabelScopes.functionScopeOf(this) ?: return super.getUseScope()
        return LocalSearchScope(boundary)
    }
}

// Name Reference

interface LuaNameRefElement : PsiNamedElement

abstract class LuaNameRefElementImpl(
    node: ASTNode,
) : LuaBaseElement(node),
    LuaNameRefElement {
    override fun getName(): String? = findChildByType<PsiElement?>(LuaElementTypes.IDENTIFIER)?.text

    override fun setName(newName: String): PsiElement {
        val identifierNode = node.findChildByType(LuaElementTypes.IDENTIFIER)
        if (identifierNode != null) {
            val newIdentifier = LuaElementFactory.createIdentifier(project, newName)
            if (newIdentifier != null) {
                node.replaceChild(identifierNode, newIdentifier.node)
            }
        }
        return this
    }
}

/**
 * The `nameRef` mixin (`lua.bnf:169-172`), and the sole carrier of [PsiNameIdentifierOwner] for Lua names.
 *
 * The supertype is the one primitive the platform's in-place rename requires: both routes test it with
 * `instanceof` (`MemberInplaceRenameHandler.java:46`, `InplaceRefactoring.java:597`). REFACT-07 design §3.1.
 *
 * **Do not move it onto [LuaNameRefElement].** That interface is the `implements=` of both `nameRef`
 * (`lua.bnf:171`) and `labelRef` (`lua.bnf:249`), so hoisting it there would also make every `goto` target a
 * [PsiNameIdentifierOwner], widening the consumer audit for no gain. [LuaNameRefBaseImpl] is the `mixin=` of
 * `nameRef` alone (`lua.bnf:170`), so putting it here necessarily does not reach label references.
 *
 * **Do not move it into `lua.bnf` either.** Adding `implements="com.intellij.psi.PsiNameIdentifierOwner"` to
 * `lua.bnf:169` would put the supertype on the generated `LuaNameRef` interface and buy Kotlin-side smart
 * casts, at the price of a `src/main/gen` regeneration that nothing in this repo uses: Lunar's own call sites
 * test `LuaNameRef` and read `.identifier`, not `.nameIdentifier`. REFACT-07 design Alternative D.
 */
open class LuaNameRefBaseImpl(
    node: ASTNode,
) : LuaNameRefElementImpl(node),
    PsiNameIdentifierOwner {
    override fun getNameIdentifier(): PsiElement? = findChildByType<PsiElement?>(LuaElementTypes.IDENTIFIER)

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

open class LuaLabelRefBaseImpl(
    node: ASTNode,
) : LuaNameRefElementImpl(node) {
    override fun getReference(): PsiReference? {
        val value = name ?: return null
        val range = TextRange(0, value.length)
        return LuaLabelReference(this, range)
    }
}

// Comment Owner

interface LuaCommentOwner : LuaCatsCommentOwner {
    fun getComment(): PsiComment?
}

// Lazy Elements

object LuaLazyElementTypes {
    /**
     * LuaCats comment
     */
    var LUACATS_COMMENT: ILazyParseableElementType =
        object : ILazyParseableElementType("LAZY_COMMENT") {
            override fun getLanguage(): Language = LuaLanguage

            override fun parseContents(chameleon: ASTNode): ASTNode? {
                val parentElement = checkNotNull(chameleon.getTreeParent().getPsi())
                val project = parentElement.getProject()
                val parser: PsiParser = LuaCatsParser()
                val lexer: Lexer = LuaCatsLexer()

                val builder =
                    PsiBuilderFactory
                        .getInstance()
                        .createBuilder(project, chameleon, lexer, getLanguage(), chameleon.getText())
                val root = parser.parse(this, builder)
                return root.firstChildNode
            }

            override fun createNode(text: CharSequence?): ASTNode = LuaCatsLazyCommentImpl(text)
        }
}

abstract class LuaStatementImpl(
    node: ASTNode,
) : LuaBaseElement(node),
    LuaStatement {
    open fun accept(visitor: LuaVisitor) {
        visitor.visitStatement(this)
    }

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is LuaVisitor) {
            accept(visitor)
        } else {
            super.accept(visitor)
        }
    }
}

abstract class LuaStubbedStatementImpl<T : StubElement<*>> :
    StubBasedPsiElementBase<T>,
    LuaStatement {
    constructor(stub: T, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
    constructor(node: ASTNode) : super(node)

    override fun getElementType(): IStubElementType<out T, *> = getElementTypeImpl() as IStubElementType<out T, *>

    override fun toString(): String = elementType.toString()

    open fun accept(visitor: LuaVisitor) {
        visitor.visitStatement(this)
    }

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is LuaVisitor) {
            accept(visitor)
        } else {
            super.accept(visitor)
        }
    }
}

// Block Owner

interface LuaBlockParent : PsiElement {
    fun getBlockList(): List<LuaBlock>
}
