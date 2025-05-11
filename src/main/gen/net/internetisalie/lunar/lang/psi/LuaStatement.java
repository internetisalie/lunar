// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface LuaStatement extends PsiElement {

  @Nullable
  LuaAssignmentStatement getAssignmentStatement();

  @Nullable
  LuaDoStatement getDoStatement();

  @Nullable
  LuaEmptyStatement getEmptyStatement();

  @Nullable
  LuaFuncCall getFuncCall();

  @Nullable
  LuaFuncDecl getFuncDecl();

  @Nullable
  LuaGenericForStatement getGenericForStatement();

  @Nullable
  LuaGotoStatement getGotoStatement();

  @Nullable
  LuaIfStatement getIfStatement();

  @Nullable
  LuaLabel getLabel();

  @Nullable
  LuaLocalFuncDecl getLocalFuncDecl();

  @Nullable
  LuaLocalVarDecl getLocalVarDecl();

  @Nullable
  LuaNumericForStatement getNumericForStatement();

  @Nullable
  LuaRepeatStatement getRepeatStatement();

  @Nullable
  LuaWhileStatement getWhileStatement();

}
