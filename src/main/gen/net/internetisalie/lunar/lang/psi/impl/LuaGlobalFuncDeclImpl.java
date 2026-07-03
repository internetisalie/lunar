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
import com.intellij.psi.PsiComment;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment;

public class LuaGlobalFuncDeclImpl extends LuaStatementImpl implements LuaGlobalFuncDecl {

  public LuaGlobalFuncDeclImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitGlobalFuncDecl(this);
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
  public LuaNameRef getNameRef() {
    return findNotNullChildByClass(LuaNameRef.class);
  }

  @Override
  @Nullable
  public LuaParList getParList() {
    return findChildByClass(LuaParList.class);
  }

  @Override
  @Nullable
  public PsiComment getComment() {
    return LuaPsiImplUtil.getComment(this);
  }

  @Override
  @Nullable
  public LuaCatsComment getCatsComment() {
    return LuaPsiImplUtil.getCatsComment(this);
  }

  @Override
  @NotNull
  public List<LuaBlock> getBlockList() {
    return LuaPsiImplUtil.getBlockList(this);
  }

}
