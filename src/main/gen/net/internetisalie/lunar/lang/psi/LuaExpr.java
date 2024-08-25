// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface LuaExpr extends PsiElement {

  @NotNull
  List<LuaBinOp> getBinOpList();

  @NotNull
  List<LuaExpr> getExprList();

  @Nullable
  LuaFuncDef getFuncDef();

  @Nullable
  LuaPrefixExpr getPrefixExpr();

  @Nullable
  LuaTableConstructor getTableConstructor();

  @Nullable
  LuaUnOp getUnOp();

  @Nullable
  PsiElement getNumber();

  @Nullable
  PsiElement getString();

}
