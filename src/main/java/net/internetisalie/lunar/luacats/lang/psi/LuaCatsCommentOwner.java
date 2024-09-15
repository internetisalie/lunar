package net.internetisalie.lunar.luacats.lang.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface LuaCatsCommentOwner extends PsiElement {
    @Nullable
    LuaCatsComment getCatsComment();
}
