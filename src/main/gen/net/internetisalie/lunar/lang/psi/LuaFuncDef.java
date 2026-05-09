// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;

public interface LuaFuncDef extends LuaExpr, LuaBlockParent {

  @NotNull
  LuaBlock getBlock();

  @Nullable
  LuaParList getParList();

  @NotNull List<@NotNull LuaBlock> getBlockList();

  boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, @Nullable PsiElement lastParent, @NotNull PsiElement place);

}
