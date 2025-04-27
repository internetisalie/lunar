// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static net.internetisalie.lunar.lang.psi.LuaElementTypes.*;
import net.internetisalie.lunar.lang.psi.LuaBaseElement;
import net.internetisalie.lunar.lang.psi.*;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment;
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocComment;

public class LuaLocalFuncDeclImpl extends LuaBaseElement implements LuaLocalFuncDecl {

  public LuaLocalFuncDeclImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitLocalFuncDecl(this);
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
  public @NotNull LuaComment getComment() {
    return LuaPsiImplUtil.getComment(this);
  }

  @Override
  public @Nullable LuaDocComment getDocComment() {
    return LuaPsiImplUtil.getDocComment(this);
  }

  @Override
  public @Nullable LuaCatsComment getCatsComment() {
    return LuaPsiImplUtil.getCatsComment(this);
  }

}
