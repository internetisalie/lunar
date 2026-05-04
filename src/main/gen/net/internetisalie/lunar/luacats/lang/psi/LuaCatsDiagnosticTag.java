// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.luacats.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface LuaCatsDiagnosticTag extends PsiElement {

  @NotNull
  LuaCatsArgKeyword getArgKeyword();

  @Nullable
  LuaCatsArgSymbol getArgSymbol();

  @Nullable
  LuaCatsDescription getDescription();

  @Nullable
  LuaCatsDiagnostics getDiagnostics();

}
