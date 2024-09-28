// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.luacats.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import net.internetisalie.lunar.luacats.lang.psi.*;

public class LuaCatsDistinctTypeImpl extends ASTWrapperPsiElement implements LuaCatsDistinctType {

  public LuaCatsDistinctTypeImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaCatsVisitor visitor) {
    visitor.visitDistinctType(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaCatsVisitor) accept((LuaCatsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public LuaCatsBuiltinType getBuiltinType() {
    return findChildByClass(LuaCatsBuiltinType.class);
  }

  @Override
  @Nullable
  public LuaCatsDictionaryType getDictionaryType() {
    return findChildByClass(LuaCatsDictionaryType.class);
  }

  @Override
  @Nullable
  public LuaCatsFunctionSignatureType getFunctionSignatureType() {
    return findChildByClass(LuaCatsFunctionSignatureType.class);
  }

  @Override
  @Nullable
  public LuaCatsLiteralTableType getLiteralTableType() {
    return findChildByClass(LuaCatsLiteralTableType.class);
  }

  @Override
  @Nullable
  public LuaCatsNamedType getNamedType() {
    return findChildByClass(LuaCatsNamedType.class);
  }

  @Override
  @Nullable
  public LuaCatsParameterName getParameterName() {
    return findChildByClass(LuaCatsParameterName.class);
  }

  @Override
  @Nullable
  public LuaCatsParameterizedName getParameterizedName() {
    return findChildByClass(LuaCatsParameterizedName.class);
  }

  @Override
  @Nullable
  public LuaCatsTupleType getTupleType() {
    return findChildByClass(LuaCatsTupleType.class);
  }

}
