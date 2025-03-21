package net.internetisalie.lunar.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import net.internetisalie.lunar.lang.LuaVarNameReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LuaVarNameRefBaseImpl extends LuaNameRefElementImpl {
    public LuaVarNameRefBaseImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @Nullable
    public PsiReference getReference() {
        String value = getName();
        if (value != null) {
            TextRange range = new TextRange(0, value.length());
            return new LuaVarNameReference(this, range);
        }
        return null;
    }
}
