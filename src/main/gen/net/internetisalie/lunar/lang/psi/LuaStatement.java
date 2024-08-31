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
  LuaFuncCall getFuncCall();

  @Nullable
  LuaFuncDecl getFuncDecl();

  @Nullable
  LuaGotoStatement getGotoStatement();

  @Nullable
  LuaLabel getLabel();

  @Nullable
  LuaLocalFuncDecl getLocalFuncDecl();

  @Nullable
  LuaLocalVarDecl getLocalVarDecl();

  @Nullable
  LuaNameList getNameList();

  @Nullable
  LuaVarList getVarList();

  @Nullable
  PsiElement getIdentifier();

}
