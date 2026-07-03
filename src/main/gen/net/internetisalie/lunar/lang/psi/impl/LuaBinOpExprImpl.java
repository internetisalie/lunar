// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static net.internetisalie.lunar.lang.psi.LuaElementTypes.*;
import net.internetisalie.lunar.lang.psi.*;

public class LuaBinOpExprImpl extends LuaExprImpl implements LuaBinOpExpr {

  public LuaBinOpExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitBinOpExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public LuaBinOp getBinOp() {
    return findNotNullChildByClass(LuaBinOp.class);
  }

  @Override
  @NotNull
  public List<LuaExpr> getExprList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaExpr.class);
  }

  @Override
  @NotNull
  public LuaExpr getLeft() {
    List<LuaExpr> p1 = getExprList();
    return p1.get(0);
  }

  @Override
  @Nullable
  public LuaExpr getRight() {
    List<LuaExpr> p1 = getExprList();
    return p1.size() < 2 ? null : p1.get(1);
  }

}
