// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface LuaGenericForStatement extends LuaStatement, LuaBlockParent {

  @NotNull
  LuaBlock getBlock();

  @NotNull
  LuaExprList getExprList();

  @NotNull
  LuaNameList getNameList();

  @NotNull List<@NotNull LuaBlock> getBlockList();

}
