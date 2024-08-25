// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface LuaStatement extends PsiElement {

  @Nullable
  LuaBlock getBlock();

  @Nullable
  LuaExpr getExpr();

  @Nullable
  LuaExprList getExprList();

  @Nullable
  LuaFuncBody getFuncBody();

  @Nullable
  LuaFuncCall getFuncCall();

  @Nullable
  LuaFuncName getFuncName();

  @Nullable
  LuaLabel getLabel();

  @Nullable
  LuaNameList getNameList();

  @Nullable
  LuaVarList getVarList();

  @Nullable
  PsiElement getIdentifier();

}
