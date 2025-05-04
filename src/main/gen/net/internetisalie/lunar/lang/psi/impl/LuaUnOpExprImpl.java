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

public class LuaUnOpExprImpl extends LuaExprImpl implements LuaUnOpExpr {

  public LuaUnOpExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitUnOpExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public LuaExpr getExpr() {
    return findChildByClass(LuaExpr.class);
  }

  @Override
  @NotNull
  public LuaUnOp getUnOp() {
    return findNotNullChildByClass(LuaUnOp.class);
  }

  @Override
  @Nullable
  public LuaExpr getRight() {
    return getExpr();
  }

}
