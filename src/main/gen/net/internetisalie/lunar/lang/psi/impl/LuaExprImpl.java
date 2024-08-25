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

public class LuaExprImpl extends ASTWrapperPsiElement implements LuaExpr {

  public LuaExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<LuaBinOp> getBinOpList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaBinOp.class);
  }

  @Override
  @NotNull
  public List<LuaExpr> getExprList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaExpr.class);
  }

  @Override
  @Nullable
  public LuaFuncDef getFuncDef() {
    return findChildByClass(LuaFuncDef.class);
  }

  @Override
  @Nullable
  public LuaPrefixExpr getPrefixExpr() {
    return findChildByClass(LuaPrefixExpr.class);
  }

  @Override
  @Nullable
  public LuaTableConstructor getTableConstructor() {
    return findChildByClass(LuaTableConstructor.class);
  }

  @Override
  @Nullable
  public LuaUnOp getUnOp() {
    return findChildByClass(LuaUnOp.class);
  }

  @Override
  @Nullable
  public PsiElement getNumber() {
    return findChildByType(NUMBER);
  }

  @Override
  @Nullable
  public PsiElement getString() {
    return findChildByType(STRING);
  }

}
