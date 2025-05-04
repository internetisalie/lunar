// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface LuaBinOpExpr extends LuaExpr {

  @NotNull
  LuaBinOp getBinOp();

  @NotNull
  List<LuaExpr> getExprList();

  @NotNull
  LuaExpr getLeft();

  @Nullable
  LuaExpr getRight();

}
