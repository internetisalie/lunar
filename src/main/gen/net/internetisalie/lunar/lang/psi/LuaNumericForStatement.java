// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface LuaNumericForStatement extends LuaBlockParent {

  @NotNull
  LuaBlock getBlock();

  @NotNull
  List<LuaExpr> getExprList();

  @NotNull
  PsiElement getIdentifier();

  @NotNull List<@NotNull LuaBlock> getBlockList();

}
