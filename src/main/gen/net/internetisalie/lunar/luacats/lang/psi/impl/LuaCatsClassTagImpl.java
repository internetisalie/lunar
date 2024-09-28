// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.luacats.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import net.internetisalie.lunar.luacats.lang.psi.*;

public class LuaCatsClassTagImpl extends ASTWrapperPsiElement implements LuaCatsClassTag {

  public LuaCatsClassTagImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaCatsVisitor visitor) {
    visitor.visitClassTag(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaCatsVisitor) accept((LuaCatsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public LuaCatsArgKeyword getArgKeyword() {
    return findChildByClass(LuaCatsArgKeyword.class);
  }

  @Override
  @NotNull
  public LuaCatsArgType getArgType() {
    return findNotNullChildByClass(LuaCatsArgType.class);
  }

  @Override
  @Nullable
  public LuaCatsParentTypes getParentTypes() {
    return findChildByClass(LuaCatsParentTypes.class);
  }

}
