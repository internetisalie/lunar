package net.internetisalie.lunar.lang.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

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
}
