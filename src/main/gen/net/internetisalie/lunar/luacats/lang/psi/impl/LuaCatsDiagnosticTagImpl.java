// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.luacats.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes.*;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsBaseElement;
import net.internetisalie.lunar.luacats.lang.psi.*;

public class LuaCatsDiagnosticTagImpl extends LuaCatsBaseElement implements LuaCatsDiagnosticTag {

  public LuaCatsDiagnosticTagImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaCatsVisitor visitor) {
    visitor.visitDiagnosticTag(this);
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
  @Nullable
  public LuaCatsArgSymbol getArgSymbol() {
    return findChildByClass(LuaCatsArgSymbol.class);
  }

  @Override
  @Nullable
  public LuaCatsDescription getDescription() {
    return findChildByClass(LuaCatsDescription.class);
  }

  @Override
  @Nullable
  public LuaCatsDiagnostics getDiagnostics() {
    return findChildByClass(LuaCatsDiagnostics.class);
  }

}
