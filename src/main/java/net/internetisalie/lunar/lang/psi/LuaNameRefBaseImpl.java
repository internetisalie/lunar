package net.internetisalie.lunar.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import net.internetisalie.lunar.lang.LuaNameReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LuaNameRefBaseImpl extends LuaNameRefElementImpl {
    public LuaNameRefBaseImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @Nullable
    public PsiReference getReference() {
        String value = getName();
        if (value != null) {
            TextRange range = new TextRange(0, value.length());
            return new LuaNameReference(this, range);
        }
        return null;
    }
}
