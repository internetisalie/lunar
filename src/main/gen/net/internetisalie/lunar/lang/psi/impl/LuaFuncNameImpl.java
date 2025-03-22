// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static net.internetisalie.lunar.lang.psi.LuaElementTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import net.internetisalie.lunar.lang.psi.*;

public class LuaFuncNameImpl extends ASTWrapperPsiElement implements LuaFuncName {

  public LuaFuncNameImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitFuncName(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public LuaFuncNameMethod getFuncNameMethod() {
    return findChildByClass(LuaFuncNameMethod.class);
  }

  @Override
  @NotNull
  public List<LuaFuncNameProperty> getFuncNamePropertyList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaFuncNameProperty.class);
  }

  @Override
  @NotNull
  public PsiElement getIdentifier() {
    return findNotNullChildByType(IDENTIFIER);
  }

}
