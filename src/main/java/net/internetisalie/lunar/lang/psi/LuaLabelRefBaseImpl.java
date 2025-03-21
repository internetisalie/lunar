package net.internetisalie.lunar.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import net.internetisalie.lunar.lang.LuaLabelReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LuaLabelRefBaseImpl extends LuaNameRefElementImpl {
    public LuaLabelRefBaseImpl(@NotNull ASTNode node) {
        super(node);
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
