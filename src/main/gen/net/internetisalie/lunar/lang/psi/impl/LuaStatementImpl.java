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

public class LuaStatementImpl extends ASTWrapperPsiElement implements LuaStatement {

  public LuaStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public LuaBlock getBlock() {
    return findChildByClass(LuaBlock.class);
  }

  @Override
  @Nullable
  public LuaExpr getExpr() {
    return findChildByClass(LuaExpr.class);
  }

  @Override
  @Nullable
  public LuaExprList getExprList() {
    return findChildByClass(LuaExprList.class);
  }

  @Override
  @Nullable
  public LuaFuncBody getFuncBody() {
    return findChildByClass(LuaFuncBody.class);
  }

  @Override
  @Nullable
  public LuaFuncCall getFuncCall() {
    return findChildByClass(LuaFuncCall.class);
  }

  @Override
  @Nullable
  public LuaFuncName getFuncName() {
    return findChildByClass(LuaFuncName.class);
  }

  @Override
  @Nullable
  public LuaLabel getLabel() {
    return findChildByClass(LuaLabel.class);
  }

  @Override
  @Nullable
  public LuaNameList getNameList() {
    return findChildByClass(LuaNameList.class);
  }

  @Override
  @Nullable
  public LuaVarList getVarList() {
    return findChildByClass(LuaVarList.class);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

}
