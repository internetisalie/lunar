// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.luacats.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface LuaCatsDistinctType extends PsiElement {

  @Nullable
  LuaCatsBuiltinType getBuiltinType();

  @Nullable
  LuaCatsDictionaryType getDictionaryType();

  @Nullable
  LuaCatsFunctionSignatureType getFunctionSignatureType();

  @Nullable
  LuaCatsLiteralTableType getLiteralTableType();

  @Nullable
  LuaCatsNamedType getNamedType();

  @Nullable
  LuaCatsParameterName getParameterName();

  @Nullable
  LuaCatsParameterizedName getParameterizedName();

  @Nullable
  LuaCatsTupleType getTupleType();

}
