// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.luacats.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface LuaCatsFunctionSignatureType extends PsiElement {

  @NotNull
  List<LuaCatsFunctionSignatureArgument> getFunctionSignatureArgumentList();

  @Nullable
  LuaCatsFunctionSignatureReturnType getFunctionSignatureReturnType();

}
