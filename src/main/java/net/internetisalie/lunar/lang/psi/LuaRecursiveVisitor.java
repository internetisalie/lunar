package net.internetisalie.lunar.lang.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class LuaRecursiveVisitor extends LuaVisitor {
    @Override
    public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        element.acceptChildren(this);
    }
}
