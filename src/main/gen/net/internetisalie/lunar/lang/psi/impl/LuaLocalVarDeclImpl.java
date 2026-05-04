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
import net.internetisalie.lunar.lang.psi.stubs.LuaLocalVarStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.PsiComment;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment;

public class LuaLocalVarDeclImpl extends LuaStubbedStatementImpl<LuaLocalVarStub> implements LuaLocalVarDecl {

  public LuaLocalVarDeclImpl(@NotNull LuaLocalVarStub stub, @NotNull IStubElementType<?, ?> type) {
    super(stub, type);
  }

  public LuaLocalVarDeclImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitLocalVarDecl(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<LuaAttName> getAttNameList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaAttName.class);
  }

  @Override
  @Nullable
  public LuaExprList getExprList() {
    return findChildByClass(LuaExprList.class);
  }

  @Override
  public @Nullable PsiComment getComment() {
    return LuaPsiImplUtil.getComment(this);
  }

  @Override
  public @Nullable LuaCatsComment getCatsComment() {
    return LuaPsiImplUtil.getCatsComment(this);
  }

}
