package net.internetisalie.lunar.lang.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import net.internetisalie.lunar.lang.LuaLabelReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class LuaNameRefElementImpl extends ASTWrapperPsiElement implements LuaNameRefElement {
    public LuaNameRefElementImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public String getName() {
        return findChildByType(LuaElementTypes.IDENTIFIER).getText();
    }

    @Override
    public PsiElement setName(String newName) {
        ASTNode identifierNode = getNode().findChildByType(LuaElementTypes.IDENTIFIER);
        if (identifierNode != null) {
            var newIdentifier = LuaElementFactory.createIdentifier(getProject(), newName);
            getNode().replaceChild(identifierNode, newIdentifier.getNode());
        }
        return this;
    }

    @Override
    @Nullable
    public PsiReference getReference() {
        String value = getName();
        if (value != null) {
            TextRange range = new TextRange(0, value.length());
            return new LuaLabelReference(this, range);
        }
        return null;
    }
}
