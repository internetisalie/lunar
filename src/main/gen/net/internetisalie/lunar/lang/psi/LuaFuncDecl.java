// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiComment;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment;

import com.intellij.psi.StubBasedPsiElement;
import net.internetisalie.lunar.lang.psi.stubs.LuaFuncStub;

public interface LuaFuncDecl extends LuaStatement, LuaCommentOwner, LuaBlockParent, StubBasedPsiElement<LuaFuncStub> {

  @NotNull
  LuaBlock getBlock();

  @NotNull
  LuaFuncName getFuncName();

  @Nullable
  LuaParList getParList();

  @Nullable PsiComment getComment();

  //WARNING: getDocComment(...) is skipped
  //matching getDocComment(LuaFuncDecl, ...)
  //methods are not found in LuaPsiImplUtil

  @Nullable LuaCatsComment getCatsComment();

  @NotNull List<@NotNull LuaBlock> getBlockList();

  boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, @Nullable PsiElement lastParent, @NotNull PsiElement place);

}
