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

public class LuaCatsOverloadFunctionSignatureImpl extends ASTWrapperPsiElement implements LuaCatsOverloadFunctionSignature {

  public LuaCatsOverloadFunctionSignatureImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaCatsVisitor visitor) {
    visitor.visitOverloadFunctionSignature(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaCatsVisitor) accept((LuaCatsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public LuaCatsArgKeyword getArgKeyword() {
    return findNotNullChildByClass(LuaCatsArgKeyword.class);
  }

  @Override
  @NotNull
  public List<LuaCatsArgSymbol> getArgSymbolList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsArgSymbol.class);
  }

  @Override
  @NotNull
  public List<LuaCatsFunctionSignatureArgument> getFunctionSignatureArgumentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsFunctionSignatureArgument.class);
  }

  @Override
  @Nullable
  public LuaCatsFunctionSignatureReturnType getFunctionSignatureReturnType() {
    return findChildByClass(LuaCatsFunctionSignatureReturnType.class);
  }

}
