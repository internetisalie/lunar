// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import static net.internetisalie.lunar.lang.psi.LuaElementTypes.*;
import net.internetisalie.lunar.lang.psi.*;
import net.internetisalie.lunar.lang.psi.stubs.LuaFuncStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.PsiComment;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment;

public class LuaFuncDeclImpl extends LuaStubbedStatementImpl<LuaFuncStub> implements LuaFuncDecl {

  public LuaFuncDeclImpl(@NotNull LuaFuncStub stub, @NotNull IStubElementType<?, ?> type) {
    super(stub, type);
  }

  public LuaFuncDeclImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitFuncDecl(this);
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
  public LuaFuncName getFuncName() {
    return findNotNullChildByClass(LuaFuncName.class);
  }

  @Override
  @Nullable
  public LuaParList getParList() {
    return findChildByClass(LuaParList.class);
  }

  @Override
  public @Nullable PsiComment getComment() {
    return LuaPsiImplUtil.getComment(this);
  }

  @Override
  public @Nullable LuaCatsComment getCatsComment() {
    return LuaPsiImplUtil.getCatsComment(this);
  }

  @Override
  public @NotNull List<@NotNull LuaBlock> getBlockList() {
    return LuaPsiImplUtil.getBlockList(this);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, @Nullable PsiElement lastParent, @NotNull PsiElement place) {
    return net.internetisalie.lunar.lang.psi.LuaFunctionExtKt.processDeclarations(this, processor, state, lastParent, place);
  }

}
