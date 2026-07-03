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

public class LuaWhileStatementImpl extends LuaStatementImpl implements LuaWhileStatement {

  public LuaWhileStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitWhileStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public LuaBlock getBlock() {
    return findNotNullChildByClass(LuaBlock.class);
  }

  @Override
  @NotNull
  public LuaExpr getExpr() {
    return findNotNullChildByClass(LuaExpr.class);
  }

  @Override
  @NotNull
  public List<LuaBlock> getBlockList() {
    return LuaPsiImplUtil.getBlockList(this);
  }

}
