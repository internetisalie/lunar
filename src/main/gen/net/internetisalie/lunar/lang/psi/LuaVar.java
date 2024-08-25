// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface LuaVar extends PsiElement {

  @Nullable
  LuaExpr getExpr();

  @NotNull
  List<LuaVarSuffix> getVarSuffixList();

  @Nullable
  PsiElement getIdentifier();

}
