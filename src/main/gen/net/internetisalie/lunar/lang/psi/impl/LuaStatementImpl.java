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
  public LuaAssignmentStatement getAssignmentStatement() {
    return findChildByClass(LuaAssignmentStatement.class);
  }

  @Override
  @Nullable
  public LuaDoStatement getDoStatement() {
    return findChildByClass(LuaDoStatement.class);
  }

  @Override
  @Nullable
  public LuaFuncCall getFuncCall() {
    return findChildByClass(LuaFuncCall.class);
  }

  @Override
  @Nullable
  public LuaFuncDecl getFuncDecl() {
    return findChildByClass(LuaFuncDecl.class);
  }

  @Override
  @Nullable
  public LuaGenericForStatement getGenericForStatement() {
    return findChildByClass(LuaGenericForStatement.class);
  }

  @Override
  @Nullable
  public LuaGotoStatement getGotoStatement() {
    return findChildByClass(LuaGotoStatement.class);
  }

  @Override
  @Nullable
  public LuaIfStatement getIfStatement() {
    return findChildByClass(LuaIfStatement.class);
  }

  @Override
  @Nullable
  public LuaLabel getLabel() {
    return findChildByClass(LuaLabel.class);
  }

  @Override
  @Nullable
  public LuaLocalFuncDecl getLocalFuncDecl() {
    return findChildByClass(LuaLocalFuncDecl.class);
  }

  @Override
  @Nullable
  public LuaLocalVarDecl getLocalVarDecl() {
    return findChildByClass(LuaLocalVarDecl.class);
  }

  @Override
  @Nullable
  public LuaNumericForStatement getNumericForStatement() {
    return findChildByClass(LuaNumericForStatement.class);
  }

  @Override
  @Nullable
  public LuaRepeatStatement getRepeatStatement() {
    return findChildByClass(LuaRepeatStatement.class);
  }

  @Override
  @Nullable
  public LuaWhileStatement getWhileStatement() {
    return findChildByClass(LuaWhileStatement.class);
  }

}
