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

public class LuaGenericForStatementImpl extends LuaStatementImpl implements LuaGenericForStatement {

  public LuaGenericForStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitGenericForStatement(this);
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
  public LuaExprList getExprList() {
    return findNotNullChildByClass(LuaExprList.class);
  }

  @Override
  @NotNull
  public LuaNameList getNameList() {
    return findNotNullChildByClass(LuaNameList.class);
  }

  @Override
  public @NotNull List<@NotNull LuaBlock> getBlockList() {
    return LuaPsiImplUtil.getBlockList(this);
  }

}
