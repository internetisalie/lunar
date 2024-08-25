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

public class LuaFuncCallImpl extends ASTWrapperPsiElement implements LuaFuncCall {

  public LuaFuncCallImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitFuncCall(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<LuaNameAndArgs> getNameAndArgsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaNameAndArgs.class);
  }

  @Override
  @NotNull
  public LuaVarOrExp getVarOrExp() {
    return findNotNullChildByClass(LuaVarOrExp.class);
  }

}
